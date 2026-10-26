package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.FoldProfile;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.Tendencies;
import java.util.Random;

/**
 * The blueprint, bent toward what an opponent's habits give away, by a bounded amount.
 *
 * <p>After the flop, every action is valued the way local best response values it (equity against
 * the opponent's range; the share of their hands that fold to a bet), twice: once with the
 * blueprint's own beliefs (the public range, and the fold chances of the blueprint or the re-solved
 * river), and once with the opponent as observed. In the second, the blueprint's fold chance at
 * this spot is shifted by how much more or less than a typical player this opponent folds to bets,
 * and players who bet, bluff or call far more than typical have a range that says less about their
 * cards ({@link #temper}).
 *
 * <p>That valuation is crude (it assumes the hand is checked down after the next action) and on its
 * own disagrees with the blueprint everywhere. So {@code best}, the action the opponent model
 * values highest, counts as a reason to deviate only by how much better than the blueprint's mix it
 * looks under the model than it already looks under the blueprint's own beliefs: against a typical
 * opponent that is about zero. The strategy played is {@code (1 − λ)·blueprint + λ·best}; {@code λ}
 * grows with the evidence on the opponent and with that gain, and never exceeds {@link #MAX_MIX}.
 * With no evidence it is 0 and the play is exactly the blueprint's. Before the flop the blueprint
 * is played as it is.
 */
public final class AdaptiveBlueprintStrategy extends BlueprintStrategy {

    /** The most the strategy may move toward its best response (experiments: holdem.adapt.maxMix). */
    static final double MAX_MIX = Double.parseDouble(System.getProperty("holdem.adapt.maxMix", "0.6"));
    /**
     * Fold-rate differences smaller than this are taken for ordinary variation between sound
     * players, not a leak (experiments: holdem.adapt.deadZone).
     */
    static final double DEAD_ZONE = Double.parseDouble(System.getProperty("holdem.adapt.deadZone", "0.1"));
    /** Hands seen at which the evidence counts for half. */
    static final double EVIDENCE_HANDS = 30;
    /** A gain, as a share of the pot, at which deviation reaches its cap (given full evidence). */
    static final double FULL_GAIN = 0.25;
    /** The most an opponent's actions may be discounted as evidence of their cards. */
    static final double MAX_TEMPER = 0.9;
    /** Times a situation must be seen for its fold rate to count for half. */
    static final double SITUATION_EVIDENCE = 20;

    /**
     * What the blueprint itself does, measured by playing it against itself in the arena (40,000
     * hands, the 60-million-iteration blueprint with 200 histogram buckets): its fold rates by
     * situation, and how often it bets and shows a weak hand after betting. An opponent is
     * exploitable where it differs from these, not from a casual player. Small bets are rare in
     * the blueprint's game, so those two rates are mostly the typical player's.
     */
    static final FoldProfile BLUEPRINT_FOLDS =
            new FoldProfile(new double[] {0.40, 0.44, 0.74, 0.13, 0.60, 0.60}, new int[FoldProfile.CELLS]);
    static final double BLUEPRINT_AGGRESSION = 0.27;
    static final double BLUEPRINT_BLUFFS = 0.55;
    static final double BLUEPRINT_FOLDS_TO_BET = 0.49;

    public AdaptiveBlueprintStrategy(CardBuckets buckets, BlueprintTable table, BotStrategy fallback,
            int resolveIterations) {
        super(buckets, table, fallback, resolveIterations);
    }

    @Override
    public String name() {
        return "blueprint-adaptive";
    }

    private static Tendencies habits(Observation seen) {
        return seen.opponents().isEmpty() ? Tendencies.TYPICAL : seen.opponents().get(0).habits();
    }

    /** How far to trust the notes on a player: 0 with none, a half at {@link #EVIDENCE_HANDS}. */
    static double confidence(Tendencies habits) {
        return habits.handsSeen() / (habits.handsSeen() + EVIDENCE_HANDS);
    }

    /**
     * Switches for the ablation experiments, read once from system properties; everything is on by
     * default. {@code holdem.adapt.noTemper}: count opponent actions as full evidence;
     * {@code holdem.adapt.noShift}: ignore fold habits; {@code holdem.adapt.only}: {@code raise} to
     * deviate only toward bets and raises, {@code passive} only toward check, call or fold.
     */
    private static final boolean TEMPER_ON = !Boolean.getBoolean("holdem.adapt.noTemper");
    private static final boolean SHIFT_ON = !Boolean.getBoolean("holdem.adapt.noShift");
    private static final String ONLY = System.getProperty("holdem.adapt.only", "");

