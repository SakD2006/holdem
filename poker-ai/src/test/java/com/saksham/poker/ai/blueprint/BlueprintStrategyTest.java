package com.saksham.poker.ai.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.HandHistory;
import com.saksham.poker.ai.HandHistory.PublicAction;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.TableObserver;
import com.saksham.poker.ai.sim.FastHand;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The blueprint player follows a real heads-up hand through the blueprint's game. */
class BlueprintStrategyTest {

    private static final CardBuckets BUCKETS = new CategoryBuckets();

    /** A blueprint file holding the given information sets and strategy sums. */
    private static BlueprintTable table(Map<Long, float[]> entries) {
        ByteBuffer out = ByteBuffer.allocate(16 + entries.size() * 32).order(ByteOrder.LITTLE_ENDIAN);
        out.put("HOLDEMBP".getBytes(StandardCharsets.US_ASCII));
        out.putLong(entries.size());
        entries.forEach((key, sums) -> {
            out.putLong(key == 0 ? 1 : key);
            for (int a = 0; a < 6; a++) {
                out.putFloat(a < sums.length ? sums[a] : 0);
            }
        });
        return BlueprintTable.read(out.flip());
    }

    /** Never used heads-up; marks its decisions with a raise to 1234. */
    private static final BotStrategy FALLBACK = new BotStrategy() {
        @Override
        public String name() {
            return "fallback";
        }

        @Override
        protected Decision choose(Observation seen, Random random) {
            return Decision.raise(1234);
        }
    };

    /** The small blind's first decision, blinds {@code sb}/{@code bb}, 100 big blinds each. */
    private static Observation smallBlindFirst(String hole, long sb, long bb, boolean headsUp) {
        List<Card> cards = Card.parseAll(hole);
        HandHistory history = new HandHistory(headsUp, true, sb, bb, 100 * bb, headsUp ? 100 * bb : 0, List.of());
        return new Observation(cards, List.of(), sb + bb, bb - sb, false, false, true, 2 * bb, 100 * bb, 100 * bb - sb,
                sb, bb, headsUp ? 2 : 3, headsUp ? 2 : 3, 0, 0, List.of(), false, history);
    }

    private static long rootKey(String hole) {
        List<Card> cards = Card.parseAll(hole);
        int bucket = Cards.preflopClass(FastHand.code(cards.get(0)), FastHand.code(cards.get(1)));
        return AbstractHand.start().key(bucket);
    }

    @Test
    void a_pot_sized_raise_in_the_blueprint_is_three_big_blinds_at_any_stakes() {
        // At the start the legal actions are fold, call, raise half, raise pot, raise twice, all-in.
        BlueprintTable table = table(Map.of(rootKey("As Ad"), new float[] {0, 0, 0, 1, 0, 0}));
        BlueprintStrategy bot = new BlueprintStrategy(BUCKETS, table, FALLBACK, 0);
        assertThat(bot.decide(smallBlindFirst("As Ad", 50, 100, true), new Random(1)))
                .isEqualTo(Decision.raise(300));
        assertThat(bot.decide(smallBlindFirst("As Ad", 10, 20, true), new Random(1)))
                .isEqualTo(Decision.raise(60));
    }

    @Test
    void where_the_blueprint_never_trained_it_calls() {
        BlueprintStrategy bot = new BlueprintStrategy(BUCKETS, table(Map.of()), FALLBACK, 0);
        assertThat(bot.decide(smallBlindFirst("7s 2d", 50, 100, true), new Random(1))).isEqualTo(Decision.call());
    }

    @Test
    void with_three_players_the_fallback_decides() {
        BlueprintStrategy bot = new BlueprintStrategy(BUCKETS, table(Map.of()), FALLBACK, 0);
        assertThat(bot.decide(smallBlindFirst("As Ad", 50, 100, false), new Random(1)))
                .isEqualTo(Decision.raise(1234).madeLegal(smallBlindFirst("As Ad", 50, 100, false)));
    }

