package com.saksham.poker.ai.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.FoldProfile;
import com.saksham.poker.ai.HandHistory;
import com.saksham.poker.ai.HandHistory.PublicAction;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.Opponent;
import com.saksham.poker.ai.Tendencies;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The adaptive blueprint bends toward an opponent's habits, and only when it knows them. */
class AdaptiveBlueprintStrategyTest {

    /** A blueprint that never trained anywhere: it checks or calls every time. */
    private static final BlueprintTable EMPTY = BlueprintTable.read(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .put("HOLDEMBP".getBytes(StandardCharsets.US_ASCII)).putLong(0).flip());

    private static final BotStrategy NEVER = new BotStrategy() {
        @Override
        public String name() {
            return "never";
        }

        @Override
        protected Decision choose(Observation seen, Random random) {
            throw new AssertionError("heads-up hands never reach the fallback");
        }
    };

    private static final CardBuckets BUCKETS = new CategoryBuckets();

    /**
     * A blueprint that folds a typical 45% of every hand to any bet on the test's flop, and
     * otherwise checks or calls.
     */
    private static final BlueprintTable TYPICAL_FOLDS = typicalFolds();

    private static BlueprintTable typicalFolds() {
        AbstractHand flop = AbstractHand.start().apply(AbstractHand.CALL).apply(AbstractHand.CALL);
        java.util.Map<Long, float[]> entries = new java.util.HashMap<>();
        for (int action : flop.legalActions()) {
            if (action == AbstractHand.CALL) {
                continue;
            }
            AbstractHand facing = flop.apply(action);
            int[] answers = facing.legalActions();
            float[] sums = new float[6];
            for (int i = 0; i < answers.length; i++) {
                sums[i] = answers[i] == AbstractHand.FOLD ? 0.45f : answers[i] == AbstractHand.CALL ? 0.55f : 0;
            }
            for (int bucket = 0; bucket < 9; bucket++) {
                entries.put(facing.key(bucket), sums);
            }
        }
        ByteBuffer out = ByteBuffer.allocate(16 + entries.size() * 32).order(ByteOrder.LITTLE_ENDIAN);
        out.put("HOLDEMBP".getBytes(StandardCharsets.US_ASCII)).putLong(entries.size());
        entries.forEach((key, sums) -> {
            out.putLong(key);
            for (float x : sums) {
                out.putFloat(x);
            }
        });
        return BlueprintTable.read(out.flip());
    }

    /** The big blind, first to act on a flop after a limped pot, holding {@code hole}. */
    private static Observation flopFirst(String hole, Tendencies habits) {
        HandHistory history = new HandHistory(true, false, 50, 100, 10_000, 10_000, List.of(
                new PublicAction(0, false, ActionType.CALL, 100, false),
                new PublicAction(0, true, ActionType.CHECK, 100, false)));
        Opponent opponent = new Opponent(0, 9_900, 0, 0, false, false, 0, habits);
        return new Observation(Card.parseAll(hole), Card.parseAll("Kh 9d 4s"), 200, 0, true, true, false, 100,
                9_900, 9_900, 0, 100, 2, 2, 1, 0, List.of(opponent), false, history);
    }

    /**
     * A player seen for {@code hands} hands who folds {@code foldsToBet} of the time to a bet after
     * checking, at every size, in 200 such spots each; after betting they fold as the blueprint does.
     */
    private static Tendencies seen(double foldsToBet, int hands) {
        Tendencies t = Tendencies.TYPICAL;
        double[] rates = AdaptiveBlueprintStrategy.BLUEPRINT_FOLDS.rates();
        int[] counts = new int[FoldProfile.CELLS];
        for (int size = 0; size < FoldProfile.SIZES; size++) {
            rates[FoldProfile.cell(false, size)] = foldsToBet;
            counts[FoldProfile.cell(false, size)] = 200;
        }
        return new Tendencies(t.entersPot(), t.raisesPreflop(), t.foldsToRaisePreflop(), foldsToBet, t.aggression(),
                t.bluffs(), hands, new FoldProfile(rates, counts));
    }

    private static int bets(BotStrategy bot, Observation seen) {
        Random random = new Random(9);
        int bets = 0;
        for (int i = 0; i < 200; i++) {
            if (bot.decide(seen, random).type() == ActionType.BET) {
                bets++;
            }
        }
        return bets;
    }

