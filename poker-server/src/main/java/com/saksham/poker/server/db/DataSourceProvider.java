package com.saksham.poker.server.db;

import com.saksham.poker.server.io.ServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/** Owns the pool of database connections for the whole server. */
public final class DataSourceProvider implements AutoCloseable {

    private final HikariDataSource dataSource;

    /**
     * Opens the pool.
     *
     * @throws RuntimeException if the database cannot be reached
     */
    public DataSourceProvider(ServerConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("holdem-db");
        // Named explicitly: inside Tomcat the driver in the WAR is not found automatically.
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.dbPoolSize());
        hikari.setConnectionTimeout(5_000);
        this.dataSource = new HikariDataSource(hikari);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
