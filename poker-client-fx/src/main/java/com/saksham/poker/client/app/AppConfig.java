package com.saksham.poker.client.app;

import com.saksham.poker.common.exception.StorageException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The player's preferences, kept in {@code client.properties} between runs: the last server they
 * used, the last username, and whether sound is on.
 */
public final class AppConfig {

    private static final String FILE_NAME = "client.properties";
    private static final String LAST_SERVER = "last.server";
    private static final String LAST_USERNAME = "last.username";
    private static final String SOUND = "sound";
    private static final String CARD_BACK = "card.back";
    private static final String FOUR_COLOUR = "deck.four.colour";

    private final Path file;
    private final Properties properties = new Properties();

    private AppConfig(Path file) {
        this.file = file;
    }

    /**
     * Reads the preferences from a folder. A missing file gives the defaults.
     *
     * @throws StorageException if the file exists but cannot be read
     */
    public static AppConfig load(Path folder) {
        AppConfig config = new AppConfig(folder.resolve(FILE_NAME));
        if (Files.isRegularFile(config.file)) {
            try (Reader reader = Files.newBufferedReader(config.file, StandardCharsets.UTF_8)) {
                config.properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                throw new StorageException("Could not read your settings from " + config.file + ".", e);
            }
        }
        return config;
    }

    /** The default preferences, to be saved in a folder later. Reads nothing. */
    public static AppConfig defaults(Path folder) {
        return new AppConfig(folder.resolve(FILE_NAME));
    }

    /**
     * Writes the preferences back.
     *
     * @throws StorageException if the file cannot be written
     */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "Hold'em desktop app settings");
            }
        } catch (IOException e) {
            throw new StorageException("Could not save your settings to " + file + ".", e);
        }
    }

    /** The server address last used, or an empty string. */
    public String lastServer() {
        return properties.getProperty(LAST_SERVER, "");
    }

    public void setLastServer(String address) {
        properties.setProperty(LAST_SERVER, address);
    }

    /** The username last logged in with, or an empty string. */
    public String lastUsername() {
        return properties.getProperty(LAST_USERNAME, "");
    }

    public void setLastUsername(String username) {
        properties.setProperty(LAST_USERNAME, username);
    }

    public boolean soundOn() {
        return Boolean.parseBoolean(properties.getProperty(SOUND, "true"));
    }

    public void setSoundOn(boolean on) {
        properties.setProperty(SOUND, Boolean.toString(on));
    }

    /** The name of the chosen card back design; "crimson" unless the player picked another. */
    public String cardBack() {
        return properties.getProperty(CARD_BACK, "crimson");
    }

    public void setCardBack(String design) {
        properties.setProperty(CARD_BACK, design);
    }

    /** True for a four-colour deck: clubs green and diamonds blue, so suits are told apart at a glance. */
    public boolean fourColourDeck() {
        return Boolean.parseBoolean(properties.getProperty(FOUR_COLOUR, "false"));
    }

    public void setFourColourDeck(boolean on) {
        properties.setProperty(FOUR_COLOUR, Boolean.toString(on));
    }
}
