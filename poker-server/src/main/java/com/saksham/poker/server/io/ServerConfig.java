package com.saksham.poker.server.io;

import com.saksham.poker.common.exception.StorageException;
import com.saksham.poker.server.room.RoomTimings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's settings, read once at startup from {@code server.properties}. Any setting left out
 * of the file keeps its default, and the defaults match {@code docker-compose.yml}.
 */
public final class ServerConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerConfig.class);

    /** System property naming the settings file; the server's start command sets it. */
    public static final String FILE_PROPERTY = "holdem.config";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int dbPoolSize;
    private final int httpPort;
    private final int discoveryPort;
    private final Duration tokenLifetime;
    private final RoomTimings roomTimings;

    /** Builds the settings from properties, using the default for anything missing. */
    public ServerConfig(Properties properties) {
        this.dbUrl = properties.getProperty("db.url", "jdbc:postgresql://localhost:5433/holdem").trim();
        this.dbUser = properties.getProperty("db.user", "holdem").trim();
        this.dbPassword = properties.getProperty("db.password", "holdem");
        this.dbPoolSize = number(properties, "db.pool.size", 5, 1, 50);
        this.httpPort = number(properties, "http.port", 8080, 1, 65535);
        this.discoveryPort = number(properties, "discovery.port", 8888, 1, 65535);
        this.tokenLifetime = Duration.ofDays(number(properties, "token.days", 7, 1, 365));
        this.roomTimings = new RoomTimings(
                number(properties, "hand.delay.ms", 3_000, 0, 60_000),
                number(properties, "runout.pause.ms", 1_000, 0, 10_000),
                number(properties, "reconnect.grace.seconds", 60, 0, 3_600) * 1_000L,
                number(properties, "room.idle.minutes", 30, 1, 24 * 60) * 60_000L);
    }

    /**
     * Reads the file named by the {@value #FILE_PROPERTY} system property, or {@code server.properties}
     * in the working directory. A missing file is not an error: the defaults are used.
     *
     * @throws StorageException if the file exists but cannot be read
     */
    public static ServerConfig load() {
        return load(Path.of(System.getProperty(FILE_PROPERTY, "server.properties")));
    }

    /** As {@link #load()}, for a given file. */
    public static ServerConfig load(Path file) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            log.info("No settings file at {}; using the default settings", file.toAbsolutePath());
            return new ServerConfig(properties);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException e) {
            throw new StorageException("Could not read the settings file " + file.toAbsolutePath()
                    + ". Check that it is a readable properties file.", e);
        }
        log.info("Settings read from {}", file.toAbsolutePath());
        return new ServerConfig(properties);
    }

    private static int number(Properties properties, String key, int fallback, int min, int max) {
        String text = properties.getProperty(key);
        if (text == null || text.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The setting " + key + " must be a whole number, but was \"" + text + "\".", e);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    "The setting " + key + " must be from " + min + " to " + max + ", but was " + value + ".");
        }
        return value;
    }

    public String dbUrl() {
        return dbUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    /** The most database connections kept open at once. */
    public int dbPoolSize() {
        return dbPoolSize;
    }

    /** The port Tomcat listens on, used to tell players where to connect. */
    public int httpPort() {
        return httpPort;
    }

    /** The UDP port for "Find server" on the LAN. */
    public int discoveryPort() {
        return discoveryPort;
    }

    /** How long a login lasts. */
    public Duration tokenLifetime() {
        return tokenLifetime;
    }

    /** The waits rooms use between hands, during run-outs and after a disconnect. */
    public RoomTimings roomTimings() {
        return roomTimings;
    }
}
