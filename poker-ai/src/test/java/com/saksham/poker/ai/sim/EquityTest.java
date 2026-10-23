package com.saksham.poker.ai.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.saksham.poker.common.card.Card;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EquityTest {

    private static double chance(String hole, String board, List<Range> opponents) {
        return Equity.winChance(FastHand.codes(Card.parseAll(hole)), FastHand.codes(Card.parseAll(board)), opponents,
                20_000, new Random(5));
    }

    private static List<Range> anyone(int players) {
        return Collections.nCopies(players, Range.ANY);
    }

    private static double place(String hole) {
        int[] codes = FastHand.codes(Card.parseAll(hole));
        return PreflopTable.place(codes[0], codes[1]);
    }

    @Test
    void startingHandsMatchTheirWellKnownChancesAgainstOneRandomHand() {
        assertThat(chance("As Ah", "", anyone(1))).isCloseTo(0.85, within(0.02));
        assertThat(chance("As Ks", "", anyone(1))).isCloseTo(0.67, within(0.02));
        assertThat(chance("7c 2d", "", anyone(1))).isCloseTo(0.35, within(0.02));
        assertThat(chance("2c 2d", "", anyone(1))).isCloseTo(0.50, within(0.03));
    }

    @Test
    void moreOpponentsMeanALowerChance() {
        double one = chance("As Ah", "", anyone(1));
        double three = chance("As Ah", "", anyone(3));
        double eight = chance("As Ah", "", anyone(8));

        assertThat(three).isLessThan(one);
        assertThat(eight).isLessThan(three);
        assertThat(eight).isCloseTo(0.35, within(0.04));
    }

    @Test
    void theBoardChangesEverything() {
        assertThat(chance("Ah Kh", "Qh Jh Th", anyone(2))).as("a royal flush cannot lose").isEqualTo(1.0);
        assertThat(chance("9c 9s", "Ah Ks 9d", anyone(1))).as("a set").isGreaterThan(0.9);
        assertThat(chance("7c 2d", "Ah Ks 9d", anyone(1))).as("nothing").isLessThan(0.25);
        // On the river nothing is left to chance but the opponent's cards.
        assertThat(chance("7c 2d", "Ah Ks 9d 5c 3h", anyone(1))).isLessThan(0.1);
    }

    @Test
    void aStrongerOpposingRangeLowersTheChance() {
        double againstAnyone = chance("Kc Qd", "", List.of(Range.ANY));
        double againstARaiser = chance("Kc Qd", "", List.of(new Range(0.2, 0)));
        double againstAReraiser = chance("Kc Qd", "", List.of(new Range(0.05, 0)));

        assertThat(againstARaiser).isLessThan(againstAnyone - 0.08);
        assertThat(againstAReraiser).isLessThan(againstARaiser - 0.03);
    }

    @Test
    void anOpponentWhoIsBettingProbablyHasSomething() {
        // Middle pair looks fine against two random cards, and much worse against someone who bets.
        double quiet = chance("9c 8c", "Ah 9s 4d", List.of(new Range(0.8, 0)));
        double betting = chance("9c 8c", "Ah 9s 4d", List.of(new Range(0.8, 0.9)));

        assertThat(betting).isLessThan(quiet - 0.05);
    }

    @Test
    void theSameSeedGivesTheSameAnswerAndAnswersAreSharesOfAPot() {
        int[] hole = FastHand.codes(Card.parseAll("Jc Td"));
        int[] board = FastHand.codes(Card.parseAll("9h 8s 2c"));

        double first = Equity.winChance(hole, board, anyone(2), 500, new Random(1));
        double again = Equity.winChance(hole, board, anyone(2), 500, new Random(1));

        assertThat(first).isEqualTo(again).isBetween(0.0, 1.0);
    }

    @Test
    void startingHandsAreQueuedFromAcesDown() {
        assertThat(place("As Ah")).isZero();
        assertThat(place("Ks Kh")).isBetween(0.004, 0.006);
        assertThat(place("As Ks")).isLessThan(0.05);
        assertThat(place("7c 2d")).isGreaterThan(0.95);
        assertThat(place("Js Ts")).as("suited beats offsuit").isLessThan(place("Js Td"));
        assertThat(place("Kd As")).as("order does not matter").isEqualTo(place("As Kd"));
        assertThat(place("9h 9c")).isLessThan(0.1);
    }

    @Test
    void hittingTheBoardMeansAPairOrADraw() {
        int[] board = FastHand.codes(Card.parseAll("Ah 9s 4s"));
        assertThat(Equity.hitsBoard(code("9c"), code("8c"), board)).as("a pair").isTrue();
        assertThat(Equity.hitsBoard(code("Ks"), code("2s"), board)).as("a flush draw").isTrue();
        assertThat(Equity.hitsBoard(code("5d"), code("5c"), board)).as("a pocket pair").isTrue();
        assertThat(Equity.hitsBoard(code("Kc"), code("Qd"), board)).as("nothing").isFalse();
    }

    private static int code(String card) {
        return FastHand.code(Card.parse(card));
    }
}
