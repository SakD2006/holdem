package com.saksham.poker.common.exception;

/** A player tried something the rules of the hand do not allow. */
public abstract class GameRuleException extends PokerException {

    private static final long serialVersionUID = 1L;

    protected GameRuleException(String message) {
        super(message);
    }
}
