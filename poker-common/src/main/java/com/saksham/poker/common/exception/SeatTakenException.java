package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** Another player already has this seat. */
public class SeatTakenException extends RoomException {

    private static final long serialVersionUID = 1L;

    public SeatTakenException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.SEAT_TAKEN;
    }
}
