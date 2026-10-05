package com.saksham.poker.client.view;

import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Asks for a new room's settings. The result is the settings, or nothing if cancelled. */
public final class CreateRoomDialog extends Dialog<RoomSettingsInfo> {

    private final TextField name = new TextField();
    private final Spinner<Integer> maxPlayers = new Spinner<>(2, 9, 6);
    private final TextField smallBlind = new TextField("50");
    private final TextField bigBlind = new TextField("100");
    private final TextField startingStack = new TextField("10000");
    private final Spinner<Integer> turnSeconds = new Spinner<>(10, 60, 25, 5);
    private final CheckBox rebuy = new CheckBox("Players who lose all their chips may rebuy");
    private final Label message = Ui.message();

    public CreateRoomDialog(String hostUsername) {
        setTitle("Create room");
        setHeaderText("Create a room");
        Ui.style(this);

        name.setText(hostUsername + "'s game");
        rebuy.setSelected(true);
        maxPlayers.setMaxWidth(Double.MAX_VALUE);
        turnSeconds.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);
        grid.add(Ui.field("Room name", name), 0, 0, 2, 1);
        grid.add(Ui.field("Seats (2 to 9)", maxPlayers), 0, 1);
        grid.add(Ui.field("Seconds per turn (10 to 60)", turnSeconds), 1, 1);
        grid.add(Ui.field("Small blind", smallBlind), 0, 2);
        grid.add(Ui.field("Big blind", bigBlind), 1, 2);
        grid.add(Ui.field("Starting stack", startingStack), 0, 3);

        VBox content = new VBox(14, grid, rebuy, message);
        content.setPrefWidth(440);
        getDialogPane().setContent(content);

        ButtonType create = new ButtonType("Create room", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, create);
        getDialogPane().lookupButton(create).getStyleClass().add("primary");
        // Keep the dialog open, with the reason shown, while the settings do not make sense.
        getDialogPane().lookupButton(create).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = problem();
            if (problem != null) {
                Ui.showError(message, problem);
                event.consume();
            }
        });
        setResultConverter(button -> button == create ? settings() : null);
    }

    /** What is wrong with the settings as typed, or null if they are fine. */
    private String problem() {
        if (name.getText().isBlank() || name.getText().trim().length() > 40) {
            return "Give the room a name of 1 to 40 characters.";
        }
        long small = number(smallBlind);
        long big = number(bigBlind);
        long stack = number(startingStack);
        if (small < 1 || big < 1 || stack < 1) {
            return "The blinds and the starting stack must be whole numbers of 1 or more.";
        }
        if (big < small) {
            return "The big blind must be at least the small blind.";
        }
        if (stack < big) {
            return "The starting stack must be at least the big blind.";
        }
        return null;
    }

    private RoomSettingsInfo settings() {
        return new RoomSettingsInfo(name.getText().trim(), maxPlayers.getValue(), number(smallBlind),
                number(bigBlind), number(startingStack), turnSeconds.getValue(), rebuy.isSelected());
    }

    /** The whole number typed in a field, or -1 if it is not one. Commas and spaces are ignored. */
    private static long number(TextField field) {
        try {
            return Long.parseLong(field.getText().replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
