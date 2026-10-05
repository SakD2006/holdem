package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** Only the room host may do this. */
public class NotHostException extends RoomException {

    private static final long serialVersionUID = 1L;

    public NotHostException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.NOT_HOST;
    }
}
