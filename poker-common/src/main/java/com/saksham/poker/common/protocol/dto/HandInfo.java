package com.saksham.poker.common.protocol.dto;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * The public state of the hand being played, for a snapshot.
 *
 * @param handNo the hand's number in this room
 * @param buttonSeat the seat with the dealer button
 * @param smallBlindSeat the seat that posted the small blind
 * @param bigBlindSeat the seat that posted the big blind
 * @param street PREFLOP, FLOP, TURN, RIVER or SHOWDOWN
 * @param board the community cards dealt so far
 * @param pots the pots collected so far, main pot first
 * @param seats every player dealt into the hand
 * @param turn whose turn it is, or null when nobody is being waited on
 */
public record HandInfo(
        long handNo,
        int buttonSeat,
        int smallBlindSeat,
        int bigBlindSeat,
        String street,
        List<Card> board,
        List<PotInfo> pots,
        List<HandSeatInfo> seats,
        TurnInfo turn) {

    public HandInfo {
        board = List.copyOf(board);
        pots = List.copyOf(pots);
        seats = List.copyOf(seats);
    }
}
