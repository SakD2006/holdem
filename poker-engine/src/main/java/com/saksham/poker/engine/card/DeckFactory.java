package com.saksham.poker.engine.card;

/**
 * Supplies the deck for each hand. The engine never shuffles by itself, so a hand is fully decided by
 * the deck it is given and the actions played.
 */
public interface DeckFactory {

    /** A new full deck for one hand. */
    Deck create();
}
