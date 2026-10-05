package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** After logging in: create a room, join one, or log out. */
public final class HomeView extends StackPane {

    private final SceneRouter router;
    private final Label message = Ui.message();
    private final Button create;
    private final Button join;

    public HomeView(SceneRouter router, String notice) {
        this.router = router;
        getStyleClass().add("screen");

        create = Ui.button("Create room", this::createRoom, "primary");
        join = Ui.button("Join room", this::joinRoom);
        create.setMaxWidth(Double.MAX_VALUE);
        join.setMaxWidth(Double.MAX_VALUE);
        Button history = Ui.button("My hand history", router::showHistory);
        Button leaderboard = Ui.button("Leaderboard", router::showLeaderboard);
        history.setMaxWidth(Double.MAX_VALUE);
        leaderboard.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(history, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(leaderboard, javafx.scene.layout.Priority.ALWAYS);
        HBox footer = new HBox(Ui.button("Log out", this::logOut, "link"));
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(16,
                Ui.label("Hold'em", "title"),
                Ui.label("Signed in as " + router.context().user().username() + " on "
                        + router.context().api().server().display(), "muted"),
                message,
                create,
                Ui.label("Start a new table. You get a code to share and become the host.", "muted"),
                join,
                Ui.label("Enter the code a host has given you.", "muted"),
                new HBox(10, history, leaderboard),
                footer);
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(460);
        panel.setMaxHeight(USE_PREF_SIZE);
        getChildren().add(panel);

        if (notice != null && !notice.isEmpty()) {
            Ui.showError(message, notice);
        }
    }

    private void createRoom() {
        new CreateRoomDialog(router.context().user().username()).showAndWait().ifPresent(settings -> {
            busy(true);
            message.setText("");
            Ui.whenDone(router.context().api().createRoom(router.context().token(), settings),
                    router::enterRoom,
                    failure -> {
                        busy(false);
                        Ui.showError(message, failure);
                    });
        });
    }

    private void joinRoom() {
        new JoinRoomDialog(router.context()).showAndWait().ifPresent(router::enterRoom);
    }

    private void logOut() {
        // Tell the server so the token stops working; the app forgets it either way.
        router.context().api().logout(router.context().token());
        router.context().loggedOut();
        router.showLogin(null);
    }

    private void busy(boolean busy) {
        create.setDisable(busy);
        join.setDisable(busy);
    }
}
