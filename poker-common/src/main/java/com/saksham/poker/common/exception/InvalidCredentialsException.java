package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The username or password is wrong. */
public class InvalidCredentialsException extends AuthException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.INVALID_CREDENTIALS;
    }
}