    @Test
    void a_bet_between_two_sizes_goes_to_either_by_the_pseudo_harmonic_rule() {
        AbstractHand root = AbstractHand.start();
        // The pot after calling is 200 chips; raise-to 400 is 1.5 pots, between 1 (to 300) and 2 (to 500).
        // It goes to the smaller with chance (2 − 1.5)(1 + 1) / ((2 − 1)(1 + 1.5)) = 0.4.
        assertThat(ActionTranslator.raise(root, 400, false, 0.39)).isEqualTo(AbstractHand.RAISE + 1);
        assertThat(ActionTranslator.raise(root, 400, false, 0.41)).isEqualTo(AbstractHand.RAISE + 2);
        assertThat(ActionTranslator.raise(root, 300, false, 0.99)).isEqualTo(AbstractHand.RAISE + 1);
        assertThat(ActionTranslator.raise(root, 150, false, 0.5)).isEqualTo(AbstractHand.RAISE);
        assertThat(ActionTranslator.raise(root, 900, false, 0.5)).isEqualTo(AbstractHand.RAISE + 2);
        assertThat(ActionTranslator.raise(root, 900, true, 0.5)).isEqualTo(AbstractHand.ALL_IN);
    }

    @Test
    void an_off_tree_raise_is_followed_and_the_answer_is_legal() {
        // The small blind limps, the big blind raises to 7 big blinds (not a blueprint size).
        HandHistory history = new HandHistory(true, true, 50, 100, 10_000, 10_000, List.of(
                new PublicAction(0, true, ActionType.CALL, 100, false),
                new PublicAction(0, false, ActionType.RAISE, 700, false)));
        Observation seen = new Observation(Card.parseAll("Ks Kd"), List.of(), 800, 600, false, false, true, 1300,
                10_000, 9_900, 100, 100, 2, 2, 0, 1, List.of(), false, history);
        BlueprintStrategy bot = new BlueprintStrategy(BUCKETS, table(Map.of()), FALLBACK, 0);
        Decision decision = bot.decide(seen, new Random(3));
        assertThat(decision).isEqualTo(decision.madeLegal(seen));
        assertThat(decision.amount()).isNotEqualTo(1234);
    }

    @Test
    void on_the_river_it_plays_a_re_solved_strategy() {
        // Checked down to a river where the button holds the nuts. The empty blueprint would only
        // ever check; the re-solved river bets the nuts at least sometimes.
        List<PublicAction> checks = List.of(
                new PublicAction(0, true, ActionType.CALL, 100, false),
                new PublicAction(0, false, ActionType.CHECK, 100, false),
                new PublicAction(1, false, ActionType.CHECK, 0, false),
                new PublicAction(1, true, ActionType.CHECK, 0, false),
                new PublicAction(2, false, ActionType.CHECK, 0, false),
                new PublicAction(2, true, ActionType.CHECK, 0, false),
                new PublicAction(3, false, ActionType.CHECK, 0, false));
        HandHistory history = new HandHistory(true, true, 50, 100, 10_000, 10_000, checks);
        Observation seen = new Observation(Card.parseAll("As Ks"), Card.parseAll("Qs Js Ts 2h 3d"), 200, 0, true, true,
                false, 100, 9_900, 9_900, 0, 100, 2, 2, 0, 0, List.of(), false, history);
        BlueprintStrategy plain = new BlueprintStrategy(BUCKETS, table(Map.of()), FALLBACK, 0);
        BlueprintStrategy solving = new BlueprintStrategy(BUCKETS, table(Map.of()), FALLBACK, 60);
        Random random = new Random(5);
        int bets = 0;
        for (int i = 0; i < 40; i++) {
            assertThat(plain.decide(seen, random)).isEqualTo(Decision.check());
            if (solving.decide(seen, random).type() == ActionType.BET) {
                bets++;
            }
        }
        assertThat(bets).isPositive();
    }

    @Test
    void the_table_observer_writes_down_the_hand_in_order() {
        TableObserver observer = new TableObserver();
        observer.accept(new HandStarted(1, 0, 0, 1, 50, 100, Map.of(0, 10_000L, 1, 8_000L)));
        observer.accept(new HoleCards(0, Card.parseAll("As Ad")));
        observer.accept(new BlindPosted(0, 50, false, false));
        observer.accept(new BlindPosted(1, 100, true, false));
        observer.accept(new PlayerActed(0, ActionType.RAISE, 250, 300, 9_700, false));
        observer.accept(new PlayerActed(1, ActionType.RAISE, 800, 900, 7_100, false));
        HandHistory history = observer.observe(false, 600, false, true, 1500, 10_000).history();
        assertThat(history.headsUp()).isTrue();
        assertThat(history.smallBlind()).isTrue();
        assertThat(history.myStack()).isEqualTo(10_000);
        assertThat(history.theirStack()).isEqualTo(8_000);
        assertThat(history.actions()).containsExactly(
                new PublicAction(0, true, ActionType.RAISE, 300, false),
                new PublicAction(0, false, ActionType.RAISE, 900, false));
    }
}
