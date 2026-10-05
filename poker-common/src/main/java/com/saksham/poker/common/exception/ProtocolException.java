package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** A message could not be read: bad JSON, an unknown type, or too large. */
public class ProtocolException extends PokerException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.MALFORMED_MESSAGE;
    }
}
