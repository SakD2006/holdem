package com.saksham.poker.engine.event;

/** The part of a bet that nobody called went back to the player who made it. */
public final class UncalledBetReturned extends GameEvent {

    private final int seat;
    private final long amount;

    public UncalledBetReturned(int seat, long amount) {
        this.seat = seat;
        this.amount = amount;
    }

    public int seat() {
        return seat;
    }

    public long amount() {
        return amount;
    }

    @Override
    public String describe() {
        return "Uncalled bet of " + amount + " returned to seat " + seat;
    }
}
