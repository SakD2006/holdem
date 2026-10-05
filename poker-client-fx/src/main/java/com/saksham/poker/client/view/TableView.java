package com.saksham.poker.client.view;

import com.saksham.poker.client.state.Cue;
import com.saksham.poker.client.util.SoundPlayer;
import com.saksham.poker.client.util.SoundPlayer.Sound;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import com.saksham.poker.client.app.RoomSession;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.PauseGame;
import com.saksham.poker.common.protocol.client.ResumeGame;
import com.saksham.poker.common.protocol.client.SitOut;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The game screen: the table in the middle, the action panel under it and the hand log beside it. */
public final class TableView extends BorderPane {

    private final RoomState state;
    private final Label title = Ui.label("", "heading");
    private final Label subtitle = Ui.label("", "muted");
    private final Button sitOut;
    private final Button pause;
    private final Button endRoom;

    public TableView(RoomSession session) {
        this.state = session.state();
        getStyleClass().add("table-screen");

        // ---- top bar
        VBox titles = new VBox(1, title, subtitle);
        sitOut = Ui.button("Sit out", () -> session.send(new SitOut()), "small");
        pause = Ui.button("Pause", () -> session.send(state.paused() ? new ResumeGame() : new PauseGame()), "small");
        endRoom = Ui.button("End room", () -> confirm("End the room for everyone?",
                "The game stops and every player is sent back to the home screen.", "End room",
                () -> session.send(new EndRoom())), "small", "danger");
        Button leave = Ui.button("Leave room", () -> {
            if (state.handInProgressProperty().get() && state.youAreSeated()) {
                confirm("Leave in the middle of a hand?", "Your hand is folded and your seat is given up.",
                        "Leave room", session::leave);
            } else {
                session.leave();
            }
        }, "small");
        HBox top = new HBox(8, titles, SeatNode.spacer(), sitOut, pause, endRoom, leave);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("top-bar");
        setTop(top);

        // ---- the table and the action panel
        TableSurface surface = new TableSurface(state, seat -> session.send(new TakeSeat(seat)));
        ActionPanel actions = new ActionPanel(state, session::send);
        setCenter(surface);
        setBottom(actions);
        // Bets change the pot total shown in the middle.
        InvalidationListener potChanged = observable -> surface.refreshPot();
        state.turnProperty().addListener(potChanged);
        state.handInProgressProperty().addListener(potChanged);
        state.streetProperty().addListener(potChanged);

        // ---- the hand log
        ListView<String> log = new ListView<>(state.handLog());
        log.setFocusTraversable(false);
        VBox.setVgrow(log, Priority.ALWAYS);
        state.handLog().addListener((InvalidationListener) observable ->
                log.scrollTo(Math.max(0, state.handLog().size() - 1)));
        ChatPanel chat = new ChatPanel(state, session::send);
        chat.setPrefHeight(230);
        chat.setMinHeight(170);
        VBox side = new VBox(8, Ui.label("Hand log", "field-label"), log, chat);
        side.getStyleClass().add("side-panel");
        setRight(side);

        // ---- sounds
        state.onCue(this::sound);

        // ---- shortcuts: F, C and R, unless the player is typing in a box
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!(event.getTarget() instanceof TextInputControl) && actions.handleKey(event.getCode())) {
                event.consume();
            }
        });

        InvalidationListener header = observable -> drawHeader();
        state.settingsProperty().addListener(header);
        state.handNoProperty().addListener(header);
        state.stageProperty().addListener(header);
        state.hostUserIdProperty().addListener(header);
        state.players().addListener(header);
        state.yourSeatProperty().addListener(header);
        drawHeader();
    }

    private void sound(Cue cue) {
        switch (cue) {
            case DEAL -> SoundPlayer.play(Sound.DEAL);
            case BOARD -> SoundPlayer.play(Sound.CARD);
            case CHECK -> SoundPlayer.play(Sound.CHECK);
            case CHIPS -> SoundPlayer.play(Sound.CHIPS);
            case FOLD -> SoundPlayer.play(Sound.FOLD);
            case YOUR_TURN -> SoundPlayer.play(Sound.YOUR_TURN);
            case YOU_WIN -> {
                // Held back until the table has shown who won, so the sound does not give it away.
                PauseTransition wait = new PauseTransition(
                        Duration.millis(Math.max(1, Motion.winnerDelayMs(state.handsShown()))));
                wait.setOnFinished(event -> SoundPlayer.play(Sound.WIN));
                wait.play();
            }
        }
    }

    /** Asks before doing something that cannot be undone. */
    private void confirm(String question, String consequence, String yes, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, consequence, new ButtonType(yes,
                ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        alert.setTitle(yes);
        alert.setHeaderText(question);
        alert.initOwner(getScene().getWindow());
        Ui.style(alert);
        alert.showAndWait().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(button -> action.run());
    }

    private static void show(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void drawHeader() {
        if (state.settingsProperty().get() == null) {
            return;
        }
        // Only the host can pause or end; only a seated player who is sitting in can sit out.
        boolean host = state.youAreHost();
        show(pause, host);
        show(endRoom, host);
        pause.setText(state.paused() ? "Resume" : "Pause");
        PlayerInfo me = state.player(state.yourUserIdProperty().get());
        show(sitOut, me != null && me.seated() && !me.sittingOut());
        title.setText(state.settingsProperty().get().name());
        String hand = state.handNoProperty().get() > 0 ? "Hand #" + state.handNoProperty().get() : "Starting";
        subtitle.setText("Room " + state.codeProperty().get() + "  -  blinds "
                + state.settingsProperty().get().smallBlind() + "/" + state.settingsProperty().get().bigBlind()
                + "  -  " + (state.paused() ? "Paused" : hand));
    }
}
