package com.saksham.poker.ai;

import com.saksham.poker.common.action.ActionType;
import java.util.List;

/**
 * The public story of the current hand, in order: what a player at the table could write down. A
 * strategy that plays a trained blueprint needs the exact sequence of bets to find where it is in
 * the blueprint's game tree.
 *
 * @param headsUp true when exactly two players were dealt in
 * @param smallBlind true when this bot posted the small blind (the button, heads-up)
 * @param smallBlindAmount the small blind
 * @param bigBlindAmount the big blind
 * @param myStack this bot's chips when the hand started, before blinds
 * @param theirStack heads-up, the opponent's chips when the hand started; otherwise 0
 * @param actions every action after the blinds, oldest first
 */
public record HandHistory(boolean headsUp, boolean smallBlind, long smallBlindAmount, long bigBlindAmount,
        long myStack, long theirStack, List<PublicAction> actions) {

    /** A history that says nothing, for strategies and tests that do not need one. */
    public static final HandHistory EMPTY = new HandHistory(false, false, 0, 0, 0, 0, List.of());

    public HandHistory {
        actions = List.copyOf(actions);
    }

    /**
     * One action.
     *
     * @param street 0 before the flop, then 1, 2 and 3
     * @param mine true if this bot made it
     * @param type what was done
     * @param streetTotal the player's total bet on this street after the action
     * @param allIn true if the action put the player all-in
     */
    public record PublicAction(int street, boolean mine, ActionType type, long streetTotal, boolean allIn) {
    }
}
