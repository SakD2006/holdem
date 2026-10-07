package com.saksham.poker.ai;

import com.saksham.poker.ai.sim.Equity;
import com.saksham.poker.ai.sim.FastHand;
import com.saksham.poker.ai.sim.PreflopTable;
import com.saksham.poker.ai.sim.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A bot that works out its chance of winning before it acts. Before the flop it plays a share of
 * starting hands that depends on its seat; after the flop it deals the hand out hundreds of times
 * against the hands its opponents could plausibly hold, given how they have bet, and compares its
 * winning chance with the price of continuing.
 */
public final class MonteCarloStrategy extends BotStrategy {

    private final int deals;

    /** @param deals how many times to deal a hand out for each decision; more is steadier and slower */
    public MonteCarloStrategy(int deals) {
        this.deals = Math.max(50, deals);
    }

    @Override
    public String name() {
        return "simulation";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        return seen.preflop() ? preflop(seen, random) : postflop(seen, random);
    }

    // =====================================================================================
    // Before the flop: a share of starting hands that widens toward the button
    // =====================================================================================

    private Decision preflop(Observation seen, Random random) {
        int[] hole = FastHand.codes(seen.holeCards());
        double place = PreflopTable.place(hole[0], hole[1]);
        long bigBlind = seen.bigBlind();
        long highest = seen.highestBet();
        long behind = seen.stack() + seen.streetBet();
        boolean committing = seen.toCall() * 5 >= behind * 2; // the call is 40% of everything or more

        // A big part of the stack is at stake: decide it on the real odds, not on a table.
        if (committing) {
            double chance = Equity.winChance(hole, new int[0], ranges(seen), deals, random);
            if (chance < seen.potOdds() + 0.03) {
                return Decision.fold();
            }
            return chance > 0.6 && seen.canRaise() ? Decision.raise(seen.maxRaiseTo()) : Decision.call();
        }

        // Short of chips there is no room to raise and fold: all-in or nothing.
        if (behind <= 12 * bigBlind) {
            double shove = highest <= bigBlind ? 0.28 : 0.1;
            return place <= shove ? Decision.raise(seen.maxRaiseTo()) : seen.canCheck() ? Decision.check()
                    : Decision.fold();
        }

        if (highest <= bigBlind) {
            if (place <= openShare(seen)) {
                long limpers = Math.max(0, (seen.pot() - bigBlind - bigBlind / 2) / bigBlind);
                return Decision.raise(Math.round(2.5 * bigBlind) + limpers * bigBlind);
            }
            if (seen.canCheck()) {
                return Decision.check();
            }
            // Completing the small blind is cheap; anything else unfit to raise is folded.
            return seen.streetBet() > 0 && place <= 0.6 ? Decision.call() : Decision.fold();
        }

        if (seen.raisesThisStreet() <= 1) {
            // One raise so far. Re-raise the very best, plus a few more now and then so that a
            // re-raise does not always mean aces.
            if (place <= 0.04 || (place <= 0.07 && random.nextDouble() < 0.4)) {
                return Decision.raise(highest * 3);
            }
            double callShare = Math.max(0.05, 0.95 - 2.1 * seen.potOdds()) + 0.03 * (seen.opponentCount() - 1);
            return place <= callShare ? Decision.call() : Decision.fold();
        }

        // A raise and a re-raise: only the top of the range goes on.
        if (place <= 0.012 || (place <= 0.03 && random.nextDouble() < 0.5)) {
            long to = Math.round(highest * 2.3);
            return to * 2 >= behind ? Decision.raise(seen.maxRaiseTo()) : Decision.raise(to);
        }
        return place <= 0.07 && seen.potOdds() < 0.33 ? Decision.call() : Decision.fold();
    }

    /** The share of hands worth opening from this seat: tight when many are still to act, loose late. */
    private static double openShare(Observation seen) {
        int players = seen.playersDealt();
        int seat = seen.seatsAfterButton();
        if (players == 2) {
            return seat == 0 ? 0.75 : 0.5;
        }
        if (seat == 0) {
            return 0.45; // the button
        }
        if (seat == 1) {
            return 0.36; // the small blind: only one player left, but out of position after the flop
        }
        if (seat == 2) {
            return 0.3; // the big blind, raising when everyone has only called
        }
        int stillToAct = players - seat + 2; // players behind, counting the button and both blinds
        return switch (stillToAct) {
            case 3 -> 0.3; // one seat before the button
            case 4 -> 0.23;
            case 5 -> 0.18;
            default -> 0.15;
        };
    }

