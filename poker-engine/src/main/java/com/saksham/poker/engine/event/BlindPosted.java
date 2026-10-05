package com.saksham.poker.engine.event;

/** A player posted the small or big blind. */
public final class BlindPosted extends GameEvent {

    private final int seat;
    private final long amount;
    private final boolean bigBlind;
    private final boolean allIn;

    public BlindPosted(int seat, long amount, boolean bigBlind, boolean allIn) {
        this.seat = seat;
        this.amount = amount;
        this.bigBlind = bigBlind;
        this.allIn = allIn;
    }

    public int seat() {
        return seat;
    }

    /** Chips posted, which is less than the blind when the player could not cover it. */
    public long amount() {
        return amount;
    }

    /** True for the big blind, false for the small blind. */
    public boolean bigBlind() {
        return bigBlind;
    }

    public boolean allIn() {
        return allIn;
    }

    @Override
    public String describe() {
        return "Seat " + seat + " posts the " + (bigBlind ? "big" : "small") + " blind " + amount
                + (allIn ? " and is all-in" : "");
    }
}
