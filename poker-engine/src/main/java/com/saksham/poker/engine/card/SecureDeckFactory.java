package com.saksham.poker.engine.card;

import com.saksham.poker.common.card.Card;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

/** Decks for real play, shuffled with {@link SecureRandom} so the order cannot be predicted. */
public final class SecureDeckFactory implements DeckFactory {

    private final SecureRandom random = new SecureRandom();

    @Override
    public Deck create() {
        List<Card> cards = Deck.standardOrder();
        Collections.shuffle(cards, random);
        return new Deck(cards);
    }
}
