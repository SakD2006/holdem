package com.saksham.poker.common.action;

/** Put every remaining chip in. The engine turns it into the matching bet, call or raise. */
public final class AllIn extends PlayerAction {

    public AllIn() {
        super(0);
    }

    @Override
    public ActionType type() {
        return ActionType.ALL_IN;
    }
}
