package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The game was started a second time. */
public class GameAlreadyStartedException extends RoomException {

    private static final long serialVersionUID = 1L;

    public GameAlreadyStartedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.GAME_ALREADY_STARTED;
    }
}
