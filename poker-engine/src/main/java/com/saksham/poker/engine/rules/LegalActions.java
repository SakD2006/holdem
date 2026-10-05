package com.saksham.poker.engine.rules;

/**
 * What a player may do right now.
 *
 * @param canFold true whenever it is this player's turn
 * @param canCheck true when there is nothing to call
 * @param callAmount chips a call would add, already capped at the player's stack; 0 when there is
 *     nothing to call
 * @param canBet true when nobody has bet on this street and the player may open the betting
 * @param canRaise true when there is a bet and the player may raise it
 * @param minRaiseTo the smallest legal bet, or the smallest total to raise to; 0 when the player can
 *     neither bet nor raise. It is less than a full bet or raise only when it is the player's whole
 *     stack.
 * @param maxRaiseTo the largest legal bet or total to raise to, which puts the player all-in; 0 when
 *     the player can neither bet nor raise
 */
public record LegalActions(
        boolean canFold,
        boolean canCheck,
        long callAmount,
        boolean canBet,
        boolean canRaise,
        long minRaiseTo,
        long maxRaiseTo) {

    /** Nothing is allowed: it is not this player's turn. */
    public static final LegalActions NONE = new LegalActions(false, false, 0, false, false, 0, 0);

    /** True when it is this player's turn. */
    public boolean canAct() {
        return canFold;
    }
}
