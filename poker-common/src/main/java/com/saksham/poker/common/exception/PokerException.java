package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/**
 * Root of the checked exceptions for game, room, login and protocol errors. Every subclass names the
 * {@link ErrorCode} that is sent to the client.
 */
public abstract class PokerException extends Exception {

    private static final long serialVersionUID = 1L;

    protected PokerException(String message) {
        super(message);
    }

    protected PokerException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The code the client receives for this error. */
    public abstract ErrorCode code();
}
