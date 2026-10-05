package com.saksham.poker.client.app;

import com.saksham.poker.client.net.GameSocket;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.LeaveRoom;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.util.function.Consumer;
import javafx.application.Platform;

/**
 * Being in one room: the game connection and the {@link RoomState} it keeps up to date. A session
 * starts when the player asks to join and ends when they leave, are removed, or the room closes.
 */
public final class RoomSession {

    private final ClientContext context;
    private final String code;
    private final RoomState state = new RoomState();
    private final GameSocket socket;
    private final Runnable onEntered;
    private final Consumer<String> onEnded;
    private boolean entered;
    private boolean ended;

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
        this.socket = new GameSocket(Platform::runLater, this::received, this::connectionLost);
        state.goneProperty().addListener((property, was, gone) -> {
            if (gone) {
                end(state.goneReasonProperty().get());
            }
        });
    }

    /** Opens the connection and asks to join. The outcome arrives through the two callbacks. */
    public void start() {
        String url = context.api().server().gameSocketUrl(context.token());
        socket.connect(context.api().http(), url).whenComplete((done, failure) -> Platform.runLater(() -> {
            if (failure != null) {
                end("Could not open the game connection to " + context.api().server().display()
                        + ". Check the server is still running.");
            } else {
                socket.send(new JoinRoom(code));
            }
        }));
    }

    private void received(ServerMessage message) {
        if (ended) {
            return;
        }
        if (!entered && message instanceof ErrorMessage refusal) {
            // The join itself was refused: full, closed, or already in another room.
            end(refusal.message());
            return;
        }
        state.apply(message);
        if (!entered && message instanceof RoomSnapshot) {
            entered = true;
            onEntered.run();
        }
    }

    private void connectionLost(int closeCode) {
        end(closeCode == 4000
                ? "You connected from somewhere else, so this window was disconnected."
                : "The connection to the server was lost.");
    }

    private void end(String reason) {
        if (ended) {
            return;
        }
        ended = true;
        socket.close();
        onEnded.accept(reason);
    }

    public RoomState state() {
        return state;
    }

    public void send(ClientMessage message) {
        socket.send(message);
    }

    /** Leaves the room. The server confirms, and the session then ends itself. */
    public void leave() {
        socket.send(new LeaveRoom());
    }

    /** Drops the connection without leaving the room, as when the app is closed. */
    public void disconnect() {
        ended = true;
        socket.close();
    }
}
