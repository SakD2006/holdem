package com.saksham.poker.server.db;

import com.saksham.poker.common.card.Card;
import java.time.Instant;
import java.util.List;

/**
 * Everything worth keeping about a finished hand. A room builds one when a hand ends and hands it
 * over; saving it is somebody else's job, on another thread.
 *
 * @param roomCode the room it was played in
 * @param roomName that room's name
 * @param handNo the hand's number within the room, starting at 1
 * @param smallBlind the small blind
 * @param bigBlind the big blind
 * @param buttonSeat the seat that had the dealer button
 * @param board the community cards that were dealt, none to five
 * @param totalPot all chips paid out
 * @param startedAt when the hand began
 * @param endedAt when it ended
 * @param players everyone dealt in, in seat order
 * @param actions every blind and action, in order
 */
public record HandRecord(String roomCode, String roomName, long handNo, long smallBlind, long bigBlind,
        int buttonSeat, List<Card> board, long totalPot, Instant startedAt, Instant endedAt,
        List<PlayerRecord> players, List<ActionRecord> actions) {

    public HandRecord {
        board = List.copyOf(board);
        players = List.copyOf(players);
        actions = List.copyOf(actions);
    }

    /**
     * One player's part in the hand.
     *
     * @param userId their account
     * @param username their name
     * @param seat their seat
     * @param holeCards their two hole cards; private unless {@code showedDown}
     * @param startStack chips before the hand
     * @param endStack chips after it
     * @param net chips won (positive) or lost (negative)
     * @param showedDown true if the cards were revealed at showdown
     * @param won true if they were paid from any pot
     */
    public record PlayerRecord(long userId, String username, int seat, List<Card> holeCards, long startStack,
            long endStack, long net, boolean showedDown, boolean won) {

        public PlayerRecord {
            holeCards = List.copyOf(holeCards);
        }
    }

    /**
     * One blind or action.
     *
     * @param seq its position in the hand, starting at 1
     * @param userId who acted
     * @param seat their seat
     * @param street PREFLOP, FLOP, TURN or RIVER
     * @param action POST_SB, POST_BB, FOLD, CHECK, CALL, BET or RAISE
     * @param amount chips the action put in
     * @param streetTotal the player's total bet on that street afterwards; kept for the readable
     *     hand history ("raises to 300"), not stored in the database
     * @param allIn true if the action left the player with no chips; also for the hand history only
     */
    public record ActionRecord(int seq, long userId, int seat, String street, String action, long amount,
            long streetTotal, boolean allIn) {
    }
}
