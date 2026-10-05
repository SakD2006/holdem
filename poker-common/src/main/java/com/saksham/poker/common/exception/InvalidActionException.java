package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The action is not legal right now, such as checking when facing a bet. */
public class InvalidActionException extends GameRuleException {

    private static final long serialVersionUID = 1L;

    public InvalidActionException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.INVALID_ACTION;
    }
}
