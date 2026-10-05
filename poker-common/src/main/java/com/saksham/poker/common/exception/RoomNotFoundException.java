package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** No room has this code. */
public class RoomNotFoundException extends RoomException {

    private static final long serialVersionUID = 1L;

    public RoomNotFoundException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.ROOM_NOT_FOUND;
    }
}
