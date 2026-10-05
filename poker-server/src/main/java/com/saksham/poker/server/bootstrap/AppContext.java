package com.saksham.poker.server.bootstrap;

import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.io.ServerConfig;
import com.saksham.poker.server.room.RoomManager;
import com.saksham.poker.server.room.RoomService;
import com.saksham.poker.server.ws.ConnectionRegistry;
import jakarta.servlet.ServletContext;

/**
 * The server's long-lived objects, built once at startup and shared by every servlet, filter and
 * game connection.
 *
 * @param config the settings the server started with
 * @param sessions registering, logging in and checking tokens
 * @param rooms creating rooms and looking them up
 * @param roomManager the open rooms, where game connections send their commands
 * @param connections the open game connection of each user
 * @param codec reads and writes game messages
 */
public record AppContext(ServerConfig config, SessionService sessions, RoomService rooms,
        RoomManager roomManager, ConnectionRegistry connections, MessageCodec codec) {

    public static final String SERVER_NAME = "Hold'em";
    public static final String SERVER_VERSION = "0.1.0";

    private static final String ATTRIBUTE = AppContext.class.getName();

    /** WebSocket endpoints are not handed the servlet context, so they find the context here. */
    private static volatile AppContext current;

    void storeIn(ServletContext servletContext) {
        servletContext.setAttribute(ATTRIBUTE, this);
        current = this;
    }

    static void clear() {
        current = null;
    }

    /** The context stored at startup. */
    public static AppContext from(ServletContext servletContext) {
        AppContext context = (AppContext) servletContext.getAttribute(ATTRIBUTE);
        if (context == null) {
            throw new IllegalStateException("The server did not start correctly. Check the server log.");
        }
        return context;
    }

    /** The running server's context, for code that has no servlet context to ask. */
    public static AppContext current() {
        AppContext context = current;
        if (context == null) {
            throw new IllegalStateException("The server is not running.");
        }
        return context;
    }
}
