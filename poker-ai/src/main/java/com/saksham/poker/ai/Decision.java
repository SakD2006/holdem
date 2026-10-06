package com.saksham.poker.ai;

import com.saksham.poker.common.action.ActionType;

/**
 * What a bot has chosen to do.
 *
 * @param type fold, check, call, bet or raise
 * @param amount for a bet or raise, the total the bot's bet on this street becomes; otherwise 0
 */
public record Decision(ActionType type, long amount) {

    public static Decision fold() {
        return new Decision(ActionType.FOLD, 0);
    }

    public static Decision check() {
        return new Decision(ActionType.CHECK, 0);
    }

    public static Decision call() {
        return new Decision(ActionType.CALL, 0);
    }

    public static Decision bet(long to) {
        return new Decision(ActionType.BET, to);
    }

    public static Decision raise(long to) {
        return new Decision(ActionType.RAISE, to);
    }

    /**
     * The nearest thing to this decision that the rules allow right now. A strategy can then say what
     * it wants without checking every rule: a bet where only a raise is possible becomes a raise, an
     * amount outside the limits is brought inside them, and a fold when checking is free becomes a
     * check, because folding for nothing is never right.
     */
    public Decision madeLegal(Observation seen) {
        switch (type) {
            case FOLD:
                return seen.canCheck() ? check() : fold();
            case CHECK:
                return seen.canCheck() ? check() : fold();
            case CALL:
                return seen.canCheck() ? check() : call();
            case BET:
            case RAISE:
            case ALL_IN:
                if (!seen.canBet() && !seen.canRaise()) {
                    return seen.canCheck() ? check() : call();
                }
                long wanted = type == ActionType.ALL_IN ? seen.maxRaiseTo() : amount;
                long to = Math.max(seen.minRaiseTo(), Math.min(seen.maxRaiseTo(), wanted));
                return seen.canBet() ? bet(to) : raise(to);
            default:
                return seen.canCheck() ? check() : fold();
        }
    }
}
