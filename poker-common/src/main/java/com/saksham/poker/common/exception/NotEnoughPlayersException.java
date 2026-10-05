package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** A game needs at least two seated players. */
public class NotEnoughPlayersException extends RoomException {

    private static final long serialVersionUID = 1L;

    public NotEnoughPlayersException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.NOT_ENOUGH_PLAYERS;
    }
}
