package com.saksham.poker.client.view;

import com.saksham.poker.client.app.RoomSession;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A stand-in for the table until it is built in the next step. It shows the live hand log, which
 * proves the game is running and the app is following it.
 */
public final class GameStartedView extends StackPane {

    public GameStartedView(RoomSession session) {
        getStyleClass().add("screen");

        ListView<String> log = new ListView<>(session.state().handLog());
        log.setFocusTraversable(false);
        VBox.setVgrow(log, Priority.ALWAYS);
        // Keep the newest line in view.
        session.state().handLog().addListener((javafx.beans.InvalidationListener) observable ->
                log.scrollTo(Math.max(0, session.state().handLog().size() - 1)));

        VBox panel = new VBox(14,
                Ui.label("The game has started", "heading"),
                Ui.label("The table, cards and action buttons arrive in the next step. Until then you can "
                        + "watch the hand log below; since you cannot act yet, your turn will time out and "
                        + "you will be sat out.", "muted"),
                log,
                Ui.button("Leave room", session::leave));
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(720);
        panel.setMaxHeight(560);
        getChildren().add(panel);
    }
}
