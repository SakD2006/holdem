package com.saksham.poker.common.error;

/** Every error the server can report to a client (SPEC §5). */
public enum ErrorCode {
    NOT_YOUR_TURN,
    INVALID_ACTION,
    INVALID_AMOUNT,
    ROOM_NOT_FOUND,
    ROOM_FULL,
    ROOM_CLOSED,
    SEAT_TAKEN,
    NOT_HOST,
    GAME_ALREADY_STARTED,
    NOT_ENOUGH_PLAYERS,
    REBUY_NOT_ALLOWED,
    NOT_IN_ROOM,
    ALREADY_IN_ROOM,
    INVALID_CREDENTIALS,
    USERNAME_TAKEN,
    MALFORMED_MESSAGE,
    UNAUTHORIZED,
    INTERNAL
}
