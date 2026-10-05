package com.saksham.poker.engine.rules;

import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.common.exception.InvalidActionException;
import com.saksham.poker.common.exception.InvalidAmountException;
import com.saksham.poker.common.exception.NotYourTurnException;

/** Checks a requested action against what is legal. */
public final class ActionValidator {

    private ActionValidator() {
    }

    /**
     * Checks the action and returns the one to carry out. An all-in is turned into the bet, call or
     * raise that puts the whole stack in; every other legal action is returned unchanged.
     *
     * @param action what the player asked to do
     * @param legal what the player may do
     * @param stack the player's chips behind
     * @throws NotYourTurnException if the player may not act at all
     * @throws InvalidActionException if this kind of action is not allowed now
     * @throws InvalidAmountException if the bet or raise is outside the legal range
     */
    public static PlayerAction resolve(PlayerAction action, LegalActions legal, long stack)
            throws GameRuleException {
        if (!legal.canAct()) {
            throw new NotYourTurnException("It is not your turn. Wait until you are asked to act.");
        }
        switch (action.type()) {
            case FOLD:
                return action;
            case CHECK:
                if (!legal.canCheck()) {
                    throw new InvalidActionException(
                            "You cannot check: there are " + legal.callAmount()
                                    + " chips to call. Call, raise or fold.");
                }
                return action;
            case CALL:
                if (legal.callAmount() == 0) {
                    throw new InvalidActionException("There is nothing to call. Check or bet instead.");
                }
                return action;
            case BET:
                if (!legal.canBet()) {
                    throw new InvalidActionException(legal.canRaise()
                            ? "There is already a bet on this street. Raise instead of betting."
                            : "You cannot bet now.");
                }
                requireInRange("bet", action.amount(), legal);
                return action;
            case RAISE:
                if (!legal.canRaise()) {
                    throw new InvalidActionException(legal.canBet()
                            ? "Nobody has bet on this street. Bet instead of raising."
                            : "You cannot raise now. Call or fold.");
                }
                requireInRange("raise", action.amount(), legal);
                return action;
            case ALL_IN:
                if (legal.canBet()) {
                    return new Bet(legal.maxRaiseTo());
                }
                if (legal.canRaise()) {
                    return new Raise(legal.maxRaiseTo());
                }
                if (legal.callAmount() > 0 && legal.callAmount() >= stack) {
                    return new Call();
                }
                throw new InvalidActionException(
                        "You cannot go all-in now, because you cannot raise. "
                                + (legal.canCheck() ? "Check or fold." : "Call or fold."));
            default:
                throw new InvalidActionException("Unknown action " + action.type() + ".");
        }
    }

    private static void requireInRange(String what, long amount, LegalActions legal)
            throws InvalidAmountException {
        if (amount < legal.minRaiseTo() || amount > legal.maxRaiseTo()) {
            throw new InvalidAmountException(
                    "A " + what + " must be from " + legal.minRaiseTo() + " to " + legal.maxRaiseTo()
                            + ", but was " + amount + ".");
        }
    }
}
