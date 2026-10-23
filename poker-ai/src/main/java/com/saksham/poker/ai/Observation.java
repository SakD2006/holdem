package com.saksham.poker.ai;

import com.saksham.poker.common.card.Card;
import java.util.Collections;
import java.util.List;

/**
 * Everything a bot knows at the moment it must act. All of it is what a person in the same seat
 * could see on their screen.
 *
 * @param holeCards the bot's own two cards
 * @param board the community cards so far: none, three, four or five
 * @param pot every chip already in the middle, including bets made on this street
 * @param toCall what it costs to stay in; 0 when checking is free
 * @param canCheck whether checking is allowed
 * @param canBet whether a first bet on this street is allowed
 * @param canRaise whether a raise is allowed
 * @param minRaiseTo the smallest total a bet or raise may make the bot's bet on this street
 * @param maxRaiseTo the largest: everything the bot has
 * @param stack the bot's chips not yet bet
 * @param streetBet what the bot has already put in on this street
 * @param bigBlind the big blind, the natural unit for sizing
 * @param playersInHand players who have not folded, the bot included
 * @param playersDealt players dealt into the hand
 * @param seatsAfterButton 0 for the dealer button, 1 for the next seat clockwise, and so on
 * @param raisesThisStreet bets and raises made on this street so far, by anyone
 * @param opponents the players still in against the bot, and what each has done this hand
 * @param aggressor true if the bot made the last raise before the flop
 */
public record Observation(List<Card> holeCards, List<Card> board, long pot, long toCall, boolean canCheck,
        boolean canBet, boolean canRaise, long minRaiseTo, long maxRaiseTo, long stack, long streetBet,
        long bigBlind, int playersInHand, int playersDealt, int seatsAfterButton, int raisesThisStreet,
        List<Opponent> opponents, boolean aggressor) {

    public Observation {
        holeCards = List.copyOf(holeCards);
        board = List.copyOf(board);
        opponents = List.copyOf(opponents);
    }

    /** An observation that says how many opponents remain but nothing about what they have done. */
    public Observation(List<Card> holeCards, List<Card> board, long pot, long toCall, boolean canCheck,
            boolean canBet, boolean canRaise, long minRaiseTo, long maxRaiseTo, long stack, long streetBet,
            long bigBlind, int playersInHand, int playersDealt, int seatsAfterButton, int raisesThisStreet) {
        this(holeCards, board, pot, toCall, canCheck, canBet, canRaise, minRaiseTo, maxRaiseTo, stack, streetBet,
                bigBlind, playersInHand, playersDealt, seatsAfterButton, raisesThisStreet,
                Collections.nCopies(Math.max(1, playersInHand - 1), Opponent.unknown()), false);
    }

    /** True before the flop. */
    public boolean preflop() {
        return board.isEmpty();
    }

    /** True once all five community cards are out. */
    public boolean river() {
        return board.size() == 5;
    }

    /** How many players are still in against the bot. */
    public int opponentCount() {
        return Math.max(1, playersInHand - 1);
    }

    /** The highest bet anyone has made on this street. */
    public long highestBet() {
        return streetBet + toCall;
    }

    /**
     * The share of the final pot the bot would be paying to call: it must win more often than this
     * for a call to pay. 0 when checking is free.
     */
    public double potOdds() {
        return toCall <= 0 ? 0 : (double) toCall / (pot + toCall);
    }

    /** True for the button and the seat before it, which act last after the flop. */
    public boolean latePosition() {
        return seatsAfterButton == 0 || seatsAfterButton == playersDealt - 1;
    }
}
