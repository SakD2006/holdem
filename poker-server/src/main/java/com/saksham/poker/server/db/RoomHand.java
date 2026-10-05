package com.saksham.poker.server.db;

import com.saksham.poker.common.card.Card;
import java.time.Instant;
import java.util.List;

/**
 * One line of a room's list of hands. It holds nothing private: no hole cards.
 *
 * @param id the hand's id, for its replay
 * @param handNo the hand's number within the room
 * @param board the community cards that were dealt
 * @param totalPot all chips paid out
 * @param endedAt when it ended
 * @param winners the names of the players paid from a pot, separated by commas
 */
public record RoomHand(long id, long handNo, List<Card> board, long totalPot, Instant endedAt, String winners) {
}
