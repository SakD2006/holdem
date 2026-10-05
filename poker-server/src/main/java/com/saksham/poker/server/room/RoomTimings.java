package com.saksham.poker.server.room;

/**
 * The waits a room uses. A wait of 0 means "at once", which tests and the bot soak run use.
 *
 * @param betweenHandsMs the gap between the end of one hand and the start of the next
 * @param runOutPauseMs the pause before each card when the board is dealt with everyone all-in
 * @param reconnectGraceMs how long a disconnected player keeps playing before being sat out
 * @param idleCloseMs how long a room survives with nobody connected
 */
public record RoomTimings(long betweenHandsMs, long runOutPauseMs, long reconnectGraceMs, long idleCloseMs) {

    /** The defaults from SPEC §4.4. */
    public static final RoomTimings DEFAULT = new RoomTimings(3_000, 1_000, 60_000, 30 * 60_000L);
}
