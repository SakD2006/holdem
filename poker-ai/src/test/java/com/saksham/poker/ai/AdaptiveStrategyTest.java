package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.sim.Range;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AdaptiveStrategyTest {

    private final AdaptiveStrategy bot = new AdaptiveStrategy(300);

    private static final Tendencies FOLDER = new Tendencies(0.2, 0.1, 0.8, 0.85, 0.2, 0.1, 200);
    private static final Tendencies CALLER = new Tendencies(0.7, 0.05, 0.2, 0.08, 0.1, 0.3, 200);
    private static final Tendencies BLUFFER = new Tendencies(0.6, 0.45, 0.3, 0.3, 0.7, 0.75, 200);
    private static final Tendencies ROCK = new Tendencies(0.12, 0.06, 0.8, 0.6, 0.15, 0.03, 200);

    private static Opponent quiet(Tendencies habits) {
        return new Opponent(1, 9_000, 0, 0, false, false, 0, habits);
    }

    private static Opponent betting(Tendencies habits) {
        return new Opponent(1, 8_000, 400, 0, false, true, 1, habits);
    }

    /** Heads-up after the flop with the action on the bot. */
    private static Observation spot(String hole, String board, long toCall, long pot, Opponent opponent) {
        boolean free = toCall == 0;
        return new Observation(Card.parseAll(hole), Card.parseAll(board), pot, toCall, free, free, !free,
                Math.max(100, toCall * 2), 9_000, 9_000, 0, 100, 2, 6, 0, free ? 0 : 1, List.of(opponent), false);
    }

    private Map<ActionType, Integer> tally(BotStrategy strategy, Observation seen) {
        Map<ActionType, Integer> counts = new EnumMap<>(ActionType.class);
        Random random = new Random(42);
        for (int i = 0; i < 300; i++) {
            counts.merge(strategy.decide(seen, random).type(), 1, Integer::sum);
        }
        return counts;
    }

    private int bets(Observation seen) {
        return tally(bot, seen).getOrDefault(ActionType.BET, 0);
    }

    @Test
    void itBluffsAPlayerWhoFoldsAndNeverOneWhoAlwaysCalls() {
        // Seven-high on the river: it cannot win unless the other player folds.
        String hole = "7c 2d";
        String board = "Ah Ks 9d 5c 3h";

        int againstFolder = bets(spot(hole, board, 0, 600, quiet(FOLDER)));
        int againstTypical = bets(spot(hole, board, 0, 600, quiet(Tendencies.TYPICAL)));
        int againstCaller = bets(spot(hole, board, 0, 600, quiet(CALLER)));

        assertThat(againstFolder).as("of 300 tries").isGreaterThan(120);
        assertThat(againstTypical).isBetween(10, 90);
        assertThat(againstCaller).isZero();
    }

    @Test
    void itBluffsLessOnTheFlopThanOnTheRiverBecauseMoreBetsCanFollow() {
        int flop = bets(spot("7c 2d", "Ah Ks 9d", 0, 600, quiet(FOLDER)));
        int river = bets(spot("7c 2d", "Ah Ks 9d 5c 3h", 0, 600, quiet(FOLDER)));

        assertThat(flop).isPositive().isLessThan(river);
    }

    /** The usual size of the bot's bets in a spot, leaving out the times it checks. */
    private long usualBet(Observation seen) {
        Random random = new Random(1);
        long total = 0;
        int bets = 0;
        for (int i = 0; i < 100; i++) {
            Decision decision = bot.decide(seen, random);
            if (decision.type() == ActionType.BET) {
                total += decision.amount();
                bets++;
            }
        }
        assertThat(bets).as("it bets this hand most of the time").isGreaterThan(60);
        return total / bets;
    }

    @Test
    void itChargesACallerMoreAndAsksAFolderForLess() {
        long fromCaller = usualBet(spot("Ac Kd", "Ah Ks 9d", 0, 1_000, quiet(CALLER)));
        long fromFolder = usualBet(spot("Ac Kd", "Ah Ks 9d", 0, 1_000, quiet(FOLDER)));

        assertThat(fromCaller).isGreaterThan(900);
        assertThat(fromFolder).isLessThan(500);
    }

    @Test
    void itCallsAKnownBlufferWithAHandItMostlyFoldsToAPlayerWhoseBetsAreAlwaysReal() {
        // Second pair, facing a bet of half the pot on the river.
        Observation fromBluffer = spot("Kc Td", "Ah Ks 9d 5c 3h", 400, 1_200, betting(BLUFFER));
        Observation fromRock = spot("Kc Td", "Ah Ks 9d 5c 3h", 400, 1_200, betting(ROCK));

        // Against the bluffer it never folds: it calls, or raises when the hand looks far enough ahead.
        assertThat(tally(bot, fromBluffer)).doesNotContainKey(ActionType.FOLD);
        // Against the rock the same hand is a close decision, and it lets it go more often than not.
        assertThat(tally(bot, fromRock).getOrDefault(ActionType.FOLD, 0)).isGreaterThan(150);
    }

    @Test
    void aLoosePlayersRaiseMeansLessThanATightPlayersRaise() {
        Opponent looseRaiser = new Opponent(1, 9_000, 300, 1, false, true, 0, BLUFFER);
        Opponent tightRaiser = new Opponent(1, 9_000, 300, 1, false, true, 0, ROCK);

        Range loose = bot.rangeFor(looseRaiser);
        Range tight = bot.rangeFor(tightRaiser);

        assertThat(loose.width()).isGreaterThan(0.4);
        assertThat(tight.width()).isLessThan(0.12);
    }

    @Test
    void itOpensMoreHandsWhenThePlayersBehindGiveUpToARaise() {
        Observation typical = new Observation(Card.parseAll("Qd 7c"), List.of(), 150, 100, false, false, true, 200,
                10_000, 10_000, 0, 100, 3, 3, 0, 0, List.of(quiet(Tendencies.TYPICAL), quiet(Tendencies.TYPICAL)),
                false);
        Observation folders = new Observation(Card.parseAll("Qd 7c"), List.of(), 150, 100, false, false, true, 200,
                10_000, 10_000, 0, 100, 3, 3, 0, 0, List.of(quiet(FOLDER), quiet(FOLDER)), false);

        assertThat(bot.openShare(folders)).isGreaterThan(bot.openShare(typical) * 1.2);
        assertThat(bot.openShare(typical)).isEqualTo(new MonteCarloStrategy(300).openShare(typical));
    }

    @Test
    void againstAPlayerItKnowsNothingAboutItPlaysExactlyAsTheSimulationBotDoes() {
        MonteCarloStrategy plain = new MonteCarloStrategy(120);
        AdaptiveStrategy adaptive = new AdaptiveStrategy(120);
        Random shuffle = new Random(9);
        List<Card> deck = new ArrayList<>(Card.parseAll(
                "2c 3c 4c 5c 6c 7c 8c 9c Tc Jc Qc Kc Ac 2d 3d 4d 5d 6d 7d 8d 9d Td Jd Qd Kd Ad "
                        + "2h 3h 4h 5h 6h 7h 8h 9h Th Jh Qh Kh Ah 2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As"));
        for (int i = 0; i < 400; i++) {
            Collections.shuffle(deck, shuffle);
            int boardCards = new int[] {0, 3, 4, 5}[i % 4];
            long toCall = i % 3 == 0 ? 0 : 100 + shuffle.nextInt(900);
            boolean free = toCall == 0;
            Observation seen = new Observation(deck.subList(0, 2), deck.subList(2, 2 + boardCards),
                    600 + shuffle.nextInt(3_000), toCall, free, free && boardCards > 0, !free || boardCards == 0,
                    Math.max(200, toCall * 2), 9_000, 9_000, 0, 100, 2 + i % 3, 6, i % 6, free ? 0 : 1);

            assertThat(adaptive.decide(seen, new Random(i))).as("%s", seen)
                    .isEqualTo(plain.decide(seen, new Random(i)));
        }
    }

    @Test
    void everyDecisionIsLegalWhateverTheOpponentsHabits() {
        Random random = new Random(2026);
        List<Card> deck = new ArrayList<>(Card.parseAll(
                "2c 3c 4c 5c 6c 7c 8c 9c Tc Jc Qc Kc Ac 2d 3d 4d 5d 6d 7d 8d 9d Td Jd Qd Kd Ad "
                        + "2h 3h 4h 5h 6h 7h 8h 9h Th Jh Qh Kh Ah 2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As"));
        BotStrategy quick = new AdaptiveStrategy(50);
        List<Tendencies> kinds = List.of(FOLDER, CALLER, BLUFFER, ROCK, Tendencies.TYPICAL,
                new Tendencies(0, 0, 0, 0, 0, 0, 0), new Tendencies(1, 1, 1, 1, 1, 1, 9_999));
        for (int i = 0; i < 6_000; i++) {
            Collections.shuffle(deck, random);
            int boardCards = new int[] {0, 3, 4, 5}[random.nextInt(4)];
            long stack = 1 + random.nextInt(20_000);
            long streetBet = random.nextBoolean() ? 0 : random.nextInt(500);
            long toCall = random.nextBoolean() ? 0 : Math.min(stack, 1 + random.nextInt(8_000));
            boolean canCheck = toCall == 0;
            boolean canRaiseAtAll = stack > toCall && random.nextInt(10) > 0;
            boolean canBet = canCheck && canRaiseAtAll && streetBet == 0;
            boolean canRaise = canRaiseAtAll && !canBet;
            long max = streetBet + stack;
            long min = Math.min(max, streetBet + toCall + 100 + random.nextInt(400));
            List<Opponent> opponents = new ArrayList<>();
            for (int o = 1 + random.nextInt(4); o > 0; o--) {
                opponents.add(new Opponent(o, 5_000, 0, random.nextInt(3), random.nextBoolean(), random.nextBoolean(),
                        random.nextInt(3), kinds.get(random.nextInt(kinds.size()))));
            }
            Observation seen = new Observation(deck.subList(0, 2), deck.subList(2, 2 + boardCards),
                    random.nextInt(30_000), toCall, canCheck, canBet, canRaise, canRaiseAtAll ? min : 0,
                    canRaiseAtAll ? max : 0, stack, streetBet, 100, opponents.size() + 1, 9, random.nextInt(9),
                    random.nextInt(4), opponents, random.nextBoolean());

            Decision decision = quick.decide(seen, random);

            switch (decision.type()) {
                case FOLD -> assertThat(canCheck).as("never fold when checking is free: %s", seen).isFalse();
                case CHECK -> assertThat(canCheck).as("%s", seen).isTrue();
                case CALL -> assertThat(toCall).as("%s", seen).isPositive();
                case BET -> {
                    assertThat(canBet).as("%s", seen).isTrue();
                    assertThat(decision.amount()).as("%s", seen).isBetween(min, max);
                }
                case RAISE -> {
                    assertThat(canRaise).as("%s", seen).isTrue();
                    assertThat(decision.amount()).as("%s", seen).isBetween(min, max);
                }
                default -> throw new AssertionError("unexpected " + decision);
            }
        }
    }
}
