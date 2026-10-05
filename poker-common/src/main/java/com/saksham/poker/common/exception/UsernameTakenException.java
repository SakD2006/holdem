package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** Another account already uses this username. */
public class UsernameTakenException extends AuthException {

    private static final long serialVersionUID = 1L;

    public UsernameTakenException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.USERNAME_TAKEN;
    }
}
