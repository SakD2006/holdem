package com.saksham.poker.client.app;

import com.saksham.poker.client.net.GameSocket;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.LeaveRoom;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Duration;

/**
 * Being in one room: the game connection and the {@link RoomState} it keeps up to date. A session
 * starts when the player asks to join and ends when they leave, are removed, or the room closes.
 *
 * <p>If the connection drops, the session does not end: it tries again every two seconds, and when
 * it gets back in the server sends a snapshot that puts the state right. Meanwhile
 * {@link #reconnectingProperty()} is true, which the screen shows as an overlay.
 */
public final class RoomSession {

    private static final Duration RETRY_EVERY = Duration.seconds(2);
    /** Close codes after which trying again cannot help. */
    private static final int CLOSE_REPLACED = 4000;
    private static final int CLOSE_UNAUTHORIZED = 4401;

    private final ClientContext context;
    private final String code;
    private final RoomState state = new RoomState();
    private final Runnable onEntered;
    private final Consumer<String> onEnded;
    private final BooleanProperty reconnecting = new SimpleBooleanProperty();
    private GameSocket socket;
    /** Tells one connection attempt from the next, so a late callback from an old one is ignored. */
    private int attempt;
    private boolean entered;
    private boolean ended;
    /** True from asking to join until the server answers with a snapshot or a refusal. */
    private boolean joining;

    /**
     * @param code the room to join
     * @param onEntered called once, when the room's first snapshot has arrived
     * @param onEnded called once with the reason, when the player is no longer in the room
     */
    public RoomSession(ClientContext context, String code, Runnable onEntered, Consumer<String> onEnded) {
        this.context = context;
        this.code = code;
        this.onEntered = onEntered;
        this.onEnded = onEnded;
        state.goneProperty().addListener((property, was, gone) -> {
            if (gone) {
                end(state.goneReasonProperty().get());
            }
        });
    }

    /** Opens the connection and asks to join. The outcome arrives through the two callbacks. */
    public void start() {
        connect();
    }

    private void connect() {
        int mine = ++attempt;
        joining = true;
        GameSocket fresh = new GameSocket(Platform::runLater,
                message -> {
                    if (mine == attempt) {
                        received(message);
                    }
                },
                closeCode -> {
                    if (mine == attempt) {
                        connectionLost(closeCode);
                    }
                });
        socket = fresh;
        String url = context.api().server().gameSocketUrl(context.token());
        fresh.connect(context.api().http(), url).whenComplete((done, failure) -> Platform.runLater(() -> {
            if (mine != attempt || ended) {
                return;
            }
            if (failure != null) {
                connectionLost(1006);
            } else {
                fresh.send(new JoinRoom(code));
            }
        }));
    }

    private void received(ServerMessage message) {
        if (ended) {
            return;
        }
        if (joining && message instanceof ErrorMessage refusal) {
            // The join itself was refused: the room is full or gone, or we are in another room.
            end(refusal.code() == ErrorCode.ROOM_NOT_FOUND && entered
                    ? "The room is no longer open. The server may have been restarted."
                    : refusal.message());
            return;
        }
        state.apply(message);
        if (message instanceof RoomSnapshot) {
            joining = false;
            reconnecting.set(false);
            if (!entered) {
                entered = true;
                onEntered.run();
            }
        }
    }

    private void connectionLost(int closeCode) {
        if (ended) {
            return;
        }
        if (closeCode == CLOSE_REPLACED) {
            end("You connected from somewhere else, so this window was disconnected.");
        } else if (closeCode == CLOSE_UNAUTHORIZED) {
            end("Your login has expired. Log in again.");
        } else if (!entered) {
            end("Could not open the game connection to " + context.api().server().display()
                    + ". Check the server is still running.");
        } else {
            // Keep the seat warm: the server holds it, and auto-plays our turns after a minute.
            reconnecting.set(true);
            PauseTransition wait = new PauseTransition(RETRY_EVERY);
            wait.setOnFinished(event -> {
                if (!ended && reconnecting.get()) {
                    connect();
                }
            });
            wait.play();
        }
    }

    private void end(String reason) {
        if (ended) {
            return;
        }
        ended = true;
        reconnecting.set(false);
        attempt++;
        if (socket != null) {
            socket.close();
        }
        onEnded.accept(reason);
    }

    public RoomState state() {
        return state;
    }

    public String code() {
        return code;
    }

    /** True while the connection is down and the session is trying to get back in. */
    public BooleanProperty reconnectingProperty() {
        return reconnecting;
    }

    public void send(ClientMessage message) {
        if (socket != null) {
            socket.send(message);
        }
    }

    /** Leaves the room. The server confirms, and the session then ends itself. */
    public void leave() {
        if (reconnecting.get()) {
            // There is nobody to tell. The server will sit us out and keep the seat for a while.
            end("You left while disconnected. Your seat is kept for a while if you want to rejoin.");
        } else {
            send(new LeaveRoom());
        }
    }

    /** Drops the connection without leaving the room, as when the app is closed. */
    public void disconnect() {
        ended = true;
        attempt++;
        if (socket != null) {
            socket.close();
        }
    }

    /** Cuts the connection as a network failure would, to rehearse reconnecting. */
    public void dropConnection() {
        if (socket != null) {
            socket.drop();
        }
    }
}
