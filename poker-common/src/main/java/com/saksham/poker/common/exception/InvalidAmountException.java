package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The bet or raise amount is outside the legal range. */
public class InvalidAmountException extends GameRuleException {

    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.INVALID_AMOUNT;
    }
}
