package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The host asked for a kind of computer player this server has not been set up to run. */
public class BotUnavailableException extends RoomException {

    private static final long serialVersionUID = 1L;

    public BotUnavailableException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.BOT_UNAVAILABLE;
    }
}
