package com.saksham.poker.common.protocol.dto;

/**
 * Whose turn it is and what they may do. The fields match the {@code ACTION_REQUIRED} message.
 *
 * @param seat the seat being waited on
 * @param turnId sent back in {@code ACTION}
 * @param canCheck true when there is nothing to call
 * @param callAmount chips a call would add; 0 when there is nothing to call
 * @param canBet true when the player may open the betting
 * @param canRaise true when the player may raise a bet
 * @param minRaiseTo smallest bet, or smallest total to raise to; 0 if neither is allowed
 * @param maxRaiseTo largest bet or total to raise to; 0 if neither is allowed
 * @param deadlineEpochMs when the turn times out, in milliseconds since 1970 UTC
 */
public record TurnInfo(
        int seat,
        long turnId,
        boolean canCheck,
        long callAmount,
        boolean canBet,
        boolean canRaise,
        long minRaiseTo,
        long maxRaiseTo,
        long deadlineEpochMs) {
}
