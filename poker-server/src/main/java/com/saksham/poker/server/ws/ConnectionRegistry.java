package com.saksham.poker.server.ws;

import com.saksham.poker.common.protocol.ServerMessage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open connection of each user. A user has at most one: connecting again replaces, and closes,
 * the old one.
 *
 * <p>Thread-safe.
 */
public final class ConnectionRegistry {

    private final ConcurrentHashMap<Long, Connection> connections = new ConcurrentHashMap<>();

    /** Makes this the user's connection, closing any earlier one. */
    public void register(Connection connection) {
        Connection previous = connections.put(connection.userId(), connection);
        if (previous != null && previous != connection) {
            previous.close(Connection.CLOSE_REPLACED, "Connected from somewhere else");
        }
    }

    /**
     * Forgets a connection that has closed.
     *
     * @return true if it was still the user's current connection; false if a newer one had already
     *     replaced it, in which case the user is not really gone
     */
    public boolean unregister(Connection connection) {
        return connections.remove(connection.userId(), connection);
    }

    /** Sends to the user's current connection, if they have one. */
    public void send(long userId, ServerMessage message) {
        Connection connection = connections.get(userId);
        if (connection != null) {
            connection.send(message);
        }
    }

    public boolean isConnected(long userId) {
        Connection connection = connections.get(userId);
        return connection != null && connection.isOpen();
    }

    public int size() {
        return connections.size();
    }

    /** Closes every connection. Called when the server shuts down. */
    public void closeAll() {
        for (Connection connection : connections.values()) {
            connection.close(jakarta.websocket.CloseReason.CloseCodes.GOING_AWAY.getCode(), "Server stopping");
        }
        connections.clear();
    }
}
