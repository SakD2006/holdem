package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** Every seat in the room is taken. */
public class RoomFullException extends RoomException {

    private static final long serialVersionUID = 1L;

    public RoomFullException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.ROOM_FULL;
    }
}
