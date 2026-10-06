package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.RuleBasedStrategy.Style;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.BotLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RuleBasedStrategyTest {

    private final BotStrategy solid = new RuleBasedStrategy(Style.SOLID);

    /** Six players, blinds 50 and 100, a 10,000 stack, in an early seat unless said otherwise. */
    private static Observation preflop(String hole, long toCall, long pot, int raises) {
        boolean free = toCall == 0;
        return new Observation(Card.parseAll(hole), List.of(), pot, toCall, free, false, true, Math.max(200, toCall * 2),
                10_000, 10_000, 0, 100, 6, 6, 3, raises);
    }

    private static Observation postflop(String hole, String board, long toCall, long pot, int players) {
        boolean free = toCall == 0;
        return new Observation(Card.parseAll(hole), Card.parseAll(board), pot, toCall, free, free, !free,
                Math.max(100, toCall * 2), 9_000, 9_000, 0, 100, players, 6, 3, free ? 0 : 1);
    }

    /** What the strategy does most often in a spot, since some choices involve chance. */
    private Map<ActionType, Integer> tally(BotStrategy strategy, Observation seen) {
        Map<ActionType, Integer> counts = new EnumMap<>(ActionType.class);
        Random random = new Random(42);
        for (int i = 0; i < 400; i++) {
            counts.merge(strategy.decide(seen, random).type(), 1, Integer::sum);
        }
        return counts;
    }

    private ActionType usually(Observation seen) {
        Map<ActionType, Integer> counts = tally(solid, seen);
        return Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    // ---- before the flop

    @Test
    void aPremiumHandOpensWithARaiseOfAboutThreeBlinds() {
        Decision decision = solid.decide(preflop("As Ah", 100, 150, 0), new Random(1));

        assertThat(decision.type()).isEqualTo(ActionType.RAISE);
        assertThat(decision.amount()).isBetween(250L, 500L);
    }

    @Test
    void rubbishIsFoldedButNeverWhenCheckingIsFree() {
        assertThat(usually(preflop("7c 2d", 100, 150, 0))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("7c 2d", 0, 200, 0))).isEqualTo(ActionType.CHECK);
    }

    @Test
    void againstARaiseOnlyGoodHandsContinueAndTheBestRaiseAgain() {
        assertThat(usually(preflop("9c 6d", 300, 450, 1))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("Ts Th", 300, 450, 1))).isEqualTo(ActionType.CALL);
        assertThat(tally(solid, preflop("Ks Kh", 300, 450, 1))).containsKey(ActionType.RAISE);
    }

    @Test
    void aMediumHandDoesNotCallOffItsStackBeforeTheFlop() {
        assertThat(usually(preflop("Ts Th", 6_000, 6_150, 1))).isEqualTo(ActionType.FOLD);
        assertThat(usually(preflop("As Ah", 6_000, 6_150, 1))).isIn(ActionType.CALL, ActionType.RAISE);
    }

    @Test
    void aLateSeatPlaysMoreHandsThanAnEarlyOne() {
        Observation early = preflop("Kd Jc", 100, 150, 0);
        Observation button = new Observation(early.holeCards(), List.of(), 150, 100, false, false, true, 200, 10_000,
                10_000, 0, 100, 6, 6, 0, 0);

        assertThat(usually(early)).isEqualTo(ActionType.FOLD);
        assertThat(usually(button)).isEqualTo(ActionType.RAISE);
    }

    // ---- after the flop

    @Test
    void aStrongHandBetsWhenCheckedToAndTheBetIsASensibleShareOfThePot() {
        Decision decision = solid.decide(postflop("Ac Kd", "Ah Ks 9d", 0, 600, 2), new Random(1));

        assertThat(decision.type()).isEqualTo(ActionType.BET);
        assertThat(decision.amount()).isBetween(300L, 600L);
    }

    @Test
    void nothingChecksWhenFreeAndFoldsToABet() {
        assertThat(usually(postflop("7c 2d", "Ah Ks 9d", 0, 600, 3))).isEqualTo(ActionType.CHECK);
        assertThat(usually(postflop("7c 2d", "Ah Ks 9d", 400, 1_000, 2))).isEqualTo(ActionType.FOLD);
    }

    @Test
    void aDrawCallsACheapBetAndFoldsToADearOne() {
        // A flush draw comes in about 35% of the time: worth 200 into 800, not 2,000 into 2,600.
        assertThat(usually(postflop("Ah 5h", "Kh 9h 2c", 200, 800, 2))).isEqualTo(ActionType.CALL);
        assertThat(usually(postflop("Ah 5h", "Kh 9h 2c", 2_000, 2_600, 2))).isEqualTo(ActionType.FOLD);
    }

    @Test
    void aMonsterRaisesMoreOftenThanNot() {
        Map<ActionType, Integer> counts = tally(solid, postflop("9c 9s", "Ah Ks 9d", 400, 1_000, 2));

        assertThat(counts.getOrDefault(ActionType.RAISE, 0)).isGreaterThan(counts.getOrDefault(ActionType.CALL, 0));
        assertThat(counts).doesNotContainKey(ActionType.FOLD);
    }

    @Test
    void theEasyBotPlaysMoreHandsAndCallsMoreThanTheSolidOne() {
        BotStrategy easy = Strategies.forLevel(BotLevel.EASY);
        Random random = new Random(7);
        List<Card> deck = new ArrayList<>(Card.parseAll(
                "2c 3c 4c 5c 6c 7c 8c 9c Tc Jc Qc Kc Ac 2d 3d 4d 5d 6d 7d 8d 9d Td Jd Qd Kd Ad "
                        + "2h 3h 4h 5h 6h 7h 8h 9h Th Jh Qh Kh Ah 2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As"));
        int easyPlays = 0;
        int solidPlays = 0;
        for (int i = 0; i < 2_000; i++) {
            Collections.shuffle(deck, random);
            Observation seen = new Observation(deck.subList(0, 2), List.of(), 150, 100, false, false, true, 200,
                    10_000, 10_000, 0, 100, 6, 6, 3, 0);
            if (easy.decide(seen, random).type() != ActionType.FOLD) {
                easyPlays++;
            }
            if (solid.decide(seen, random).type() != ActionType.FOLD) {
                solidPlays++;
            }
        }

        assertThat(easyPlays).isGreaterThan(solidPlays);
        // Even the loose bot throws most hands away from an early seat.
        assertThat(easyPlays).isLessThan(1_200);
        assertThat(solidPlays).isBetween(150, 700);
    }

    // ---- whatever it wants, it may only do what the rules allow

    @Test
    void everyDecisionIsLegalWhateverTheSituation() {
        Random random = new Random(2026);
        List<Card> deck = new ArrayList<>(Card.parseAll(
                "2c 3c 4c 5c 6c 7c 8c 9c Tc Jc Qc Kc Ac 2d 3d 4d 5d 6d 7d 8d 9d Td Jd Qd Kd Ad "
                        + "2h 3h 4h 5h 6h 7h 8h 9h Th Jh Qh Kh Ah 2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As"));
        for (BotStrategy strategy : List.of(solid, Strategies.forLevel(BotLevel.EASY))) {
            for (int i = 0; i < 20_000; i++) {
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

                Decision decision = strategy.decide(seen, random);

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
}
