package com.saksham.poker.client.view;

import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.client.state.SeatViewModel;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * One seat at the table: the player's cards, name and chips, what they last did, and a timer while
 * it is their turn. It watches its {@link SeatViewModel} and redraws itself on any change.
 *
 * <p>The cards move rather than jump: they are dealt in from the middle of the table, turn over when
 * they become known, and slide away when the player folds. When a hand ends, the winner is lit up
 * only after every hand has been turned over.
 */
public final class SeatNode extends VBox {

    static final double WIDTH = 150;
    static final double HEIGHT = 146;
    private static final double CARD_WIDTH = 44;
    private static final double OWN_CARD_WIDTH = 50;

    /** What the card area is showing. */
    private enum Showing { NOTHING, BACKS, FACES }

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

    private Showing showing = Showing.NOTHING;
    private List<Card> shownCards = List.of();
    /** Where cards are dealt from, relative to this seat: the middle of the table. */
    private double dealFromX;
    private double dealFromY;
    /** True once the winner (or loser) of the finished hand may be shown as such. */
    private boolean resultShown;
    private PauseTransition resultWait;
    /** The stack as last displayed; held while a result is being revealed, so it gives nothing away. */
    private long stackShown;

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
        cards.setMinHeight(Math.rint(OWN_CARD_WIDTH * 1.4));
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
        state.handInProgressProperty().addListener(redraw);
        draw();
    }

    /**
     * Tells the seat where the middle of the table is, as an offset from its own centre, so its
     * cards can be dealt from there.
     */
    void placedAt(double fromX, double fromY) {
        dealFromX = fromX;
        dealFromY = fromY;
    }

    /** How long after a hand starts this seat's given card (0 or 1) is dealt, in milliseconds. */
    private long dealWaitMs(int card) {
        return (long) (seat.dealPositionProperty().get() + card * Math.max(1, state.playersInHand())) * 60;
    }

    private boolean yours() {
        return seat.occupiedProperty().get() && seat.userIdProperty().get() == state.yourUserIdProperty().get();
    }

    private void draw() {
        drawCards();
        drawResultTiming();
        drawPlate();
        drawBubble();
        drawTimer();
    }

    // =====================================================================================
    // Cards
    // =====================================================================================

    private void drawCards() {
        boolean inHand = seat.inHandProperty().get();
        boolean folded = seat.foldedProperty().get();
        List<Card> known = new ArrayList<>(seat.cards());
        Showing wanted = !known.isEmpty() ? Showing.FACES : inHand && !folded ? Showing.BACKS : Showing.NOTHING;
        double cardWidth = yours() ? OWN_CARD_WIDTH : CARD_WIDTH;

        if (wanted == Showing.NOTHING) {
            if (showing != Showing.NOTHING && folded && inHand && Motion.enabled) {
                muck();
            } else {
                cards.getChildren().clear();
            }
        } else if (wanted == Showing.BACKS && showing != Showing.BACKS) {
            cards.getChildren().setAll(new CardNode(null, cardWidth), new CardNode(null, cardWidth));
            // Only a hand that has just begun is dealt; joining mid-hand shows the cards in place.
            if (showing == Showing.NOTHING && "PREFLOP".equals(state.streetProperty().get())
                    && state.board().isEmpty()) {
                dealIn();
            }
        } else if (wanted == Showing.FACES && !(showing == Showing.FACES && known.equals(shownCards))) {
            if (showing == Showing.BACKS && cards.getChildren().size() == known.size()) {
                turnOver(known);
            } else {
                cards.getChildren().clear();
                for (Card card : known) {
                    cards.getChildren().add(new CardNode(card, cardWidth));
                }
            }
        }
        showing = wanted;
        shownCards = known;

        // A folded hand you can still see (your own) is greyed; so is a hand that lost at showdown.
        boolean lost = resultShown && seat.wonProperty().get() == 0 && !seat.shownHandProperty().get().isEmpty();
        double opacity = folded ? 0.35 : lost ? 0.5 : 1.0;
        if (wanted != Showing.NOTHING) {
            cards.setOpacity(opacity);
        }
    }

    /** Slides each card in from the middle of the table, one round at a time, as a dealer would. */
    private void dealIn() {
        if (!Motion.enabled) {
            return;
        }
        for (int i = 0; i < cards.getChildren().size(); i++) {
            Node card = cards.getChildren().get(i);
            long wait = dealWaitMs(i);
            card.setOpacity(0);
            card.setTranslateX(dealFromX);
            card.setTranslateY(dealFromY);
            TranslateTransition slide = new TranslateTransition(Motion.DEAL, card);
            slide.setToX(0);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition appear = new FadeTransition(Duration.millis(90), card);
            appear.setToValue(1);
            new SequentialTransition(new PauseTransition(Duration.millis(wait)),
                    new ParallelTransition(slide, appear)).play();
        }
    }

    /** Turns face-down cards face up: your own once dealt, everyone's in turn at showdown. */
    private void turnOver(List<Card> known) {
        int order = seat.revealOrderProperty().get();
        long wait;
        if (order >= 0) {
            wait = order * Motion.REVEAL_GAP_MS;
        } else {
            // Your own cards: wait for the deal to reach you first.
            wait = dealWaitMs(1) + (long) Motion.DEAL.toMillis() + 80;
        }
        for (int i = 0; i < known.size(); i++) {
            ((CardNode) cards.getChildren().get(i)).flipTo(known.get(i),
                    Duration.millis(Motion.enabled ? wait + i * Motion.STAGGER_MS : 0));
        }
    }

    /** A folded hand slides towards the middle and fades. */
    private void muck() {
        List<Node> leaving = new ArrayList<>(cards.getChildren());
        for (Node card : leaving) {
            TranslateTransition slide = new TranslateTransition(Motion.MUCK, card);
            slide.setByX(dealFromX * 0.25);
            slide.setByY(dealFromY * 0.25);
            FadeTransition fade = new FadeTransition(Motion.MUCK, card);
            fade.setToValue(0);
            ParallelTransition away = new ParallelTransition(slide, fade);
            away.setOnFinished(event -> cards.getChildren().remove(card));
            away.play();
        }
    }

    // =====================================================================================
    // The result of a hand
    // =====================================================================================

    /**
     * When a hand ends, holds back the winner's highlight until every hand has been turned over, so
     * the result is not given away before the cards are seen.
     */
    private void drawResultTiming() {
        boolean ended = !state.handInProgressProperty().get()
                && (seat.wonProperty().get() > 0 || !seat.shownHandProperty().get().isEmpty());
        if (!ended) {
            resultShown = false;
            if (resultWait != null) {
                resultWait.stop();
                resultWait = null;
            }
            return;
        }
        if (resultShown || resultWait != null) {
            return;
        }
        long wait = Motion.winnerDelayMs(state.handsShown());
        if (wait <= 0) {
            resultShown = true;
            return;
        }
        resultWait = new PauseTransition(Duration.millis(wait));
        resultWait.setOnFinished(event -> {
            resultWait = null;
            resultShown = true;
            draw();
            if (seat.wonProperty().get() > 0) {
                celebrate();
            }
        });
        resultWait.play();
    }

    /** The winner's plate swells for a moment and the winnings pop up above it. */
    private void celebrate() {
        ScaleTransition swell = new ScaleTransition(Duration.millis(170), plate);
        swell.setToX(1.08);
        swell.setToY(1.08);
        swell.setAutoReverse(true);
        swell.setCycleCount(2);
        swell.play();
        bubble.setScaleX(0.5);
        bubble.setScaleY(0.5);
        bubble.setOpacity(0);
        ScaleTransition pop = new ScaleTransition(Duration.millis(220), bubble);
        pop.setToX(1);
        pop.setToY(1);
        pop.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition appear = new FadeTransition(Duration.millis(160), bubble);
        appear.setToValue(1);
        new ParallelTransition(pop, appear).play();
    }

    // =====================================================================================
    // Name plate, bubble and timer
    // =====================================================================================

    private void drawPlate() {
        boolean occupied = seat.occupiedProperty().get();
        boolean inHand = seat.inHandProperty().get();
        boolean folded = seat.foldedProperty().get();
        boolean winner = resultShown && seat.wonProperty().get() > 0;

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
            return;
        }
        // No "(you)" label: your seat is always the one at the bottom, and the room is tight.
        name.setText(occupied ? seat.usernameProperty().get() : "Left");
        if (!occupied) {
            detail.getStyleClass().setAll("label", "plate-note");
            detail.setText("");
        } else if (!seat.connectedProperty().get()) {
            detail.getStyleClass().setAll("label", "plate-note");
            detail.setText("Offline - " + Formatters.chips(seat.stackProperty().get()));
        } else if (seat.sittingOutProperty().get() && !inHand) {
            detail.getStyleClass().setAll("label", "plate-note");
            detail.setText(seat.stackProperty().get() == 0 ? "Out of chips" : "Sitting out");
        } else if (inHand && seat.allInProperty().get() && state.handInProgressProperty().get()) {
            detail.setText("All-in");
        } else {
            // While the cards are still being turned over, keep the old figure on show: the new
            // stack would say who won before the hands have been seen.
            if (resultWait == null) {
                stackShown = seat.stackProperty().get();
            }
            detail.setText(Formatters.chips(stackShown));
        }
        if (winner) {
            plate.getStyleClass().add("winner");
        } else if (seat.turnProperty().get()) {
            plate.getStyleClass().add("turn");
        }
        if (folded || (!inHand && (seat.sittingOutProperty().get() || !seat.connectedProperty().get()))) {
            plate.getStyleClass().add("away");
        }
    }

    /** The bubble above the cards: winnings, the hand shown, or the last action. */
    private void drawBubble() {
        bubble.getStyleClass().setAll("label");
        String hand = seat.shownHandProperty().get();
        if (resultShown && seat.wonProperty().get() > 0) {
            bubble.getStyleClass().add("win-bubble");
            bubble.setText("+" + Formatters.chips(seat.wonProperty().get()) + (hand.isEmpty() ? "" : "  " + hand));
        } else if (!hand.isEmpty() && (resultShown || !Motion.enabled)) {
            bubble.getStyleClass().add("action-bubble");
            bubble.setText(hand);
        } else if (!seat.lastActionProperty().get().isEmpty() && hand.isEmpty()) {
            bubble.getStyleClass().add("action-bubble");
            bubble.setText(seat.lastActionProperty().get());
        } else {
            bubble.setText("");
        }
    }

    private void drawTimer() {
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
