package com.saksham.poker.client.view;

import com.saksham.poker.client.app.ClientContext;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.api.RoomPreview;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;

/**
 * Asks for a room code and shows what room it is before joining. The result is the code, or nothing
 * if cancelled.
 */
public final class JoinRoomDialog extends Dialog<String> {

    private static final int CODE_LENGTH = 6;

    private final ClientContext context;
    private final TextField code = new TextField();
    private final Label preview = Ui.label("Ask the host for the 6-character room code.", "muted");
    private final Label message = Ui.message();
    /** The code whose room has been looked up and can be joined; null otherwise. */
    private String joinable;

    public JoinRoomDialog(ClientContext context) {
        this.context = context;
        setTitle("Join room");
        setHeaderText("Join a room");
        Ui.style(this);

        code.setPromptText("ABC234");
        code.getStyleClass().add("code-entry");
        code.setStyle("-fx-font-family: 'Menlo', 'Consolas', monospace; -fx-font-size: 22px;");
        // Codes are upper case; anything typed is upper-cased and cut to length.
        code.setTextFormatter(new TextFormatter<String>(change -> {
            change.setText(change.getText().toUpperCase().replaceAll("\\s", ""));
            return change.getControlNewText().length() <= CODE_LENGTH ? change : null;
        }));
        code.textProperty().addListener((property, was, now) -> lookUp(now));

        VBox content = new VBox(14, Ui.field("Room code", code), preview, message);
        content.setPrefWidth(400);
        getDialogPane().setContent(content);

        ButtonType join = new ButtonType("Join room", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, join);
        getDialogPane().lookupButton(join).getStyleClass().add("primary");
        getDialogPane().lookupButton(join).addEventFilter(ActionEvent.ACTION, event -> {
            if (joinable == null || !joinable.equals(code.getText())) {
                Ui.showError(message, "Type the full 6-character code of an open room first.");
                event.consume();
            }
        });
        setResultConverter(button -> button == join ? joinable : null);
        setOnShown(event -> code.requestFocus());
    }

    /** Once a whole code has been typed, asks the server what room it is. */
    private void lookUp(String typed) {
        joinable = null;
        message.setText("");
        if (typed.length() < CODE_LENGTH) {
            preview.setText("Ask the host for the 6-character room code.");
            return;
        }
        preview.setText("Looking for room " + typed + "...");
        Ui.whenDone(context.api().previewRoom(context.token(), typed), room -> {
            if (!typed.equals(code.getText())) {
                return; // the player has typed something else since
            }
            show(room);
        }, failure -> {
            if (typed.equals(code.getText())) {
                preview.setText("");
                Ui.showError(message, failure);
            }
        });
    }

    private void show(RoomPreview room) {
        String state = switch (room.state()) {
            case WAITING -> "waiting to start";
            case PLAYING -> "game in progress";
            case PAUSED -> "game paused";
            case CLOSED -> "closed";
        };
        preview.setText("\"" + room.settings().name() + "\", hosted by " + room.hostUsername() + ".\n"
                + room.seatedPlayers() + " of " + room.settings().maxPlayers() + " seats taken, " + state + ".\n"
                + "Blinds " + Formatters.chips(room.settings().smallBlind()) + "/"
                + Formatters.chips(room.settings().bigBlind()) + ", starting stack "
                + Formatters.chips(room.settings().startingStack()) + ".");
        if (room.state() == com.saksham.poker.common.protocol.dto.RoomState.CLOSED) {
            Ui.showError(message, "This room has closed and cannot be joined.");
        } else {
            joinable = room.code();
        }
    }
}
