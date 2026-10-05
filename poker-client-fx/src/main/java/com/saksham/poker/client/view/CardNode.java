package com.saksham.poker.client.view;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Suit;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * One playing card, drawn with shapes and text: face up showing its rank and suit, or face down
 * showing its back. There are no image files.
 */
public final class CardNode extends StackPane {

    private static final Color FACE = Color.web("#f7f1e1");
    private static final Color EDGE = Color.web("#1b1b1b", 0.35);
    private static final Color RED = Color.web("#c2382c");
    private static final Color BLACK = Color.web("#1d2321");
    private static final Color BACK = Color.web("#7a2a2a");
    private static final Color BACK_INNER = Color.web("#f3ecd9", 0.35);

    /**
     * @param card the card to show face up, or null for a card lying face down
     * @param width the card's width in pixels; its height follows
     */
    public CardNode(Card card, double width) {
        double height = width * 1.4;
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);

        Rectangle body = new Rectangle(width, height);
        body.setArcWidth(width * 0.22);
        body.setArcHeight(width * 0.22);
        body.setStroke(EDGE);
        getChildren().add(body);

        if (card == null) {
            body.setFill(BACK);
            Rectangle inner = new Rectangle(width - 8, height - 8);
            inner.setArcWidth(width * 0.14);
            inner.setArcHeight(width * 0.14);
            inner.setFill(Color.TRANSPARENT);
            inner.setStroke(BACK_INNER);
            inner.setStrokeWidth(1.5);
            getChildren().add(inner);
            return;
        }

        body.setFill(FACE);
        Color ink = card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS ? RED : BLACK;
        Text rank = new Text(rankText(card));
        rank.setFont(Font.font("System", FontWeight.BOLD, width * 0.46));
        rank.setFill(ink);
        Text suit = new Text(suitSymbol(card.suit()));
        suit.setFont(Font.font("System", width * 0.44));
        suit.setFill(ink);
        StackPane.setAlignment(rank, Pos.TOP_CENTER);
        StackPane.setAlignment(suit, Pos.BOTTOM_CENTER);
        rank.setTranslateY(width * 0.06);
        suit.setTranslateY(-width * 0.08);
        getChildren().addAll(rank, suit);
    }

    private static String rankText(Card card) {
        char symbol = card.rank().symbol();
        return symbol == 'T' ? "10" : String.valueOf(symbol);
    }

    private static String suitSymbol(Suit suit) {
        return switch (suit) {
            case SPADES -> "♠";
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
        };
    }
}
