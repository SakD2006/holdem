package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.HandHistory;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.sim.FastHand;
import com.saksham.poker.common.action.ActionType;
import java.util.List;
import java.util.Random;

/**
 * Plays a strategy trained by the trainer (`trainer/`), heads-up: the blueprint before the river,
 * and on the river a re-solved subgame for its exact hand.
 *
 * <p>At every decision it rebuilds its place in the blueprint's game from the public history of the
 * hand: each real action is mapped onto the blueprint's bet sizes ({@link ActionTranslator}), and
 * the ranges both players could hold are updated with the blueprint's probabilities, using nobody's
 * private cards. Its own choice is turned back into a real bet of the same fraction of the pot.
 * With three or more players, or when the real betting cannot be followed, it hands the decision to
 * a fallback strategy.
 *
 * <p>Stateless, so one instance can serve many tables and threads; the river solution, the costly
 * part, is kept per thread for the hand being played.
 */
public final class BlueprintStrategy extends BotStrategy {

    /**
     * The re-solver never believes an opponent action less likely than this: a person can play a
     * line the blueprint almost never takes, and a range must not collapse to nothing.
     */
    static final double OPPONENT_FLOOR = 0.01;

    private final CardBuckets abstraction;
    private final BlueprintTable table;
    private final BotStrategy fallback;
    private final int resolveIterations;
    private final ThreadLocal<River> lastRiver = new ThreadLocal<>();

    /** A solved river and the hand it belongs to. */
    private record River(long hand, RiverSolver.Solution solution) {
    }

    /**
     * @param resolveIterations iterations of river re-solving, or 0 to play the river buckets
     */
    public BlueprintStrategy(CardBuckets abstraction, BlueprintTable table, BotStrategy fallback,
            int resolveIterations) {
        this.abstraction = abstraction;
        this.table = table;
        this.fallback = fallback;
        this.resolveIterations = resolveIterations;
    }

    @Override
    public String name() {
        return "blueprint";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        HandHistory history = seen.history();
        if (!history.headsUp() || seen.holeCards().size() != 2 || history.bigBlindAmount() <= 0) {
            return fallback.decide(seen, random);
        }
        Replay replay = Replay.of(seen, abstraction, table);
        if (replay == null) {
            return fallback.decide(seen, random);
        }
        AbstractHand hand = replay.hand;
        int[] legal = hand.legalActions();
        double[] probs = null;
        if (hand.street() == 3 && resolveIterations > 0 && replay.riverRoot != null) {
            probs = river(replay).probabilities(hand, Cards.comboIndex(replay.hole[0], replay.hole[1]));
        }
        if (probs == null) {
            int bucket = abstraction.bucket(hand.street(), replay.hole[0], replay.hole[1], replay.board);
            probs = table.strategyOrCall(hand.key(bucket), legal);
        }
        return real(hand, legal[pick(probs, random)], replay.me, seen);
    }

    /** The river solution for this hand, solved once per thread and reused. */
    private RiverSolver.Solution river(Replay replay) {
        River cached = lastRiver.get();
        if (cached != null && cached.hand == replay.riverKey) {
            return cached.solution;
        }
        RiverSolver.Solution solution =
                RiverSolver.solve(replay.riverRoot, replay.board, replay.riverRanges, resolveIterations);
        lastRiver.set(new River(replay.riverKey, solution));
        return solution;
    }

    private static int pick(double[] probs, Random random) {
        double x = random.nextDouble();
        for (int i = 0; i < probs.length; i++) {
            if (x < probs[i]) {
                return i;
            }
            x -= probs[i];
        }
        return probs.length - 1;
    }

    /** The real action for a blueprint action: a raise keeps its fraction of the pot. */
    private static Decision real(AbstractHand hand, int action, int me, Observation seen) {
        return switch (action) {
            case AbstractHand.FOLD -> Decision.fold();
            case AbstractHand.CALL -> seen.canCheck() ? Decision.check() : Decision.call();
            case AbstractHand.ALL_IN -> new Decision(ActionType.ALL_IN, 0);
            default -> {
                double fraction = (hand.raiseTo(action) - hand.streetBet(1 - me))
                        / (double) (hand.pot() + hand.toCall(me));
                long to = seen.highestBet() + Math.round(fraction * (seen.pot() + seen.toCall()));
                yield seen.canBet() ? Decision.bet(to) : Decision.raise(to);
            }
        };
    }

    /** The hand rebuilt in the blueprint's game from its public history. */
    static final class Replay {
        int me;
        int[] hole;
        int[] board;
        AbstractHand hand;
        AbstractHand riverRoot;
        double[][] riverRanges;
        /** Identifies the hand and its betting up to the river: the river cache's key. */
        long riverKey;
        private long running;