    // =====================================================================================
    // After the flop: simulate, then compare the winning chance with the price
    // =====================================================================================

    private Decision postflop(Observation seen, Random random) {
        int[] hole = FastHand.codes(seen.holeCards());
        int[] board = FastHand.codes(seen.board());
        double chance = Equity.winChance(hole, board, ranges(seen), deals, random);
        int outs = HandStrength.outs(seen.holeCards(), seen.board());
        int rivals = seen.opponentCount();
        long pot = seen.pot();

        if (seen.toCall() == 0) {
            // An even share of the pot is one part in the number of players. A hand is worth betting
            // when it is comfortably ahead of that: 0.6 against one opponent, 0.4 against three.
            double evenShare = 1.0 / (rivals + 1);
            double valueBar = 0.2 + 0.8 * evenShare;
            if (chance >= valueBar) {
                // Now and then a very strong hand checks, to let someone else bet into it.
                if (chance >= 0.9 && rivals == 1 && !seen.river() && random.nextDouble() < 0.15) {
                    return Decision.check();
                }
                return Decision.bet(share(pot, chance >= 0.82 ? 0.75 : 0.6, seen));
            }
            if (!seen.river() && outs >= 8 && random.nextDouble() < 0.55) {
                return Decision.bet(share(pot, 0.6, seen)); // a draw can win now, or get there later
            }
            if (seen.aggressor() && seen.board().size() == 3 && rivals <= 2 && random.nextDouble() < 0.55) {
                return Decision.bet(share(pot, 0.5, seen)); // the raiser keeps telling the same story
            }
            if (seen.river() && rivals == 1 && chance < 0.25 && random.nextDouble() < 0.14) {
                return Decision.bet(share(pot, 0.7, seen)); // nothing to show down: the only way to win is a bet
            }
            return Decision.check();
        }

        double price = seen.potOdds();
        double raiseBar = 0.8 + 0.04 * (rivals - 1);
        if (chance >= raiseBar && seen.canRaise() && random.nextDouble() < 0.75) {
            long to = seen.highestBet() + share(pot + seen.toCall(), 0.75, seen);
            // If the raise takes most of the stack, the rest may as well go in with it.
            return to * 5 >= (seen.stack() + seen.streetBet()) * 3 ? Decision.raise(seen.maxRaiseTo())
                    : Decision.raise(to);
        }
        // Calling needs a margin over the bare price: more bets may follow, and a bet that has
        // itself been raised means more strength than the ranges assume. The sizes of these margins,
        // and of the value bar above, were chosen by trying alternatives in the arena.
        double margin = (seen.river() ? 0.04 : 0.08) + (seen.raisesThisStreet() >= 2 ? 0.05 : 0);
        if (chance >= price + margin) {
            return Decision.call();
        }
        if (!seen.river() && outs >= 12 && seen.canRaise() && random.nextDouble() < 0.2) {
            return Decision.raise(seen.highestBet() + share(pot + seen.toCall(), 0.7, seen));
        }
        return Decision.fold();
    }

    private static long share(long pot, double ofPot, Observation seen) {
        return Math.max(seen.bigBlind(), Math.round(pot * ofPot));
    }

    /**
     * A guess at each opponent's possible hands from what they have done. Raising before the flop
     * narrows the range; betting after it suggests the board has helped them.
     */
    static List<Range> ranges(Observation seen) {
        List<Range> ranges = new ArrayList<>();
        for (Opponent opponent : seen.opponents()) {
            double width;
            if (opponent.preflopRaises() >= 2) {
                width = 0.06;
            } else if (opponent.preflopRaises() == 1) {
                width = 0.22;
            } else if (opponent.calledPreflopRaise()) {
                width = 0.35;
            } else {
                width = opponent.seat() < 0 ? 0.6 : 0.8;
            }
            double connected = opponent.betThisStreet() ? 0.7 : opponent.postflopBets() > 0 ? 0.45 : 0;
            if (opponent.postflopBets() >= 2) {
                connected = Math.min(0.9, connected + 0.15);
            }
            ranges.add(new Range(width, connected));
        }
        return ranges;
    }
}