    @Override
    double temper(Observation seen) {
        if (!TEMPER_ON) {
            return 0;
        }
        Tendencies t = habits(seen);
        double surprise = Math.max(0, (t.aggression() - BLUEPRINT_AGGRESSION) / (1 - BLUEPRINT_AGGRESSION))
                + Math.max(0, (t.bluffs() - BLUEPRINT_BLUFFS) / (1 - BLUEPRINT_BLUFFS))
                + Math.max(0, (BLUEPRINT_FOLDS_TO_BET - t.foldsToBet()) / BLUEPRINT_FOLDS_TO_BET);
        return confidence(t) * Math.min(MAX_TEMPER, surprise);
    }

    @Override
    double[] adjust(Observation seen, Replay replay, int[] legal, double[] base, Random random) {
        AbstractHand hand = replay.hand;
        Tendencies habits = habits(seen);
        double confidence = confidence(habits);
        if (hand.street() == 0 || confidence == 0) {
            return base;
        }
        // Both valuations see the same sampled run-outs, so only the opponent model separates them.
        long runouts = random.nextLong();
        double[] believed = values(replay, legal, replay.ranges.publicRange(1 - replay.me), null, new Random(runouts));
        double[] observed = values(replay, legal, replay.ranges.model(), SHIFT_ON ? habits : null, new Random(runouts));
        if (believed == null || observed == null) {
            return base;
        }
        double believedMix = 0;
        double observedMix = 0;
        for (int i = 0; i < legal.length; i++) {
            believedMix += base[i] * believed[i];
            observedMix += base[i] * observed[i];
        }
        // The action the opponent model rates best...
        int best = 0;
        for (int i = 1; i < legal.length; i++) {
            if (observed[i] > observed[best]) {
                best = i;
            }
        }
        // ...and how much better than the blueprint's mix it looks because of the model, rather than
        // because the crude valuation would prefer it anyway.
        double gain = (observed[best] - observedMix) - Math.max(0, believed[best] - believedMix);
        boolean raise = legal[best] != AbstractHand.FOLD && legal[best] != AbstractHand.CALL;
        if (ONLY.equals("raise") && !raise || ONLY.equals("passive") && raise) {
            gain = 0;
        }
        double pot = hand.pot() / (double) AbstractHand.BIG_BLIND;
        double lambda = MAX_MIX * confidence * Math.min(1, Math.max(0, gain) / (FULL_GAIN * pot));
        double[] mixed = new double[legal.length];
        for (int i = 0; i < legal.length; i++) {
            mixed[i] = (1 - lambda) * base[i] + (i == best ? lambda : 0);
        }
        return mixed;
    }

    /**
     * How much more (or less) this opponent folds than the blueprint in the situation a bet would
     * put them in, counted in proportion to how often that situation has been seen.
     */
    static double foldShift(Tendencies habits, boolean afterBetting, int size) {
        int seen = habits.folds().seen(afterBetting, size);
        double weight = seen / (seen + SITUATION_EVIDENCE);
        double difference = habits.folds().rate(afterBetting, size) - BLUEPRINT_FOLDS.rate(afterBetting, size);
        double beyond = Math.max(0, Math.abs(difference) - DEAD_ZONE);
        return weight * Math.signum(difference) * beyond;
    }

