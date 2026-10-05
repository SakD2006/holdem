package com.saksham.poker.common.action;

/** Give up the hand. */
public final class Fold extends PlayerAction {

    public Fold() {
        super(0);
    }

    @Override
    public ActionType type() {
        return ActionType.FOLD;
    }
}
