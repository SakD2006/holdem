package com.saksham.poker.common.action;

/** Open the betting on a street where nobody has bet yet. */
public final class Bet extends PlayerAction {

    /** @param amount the size of the bet; must be more than 0 */
    public Bet(long amount) {
        super(amount);
        if (amount <= 0) {
            throw new IllegalArgumentException("A bet must be more than 0, but was " + amount);
        }
    }

    @Override
    public ActionType type() {
        return ActionType.BET;
    }
}
