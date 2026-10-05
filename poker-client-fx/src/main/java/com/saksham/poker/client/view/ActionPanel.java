package com.saksham.poker.client.view;

import com.saksham.poker.client.util.ErrorMessages;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.client.state.SeatViewModel;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.SitIn;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.TurnInfo;
import java.util.function.Consumer;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The strip under the table. On your turn it offers Fold, Check or Call, and Bet or Raise with a
 * slider, an amount box and quick sizes; only what is legal is shown. The rest of the time it says
 * what the table is waiting for, and offers "Sit in" or "Rebuy" when those apply.
 */
public final class ActionPanel extends HBox {

    private final RoomState state;
    private final Consumer<ClientMessage> send;

    private final Label status = Ui.label("", "muted");
    private final Button sitIn;
    private final Button rebuy;
    private final HBox waiting;

    private final Button fold;
    private final Button call;
    private final Button raise;
    private final Slider slider = new Slider();
    private final TextField amount = new TextField();
    private final HBox presets = new HBox(6);
    private final VBox sizing;
    private final HBox turnControls;

    /** The turn the buttons are set up for; null when it is not your turn. */
    private TurnInfo turn;
    /** True once an answer has been sent for {@link #turn}, so a second click sends nothing. */
    private boolean answered;
    private boolean syncing;

    /**
     * @param state the room
     * @param send sends a message to the server
     */
    public ActionPanel(RoomState state, Consumer<ClientMessage> send) {
        this.state = state;
        this.send = send;
        getStyleClass().add("action-panel");
        setSpacing(12);

        sitIn = Ui.button("Sit in", () -> send.accept(new SitIn()), "primary");
        rebuy = Ui.button("Rebuy", () -> send.accept(new Rebuy()), "primary");
        waiting = new HBox(12, status, sitIn, rebuy);
        waiting.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(waiting, Priority.ALWAYS);

        fold = actionButton("Fold", "F", this::fold, "fold");
        call = actionButton("Check", "C", this::checkOrCall);
        raise = actionButton("Raise", "R", this::betOrRaise, "primary");

        amount.setPrefWidth(110);
        amount.setOnAction(event -> betOrRaise());
        amount.textProperty().addListener((property, was, now) -> typed(now));
        slider.setPrefWidth(230);
        slider.valueProperty().addListener((property, was, now) -> slid(now.longValue()));
        HBox sliderRow = new HBox(10, slider, amount);
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        sizing = new VBox(6, presets, sliderRow);
        sizing.setAlignment(Pos.CENTER_LEFT);

        turnControls = new HBox(12, SeatNode.spacer(), sizing, fold, call, raise);
        turnControls.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(turnControls, Priority.ALWAYS);
        getChildren().addAll(waiting, turnControls);

        InvalidationListener refresh = observable -> refresh();
        state.turnProperty().addListener(refresh);
        state.yourSeatProperty().addListener(refresh);
        state.players().addListener(refresh);
        state.stageProperty().addListener(refresh);
        state.handInProgressProperty().addListener(refresh);
        state.lastErrorProperty().addListener((property, was, error) -> {
            if (error != null) {
                // A refused action leaves the turn open: let the player try again.
                answered = false;
                refresh();
                status.getStyleClass().setAll("label", "error");
                status.setText(ErrorMessages.text(error.code(), error.message()));
                waiting.setVisible(true);
                waiting.setManaged(true);
            }
        });
        refresh();
    }

    private static Button actionButton(String text, String key, Runnable action, String... styles) {
        Button button = Ui.button(text, action, styles);
        button.getStyleClass().add("action-button");
        button.setUserData(key);
        return button;
    }

    // =====================================================================================
    // Showing the right controls
    // =====================================================================================

    private void refresh() {
        TurnInfo current = state.yourTurn() ? state.turnProperty().get() : null;
        if (current == null || turn == null || current.turnId() != turn.turnId()) {
            answered = false;
        }
        turn = current;
        boolean acting = turn != null && !answered;
        turnControls.setVisible(acting);
        turnControls.setManaged(acting);
        waiting.setVisible(!acting);
        waiting.setManaged(!acting);
        if (acting) {
            showTurn();
        } else {
            showWaiting();
        }
    }

    private void showTurn() {
        SeatViewModel mine = state.seatAt(state.yourSeatProperty().get());
        long stack = mine == null ? 0 : mine.stackProperty().get();
        fold.setDisable(false);
        call.setDisable(false);
        if (turn.canCheck()) {
            call.setText("Check");
        } else {
            call.setText("Call " + Formatters.chips(turn.callAmount())
                    + (turn.callAmount() >= stack ? " (all-in)" : ""));
        }

        boolean canSize = turn.canBet() || turn.canRaise();
        sizing.setVisible(canSize);
        sizing.setManaged(canSize);
        raise.setVisible(canSize);
        raise.setManaged(canSize);
        if (!canSize) {
            return;
        }
        long min = turn.minRaiseTo();
        long max = turn.maxRaiseTo();
        slider.setMin(min);
        slider.setMax(Math.max(min, max));
        slider.setDisable(max <= min);
        slider.setBlockIncrement(Math.max(1, state.settingsProperty().get().bigBlind()));
        setAmount(min);

        // Quick sizes. A pot-sized raise is the call plus what the pot would then hold.
        long streetBet = mine == null ? 0 : mine.streetBetProperty().get();
        long callTo = streetBet + turn.callAmount();
        long potAfterCall = state.chipsInPlay() + turn.callAmount();
        presets.getChildren().setAll(
                preset("Min", min),
                preset("1/2 pot", callTo + potAfterCall / 2),
                preset("Pot", callTo + potAfterCall),
                preset("All-in", max));
    }

