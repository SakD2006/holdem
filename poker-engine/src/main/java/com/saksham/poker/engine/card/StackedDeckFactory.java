package com.saksham.poker.engine.card;

import com.saksham.poker.common.card.Card;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Decks in a chosen order, for tests and demos. The chosen cards come off the top first; the rest of
 * the deck follows in {@link Deck#standardOrder()}.
 */
public final class StackedDeckFactory implements DeckFactory {

    private final List<Card> order;

    /** @param top the cards to deal first, in order, with no repeats */
    public StackedDeckFactory(List<Card> top) {
        if (new HashSet<>(top).size() != top.size()) {
            throw new IllegalArgumentException("A stacked deck cannot repeat a card: " + top);
        }
        List<Card> cards = new ArrayList<>(top);
        for (Card card : Deck.standardOrder()) {
            if (!top.contains(card)) {
                cards.add(card);
            }
        }
        this.order = List.copyOf(cards);
    }

    /** @param top the cards to deal first, as text such as {@code "Ah Kd 7c"} */
    public static StackedDeckFactory of(String top) {
        return new StackedDeckFactory(Card.parseAll(top));
    }

    @Override
    public Deck create() {
        return new Deck(order);
    }
}
