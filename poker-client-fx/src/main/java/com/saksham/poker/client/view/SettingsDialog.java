package com.saksham.poker.client.view;

import com.saksham.poker.client.app.AppConfig;
import com.saksham.poker.client.app.AppFiles;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.StorageException;
import java.io.File;
import java.nio.file.Path;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * The player's choices about how the cards look: which back design, their own picture for the back
 * if they have one, and whether the deck has four colours. Choices are saved when the dialog is
 * confirmed and apply from the next card drawn.
 */
public final class SettingsDialog extends Dialog<Void> {

    private static final double PREVIEW_WIDTH = 62;

    private final AppConfig config;
    private final Path folder;
    private final ToggleGroup backs = new ToggleGroup();
    private final HBox backRow = new HBox(6);
    private final HBox faceRow = new HBox(8);
    private final CheckBox fourColour = new CheckBox("Four-colour deck: clubs green, diamonds blue");
    private final Label message = Ui.message();
    private CardArt.Back chosen;

    public SettingsDialog(AppConfig config) {
        this(config, AppFiles.folder());
    }

    SettingsDialog(AppConfig config, Path folder) {
        this.config = config;
        this.folder = folder;
        this.chosen = CardArt.Back.named(config.cardBack());
        setTitle("Settings");
        setHeaderText("How the cards look");
        Ui.style(this);

        fourColour.setSelected(config.fourColourDeck());
        fourColour.selectedProperty().addListener(observable -> preview());
        backRow.setAlignment(Pos.CENTER_LEFT);
        faceRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12,
                Ui.label("Card back", "field-label"),
                backRow,
                Ui.button("Use my own picture...", this::choosePicture, "small"),
                Ui.label("A picture 5 wide by 7 tall fits best, such as 500 by 700 pixels. To replace card "
                        + "faces too, put pictures named like Ah.png or Td.png in " + folder.resolve("cards")
                        + ".", "muted"),
                Ui.label("Card faces", "field-label"),
                faceRow,
                fourColour,
                message);
        content.setPrefWidth(520);
        getDialogPane().setContent(content);

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        getDialogPane().lookupButton(save).getStyleClass().add("primary");
        setResultConverter(button -> {
            // Either way the table goes back to drawing cards from the saved settings.
            if (button == save) {
                config.setCardBack(chosen.name().toLowerCase());
                config.setFourColourDeck(fourColour.isSelected());
                try {
                    config.save();
                } catch (StorageException e) {
                    // The choice still applies for this run; it just will not be remembered.
                }
            }
            CardArt.use(config);
            return null;
        });
        preview();
    }

    /** Redraws the sample cards with the choices as they stand. */
    private void preview() {
        CardArt.Back keep = chosen;
        backRow.getChildren().clear();
        for (CardArt.Back back : CardArt.Back.values()) {
            if (back == CardArt.Back.CUSTOM && !CardArt.hasBackPicture(folder)) {
                continue;
            }
            CardArt.use(back, fourColour.isSelected(), folder);
            ToggleButton choice = new ToggleButton(back.label(), new CardNode(null, PREVIEW_WIDTH));
            choice.setContentDisplay(ContentDisplay.TOP);
            choice.getStyleClass().setAll("toggle-button", "back-choice");
            choice.setToggleGroup(backs);
            choice.setSelected(back == keep);
            choice.setOnAction(event -> {
                chosen = back;
                choice.setSelected(true);
            });
            backRow.getChildren().add(choice);
        }
        CardArt.use(keep, fourColour.isSelected(), folder);
        faceRow.getChildren().clear();
        for (Card card : Card.parseAll("As Kh Qd Jc Ts")) {
            faceRow.getChildren().add(new CardNode(card, PREVIEW_WIDTH));
        }
    }

    private void choosePicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a picture for the back of the cards");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            CardArt.installBackPicture(file.toPath(), folder);
            chosen = CardArt.Back.CUSTOM;
            message.setText("");
            preview();
        } catch (StorageException e) {
            Ui.showError(message, e.getMessage());
        }
    }
}
