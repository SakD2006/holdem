package com.saksham.poker.common.protocol.dto;

/** Where a room is in its life: {@code WAITING → PLAYING ⇄ PAUSED → CLOSED} (SPEC §4.1). */
public enum RoomState {
    /** Players are gathering; the host has not started the game. */
    WAITING,
    /** Hands are being dealt. */
    PLAYING,
    /** The host paused the game between hands. */
    PAUSED,
    /** The room is finished and cannot be joined. */
    CLOSED
}