    @Test
    void against_a_typical_opponent_it_plays_the_blueprint() {
        AdaptiveBlueprintStrategy adaptive = new AdaptiveBlueprintStrategy(BUCKETS, TYPICAL_FOLDS, NEVER, 0);
        Tendencies t = Tendencies.TYPICAL;
        int[] counts = new int[FoldProfile.CELLS];
        java.util.Arrays.fill(counts, 200);
        Tendencies likeTheBlueprint = new Tendencies(t.entersPot(), t.raisesPreflop(), t.foldsToRaisePreflop(),
                AdaptiveBlueprintStrategy.BLUEPRINT_FOLDS_TO_BET, AdaptiveBlueprintStrategy.BLUEPRINT_AGGRESSION,
                AdaptiveBlueprintStrategy.BLUEPRINT_BLUFFS, 300,
                new FoldProfile(AdaptiveBlueprintStrategy.BLUEPRINT_FOLDS.rates(), counts));
        assertThat(bets(adaptive, flopFirst("7c 2d", likeTheBlueprint))).isZero();
    }

    @Test
    void with_no_notes_on_the_opponent_it_plays_exactly_the_blueprint() {
        BlueprintStrategy plain = new BlueprintStrategy(BUCKETS, EMPTY, NEVER, 0);
        AdaptiveBlueprintStrategy adaptive = new AdaptiveBlueprintStrategy(BUCKETS, EMPTY, NEVER, 0);
        for (String hole : List.of("7c 2d", "Ks Kc", "Qh Jh")) {
            Observation seen = flopFirst(hole, Tendencies.TYPICAL);
            Random a = new Random(4);
            Random b = new Random(4);
            for (int i = 0; i < 50; i++) {
                assertThat(adaptive.decide(seen, a)).isEqualTo(plain.decide(seen, b));
            }
        }
    }

    @Test
    void it_bluffs_a_player_known_to_fold_too_much() {
        AdaptiveBlueprintStrategy adaptive = new AdaptiveBlueprintStrategy(BUCKETS, TYPICAL_FOLDS, NEVER, 0);
        BlueprintStrategy plain = new BlueprintStrategy(BUCKETS, TYPICAL_FOLDS, NEVER, 0);
        Observation folder = flopFirst("7c 2d", seen(0.9, 300));
        assertThat(bets(plain, folder)).isZero();
        assertThat(bets(adaptive, folder)).isGreaterThan(40);
    }

    @Test
    void it_does_not_bluff_a_player_who_never_folds() {
        AdaptiveBlueprintStrategy adaptive = new AdaptiveBlueprintStrategy(BUCKETS, TYPICAL_FOLDS, NEVER, 0);
        assertThat(bets(adaptive, flopFirst("7c 2d", seen(0.02, 300)))).isZero();
    }

    @Test
    void extra_folds_come_from_the_weakest_hands_and_the_average_hits_the_target() {
        double[] folds = new double[Cards.COMBOS];
        double[] weights = new double[Cards.COMBOS];
        double[] wins = new double[Cards.COMBOS];
        // Three hands: weak (we win 90%), middling (50%), strong (10%); each folds 30% now.
        int[] hands = {0, 1, 2};
        double[] ourWins = {0.9, 0.5, 0.1};
        for (int i = 0; i < 3; i++) {
            folds[hands[i]] = 0.3;
            weights[hands[i]] = 1;
            wins[hands[i]] = ourWins[i];
        }
        double[] more = AdaptiveBlueprintStrategy.reshaped(folds, weights, wins, 3, 0.3, 0.6);
        assertThat(more[0]).isEqualTo(1.0, within(1e-12)); // the weak hand folds first
        assertThat(more[1]).isCloseTo(0.5, within(1e-12));
        assertThat(more[2]).isCloseTo(0.3, within(1e-12)); // the strong hand still calls as often
        assertThat((more[0] + more[1] + more[2]) / 3).isCloseTo(0.6, within(1e-12));
        double[] fewer = AdaptiveBlueprintStrategy.reshaped(folds, weights, wins, 3, 0.3, 0.1);
        assertThat(fewer[2]).isZero(); // the best folding hand starts calling first
        assertThat((fewer[0] + fewer[1] + fewer[2]) / 3).isCloseTo(0.1, within(1e-12));
    }

    @Test
    void against_an_over_folder_it_bluffs_small_not_all_in() {
        AdaptiveBlueprintStrategy adaptive = new AdaptiveBlueprintStrategy(BUCKETS, TYPICAL_FOLDS, NEVER, 0);
        Observation folder = flopFirst("7c 2d", seen(0.9, 300));
        Random random = new Random(2);
        int bets = 0;
        for (int i = 0; i < 200; i++) {
            Decision d = adaptive.decide(folder, random);
            if (d.type() == ActionType.BET) {
                bets++;
                assertThat(d.amount()).as("bet size").isLessThanOrEqualTo(400);
            }
        }
        assertThat(bets).isGreaterThan(40);
    }
}
