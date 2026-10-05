package com.saksham.poker.common.action;

/** Pass the action without betting; legal only when there is no bet to call. */
public final class Check extends PlayerAction {

    public Check() {
        super(0);
    }

    @Override
    public ActionType type() {
        return ActionType.CHECK;
    }
}
