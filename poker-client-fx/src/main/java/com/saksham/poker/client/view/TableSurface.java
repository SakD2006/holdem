package com.saksham.poker.client.view;

import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.client.state.SeatViewModel;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.IntConsumer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.util.Duration;

/**
 * The felt: an oval table with the seats spaced round it, the community cards and pot in the middle,
 * and each player's bet in front of them. Seats are turned so that you always sit at the bottom.
 *
 * <p>It also plays the table's movement: community cards are dealt face down and turned over one at
 * a time, bets slide into the pot when a street ends, and the pot slides to whoever wins it.
 */
public final class TableSurface extends Pane {

    private static final double BOARD_CARD_WIDTH = 64;

    private final RoomState state;
    private final Ellipse rail = new Ellipse();
    private final Ellipse felt = new Ellipse();
    private final Ellipse line = new Ellipse();
    private final List<SeatNode> seatNodes = new ArrayList<>();
    private final List<Label> bets = new ArrayList<>();
    private final List<Label> dealerButtons = new ArrayList<>();
    private final HBox board = new HBox(8);
    private final Label pot = Ui.label("", "pot-label");
    private final Label sidePots = Ui.label("", "side-pot-label");
    private final VBox centre = new VBox(8);
    /** Chips in flight are drawn here, above everything else, and removed when they land. */
    private final Pane flights = new Pane();
    /** The pot total as displayed, which counts up or down to the real figure. */
    private final DoubleProperty potShown = new SimpleDoubleProperty();
    private Timeline potCount;

    // Where things are, worked out at layout and used to aim the moving chips.
    private double centreX;
    private double centreY;
    private final List<double[]> seatSpots = new ArrayList<>();
    private final List<double[]> betSpots = new ArrayList<>();

    /**
     * @param state the room to draw
     * @param onSit called with a seat number when the player asks to sit in an empty seat
     */
    public TableSurface(RoomState state, IntConsumer onSit) {
        this.state = state;
        rail.getStyleClass().add("rail");
        felt.getStyleClass().add("felt");
        line.getStyleClass().add("felt-line");
        board.setAlignment(Pos.CENTER);
        board.setMinHeight(Math.rint(BOARD_CARD_WIDTH * 1.4));
        centre.setAlignment(Pos.CENTER);
        centre.getChildren().addAll(pot, board, sidePots);
        flights.setMouseTransparent(true);
        flights.setManaged(false);
        getChildren().addAll(rail, felt, line, centre);

        potShown.addListener((property, was, now) -> pot.setText("Pot " + Formatters.chips(Math.round(
                now.doubleValue()))));
        buildSeats(onSit);
        state.seats().addListener((InvalidationListener) observable -> buildSeats(onSit));
        state.yourSeatProperty().addListener(observable -> requestLayout());
        state.board().addListener((ListChangeListener<Card>) change -> drawBoard());
        state.pots().addListener((InvalidationListener) observable -> drawPots());
        drawBoard();
        drawPots();
    }

    /** One seat node, one bet marker and one dealer button per seat. */
    private void buildSeats(IntConsumer onSit) {
        getChildren().removeAll(seatNodes);
        getChildren().removeAll(bets);
        getChildren().removeAll(dealerButtons);
        getChildren().remove(flights);
        seatNodes.clear();
        bets.clear();
        dealerButtons.clear();
        for (SeatViewModel seat : state.seats()) {
            int index = seatNodes.size();
            seatNodes.add(new SeatNode(seat, state, onSit));

            Label bet = chipLabel(Formatters.chips(seat.streetBetProperty().get()));
            bet.visibleProperty().bind(seat.streetBetProperty().greaterThan(0));
            seat.streetBetProperty().addListener((property, was, now) -> {
                bet.setText(Formatters.chips(now.longValue()));
                if (was.longValue() > 0 && now.longValue() == 0) {
                    // The street is over: the bet slides into the pot.
                    sweepToPot(index, was.longValue());
                }
                requestLayout();
            });
            bets.add(bet);

            // When a hand is won, chips travel from the pot to the winner, once the cards are shown.
            seat.wonProperty().addListener((property, was, now) -> {
                if (now.longValue() > 0 && was.longValue() == 0) {
                    payOut(index, now.longValue());
                }
            });
            seat.buttonProperty().addListener(observable -> requestLayout());

            Label dealer = new Label("D");
            dealer.getStyleClass().add("dealer-button");
            dealer.visibleProperty().bind(seat.buttonProperty());
            dealerButtons.add(dealer);
        }
        getChildren().addAll(bets);
        getChildren().addAll(dealerButtons);
        getChildren().addAll(seatNodes);
        getChildren().add(flights);
        drawPots();
        requestLayout();
    }

