package com.saksham.poker.server.db;

import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.common.exception.StorageException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Brings the database schema up to date at startup. Migrations are SQL files on the classpath under
 * {@code db/}, listed in {@code db/migrations.txt}; each is applied once, in its own transaction, and
 * recorded in the {@code schema_version} table.
 *
 * <p>Statements are split on semicolons, so a migration must not contain one inside a string or a
 * function body.
 */
public final class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);
    private static final String FOLDER = "db/";
    private static final Pattern FILE_NAME = Pattern.compile("V(\\d+)__.+\\.sql");

    private final DataSource dataSource;

    public MigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Applies every migration that has not been applied yet.
     *
     * @return how many were applied
     * @throws PersistenceException if a migration fails; that migration is rolled back
     */
    public int migrate() {
        List<String> files = listedFiles();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                        + "version INT PRIMARY KEY, name TEXT NOT NULL, "
                        + "applied_at TIMESTAMPTZ NOT NULL DEFAULT now())");
            }
            Set<Integer> applied = appliedVersions(connection);
            int count = 0;
            for (String file : files) {
                int version = versionOf(file);
                if (!applied.contains(version)) {
                    apply(connection, version, file);
                    count++;
                }
            }
            return count;
        } catch (SQLException e) {
            throw new PersistenceException("Could not update the database schema.", e);
        }
    }

    private void apply(Connection connection, int version, String file) throws SQLException {
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements(read(FOLDER + file))) {
                    statement.execute(sql);
                }
            }
            try (PreparedStatement record = connection.prepareStatement(
                    "INSERT INTO schema_version (version, name) VALUES (?, ?)")) {
                record.setInt(1, version);
                record.setString(2, file);
                record.executeUpdate();
            }
            connection.commit();
            log.info("Applied database migration {}", file);
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw new PersistenceException("Database migration " + file + " failed and was rolled back.", e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Set<Integer> appliedVersions(Connection connection) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT version FROM schema_version")) {
            while (rows.next()) {
                versions.add(rows.getInt(1));
            }
        }
        return versions;
    }

    /** The migration file names from the list, in order. */
    private static List<String> listedFiles() {
        List<String> files = new ArrayList<>();
        for (String line : read(FOLDER + "migrations.txt").split("\n")) {
            String name = line.trim();
            if (!name.isEmpty() && !name.startsWith("#")) {
                versionOf(name);
                files.add(name);
            }
        }
        return files;
    }

    static int versionOf(String file) {
        Matcher matcher = FILE_NAME.matcher(file);
        if (!matcher.matches()) {
            throw new StorageException("The migration \"" + file
                    + "\" is not named like V1__what_it_does.sql. Rename it or fix db/migrations.txt.");
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** Splits a script into statements, dropping comment lines and blanks. */
    static List<String> statements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\n")) {
            if (!line.trim().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.trim());
            }
        }
        return statements;
    }

    private static String read(String resource) {
        ClassLoader loader = MigrationRunner.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new StorageException("The server is missing its file " + resource + ". Rebuild the server.");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
                return text.toString();
            }
        } catch (IOException e) {
            throw new StorageException("Could not read the server's file " + resource + ".", e);
        }
    }
}
