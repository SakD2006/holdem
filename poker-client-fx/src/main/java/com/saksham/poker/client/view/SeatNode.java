package com.saksham.poker.client.view;

import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.client.state.SeatViewModel;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.util.function.IntConsumer;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One seat at the table: the player's cards, name and chips, what they last did, and a timer while
 * it is their turn. It watches its {@link SeatViewModel} and redraws itself on any change.
 */
public final class SeatNode extends VBox {

    static final double WIDTH = 150;
    static final double HEIGHT = 132;
    private static final double CARD_WIDTH = 40;

    private final SeatViewModel seat;
    private final RoomState state;
    private final IntConsumer onSit;
    private final Label bubble = new Label();
    private final HBox cards = new HBox(4);
    private final Label name = Ui.label("", "plate-name");
    private final Label detail = Ui.label("", "plate-stack");
    private final TimerRing timer = new TimerRing(13);
    private final HBox plate;
    private final VBox plateText;

    /**
     * @param seat what to show
     * @param state the room, for the turn clock and whether you are seated
     * @param onSit called with the seat number when the player asks to sit in this empty seat
     */
    public SeatNode(SeatViewModel seat, RoomState state, IntConsumer onSit) {
        this.seat = seat;
        this.state = state;
        this.onSit = onSit;
        setAlignment(Pos.BOTTOM_CENTER);
        setSpacing(3);
        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);

        bubble.setMinHeight(20);
        cards.setAlignment(Pos.CENTER);
        cards.setMinHeight(CARD_WIDTH * 1.4);
        name.setWrapText(false);
        detail.setWrapText(false);
        plateText = new VBox(1, name, detail);
        plateText.setAlignment(Pos.CENTER);
        plate = new HBox(8, plateText, timer);
        plate.getStyleClass().add("seat-plate");
        getChildren().addAll(bubble, cards, plate);

        InvalidationListener redraw = observable -> draw();
        seat.occupiedProperty().addListener(redraw);
        seat.usernameProperty().addListener(redraw);
        seat.stackProperty().addListener(redraw);
        seat.sittingOutProperty().addListener(redraw);
        seat.connectedProperty().addListener(redraw);
        seat.inHandProperty().addListener(redraw);
        seat.foldedProperty().addListener(redraw);
        seat.allInProperty().addListener(redraw);
        seat.turnProperty().addListener(redraw);
        seat.cards().addListener(redraw);
        seat.lastActionProperty().addListener(redraw);
        seat.wonProperty().addListener(redraw);
        seat.shownHandProperty().addListener(redraw);
        state.turnEndsAtMsProperty().addListener(redraw);
        state.yourSeatProperty().addListener(redraw);
        draw();
    }

    private void draw() {
        boolean occupied = seat.occupiedProperty().get();
        boolean inHand = seat.inHandProperty().get();
        boolean folded = seat.foldedProperty().get();
        boolean yours = occupied && seat.userIdProperty().get() == state.yourUserIdProperty().get();

        // ---- cards: face up if known, backs if still in the hand, nothing otherwise
        cards.getChildren().clear();
        if (!seat.cards().isEmpty()) {
            for (Card card : seat.cards()) {
                cards.getChildren().add(new CardNode(card, CARD_WIDTH));
            }
        } else if (inHand && !folded) {
            cards.getChildren().addAll(new CardNode(null, CARD_WIDTH), new CardNode(null, CARD_WIDTH));
        }
        cards.setOpacity(folded ? 0.35 : 1.0);

        // ---- the plate
        plate.getStyleClass().setAll("seat-plate");
        plateText.getChildren().setAll(name, detail);
        detail.getStyleClass().setAll("label", "plate-stack");
        if (!occupied && !inHand) {
            plate.getStyleClass().add("empty");
            name.setText(Formatters.seat(seat.seat()));
            detail.getStyleClass().setAll("label", "plate-note");
            detail.setText("Empty");
            if (!state.youAreSeated()) {
                plateText.getChildren().setAll(name,
                        Ui.button("Sit here", () -> onSit.accept(seat.seat()), "small"));
            }
        } else {
            name.setText(occupied ? (yours ? seat.usernameProperty().get() + " (you)" : seat.usernameProperty().get())
                    : "Left");
            if (!occupied) {
                detail.getStyleClass().setAll("label", "plate-note");
                detail.setText("");
            } else if (!seat.connectedProperty().get()) {
                detail.getStyleClass().setAll("label", "plate-note");
                detail.setText("Offline - " + Formatters.chips(seat.stackProperty().get()));
            } else if (seat.sittingOutProperty().get() && !inHand) {
                detail.getStyleClass().setAll("label", "plate-note");
                detail.setText(seat.stackProperty().get() == 0 ? "Out of chips" : "Sitting out");
            } else if (inHand && seat.allInProperty().get()) {
                detail.setText("All-in");
            } else {
                detail.setText(Formatters.chips(seat.stackProperty().get()));
            }
            if (seat.wonProperty().get() > 0) {
                plate.getStyleClass().add("winner");
            } else if (seat.turnProperty().get()) {
                plate.getStyleClass().add("turn");
            }
            if (folded || (!inHand && (seat.sittingOutProperty().get() || !seat.connectedProperty().get()))) {
                plate.getStyleClass().add("away");
            }
        }

        // ---- the bubble above: winnings, the hand shown, or the last action
        bubble.getStyleClass().setAll("label");
        if (seat.wonProperty().get() > 0) {
            bubble.getStyleClass().add("win-bubble");
            String hand = seat.shownHandProperty().get();
            bubble.setText("+" + Formatters.chips(seat.wonProperty().get()) + (hand.isEmpty() ? "" : "  " + hand));
        } else if (!seat.shownHandProperty().get().isEmpty()) {
            bubble.getStyleClass().add("action-bubble");
            bubble.setText(seat.shownHandProperty().get());
        } else if (!seat.lastActionProperty().get().isEmpty()) {
            bubble.getStyleClass().add("action-bubble");
            bubble.setText(seat.lastActionProperty().get());
        } else {
            bubble.setText("");
        }

        // ---- the timer
        long endsAt = state.turnEndsAtMsProperty().get();
        RoomSettingsInfo settings = state.settingsProperty().get();
        if (seat.turnProperty().get() && endsAt > 0 && settings != null) {
            timer.setManaged(true);
            timer.start(endsAt, settings.turnSeconds() * 1_000L);
        } else {
            timer.stop();
            timer.setManaged(false);
        }
    }

    /** Keeps the plate from stretching when a long name is shown. */
    @Override
    protected double computePrefWidth(double height) {
        return WIDTH;
    }

    static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }
}