    private static Label chipLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("bet-chip");
        Circle chip = new Circle(7, Color.web("#d9b44a"));
        chip.setStroke(Color.web("#7a5f1d"));
        chip.setStrokeWidth(2);
        chip.getStrokeDashArray().addAll(3.0, 2.6);
        label.setGraphic(chip);
        label.setGraphicTextGap(6);
        return label;
    }

    // =====================================================================================
    // The community cards
    // =====================================================================================

    /**
     * Shows the board. New cards are dealt face down from above, one after another, and each turns
     * over as it lands; cards already there do not move.
     */
    private void drawBoard() {
        int already = board.getChildren().size();
        List<Card> cards = state.board();
        if (cards.size() < already) {
            board.getChildren().clear();
            already = 0;
        }
        // A whole board appearing at once is a reconnect, not a deal: just show it.
        boolean dealing = Motion.enabled && cards.size() - already <= 3 && state.handInProgressProperty().get();
        for (int i = already; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (!dealing) {
                board.getChildren().add(new CardNode(card, BOARD_CARD_WIDTH));
                continue;
            }
            CardNode node = new CardNode(null, BOARD_CARD_WIDTH);
            board.getChildren().add(node);
            long wait = (i - already) * Motion.STAGGER_MS;
            node.setOpacity(0);
            node.setTranslateY(-150);
            TranslateTransition slide = new TranslateTransition(Motion.DEAL, node);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition appear = new FadeTransition(Duration.millis(100), node);
            appear.setToValue(1);
            new SequentialTransition(new PauseTransition(Duration.millis(wait)),
                    new ParallelTransition(slide, appear)).play();
            node.flipTo(card, Duration.millis(wait + Motion.DEAL.toMillis() + 60));
        }
    }

    // =====================================================================================
    // The pot and the chips that move
    // =====================================================================================

    private void drawPots() {
        long total = state.chipsInPlay();
        pot.setVisible(total > 0);
        if (potCount != null) {
            potCount.stop();
        }
        if (Motion.enabled && total > 0 && potShown.get() > 0) {
            // Count to the new total rather than jumping to it.
            potCount = new Timeline(new KeyFrame(Duration.millis(320),
                    new KeyValue(potShown, total, Interpolator.EASE_OUT)));
            potCount.play();
        } else {
            potShown.set(total);
            pot.setText("Pot " + Formatters.chips(total));
        }
        if (state.pots().size() > 1) {
            StringJoiner parts = new StringJoiner("   ");
            for (int i = 0; i < state.pots().size(); i++) {
                PotInfo each = state.pots().get(i);
                parts.add((i == 0 ? "Main " : "Side ") + Formatters.chips(each.amount()));
            }
            sidePots.setText(parts.toString());
        } else {
            sidePots.setText("");
        }
    }

    /** Called when a bet changes, so the pot total in the middle keeps up. */
    void refreshPot() {
        drawPots();
    }

    private void sweepToPot(int seatIndex, long amount) {
        if (!Motion.enabled || seatIndex >= betSpots.size() || !state.handInProgressProperty().get()) {
            return;
        }
        double[] from = betSpots.get(seatIndex);
        fly(Formatters.chips(amount), from[0], from[1], centreX, centreY - 70, 0);
    }

    private void payOut(int seatIndex, long amount) {
        if (!Motion.enabled || seatIndex >= seatSpots.size()) {
            return;
        }
        double[] to = seatSpots.get(seatIndex);
        fly("+" + Formatters.chips(amount), centreX, centreY - 70, to[0], to[1] + 20,
                Motion.winnerDelayMs(state.handsShown()));
    }

    /** Sends a chip marker from one point to another, fading as it arrives. */
    private void fly(String text, double fromX, double fromY, double toX, double toY, long waitMs) {
        Label chips = chipLabel(text);
        chips.setManaged(false);
        chips.setOpacity(0);
        flights.getChildren().add(chips);
        chips.autosize();
        chips.relocate(fromX - chips.getWidth() / 2, fromY - chips.getHeight() / 2);
        TranslateTransition move = new TranslateTransition(Motion.CHIPS, chips);
        move.setByX(toX - fromX);
        move.setByY(toY - fromY);
        move.setInterpolator(Interpolator.EASE_BOTH);
        FadeTransition appear = new FadeTransition(Duration.millis(80), chips);
        appear.setToValue(1);
        FadeTransition vanish = new FadeTransition(Duration.millis(160), chips);
        vanish.setToValue(0);
        SequentialTransition flight = new SequentialTransition(new PauseTransition(Duration.millis(waitMs)),
                appear, move, vanish);
        flight.setOnFinished(event -> flights.getChildren().remove(chips));
        flight.play();
    }

    // =====================================================================================
    // Layout
    // =====================================================================================

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        double cx = width / 2;
        double cy = height / 2 - 6;
        centreX = cx;
        centreY = cy;
        flights.resizeRelocate(0, 0, width, height);

        // The seats sit on a larger oval than the felt, so the plates overlap its rail.
        double seatRx = Math.max(120, width / 2 - SeatNode.WIDTH / 2 - 14);
        double seatRy = Math.max(90, height / 2 - SeatNode.HEIGHT / 2 - 4);
        setOval(rail, cx, cy, seatRx - 40, seatRy - 42);
        setOval(felt, cx, cy, seatRx - 52, seatRy - 54);
        setOval(line, cx, cy, seatRx - 74, seatRy - 76);

        centre.autosize();
        centre.relocate(cx - centre.getWidth() / 2, cy - centre.getHeight() / 2);

        int count = seatNodes.size();
        int yours = state.yourSeatProperty().get();
        int base = yours == PlayerInfo.NO_SEAT ? 0 : yours;
        seatSpots.clear();
        betSpots.clear();
        for (int i = 0; i < count; i++) {
            // Your seat is at the bottom (90 degrees); the others follow clockwise from there.
            double angle = Math.toRadians(90 + 360.0 * Math.floorMod(i - base, count) / count);
            double sx = cx + seatRx * Math.cos(angle);
            double sy = cy + seatRy * Math.sin(angle);
            SeatNode seat = seatNodes.get(i);
            seat.resizeRelocate(sx - SeatNode.WIDTH / 2, sy - SeatNode.HEIGHT / 2, SeatNode.WIDTH, SeatNode.HEIGHT);
            seat.placedAt(cx - sx, cy - sy);
            seatSpots.add(new double[] {sx, sy});

            // The bet sits between the player and the middle, and the button beside it.
            Label bet = bets.get(i);
            bet.autosize();
            double bx = cx + (seatRx - 158) * Math.cos(angle);
            double by = cy + (seatRy - 128) * Math.sin(angle);
            bet.relocate(bx - bet.getWidth() / 2, by - bet.getHeight() / 2);
            betSpots.add(new double[] {bx, by});

            Label dealer = dealerButtons.get(i);
            dealer.autosize();
            double dAngle = angle + Math.toRadians(16);
            double dx = cx + (seatRx - 118) * Math.cos(dAngle);
            double dy = cy + (seatRy - 104) * Math.sin(dAngle);
            dealer.relocate(dx - 11, dy - 11);
        }
    }

    private static void setOval(Ellipse oval, double cx, double cy, double rx, double ry) {
        oval.setCenterX(cx);
        oval.setCenterY(cy);
        oval.setRadiusX(Math.max(10, rx));
        oval.setRadiusY(Math.max(10, ry));
    }
}
