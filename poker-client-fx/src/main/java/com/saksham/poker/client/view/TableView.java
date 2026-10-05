package com.saksham.poker.client.view;

import com.saksham.poker.client.app.RoomSession;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.protocol.client.TakeSeat;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
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

    public TableView(RoomSession session) {
        this.state = session.state();
        getStyleClass().add("table-screen");

        // ---- top bar
        VBox titles = new VBox(1, title, subtitle);
        HBox top = new HBox(12, titles, SeatNode.spacer(), Ui.button("Leave room", session::leave, "small"));
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
        VBox side = new VBox(8, Ui.label("Hand log", "field-label"), log);
        side.getStyleClass().add("side-panel");
        setRight(side);

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
        drawHeader();
    }

    private void drawHeader() {
        if (state.settingsProperty().get() == null) {
            return;
        }
        title.setText(state.settingsProperty().get().name());
        String hand = state.handNoProperty().get() > 0 ? "Hand #" + state.handNoProperty().get() : "Starting";
        subtitle.setText("Room " + state.codeProperty().get() + "  -  blinds "
                + state.settingsProperty().get().smallBlind() + "/" + state.settingsProperty().get().bigBlind()
                + "  -  " + (state.paused() ? "Paused" : hand));
    }
}
