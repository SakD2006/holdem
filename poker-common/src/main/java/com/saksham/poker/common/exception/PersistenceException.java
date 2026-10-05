package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** A database operation failed. Unchecked: callers log it and carry on; it never crashes a room. */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The code the client receives for this error. */
    public ErrorCode code() {
        return ErrorCode.INTERNAL;
    }
}