        /** Rebuilds the hand, or returns null if the real betting cannot be followed. */
        static Replay of(Observation seen, CardBuckets abstraction, BlueprintTable table) {
            HandHistory history = seen.history();
            Replay r = new Replay();
            r.me = history.smallBlind() ? 0 : 1;
            r.hole = new int[] {FastHand.code(seen.holeCards().get(0)), FastHand.code(seen.holeCards().get(1))};
            r.board = new int[5];
            for (int i = 0; i < seen.board().size(); i++) {
                r.board[i] = FastHand.code(seen.board().get(i));
            }
            double scale = AbstractHand.BIG_BLIND / (double) history.bigBlindAmount();
            Ranges ranges = new Ranges(abstraction, table, r.board);
            AbstractHand hand = AbstractHand.start();
            List<HandHistory.PublicAction> actions = history.actions();
            r.running = 0x5EED_0000_0000_0001L ^ ((long) r.hole[0] << 8 | r.hole[1]);
            for (int i = 0; i < actions.size(); i++) {
                HandHistory.PublicAction a = actions.get(i);
                // Keep the blueprint's street in step with the real one.
                while (!hand.terminal() && hand.street() < a.street()) {
                    hand = r.advance(hand, AbstractHand.CALL);
                }
                if (hand.terminal()) {
                    return null;
                }
                int actor = a.mine() ? r.me : 1 - r.me;
                if (hand.street() > a.street() || hand.toAct() != actor) {
                    continue; // betting the blueprint already closed; nothing to follow
                }
                r.running = mix(r.running ^ (a.streetTotal() * 31 + a.type().ordinal()));
                int action = translate(hand, a, scale, unit(mix(r.running + i)));
                if (hand.street() < 3) {
                    ranges.update(hand, actor, action, actor == r.me ? 0 : OPPONENT_FLOOR);
                }
                hand = r.advance(hand, action);
            }
            int street = seen.board().isEmpty() ? 0 : seen.board().size() - 2;
            while (!hand.terminal() && hand.street() < street) {
                hand = r.advance(hand, AbstractHand.CALL);
            }
            if (hand.terminal() || hand.street() != street || hand.toAct() != r.me) {
                return null;
            }
            r.hand = hand;
            if (r.riverRoot != null) {
                r.riverRanges = ranges.atRiver();
            }
            return r;
        }

        /** Applies an action, noting the moment the river begins. */
        private AbstractHand advance(AbstractHand hand, int action) {
            AbstractHand next = hand.apply(action);
            if (riverRoot == null && next.street() == 3) {
                riverRoot = next;
                riverKey = mix(running ^ (long) board[3] << 6 ^ board[4]);
            }
            return next;
        }

        private static int translate(AbstractHand hand, HandHistory.PublicAction a, double scale, double draw) {
            int[] legal = hand.legalActions();
            return switch (a.type()) {
                case FOLD -> legal[0] == AbstractHand.FOLD ? AbstractHand.FOLD : AbstractHand.CALL;
                case CHECK, CALL -> AbstractHand.CALL;
                default -> ActionTranslator.raise(hand, a.streetTotal() * scale, a.allIn(), draw);
            };
        }

        private static long mix(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
            return z ^ (z >>> 31);
        }

        private static double unit(long bits) {
            return (bits >>> 11) * 0x1.0p-53;
        }
    }

    /**
     * The public ranges: how likely each player is to hold each pair of hole cards, given the
     * blueprint's play so far. Board cards are removed as they appear; nobody's private cards are.
     */
    static final class Ranges {
        private final CardBuckets abstraction;
        private final BlueprintTable table;
        private final int[] board;
        private final double[][] weights = new double[2][Cards.COMBOS];
        private int street = -1;
        private int[] buckets;

        Ranges(CardBuckets abstraction, BlueprintTable table, int[] board) {
            this.abstraction = abstraction;
            this.table = table;
            this.board = board;
            java.util.Arrays.fill(weights[0], 1);
            java.util.Arrays.fill(weights[1], 1);
        }

        /** Multiplies the actor's range by the blueprint's chance of {@code action} with each hand. */
        void update(AbstractHand hand, int actor, int action, double floor) {
            if (hand.street() != street) {
                street = hand.street();
                removeBoard(street == 0 ? 0 : street + 2);
                buckets = abstraction.rangeBuckets(street, board);
            }
            int[] legal = hand.legalActions();
            int choice = 0;
            while (legal[choice] != action) {
                choice++;
            }
            double[] byBucket = new double[256];
            boolean[] known = new boolean[256];
            double[] w = weights[actor];
            for (int c = 0; c < Cards.COMBOS; c++) {
                if (w[c] == 0) {
                    continue;
                }
                int b = buckets[c];
                if (!known[b]) {
                    byBucket[b] = table.strategyOrCall(hand.key(b), legal)[choice];
                    known[b] = true;
                }
                w[c] *= Math.max(byBucket[b], floor);
            }
        }

        /** Both ranges once the whole board is out. */
        double[][] atRiver() {
            removeBoard(5);
            return new double[][] {weights[0].clone(), weights[1].clone()};
        }

        private void removeBoard(int shown) {
            long used = 0;
            for (int i = 0; i < shown; i++) {
                used |= 1L << board[i];
            }
            for (double[] w : weights) {
                for (int c = 0; c < Cards.COMBOS; c++) {
                    int[] hole = Cards.combo(c);
                    if ((used & (1L << hole[0] | 1L << hole[1])) != 0) {
                        w[c] = 0;
                    }
                }
            }
        }
    }
}
