package com.saksham.poker.client.net;

import com.saksham.poker.common.error.ErrorCode;

/** A request to the server failed. The message is fit to show the player as it is. */
public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    /**
     * @param code the server's error code, or null if the server could not be reached at all
     * @param message what went wrong and how to fix it
     */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** The server's error code, or null if the server could not be reached. */
    public ErrorCode code() {
        return code;
    }

    /** True if the server never answered: wrong address, server not running, or no network. */
    public boolean unreachable() {
        return code == null;
    }
}
