package com.saksham.poker.common.api;

/**
 * One player's line on the leaderboard.
 *
 * @param rank their place, 1 being the biggest winner; players level on chips share a place
 * @param username their name
 * @param totalNet chips won less chips lost over every hand
 * @param handsPlayed hands they were dealt into
 * @param handsWon hands in which they were paid from a pot
 */
public record LeaderboardEntry(int rank, String username, long totalNet, long handsPlayed, long handsWon) {
}
