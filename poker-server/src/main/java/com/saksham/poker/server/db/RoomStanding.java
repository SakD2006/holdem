package com.saksham.poker.server.db;

/**
 * How one player did in one room.
 *
 * @param username their name
 * @param handsPlayed hands they were dealt into there
 * @param handsWon hands in which they were paid from a pot
 * @param net chips won less chips lost in that room
 */
public record RoomStanding(String username, long handsPlayed, long handsWon, long net) {
}
