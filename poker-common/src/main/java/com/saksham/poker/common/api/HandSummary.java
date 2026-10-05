package com.saksham.poker.common.api;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * One hand in a player's history, from that player's point of view.
 *
 * @param id the hand's id, for fetching its details
 * @param roomCode the room it was played in
 * @param roomName that room's name
 * @param handNo the hand's number within the room
 * @param endedAtMs when it ended, in milliseconds since 1970 UTC
 * @param yourCards the player's own hole cards
 * @param board the community cards that were dealt
 * @param totalPot all chips paid out in the hand
 * @param net chips the player won (positive) or lost (negative)
 * @param won true if the player was paid from any pot
 */
public record HandSummary(long id, String roomCode, String roomName, long handNo, long endedAtMs,
        List<Card> yourCards, List<Card> board, long totalPot, long net, boolean won) {
}