    /**
     * Each legal action's value in big blinds, local-best-response style, against an opponent
     * range {@code range}. Fold chances are the base strategy's, moved by this opponent's fold
     * habits in each situation ({@link #foldShift}) when {@code habits} is given; null if the range
     * is empty.
     */
    private double[] values(Replay replay, int[] legal, double[] range, Tendencies habits, Random random) {
        AbstractHand hand = replay.hand;
        int me = replay.me;
        int street = hand.street();
        int[] buckets = replay.ranges.bucketsOn(street);
        double[] weights = new double[Cards.COMBOS];
        double total = 0;
        for (int c = 0; c < Cards.COMBOS; c++) {
            int[] h = Cards.combo(c);
            boolean mine = h[0] == replay.hole[0] || h[0] == replay.hole[1] || h[1] == replay.hole[0]
                    || h[1] == replay.hole[1];
            weights[c] = mine ? 0 : range[c];
            total += weights[c];
        }
        if (total <= 0) {
            return null;
        }
        double[] wins = RangeEquity.winChances(replay.hole, replay.board, street + 2, weights, random);
        double win = 0;
        for (int c = 0; c < Cards.COMBOS; c++) {
            win += weights[c] * wins[c];
        }
        win /= total;
        double bb = AbstractHand.BIG_BLIND;
        double pot = hand.pot() / bb;
        double call = hand.toCall(me) / bb;
        double[] ev = new double[legal.length];
        for (int i = 0; i < legal.length; i++) {
            int action = legal[i];
            if (action == AbstractHand.FOLD) {
                ev[i] = 0;
            } else if (action == AbstractHand.CALL) {
                ev[i] = win * (pot + call) - call;
            } else {
                AbstractHand after = hand.apply(action);
                double put = (after.streetBet(me) - hand.streetBet(me)) / bb;
                double[] folds = foldChances(replay, after, buckets, weights);
                double expected = 0;
                for (int c = 0; c < Cards.COMBOS; c++) {
                    expected += weights[c] * folds[c];
                }
                expected /= total;
                // The situation the bet puts them in: had they bet this street (so we are raising),
                // and what pot odds does the call offer?
                boolean afterBetting = hand.toCall(me) > 0;
                long theirCall = after.toCall(1 - me);
                int size = FoldProfile.size((double) theirCall / (after.pot() + theirCall));
                double shift = habits == null ? 0 : foldShift(habits, afterBetting, size);
                double target = Math.max(0, Math.min(1, expected + shift));
                double[] shaped = reshaped(folds, weights, wins, total, expected, target);
                double callWeight = 0;
                double callWins = 0;
                for (int c = 0; c < Cards.COMBOS; c++) {
                    if (weights[c] == 0) {
                        continue;
                    }
                    callWeight += weights[c] * (1 - shaped[c]);
                    callWins += weights[c] * (1 - shaped[c]) * wins[c];
                }
                double winWhenCalled = callWeight > 0 ? callWins / callWeight : 0;
                ev[i] = target * pot + (1 - target) * (winWhenCalled * (pot + 2 * put - call) - put);
            }
        }
        return ev;
    }

    /**
     * Fold chances moved so that the range's average goes from {@code expected} to {@code target},
     * the way players really fold: extra folds come from their weakest hands first (those {@code
     * wins} says we beat most often), and when they fold less, it is their best folding hands that
     * start to call. Spreading the change evenly would leave the calling range as strong as the
     * whole range, which makes big bets against a player who over-folds look good when they are
     * not: such a player calls a big bet only with a strong hand.
     */
    static double[] reshaped(double[] folds, double[] weights, double[] wins, double total, double expected,
            double target) {
        double[] out = folds.clone();
        double change = (target - expected) * total;
        if (Math.abs(change) < 1e-12) {
            return out;
        }
        Integer[] order = new Integer[Cards.COMBOS];
        for (int c = 0; c < Cards.COMBOS; c++) {
            order[c] = c;
        }
        // Raising folds: weakest first, which is where our winning chance is highest.
        // Lowering them: strongest folding hands first, where our winning chance is lowest.
        boolean more = change > 0;
        java.util.Arrays.sort(order, (a, b) -> more ? Double.compare(wins[b], wins[a]) : Double.compare(wins[a], wins[b]));
        double left = Math.abs(change);
        for (int c : order) {
            if (left <= 0) {
                break;
            }
            if (weights[c] == 0) {
                continue;
            }
            double room = more ? 1 - out[c] : out[c];
            double move = Math.min(room, left / weights[c]);
            out[c] += more ? move : -move;
            left -= move * weights[c];
        }
        return out;
    }

    /** For every opponent hand, the chance the base strategy folds it at {@code after}. */
    private double[] foldChances(Replay replay, AbstractHand after, int[] buckets, double[] weights) {
        double[] out = new double[Cards.COMBOS];
        int[] answers = after.legalActions();
        int foldAt = -1;
        for (int i = 0; i < answers.length; i++) {
            if (answers[i] == AbstractHand.FOLD) {
                foldAt = i;
            }
        }
        if (foldAt < 0) {
            return out;
        }
        double[] byBucket = new double[256];
        boolean[] known = new boolean[256];
        for (int c = 0; c < Cards.COMBOS; c++) {
            if (weights[c] == 0) {
                continue;
            }
            double[] solved = replay.solution == null ? null : replay.solution.probabilities(after, c);
            if (solved != null) {
                out[c] = solved[foldAt];
                continue;
            }
            int b = buckets[c];
            if (!known[b]) {
                byBucket[b] = table().strategyOrCall(after.key(b), answers)[foldAt];
                known[b] = true;
            }
            out[c] = byBucket[b];
        }
        return out;
    }
}
