package com.saksham.poker.client.app;

import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.client.view.ConnectView;
import com.saksham.poker.client.view.GameStartedView;
import com.saksham.poker.client.view.HomeView;
import com.saksham.poker.client.view.LoginView;
import com.saksham.poker.client.view.WaitingRoomView;
import java.util.Optional;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Decides which screen is showing. There is one window and one scene; moving between screens swaps
 * what is inside it. Every method must be called on the JavaFX thread.
 */
public final class SceneRouter {

    private final Stage stage;
    private final ClientContext context;
    private final StackPane root = new StackPane();
    private RoomSession room;

    public SceneRouter(Stage stage, ClientContext context) {
        this.stage = stage;
        this.context = context;
        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(SceneRouter.class.getResource("/holdem.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        // Closing the window drops the connection but keeps the seat, as a lost connection would.
        stage.setOnCloseRequest(event -> {
            if (room != null) {
                room.disconnect();
            }
        });
    }

    public ClientContext context() {
        return context;
    }

    /** The first screen: straight in if a remembered login is still good, otherwise the connect screen. */
    public void start() {
        Optional<SavedSession> saved = context.sessions().load();
        if (saved.isEmpty()) {
            showConnect(null);
            return;
        }
        SavedSession session = saved.get();
        ServerAddress server;
        try {
            server = ServerAddress.parse(session.server());
        } catch (IllegalArgumentException e) {
            showConnect(null);
            return;
        }
        show(new StackPane(new Label("Signing in as " + session.username() + "...")));
        ApiClient api = new ApiClient(server);
        api.me(session.token()).whenComplete((user, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                context.useServer(server);
                context.resumed(session, user);
                showHome(null);
            } else if (ApiClient.reason(failure).unreachable()) {
                showConnect(ApiClient.reason(failure).getMessage());
            } else {
                // The remembered login has expired: ask for the password again.
                context.useServer(server);
                showLogin("Your remembered login has expired. Log in again.");
            }
        }));
    }

    public void showConnect(String notice) {
        show(new ConnectView(this, notice));
    }

    public void showLogin(String notice) {
        show(new LoginView(this, notice));
    }

    public void showHome(String notice) {
        show(new HomeView(this, notice));
    }

    /** Joins a room and shows it once the server has let the player in. */
    public void enterRoom(String code) {
        show(new StackPane(new Label("Joining room " + code + "...")));
        room = new RoomSession(context, code, this::showRoom, reason -> {
            room = null;
            showHome(reason);
        });
        room.start();
    }

    /** Shows the waiting room or the game, and switches between them when the game starts. */
    private void showRoom() {
        RoomSession current = room;
        Runnable choose = () -> {
            if (room == current) {
                show(current.state().waiting() ? new WaitingRoomView(current) : new GameStartedView(current));
            }
        };
        current.state().stageProperty().addListener((property, was, now) -> {
            boolean wasWaiting = was == com.saksham.poker.common.protocol.dto.RoomState.WAITING;
            boolean nowWaiting = now == com.saksham.poker.common.protocol.dto.RoomState.WAITING;
            if (wasWaiting != nowWaiting) {
                choose.run();
            }
        });
        choose.run();
    }

    private void show(Node screen) {
        root.getChildren().setAll(screen);
    }
}
