package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** A file could not be read or written. Unchecked: callers log it and carry on; it never crashes a room. */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The code the client receives for this error. */
    public ErrorCode code() {
        return ErrorCode.INTERNAL;
    }
}
