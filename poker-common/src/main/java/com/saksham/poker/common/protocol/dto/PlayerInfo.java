package com.saksham.poker.common.protocol.dto;

/**
 * One person in a room.
 *
 * @param userId the player's account
 * @param username the name to show
 * @param seat their seat number, or {@link #NO_SEAT} if they have not sat down
 * @param stack their chips
 * @param sittingOut true if they are seated but not being dealt in
 * @param connected false while their connection is lost
 * @param bot true for a computer player
 */
public record PlayerInfo(long userId, String username, int seat, long stack, boolean sittingOut, boolean connected,
        boolean bot) {

    /** The seat number of a player who is in the room but has not sat down. */
    public static final int NO_SEAT = -1;

    /** A person: the usual case, and the one almost every test wants. */
    public PlayerInfo(long userId, String username, int seat, long stack, boolean sittingOut, boolean connected) {
        this(userId, username, seat, stack, sittingOut, connected, false);
    }

    public boolean seated() {
        return seat != NO_SEAT;
    }
}
