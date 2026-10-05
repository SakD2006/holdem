package com.saksham.poker.engine.hand;

import com.saksham.poker.common.card.Card;
import java.util.List;

/** One player's chips, cards and status during a hand. Read-only outside the engine. */
public final class SeatState {

    private final int seat;
    private final long startStack;
    private long stack;
    private List<Card> holeCards = List.of();
    private long streetBet;
    private long totalCommitted;
    private boolean folded;
    private boolean acted;
    private long betLevelWhenActed;

    SeatState(int seat, long stack) {
        this.seat = seat;
        this.startStack = stack;
        this.stack = stack;
    }

    public int seat() {
        return seat;
    }

    /** Chips the player had before the hand began. */
    public long startStack() {
        return startStack;
    }

    /** Chips still behind, not yet put in the pot. */
    public long stack() {
        return stack;
    }

    public List<Card> holeCards() {
        return holeCards;
    }

    /** Chips put in on the current street, not yet collected into the pot. */
    public long streetBet() {
        return streetBet;
    }

    /** Chips put in over the whole hand, less any uncalled bet handed back. */
    public long totalCommitted() {
        return totalCommitted;
    }

    public boolean folded() {
        return folded;
    }

    /** Still in the hand with no chips left to bet. */
    public boolean allIn() {
        return !folded && stack == 0;
    }

    /** Still in the hand and has chips to bet with. */
    public boolean canBet() {
        return !folded && stack > 0;
    }

    // ---- changes, made only by the engine

    void deal(List<Card> cards) {
        this.holeCards = List.copyOf(cards);
    }

    void pay(long amount) {
        stack -= amount;
        streetBet += amount;
        totalCommitted += amount;
    }

    void refund(long amount) {
        stack += amount;
        streetBet -= amount;
        totalCommitted -= amount;
    }

    void win(long amount) {
        stack += amount;
    }

    void fold() {
        folded = true;
    }

    /** Has acted on this street since the betting was last opened to them. */
    boolean acted() {
        return acted;
    }

    /** The bet everyone had to match when this player last acted on this street. */
    long betLevelWhenActed() {
        return betLevelWhenActed;
    }

    void markActed(long betLevel) {
        acted = true;
        betLevelWhenActed = betLevel;
    }

    void startStreet() {
        streetBet = 0;
        acted = false;
        betLevelWhenActed = 0;
    }
}
