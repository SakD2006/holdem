package com.saksham.poker.common.exception;

/** A request about a room could not be carried out. */
public abstract class RoomException extends PokerException {

    private static final long serialVersionUID = 1L;

    protected RoomException(String message) {
        super(message);
    }
}
