package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The login token is missing or has expired. */
public class UnauthorizedException extends AuthException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.UNAUTHORIZED;
    }
}
