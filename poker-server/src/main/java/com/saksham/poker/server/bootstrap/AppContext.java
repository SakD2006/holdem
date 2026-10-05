package com.saksham.poker.server.bootstrap;

import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.io.ServerConfig;
import com.saksham.poker.server.room.RoomService;
import jakarta.servlet.ServletContext;

/**
 * The server's long-lived objects, built once at startup and shared by every servlet and filter.
 *
 * @param config the settings the server started with
 * @param sessions registering, logging in and checking tokens
 * @param rooms creating and finding rooms
 */
public record AppContext(ServerConfig config, SessionService sessions, RoomService rooms) {

    public static final String SERVER_NAME = "Hold'em";
    public static final String SERVER_VERSION = "0.1.0";

    private static final String ATTRIBUTE = AppContext.class.getName();

    void storeIn(ServletContext servletContext) {
        servletContext.setAttribute(ATTRIBUTE, this);
    }

    /** The context stored at startup. */
    public static AppContext from(ServletContext servletContext) {
        AppContext context = (AppContext) servletContext.getAttribute(ATTRIBUTE);
        if (context == null) {
            throw new IllegalStateException("The server did not start correctly. Check the server log.");
        }
        return context;
    }
}
