package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.saksham.poker.common.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandStrengthTest {

    private static int chen(String cards) {
        List<Card> two = Card.parseAll(cards);
        return HandStrength.chen(two.get(0), two.get(1));
    }

    private static double made(String hole, String board) {
        return HandStrength.made(Card.parseAll(hole), Card.parseAll(board));
    }

    private static int outs(String hole, String board) {
        return HandStrength.outs(Card.parseAll(hole), Card.parseAll(board));
    }

    @Test
    void theChenFormulaGivesItsPublishedScores() {
        assertThat(chen("As Ah")).isEqualTo(20);
        assertThat(chen("Ks Kh")).isEqualTo(16);
        assertThat(chen("As Ks")).isEqualTo(12);
        assertThat(chen("As Kh")).isEqualTo(10);
        assertThat(chen("Ts Th")).isEqualTo(10);
        assertThat(chen("5h 5d")).isEqualTo(5);
        assertThat(chen("2h 2d")).isEqualTo(5);
        assertThat(chen("7s 2h")).isLessThan(0);
        assertThat(chen("Kh As")).as("order does not matter").isEqualTo(chen("As Kh"));
    }

    @Test
    void betterStartingHandsScoreHigher() {
        assertThat(chen("As Ah")).isGreaterThan(chen("Qs Qh"));
        assertThat(chen("Qs Qh")).isGreaterThan(chen("As Qh"));
        assertThat(chen("Js Ts")).as("suited beats offsuit").isGreaterThan(chen("Js Th"));
        assertThat(chen("9s 8s")).as("connected beats gapped").isGreaterThan(chen("9s 5s"));
    }

    @Test
    void madeHandsAreRankedInASensibleOrder() {
        double nothing = made("7c 2d", "Ah Ks 9d");
        double bottomPair = made("9c 2d", "Ah Ks 9d");
        double middlePair = made("Kc 2d", "Ah Ks 9d");
        double topPair = made("Ac 5d", "Ah Ks 9d");
        double topPairGoodKicker = made("Ac Qd", "Ah Ks 9d");
        double overpair = made("Qc Qd", "Jh 8s 3d");
        double twoPair = made("Ac Kd", "Ah Ks 9d");
        double set = made("9c 9s", "Ah Ks 9d");
        double straight = made("Qc Jd", "Ah Ks Td");
        double flush = made("Ad 5d", "Kd 9d 2d");
        double fullHouse = made("Ac Ad", "Ah Ks Kd");

        assertThat(List.of(nothing, bottomPair, middlePair, topPair, topPairGoodKicker, overpair, twoPair, set,
                straight, flush, fullHouse)).isSorted();
        assertThat(nothing).isLessThan(0.2);
        assertThat(topPair).isBetween(0.5, 0.7);
        assertThat(fullHouse).isGreaterThan(0.9);
    }

    @Test
    void aHandThatIsOnlyTheBoardIsWorthLittle() {
        // The pair of kings belongs to everyone.
        assertThat(made("7c 2d", "Kh Ks 9d")).isLessThan(0.25);
        // The board's flush: one more heart in any hand beats ours.
        assertThat(made("7c 2d", "Ah Kh 9h 5h 3h")).isLessThan(0.2);
        // A straight on the board.
        assertThat(made("2c 2d", "9h Ts Jd Qc Kh")).isLessThan(0.2);
        // Trips on the board with no pair in hand.
        assertThat(made("Ac 4d", "9h 9s 9d")).isLessThan(0.4);
    }

    @Test
    void aPocketPairWithAPairedBoardIsStillOnlyOnePairOfOurOwn() {
        assertThat(made("Qc Qd", "Jh Js 3d")).isBetween(0.6, 0.8);
        assertThat(made("4c 4d", "Jh Js Ad")).isLessThan(0.5);
    }

    @Test
    void drawsAreCountedInOuts() {
        assertThat(outs("Ah 5h", "Kh 9h 2c")).as("flush draw").isEqualTo(9);
        assertThat(outs("Jc Td", "9h 8s 2c")).as("open-ended straight draw").isEqualTo(8);
        assertThat(outs("Jc Td", "9h 7s 2c")).as("gutshot").isEqualTo(4);
        assertThat(outs("Jh Th", "9h 8h 2c")).as("both draws, capped").isEqualTo(15);
        assertThat(outs("Ac 2d", "3h 4s Kc")).as("the ace plays low: a five makes the wheel").isEqualTo(4);
        assertThat(outs("7c 2d", "Kh 9h 5h")).as("three hearts on the board are not our draw").isZero();
        assertThat(outs("Ah 5h", "Kh 9h 2c 3d 4s")).as("nothing to come on the river").isZero();
        assertThat(outs("Ah 5h", "")).as("no board yet").isZero();
    }

    @Test
    void theChanceOfHittingADrawMatchesTheKnownFigures() {
        assertThat(HandStrength.drawChance(9, 2)).isCloseTo(0.35, within(0.01));
        assertThat(HandStrength.drawChance(9, 1)).isCloseTo(0.196, within(0.01));
        assertThat(HandStrength.drawChance(8, 2)).isCloseTo(0.315, within(0.01));
        assertThat(HandStrength.drawChance(4, 1)).isCloseTo(0.087, within(0.01));
        assertThat(HandStrength.drawChance(0, 2)).isZero();
        assertThat(HandStrength.drawChance(9, 0)).isZero();
    }
}
