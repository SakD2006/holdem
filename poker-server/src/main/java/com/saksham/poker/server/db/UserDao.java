package com.saksham.poker.server.db;

import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.common.exception.UsernameTakenException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/** Reads and writes player accounts. */
public class UserDao extends BaseDao<User> {

    private static final String COLUMNS = "id, username, password_hash, created_at, last_login_at";

    public UserDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected User mapRow(ResultSet row) throws SQLException {
        return new User(row.getLong("id"), row.getString("username"), row.getString("password_hash"),
                instant(row, "created_at"), instant(row, "last_login_at"));
    }

    /**
     * Adds an account.
     *
     * @throws UsernameTakenException if another account has this username, ignoring upper and lower case
     */
    public User create(String username, String passwordHash) throws UsernameTakenException {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?) RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return mapRow(row);
            }
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                throw new UsernameTakenException(
                        "The username \"" + username + "\" is already taken. Choose another one.");
            }
            throw new PersistenceException("Could not save the new account: " + e.getMessage(), e);
        }
    }

    /** Finds an account by username, ignoring upper and lower case. */
    public Optional<User> findByUsername(String username) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE LOWER(username) = LOWER(?)", username);
    }

    public Optional<User> findById(long id) {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE id = ?", id);
    }
}
