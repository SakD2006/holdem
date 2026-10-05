package com.saksham.poker.engine.hand;

import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The betting on one street: the bet to match, the smallest legal raise, and who still has to act.
 * It moves chips from stacks into street bets; collecting them into pots is the hand's job.
 */
final class BettingRound {

    /** Every seat in the hand, in clockwise order. */
    private final List<SeatState> seats;
    private final long bigBlind;

    private long currentBet;
    private long minRaiseIncrement;
    private int lastAggressor = -1;

    /**
     * @param seats every seat in the hand, clockwise
     * @param bigBlind the smallest bet, and the smallest raise until someone raises by more
     * @param openingBet the bet to match before anyone acts: the big blind preflop, 0 afterwards
     */
    BettingRound(List<SeatState> seats, long bigBlind, long openingBet) {
        this.seats = seats;
        this.bigBlind = bigBlind;
        this.currentBet = openingBet;
        this.minRaiseIncrement = bigBlind;
    }

    /** The highest bet on this street, which everyone must match to stay in. */
    long currentBet() {
        return currentBet;
    }

    /** The seat that last bet or raised on this street, or -1 if nobody has. */
    int lastAggressor() {
        return lastAggressor;
    }

    /**
     * The next seat that has to act, looking clockwise from the seat after {@code afterSeat} and
     * ending with {@code afterSeat} itself; -1 when the betting is over.
     */
    int nextToAct(int afterSeat) {
        Deque<Integer> waiting = new ArrayDeque<>();
        int start = 0;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).seat() > afterSeat) {
                start = i;
                break;
            }
        }
        for (int i = 0; i < seats.size(); i++) {
            SeatState seat = seats.get((start + i) % seats.size());
            if (needsToAct(seat)) {
                waiting.addLast(seat.seat());
            }
        }
        return waiting.isEmpty() ? -1 : waiting.peekFirst();
    }

    boolean needsToAct(SeatState seat) {
        if (!seat.canBet()) {
            return false;
        }
        if (!hasOpponentWithChips(seat)) {
            // Everyone else is all-in or has folded: the only decision left is whether to call.
            return seat.streetBet() < highestOpposingBet(seat);
        }
        return !seat.acted() || seat.streetBet() < currentBet;
    }

    LegalActions legalActions(SeatState seat) {
        long toMatch = hasOpponentWithChips(seat) ? currentBet : highestOpposingBet(seat);
        long callAmount = Math.min(seat.stack(), Math.max(0, toMatch - seat.streetBet()));
        boolean mayRaise = mayRaise(seat, callAmount);
        if (!mayRaise) {
            return new LegalActions(true, callAmount == 0, callAmount, false, false, 0, 0);
        }
        long allInTo = seat.streetBet() + seat.stack();
        long fullRaiseTo = currentBet == 0 ? bigBlind : currentBet + minRaiseIncrement;
        return new LegalActions(
                true, callAmount == 0, callAmount,
                currentBet == 0, currentBet > 0,
                Math.min(fullRaiseTo, allInTo), allInTo);
    }

    /**
     * A player may bet or raise when someone could respond, they have chips beyond the call, and the
     * betting is open to them: either they have not acted yet, or the bet has gone up by at least a
     * full raise since they did. A short all-in does not reopen the betting by itself.
     */
    private boolean mayRaise(SeatState seat, long callAmount) {
        if (!hasOpponentWithChips(seat) || seat.stack() <= callAmount) {
            return false;
        }
        return !seat.acted() || currentBet - seat.betLevelWhenActed() >= minRaiseIncrement;
    }

    /**
     * Carries out an action that {@link com.saksham.poker.engine.rules.ActionValidator} has accepted.
     *
     * @return chips the player put in
     */
    long apply(SeatState seat, PlayerAction action, LegalActions legal) {
        switch (action.type()) {
            case FOLD:
                seat.fold();
                return 0;
            case CHECK:
                seat.markActed(currentBet);
                return 0;
            case CALL:
                seat.pay(legal.callAmount());
                seat.markActed(currentBet);
                return legal.callAmount();
            case BET:
            case RAISE:
                long raiseTo = action.amount();
                long paid = raiseTo - seat.streetBet();
                long increment = raiseTo - currentBet;
                seat.pay(paid);
                if (increment >= minRaiseIncrement) {
                    minRaiseIncrement = increment;
                }
                currentBet = raiseTo;
                lastAggressor = seat.seat();
                seat.markActed(currentBet);
                return paid;
            default:
                throw new IllegalStateException("Unresolved action " + action.type());
        }
    }

    private boolean hasOpponentWithChips(SeatState seat) {
        for (SeatState other : seats) {
            if (other != seat && other.canBet()) {
                return true;
            }
        }
        return false;
    }

    /** The highest street bet among the other players still in the hand. */
    private long highestOpposingBet(SeatState seat) {
        long highest = 0;
        for (SeatState other : seats) {
            if (other != seat && !other.folded()) {
                highest = Math.max(highest, other.streetBet());
            }
        }
        return highest;
    }
}
