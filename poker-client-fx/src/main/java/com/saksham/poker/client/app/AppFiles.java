package com.saksham.poker.client.app;

import java.nio.file.Path;

/** Where the desktop app keeps its own files: {@code ~/.holdem}. */
public final class AppFiles {

    /** System property that moves the folder elsewhere; tests use it. */
    public static final String HOME_PROPERTY = "holdem.home";

    private AppFiles() {
    }

    public static Path folder() {
        String override = System.getProperty(HOME_PROPERTY);
        return override != null ? Path.of(override) : Path.of(System.getProperty("user.home"), ".holdem");
    }
}
