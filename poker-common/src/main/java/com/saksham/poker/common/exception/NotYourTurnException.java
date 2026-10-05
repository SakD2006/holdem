package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The player acted when it was not their turn. */
public class NotYourTurnException extends GameRuleException {

    private static final long serialVersionUID = 1L;

    public NotYourTurnException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.NOT_YOUR_TURN;
    }
}
