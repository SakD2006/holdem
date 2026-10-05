package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The user is not in a room. */
public class NotInRoomException extends RoomException {

    private static final long serialVersionUID = 1L;

    public NotInRoomException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.NOT_IN_ROOM;
    }
}
