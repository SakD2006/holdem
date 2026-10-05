package com.saksham.poker.client.view;

import com.saksham.poker.client.app.RoomSession;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A veil over the room while the connection is down. It appears and goes by itself, and offers a way
 * out for a player who would rather stop waiting.
 */
public final class ReconnectingOverlay extends StackPane {

    public ReconnectingOverlay(RoomSession session) {
        VBox box = new VBox(12,
                Ui.label("Reconnecting...", "heading"),
                Ui.label("The connection to the server was lost. Trying again every 2 seconds. Your seat is "
                        + "kept; after a minute away your turns are played for you.", "muted"),
                Ui.button("Stop and go back", session::leave));
        box.getStyleClass().add("panel");
        box.setMaxWidth(420);
        box.setMaxHeight(USE_PREF_SIZE);
        getChildren().add(box);
        setStyle("-fx-background-color: rgba(6, 14, 11, 0.78);");
        visibleProperty().bind(session.reconnectingProperty());
        managedProperty().bind(session.reconnectingProperty());
    }
}
