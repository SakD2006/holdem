package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The user is already in a room and must leave it first. */
public class AlreadyInRoomException extends RoomException {

    private static final long serialVersionUID = 1L;

    public AlreadyInRoomException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.ALREADY_IN_ROOM;
    }
}
