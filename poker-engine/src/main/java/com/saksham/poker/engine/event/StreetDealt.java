package com.saksham.poker.engine.event;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.engine.hand.Street;
import java.util.List;

/** The flop, turn or river was dealt. */
public final class StreetDealt extends GameEvent {

    private final Street street;
    private final List<Card> cards;
    private final List<Card> board;

    public StreetDealt(Street street, List<Card> cards, List<Card> board) {
        this.street = street;
        this.cards = List.copyOf(cards);
        this.board = List.copyOf(board);
    }

    public Street street() {
        return street;
    }

    /** The cards just dealt: three for the flop, one for the turn or river. */
    public List<Card> cards() {
        return cards;
    }

    /** All community cards so far, including the ones just dealt. */
    public List<Card> board() {
        return board;
    }

    @Override
    public String describe() {
        String name = street.name().charAt(0) + street.name().substring(1).toLowerCase();
        return name + ": " + cards(cards) + (board.size() > cards.size() ? "  (board " + cards(board) + ")" : "");
    }
}
