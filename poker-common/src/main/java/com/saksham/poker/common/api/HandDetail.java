package com.saksham.poker.common.api;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * Everything needed to replay a finished hand: the answer to {@code GET /api/hands/{id}}.
 *
 * @param id the hand's id
 * @param roomCode the room it was played in
 * @param roomName that room's name
 * @param handNo the hand's number within the room
 * @param buttonSeat the seat that had the dealer button
 * @param board the community cards that were dealt
 * @param totalPot all chips paid out
 * @param startedAtMs when it began, in milliseconds since 1970 UTC
 * @param endedAtMs when it ended
 * @param players everyone dealt in, by seat
 * @param actions every blind and action, in order
 */
public record HandDetail(long id, String roomCode, String roomName, long handNo, int buttonSeat, List<Card> board,
        long totalPot, long startedAtMs, long endedAtMs, List<HandPlayerInfo> players,
        List<HandActionInfo> actions) {
}
