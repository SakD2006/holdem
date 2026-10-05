package com.saksham.poker.server.ws;

import com.saksham.poker.common.exception.ProtocolException;
import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.common.protocol.ServerMessage;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One player's WebSocket, as the rest of the server uses it. {@link #send} never blocks: it puts the
 * message in a queue, and this connection's own virtual thread writes the queue to the socket. A
 * slow or stalled client therefore delays only itself, never a room.
 */
public final class Connection {

    private static final Logger log = LoggerFactory.getLogger(Connection.class);

    /** Close code for a missing or invalid login token (SPEC §5). */
    public static final int CLOSE_UNAUTHORIZED = 4401;
    /** Close code when the same user connects again from somewhere else. */
    public static final int CLOSE_REPLACED = 4000;
    /** Close code when a client is not reading its messages fast enough. */
    public static final int CLOSE_TOO_SLOW = 4002;

    private static final int MAX_QUEUED = 2_000;

    private final long userId;
    private final Session session;
    private final MessageCodec codec;
    private final LinkedBlockingQueue<ServerMessage> outbound = new LinkedBlockingQueue<>(MAX_QUEUED);
    private final Thread sender;
    private volatile boolean open = true;
    private long seq;

    public Connection(long userId, Session session, MessageCodec codec) {
        this.userId = userId;
        this.session = session;
        this.codec = codec;
        this.sender = Thread.ofVirtual().name("ws-send-" + userId).start(this::sendLoop);
    }

    public long userId() {
        return userId;
    }

    public boolean isOpen() {
        return open && session.isOpen();
    }

    /** Queues a message for this player. Returns at once. */
    public void send(ServerMessage message) {
        if (!open) {
            return;
        }
        if (!outbound.offer(message)) {
            log.warn("User {} is not keeping up with their messages; closing the connection", userId);
            close(CLOSE_TOO_SLOW, "Too many unread messages");
        }
    }

    private void sendLoop() {
        try {
            while (open) {
                ServerMessage message = outbound.take();
                session.getBasicRemote().sendText(codec.encode(message, ++seq));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalStateException e) {
            // The socket has gone; the endpoint's close handler tells the room.
            open = false;
        } catch (ProtocolException e) {
            log.error("Could not encode a message for user {}; closing the connection", userId, e);
            close(CloseReason.CloseCodes.UNEXPECTED_CONDITION.getCode(), "Server error");
        }
    }

    /** Closes the socket and stops the sender. Messages still queued are dropped. */
    public void close(int code, String reason) {
        open = false;
        sender.interrupt();
        closeQuietly(session, code, reason);
    }

    static void closeQuietly(Session session, int code, String reason) {
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.getCloseCode(code), reason));
            }
        } catch (IOException | IllegalStateException e) {
            // Already closed.
        }
    }
}
