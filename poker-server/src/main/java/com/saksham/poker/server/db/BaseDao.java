package com.saksham.poker.server.db;

import com.saksham.poker.common.exception.PersistenceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Shared plumbing for the data-access classes: borrowing a connection, binding parameters, reading
 * rows, and turning {@link SQLException} into {@link PersistenceException}. Each subclass says how
 * one row of its table becomes an object.
 *
 * @param <T> what one row is read as
 */
public abstract class BaseDao<T> {

    /** SQLSTATE for "duplicate key value violates unique constraint". */
    private static final String UNIQUE_VIOLATION = "23505";

    protected final DataSource dataSource;

    protected BaseDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Reads the current row of a result into an object. */
    protected abstract T mapRow(ResultSet row) throws SQLException;

    /** Work done on one connection. */
    @FunctionalInterface
    protected interface SqlWork<R> {
        R run(Connection connection) throws SQLException;
    }

    /** Runs a query expected to return at most one row. */
    protected Optional<T> findOne(String sql, Object... params) {
        List<T> rows = findAll(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Runs a query and reads every row. */
    protected List<T> findAll(String sql, Object... params) {
        return query(sql, this::mapRow, params);
    }

    /** Reads one row of a result as something other than {@code T}, for queries that join or total. */
    @FunctionalInterface
    protected interface RowMapper<R> {
        R map(ResultSet row) throws SQLException;
    }

    /** Runs a query and reads every row with the given mapper. */
    protected <R> List<R> query(String sql, RowMapper<R> mapper, Object... params) {
        return withConnection(connection -> query(connection, sql, mapper, params));
    }

    /** As {@link #query(String, RowMapper, Object...)}, on a connection the caller already holds. */
    protected static <R> List<R> query(Connection connection, String sql, RowMapper<R> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rows = statement.executeQuery()) {
                List<R> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapper.map(rows));
                }
                return result;
            }
        }
    }

    /**
     * Runs an insert, update or delete.
     *
     * @return how many rows changed
     */
    protected int update(String sql, Object... params) {
        return withConnection(connection -> update(connection, sql, params));
    }

    /** As {@link #update(String, Object...)}, on a connection the caller already holds. */
    protected static int update(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        }
    }

    /** Borrows a connection for the work and returns it to the pool afterwards. */
    protected <R> R withConnection(SqlWork<R> work) {
        try (Connection connection = dataSource.getConnection()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new PersistenceException("A database operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs the work as one transaction: everything in it is saved together, or, if it throws,
     * nothing is.
     */
    protected <R> R inTransaction(SqlWork<R> work) {
        return withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                R result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    /** True if the error is a unique index refusing a duplicate value. */
    protected static boolean isDuplicate(SQLException e) {
        return UNIQUE_VIOLATION.equals(e.getSQLState());
    }

    /** Reads a timestamp column, or null if it is empty. */
    protected static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value instanceof Instant instant) {
                value = instant.atOffset(ZoneOffset.UTC);
            }
            statement.setObject(i + 1, value);
        }
    }
}
