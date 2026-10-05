package com.saksham.poker.server.db;

import com.saksham.poker.server.io.ServerConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import javax.sql.DataSource;

/**
 * A private, empty copy of the schema for one test class, in the Docker PostgreSQL
 * ({@code docker compose up -d}). Each instance works in its own schema, which is dropped on close,
 * so database tests never see each other's rows or the real data.
 */
final class TestDatabase implements AutoCloseable {

    private static final String URL =
            System.getProperty("holdem.test.db.url", "jdbc:postgresql://localhost:5433/holdem");
    private static final String USER = System.getProperty("holdem.test.db.user", "holdem");
    private static final String PASSWORD = System.getProperty("holdem.test.db.password", "holdem");

    private final String schema = "test_" + Long.toHexString(System.nanoTime());
    private final DataSourceProvider provider;

    TestDatabase() throws SQLException {
        run("CREATE SCHEMA " + schema);
        Properties properties = new Properties();
        properties.setProperty("db.url", URL + "?currentSchema=" + schema);
        properties.setProperty("db.user", USER);
        properties.setProperty("db.password", PASSWORD);
        properties.setProperty("db.pool.size", "2");
        provider = new DataSourceProvider(new ServerConfig(properties));
    }

    DataSource dataSource() {
        return provider.dataSource();
    }

    @Override
    public void close() throws SQLException {
        provider.close();
        run("DROP SCHEMA " + schema + " CASCADE");
    }

    private static void run(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
