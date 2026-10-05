package com.saksham.poker.client.net;

import com.saksham.poker.common.exception.ProtocolException;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.Envelope;
import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.client.Ping;
import com.saksham.poker.common.protocol.client.RequestSnapshot;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The game connection to the server. Messages arrive on a network thread; each is decoded there and
 * then handed to the listener through the given executor, which in the app is
 * {@code Platform::runLater}, so listeners always run on the JavaFX thread and nothing on that
 * thread ever waits for the network.
 *
 * <p>It also keeps the connection alive with a ping every 15 seconds, and notices a gap in the
 * server's sequence numbers, which means a message was missed: it then asks for a fresh snapshot.
 */
public final class GameSocket implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(GameSocket.class);
    private static final int PING_SECONDS = 15;

    private final MessageCodec codec = new MessageCodec();
    private final Executor deliverOn;
    private final Consumer<ServerMessage> onMessage;
    private final IntConsumer onClosed;
    private final StringBuilder partial = new StringBuilder();
    private final Object sendLock = new Object();
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "game-ping");
        thread.setDaemon(true);
        return thread;
    });

    private volatile WebSocket socket;
    private volatile boolean closedByUs;
    private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);
    private long sentSeq;
    private long receivedSeq;
    private boolean awaitingSnapshot;

    /**
     * @param deliverOn runs the listeners; {@code Platform::runLater} in the app
     * @param onMessage called for every message from the server
     * @param onClosed called once with the close code if the connection ends without {@link #close}
     */
    public GameSocket(Executor deliverOn, Consumer<ServerMessage> onMessage, IntConsumer onClosed) {
        this.deliverOn = deliverOn;
        this.onMessage = onMessage;
        this.onClosed = onClosed;
    }

    /** Opens the connection. The future fails if the server cannot be reached. */
    public CompletableFuture<Void> connect(HttpClient http, String url) {
        return http.newWebSocketBuilder().buildAsync(URI.create(url), this).thenAccept(opened -> {
            socket = opened;
            pinger.scheduleAtFixedRate(() -> send(new Ping()), PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
        });
    }

    public boolean isOpen() {
        WebSocket current = socket;
        return current != null && !current.isOutputClosed() && !closedByUs;
    }

    /** Sends a message. Returns at once; sends are queued, as a WebSocket allows one at a time. */
    public void send(ClientMessage message) {
        WebSocket current = socket;
        if (current == null || closedByUs) {
            return;
        }
        synchronized (sendLock) {
            String text;
            try {
                text = codec.encode(message, ++sentSeq);
            } catch (ProtocolException e) {
                log.error("Could not encode {}", message.type(), e);
                return;
            }
            lastSend = lastSend.thenCompose(done -> current.sendText(text, true)).exceptionally(error -> {
                log.debug("Could not send {}", message.type(), error);
                return null;
            });
        }
    }

    /** Closes the connection on purpose; {@code onClosed} is not called. */
    public void close() {
        closedByUs = true;
        pinger.shutdownNow();
        WebSocket current = socket;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(error -> null);
        }
    }

    /**
     * Cuts the connection without a goodbye, as a pulled cable would, and reports it lost. Used to
     * rehearse reconnecting.
     */
    public void drop() {
        WebSocket current = socket;
        if (current != null) {
            current.abort();
        }
        ended(1006);
    }

    // ---- from the network thread

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            String text = partial.toString();
            partial.setLength(0);
            receive(text);
        }
        webSocket.request(1);
        return null;
    }

    private void receive(String text) {
        Envelope<ServerMessage> envelope;
        try {
            envelope = codec.decodeServer(text);
        } catch (ProtocolException e) {
            log.warn("Ignored a message that could not be read: {}", e.getMessage());
            return;
        }
        boolean gap = envelope.seq() != receivedSeq + 1;
        receivedSeq = envelope.seq();
        ServerMessage message = envelope.message();
        if (message instanceof RoomSnapshot) {
            awaitingSnapshot = false;
        } else if (gap && !awaitingSnapshot) {
            // A message went missing, so what we know may be wrong. Ask for the whole picture.
            awaitingSnapshot = true;
            log.warn("Missed a message from the server; asking for a snapshot");
            send(new RequestSnapshot());
        }
        deliverOn.execute(() -> onMessage.accept(message));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        ended(statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.debug("Game connection failed", error);
        ended(1006);
    }

    private void ended(int statusCode) {
        pinger.shutdownNow();
        if (!closedByUs) {
            closedByUs = true;
            deliverOn.execute(() -> onClosed.accept(statusCode));
        }
    }
}
