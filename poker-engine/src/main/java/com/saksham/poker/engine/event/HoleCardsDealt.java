package com.saksham.poker.engine.event;

import com.saksham.poker.common.card.Card;
import java.util.List;

/** A player was dealt their two hole cards. Visible only to that player. */
public final class HoleCardsDealt extends GameEvent {

    private final int seat;
    private final List<Card> cards;

    public HoleCardsDealt(int seat, List<Card> cards) {
        this.seat = seat;
        this.cards = List.copyOf(cards);
    }

    public int seat() {
        return seat;
    }

    public List<Card> cards() {
        return cards;
    }

    @Override
    public boolean isVisibleTo(int viewerSeat) {
        return viewerSeat == seat;
    }

    @Override
    public String describe() {
        return "Seat " + seat + " is dealt " + cards(cards);
    }
}
