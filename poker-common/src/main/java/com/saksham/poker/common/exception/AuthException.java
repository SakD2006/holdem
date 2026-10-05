package com.saksham.poker.common.exception;

/** A problem with signing in, registering or a login token. */
public abstract class AuthException extends PokerException {

    private static final long serialVersionUID = 1L;

    protected AuthException(String message) {
        super(message);
    }
}
