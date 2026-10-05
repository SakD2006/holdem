package com.saksham.poker.common.api;

/**
 * One step of a finished hand.
 *
 * @param seq its position in the hand, starting at 1
 * @param seat the seat that acted
 * @param username who that was
 * @param street PREFLOP, FLOP, TURN or RIVER
 * @param action POST_SB, POST_BB, FOLD, CHECK, CALL, BET or RAISE
 * @param amount chips the action put in
 */
public record HandActionInfo(int seq, int seat, String username, String street, String action, long amount) {
}
