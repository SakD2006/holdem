package com.saksham.poker.common.protocol.dto;

/**
 * A player's position in the hand being played.
 *
 * @param seat the player's seat
 * @param stack chips behind
 * @param streetBet chips bet on the current street, not yet collected into a pot
 * @param folded true if they have folded
 * @param allIn true if they are still in with no chips left to bet
 */
public record HandSeatInfo(int seat, long stack, long streetBet, boolean folded, boolean allIn) {
}
