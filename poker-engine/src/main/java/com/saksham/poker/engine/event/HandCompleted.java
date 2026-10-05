package com.saksham.poker.engine.event;

import com.saksham.poker.engine.hand.HandResult;

/** The hand is over and every chip is back in a stack. Always the last event of a hand. */
public final class HandCompleted extends GameEvent {

    private final HandResult result;

    public HandCompleted(HandResult result) {
        this.result = result;
    }

    public HandResult result() {
        return result;
    }

    @Override
    public String describe() {
        return "Hand over. Stacks " + result.endStacks();
    }
}
