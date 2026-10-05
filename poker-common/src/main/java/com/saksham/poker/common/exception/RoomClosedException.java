package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The room has been closed. */
public class RoomClosedException extends RoomException {

    private static final long serialVersionUID = 1L;

    public RoomClosedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.ROOM_CLOSED;
    }
}
