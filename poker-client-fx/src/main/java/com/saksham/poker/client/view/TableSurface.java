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
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * The felt: an oval table with the seats spaced round it, the community cards and pot in the middle,
 * and each player's bet in front of them. Seats are turned so that you always sit at the bottom.
 */
public final class TableSurface extends Pane {

    private static final double BOARD_CARD_WIDTH = 58;

    /** Switched off when screens are drawn to pictures, which cannot wait for a card to fade in. */
    static boolean animate = true;

    private final RoomState state;
    private final Ellipse felt = new Ellipse();
    private final Ellipse line = new Ellipse();
    private final List<SeatNode> seatNodes = new ArrayList<>();
    private final List<Label> bets = new ArrayList<>();
    private final List<Label> dealerButtons = new ArrayList<>();
    private final HBox board = new HBox(8);
    private final Label pot = Ui.label("", "pot-label");
    private final Label sidePots = Ui.label("", "side-pot-label");
    private final VBox centre = new VBox(8);

    /**
     * @param state the room to draw
     * @param onSit called with a seat number when the player asks to sit in an empty seat
     */
    public TableSurface(RoomState state, IntConsumer onSit) {
        this.state = state;
        felt.getStyleClass().add("felt");
        line.getStyleClass().add("felt-line");
        board.setAlignment(Pos.CENTER);
        board.setMinHeight(BOARD_CARD_WIDTH * 1.4);
        centre.setAlignment(Pos.CENTER);
        centre.getChildren().addAll(pot, board, sidePots);
        getChildren().addAll(felt, line, centre);

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
        seatNodes.clear();
        bets.clear();
        dealerButtons.clear();
        for (SeatViewModel seat : state.seats()) {
            SeatNode node = new SeatNode(seat, state, onSit);
            seatNodes.add(node);

            Label bet = new Label();
            bet.getStyleClass().add("bet-chip");
            Circle chip = new Circle(7, Color.web("#d9b44a"));
            chip.setStroke(Color.web("#7a5f1d"));
            chip.setStrokeWidth(2);
            bet.setGraphic(chip);
            bet.setGraphicTextGap(6);
            bet.visibleProperty().bind(seat.streetBetProperty().greaterThan(0));
            seat.streetBetProperty().addListener((property, was, now) -> {
                bet.setText(Formatters.chips(now.longValue()));
                requestLayout();
            });
            bet.setText(Formatters.chips(seat.streetBetProperty().get()));
            bets.add(bet);

            Label dealer = new Label("D");
            dealer.getStyleClass().add("dealer-button");
            dealer.visibleProperty().bind(seat.buttonProperty());
            dealerButtons.add(dealer);
        }
        getChildren().addAll(bets);
        getChildren().addAll(dealerButtons);
        getChildren().addAll(seatNodes);
        drawPots();
        requestLayout();
    }

    private void drawBoard() {
        int already = board.getChildren().size();
        List<Card> cards = state.board();
        if (cards.size() < already) {
            board.getChildren().clear();
            already = 0;
        }
        for (int i = already; i < cards.size(); i++) {
            CardNode node = new CardNode(cards.get(i), BOARD_CARD_WIDTH);
            board.getChildren().add(node);
            if (!animate) {
                continue;
            }
            // A new card slides down into place as it fades in.
            FadeTransition fade = new FadeTransition(Duration.millis(260), node);
            fade.setFromValue(0);
            fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
            slide.setFromY(-18);
            slide.setToY(0);
            ParallelTransition deal = new ParallelTransition(fade, slide);
            deal.setDelay(Duration.millis(90L * (i - already)));
            node.setOpacity(0);
            deal.play();
        }
    }

    private void drawPots() {
        long total = state.chipsInPlay();
        pot.setVisible(total > 0);
        pot.setText("Pot " + Formatters.chips(total));
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

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        double cx = width / 2;
        double cy = height / 2 - 6;

        // The seats sit on a larger oval than the felt, so the plates overlap its rail.
        double seatRx = Math.max(120, width / 2 - SeatNode.WIDTH / 2 - 14);
        double seatRy = Math.max(90, height / 2 - SeatNode.HEIGHT / 2 - 6);
        felt.setCenterX(cx);
        felt.setCenterY(cy);
        felt.setRadiusX(seatRx - 46);
        felt.setRadiusY(seatRy - 44);
        line.setCenterX(cx);
        line.setCenterY(cy);
        line.setRadiusX(felt.getRadiusX() - 22);
        line.setRadiusY(felt.getRadiusY() - 22);

        centre.autosize();
        centre.relocate(cx - centre.getWidth() / 2, cy - centre.getHeight() / 2);

        int count = seatNodes.size();
        int yours = state.yourSeatProperty().get();
        int base = yours == PlayerInfo.NO_SEAT ? 0 : yours;
        for (int i = 0; i < count; i++) {
            // Your seat is at the bottom (90 degrees); the others follow clockwise from there.
            double angle = Math.toRadians(90 + 360.0 * Math.floorMod(i - base, count) / count);
            double sx = cx + seatRx * Math.cos(angle);
            double sy = cy + seatRy * Math.sin(angle);
            SeatNode seat = seatNodes.get(i);
            seat.resizeRelocate(sx - SeatNode.WIDTH / 2, sy - SeatNode.HEIGHT / 2, SeatNode.WIDTH, SeatNode.HEIGHT);

            // The bet sits between the player and the middle, and the button beside it.
            Label bet = bets.get(i);
            bet.autosize();
            double bx = cx + (seatRx - 150) * Math.cos(angle);
            double by = cy + (seatRy - 118) * Math.sin(angle);
            bet.relocate(bx - bet.getWidth() / 2, by - bet.getHeight() / 2);

            Label dealer = dealerButtons.get(i);
            dealer.autosize();
            double dAngle = angle + Math.toRadians(16);
            double dx = cx + (seatRx - 112) * Math.cos(dAngle);
            double dy = cy + (seatRy - 96) * Math.sin(dAngle);
            dealer.relocate(dx - 11, dy - 11);
        }
    }
}