    private Button preset(String name, long target) {
        return Ui.button(name, () -> setAmount(target), "small");
    }

    private void showWaiting() {
        status.getStyleClass().setAll("label", "muted");
        PlayerInfo me = state.player(state.yourUserIdProperty().get());
        RoomSettingsInfo settings = state.settingsProperty().get();
        boolean seated = me != null && me.seated();
        boolean broke = seated && me.stack() == 0 && !inCurrentHand();
        boolean out = seated && me.sittingOut();
        rebuy.setVisible(broke && settings != null && settings.rebuyAllowed());
        rebuy.setManaged(rebuy.isVisible());
        sitIn.setVisible(out && !broke);
        sitIn.setManaged(sitIn.isVisible());

        TurnInfo other = state.turnProperty().get();
        if (!seated) {
            status.setText("You are watching. Pick an empty seat to play.");
        } else if (broke) {
            status.setText(rebuy.isVisible()
                    ? "You are out of chips. Rebuy to get back to the starting stack."
                    : "You are out of chips, and this room does not allow rebuys.");
        } else if (out) {
            status.setText("You are sitting out. Sit in to be dealt the next hand.");
        } else if (state.paused()) {
            status.setText("The host has paused the game.");
        } else if (answered) {
            status.setText("");
        } else if (other != null) {
            SeatViewModel seat = state.seatAt(other.seat());
            String who = seat == null || seat.usernameProperty().get().isEmpty()
                    ? Formatters.seat(other.seat()) : seat.usernameProperty().get();
            status.setText("Waiting for " + who + ".");
        } else if (!state.handInProgressProperty().get()) {
            status.setText(state.seatedCount() < 2 ? "Waiting for another player to sit down."
                    : "The next hand is about to start.");
        } else {
            status.setText("");
        }
    }

    private boolean inCurrentHand() {
        SeatViewModel mine = state.seatAt(state.yourSeatProperty().get());
        return mine != null && mine.inHandProperty().get() && state.handInProgressProperty().get();
    }

    // =====================================================================================
    // The bet size
    // =====================================================================================

    /** Sets the amount in both the slider and the box, kept inside what is legal. */
    private void setAmount(long wanted) {
        if (turn == null) {
            return;
        }
        long value = Math.max(turn.minRaiseTo(), Math.min(turn.maxRaiseTo(), wanted));
        syncing = true;
        slider.setValue(value);
        amount.setText(String.valueOf(value));
        syncing = false;
        labelRaise(value);
    }

    private void slid(long value) {
        if (!syncing && turn != null) {
            syncing = true;
            amount.setText(String.valueOf(value));
            syncing = false;
            labelRaise(value);
        }
    }

    private void typed(String text) {
        if (syncing || turn == null) {
            return;
        }
        long value = parse(text);
        if (value >= turn.minRaiseTo() && value <= turn.maxRaiseTo()) {
            syncing = true;
            slider.setValue(value);
            syncing = false;
        }
        labelRaise(value);
    }

    private void labelRaise(long value) {
        if (turn == null) {
            return;
        }
        String verb = turn.canBet() ? "Bet " : "Raise to ";
        boolean legal = value >= turn.minRaiseTo() && value <= turn.maxRaiseTo();
        raise.setDisable(!legal);
        raise.setText(legal ? verb + Formatters.chips(value) + (value == turn.maxRaiseTo() ? " (all-in)" : "")
                : verb.trim());
    }

    private static long parse(String text) {
        try {
            return Long.parseLong(text.replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // =====================================================================================
    // Acting
    // =====================================================================================

    private void fold() {
        submit(ActionType.FOLD, 0);
    }

    private void checkOrCall() {
        if (turn != null) {
            submit(turn.canCheck() ? ActionType.CHECK : ActionType.CALL, 0);
        }
    }

    private void betOrRaise() {
        if (turn == null || !(turn.canBet() || turn.canRaise())) {
            return;
        }
        long value = parse(amount.getText());
        if (value < turn.minRaiseTo() || value > turn.maxRaiseTo()) {
            return;
        }
        submit(turn.canBet() ? ActionType.BET : ActionType.RAISE, value);
    }

    private void submit(ActionType type, long value) {
        if (turn == null || answered) {
            return;
        }
        answered = true;
        send.accept(new SubmitAction(turn.turnId(), type, value));
        refresh();
    }

    /**
     * Handles the keyboard shortcuts: F to fold, C to check or call, R to bet or raise.
     *
     * @return true if the key was used
     */
    public boolean handleKey(KeyCode key) {
        if (turn == null || answered) {
            return false;
        }
        switch (key) {
            case F:
                fold();
                return true;
            case C:
                checkOrCall();
                return true;
            case R:
                betOrRaise();
                return true;
            default:
                return false;
        }
    }
}
