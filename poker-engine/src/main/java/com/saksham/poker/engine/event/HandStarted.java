package com.saksham.poker.engine.event;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** A new hand has begun. */
public final class HandStarted extends GameEvent {

    private final int buttonSeat;
    private final int smallBlindSeat;
    private final int bigBlindSeat;
    private final long smallBlind;
    private final long bigBlind;
    private final Map<Integer, Long> stacks;

    public HandStarted(int buttonSeat, int smallBlindSeat, int bigBlindSeat, long smallBlind,
            long bigBlind, Map<Integer, Long> stacks) {
        this.buttonSeat = buttonSeat;
        this.smallBlindSeat = smallBlindSeat;
        this.bigBlindSeat = bigBlindSeat;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.stacks = Collections.unmodifiableMap(new TreeMap<>(stacks));
    }

    public int buttonSeat() {
        return buttonSeat;
    }

    public int smallBlindSeat() {
        return smallBlindSeat;
    }

    public int bigBlindSeat() {
        return bigBlindSeat;
    }

    public long smallBlind() {
        return smallBlind;
    }

    public long bigBlind() {
        return bigBlind;
    }

    /** Chips each seat starts the hand with, before blinds. */
    public Map<Integer, Long> stacks() {
        return stacks;
    }

    @Override
    public String describe() {
        return "Hand starts. Blinds " + smallBlind + "/" + bigBlind + ", button on seat " + buttonSeat
                + ", stacks " + stacks;
    }
}
