package com.saksham.poker.engine.card;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Rank;
import com.saksham.poker.common.card.Suit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;

/** The 52 cards for one hand, in a fixed order decided when the deck is created. */
public final class Deck {

    public static final int SIZE = 52;

    private final Deque<Card> cards;

    /** @param order all 52 cards, each exactly once; the first is dealt first */
    public Deck(List<Card> order) {
        if (order.size() != SIZE || new HashSet<>(order).size() != SIZE) {
            throw new IllegalArgumentException(
                    "A deck needs all 52 cards exactly once, but got " + order.size()
                            + " cards, " + new HashSet<>(order).size() + " of them different.");
        }
        this.cards = new ArrayDeque<>(order);
    }

    /** All 52 cards unshuffled: clubs, diamonds, hearts, spades, each from two up to ace. */
    public static List<Card> standardOrder() {
        List<Card> cards = new ArrayList<>(SIZE);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        return cards;
    }

    /** Takes the top card. */
    public Card deal() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("The deck has no cards left.");
        }
        return cards.removeFirst();
    }

    /** Discards the top card face down, as is done before the flop, turn and river. */
    public void burn() {
        deal();
    }

    public int remaining() {
        return cards.size();
    }
}
