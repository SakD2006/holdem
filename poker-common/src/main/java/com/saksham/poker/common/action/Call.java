package com.saksham.poker.common.action;

/** Match the current bet. The engine works out the amount, capped at the player's stack. */
public final class Call extends PlayerAction {

    public Call() {
        super(0);
    }

    @Override
    public ActionType type() {
        return ActionType.CALL;
    }
}
