package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** A request was understood but its contents are not acceptable, such as a username that is too short. */
public class InvalidRequestException extends PokerException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.INVALID_REQUEST;
    }
}
