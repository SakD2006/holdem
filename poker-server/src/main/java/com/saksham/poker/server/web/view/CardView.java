package com.saksham.poker.server.web.view;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Rank;
import java.util.ArrayList;
import java.util.List;

/**
 * A playing card ready to be drawn on a web page.
 *
 * @param rank what to print for the rank: 2 to 10, J, Q, K or A
 * @param suit the suit's symbol
 * @param colour "red" for hearts and diamonds, "black" for clubs and spades
 */
public record CardView(String rank, String suit, String colour) {

    public static CardView of(Card card) {
        String rank = card.rank() == Rank.TEN ? "10" : String.valueOf(card.rank().symbol());
        return switch (card.suit()) {
            case SPADES -> new CardView(rank, "♠", "black");
            case HEARTS -> new CardView(rank, "♥", "red");
            case DIAMONDS -> new CardView(rank, "♦", "red");
            case CLUBS -> new CardView(rank, "♣", "black");
        };
    }

    public static List<CardView> of(List<Card> cards) {
        List<CardView> views = new ArrayList<>();
        for (Card card : cards) {
            views.add(of(card));
        }
        return views;
    }
}
