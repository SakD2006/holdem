package com.saksham.poker.common.action;

/** Increase the bet being faced. */
public final class Raise extends PlayerAction {

    /** @param toAmount the player's total bet on this street after raising; must be more than 0 */
    public Raise(long toAmount) {
        super(toAmount);
        if (toAmount <= 0) {
            throw new IllegalArgumentException("A raise must be to more than 0, but was " + toAmount);
        }
    }

    @Override
    public ActionType type() {
        return ActionType.RAISE;
    }
}
