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

class MonteCarloStrategyTest {

    private final BotStrategy bot = new MonteCarloStrategy(300);

    private static Observation preflop(String hole, long toCall, long pot, int raises, int seatsAfterButton) {
        return new Observation(Card.parseAll(hole), List.of(), pot, toCall, toCall == 0, false, true,
                Math.max(200, toCall * 2), 10_000, 10_000, 0, 100, 6, 6, seatsAfterButton, raises);
    }

    private static Observation postflop(String hole, String board, long toCall, long pot, List<Opponent> opponents) {
        boolean free = toCall == 0;
        return new Observation(Card.parseAll(hole), Card.parseAll(board), pot, toCall, free, free, !free,
                Math.max(100, toCall * 2), 9_000, 9_000, 0, 100, opponents.size() + 1, 6, 0, free ? 0 : 1, opponents,
                false);
    }

    private static Opponent quiet() {
        return new Opponent(1, 9_000, 0, 0, false, false, 0);
    }

    private static Opponent betting() {
        return new Opponent(1, 8_000, 400, 1, false, true, 1);
    }

    private Map<ActionType, Integer> tally(Observation seen) {
        Map<ActionType, Integer> counts = new EnumMap<>(ActionType.class);
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            counts.merge(bot.decide(seen, random).type(), 1, Integer::sum);
        }
        return counts;
    }

    private ActionType usually(Observation seen) {
        return Collections.max(tally(seen).entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    @Test
    void acesRaiseFromAnySeatAndRubbishFolds() {
        assertThat(usually(preflop("As Ah", 100, 150, 0, 3))).isEqualTo(ActionType.RAISE);
        assertThat(usually(preflop("7c 2d", 100, 150, 0, 3))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("7c 2d", 0, 200, 0, 2))).as("free to check").isEqualTo(ActionType.CHECK);
    }

    @Test
    void theButtonOpensHandsAnEarlySeatThrowsAway() {
        assertThat(usually(preflop("Kd 5c", 100, 150, 0, 3))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("Kd 5c", 100, 150, 0, 0))).isEqualTo(ActionType.RAISE);
    }

    @Test
    void againstARaiseItContinuesWithGoodHandsOnly() {
        assertThat(usually(preflop("9c 6d", 300, 450, 1, 0))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("Ad Jc", 300, 450, 1, 0))).isEqualTo(ActionType.CALL);
        assertThat(tally(preflop("As Ah", 300, 450, 1, 0))).containsOnlyKeys(ActionType.RAISE);
    }

    @Test
    void facingAnAllInItCallsWithAcesAndFoldsAMiddlingHand() {
        Observation aces = new Observation(Card.parseAll("As Ah"), List.of(), 10_150, 10_000, false, false, false,
                0, 0, 10_000, 0, 100, 2, 6, 0, 1, List.of(new Opponent(1, 0, 10_000, 1, false, true, 0)), false);
        Observation jackTen = new Observation(Card.parseAll("Js Td"), List.of(), 10_150, 10_000, false, false, false,
                0, 0, 10_000, 0, 100, 2, 6, 0, 1, List.of(new Opponent(1, 0, 10_000, 1, false, true, 0)), false);

        assertThat(usually(aces)).isEqualTo(ActionType.CALL);
        assertThat(usually(jackTen)).isEqualTo(ActionType.FOLD);
    }

    @Test
    void aStrongHandBetsAndAHopelessOneGivesUpToABet() {
        assertThat(usually(postflop("9c 9s", "Ah Ks 9d", 0, 600, List.of(quiet())))).isEqualTo(ActionType.BET);
        assertThat(usually(postflop("7c 2d", "Ah Ks 9d", 400, 1_000, List.of(betting())))).isEqualTo(ActionType.FOLD);
    }

    @Test
    void aBetIsASensibleShareOfThePot() {
        Decision decision = bot.decide(postflop("Ac Kd", "Ah Ks 9d", 0, 600, List.of(quiet())), new Random(3));

        assertThat(decision.type()).isEqualTo(ActionType.BET);
        assertThat(decision.amount()).isBetween(300L, 600L);
    }

    @Test
    void theSameHandIsPlayedMoreCarefullyAgainstMorePlayers() {
        // Second pair: worth a bet against one player, rarely against four.
        Observation headsUp = postflop("Kc Td", "Ah Ks 9d", 0, 600, List.of(quiet()));
        Observation fiveWay = postflop("Kc Td", "Ah Ks 9d", 0, 600, Collections.nCopies(4, quiet()));

        assertThat(tally(headsUp).getOrDefault(ActionType.BET, 0))
                .isGreaterThan(tally(fiveWay).getOrDefault(ActionType.BET, 0));
    }

    @Test
    void opponentsAreGivenRangesThatFitWhatTheyDid() {
        Observation seen = postflop("Ac 5d", "Ah Ks 9d", 400, 1_000, List.of(
                new Opponent(1, 5_000, 0, 0, false, false, 0),       // got in cheaply, has done nothing since
                new Opponent(2, 5_000, 0, 1, false, false, 0),       // raised before the flop
                new Opponent(3, 5_000, 0, 2, false, false, 0),       // re-raised
                new Opponent(4, 5_000, 0, 0, true, false, 0),        // called a raise
                new Opponent(5, 5_000, 400, 0, false, true, 1)));    // is betting now

        List<Range> ranges = MonteCarloStrategy.ranges(seen);

        assertThat(ranges).extracting(Range::width).containsExactly(0.8, 0.22, 0.06, 0.35, 0.8);
        assertThat(ranges.get(0).connected()).isZero();
        assertThat(ranges.get(4).connected()).isGreaterThan(0.5);
    }

    @Test
    void everyDecisionIsLegalWhateverTheSituation() {
        Random random = new Random(2026);
        List<Card> deck = new ArrayList<>(Card.parseAll(
                "2c 3c 4c 5c 6c 7c 8c 9c Tc Jc Qc Kc Ac 2d 3d 4d 5d 6d 7d 8d 9d Td Jd Qd Kd Ad "
                        + "2h 3h 4h 5h 6h 7h 8h 9h Th Jh Qh Kh Ah 2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As"));
        BotStrategy quick = new MonteCarloStrategy(50);
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
            Observation seen = new Observation(deck.subList(0, 2), deck.subList(2, 2 + boardCards),
                    random.nextInt(30_000), toCall, canCheck, canBet, canRaise, canRaiseAtAll ? min : 0,
                    canRaiseAtAll ? max : 0, stack, streetBet, 100, 2 + random.nextInt(8), 9, random.nextInt(9),
                    random.nextInt(4));

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
