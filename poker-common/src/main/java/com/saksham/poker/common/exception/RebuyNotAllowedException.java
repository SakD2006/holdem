package com.saksham.poker.common.exception;

import com.saksham.poker.common.error.ErrorCode;

/** The room does not allow rebuys, or the player still has chips. */
public class RebuyNotAllowedException extends RoomException {

    private static final long serialVersionUID = 1L;

    public RebuyNotAllowedException(String message) {
        super(message);
    }

    @Override
    public ErrorCode code() {
        return ErrorCode.REBUY_NOT_ALLOWED;
    }
}
