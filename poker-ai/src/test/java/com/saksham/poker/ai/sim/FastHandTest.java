package com.saksham.poker.ai.sim;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.eval.HandEvaluator;
import com.saksham.poker.engine.eval.HandValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FastHandTest {

    private static int score(String cards) {
        int[] codes = FastHand.codes(Card.parseAll(cards));
        return FastHand.score(codes, codes.length);
    }

    @Test
    void itAgreesWithTheEnginesEvaluatorOnWhichOfTwoHandsIsBetter() {
        Random random = new Random(99);
        List<Card> deck = new ArrayList<>(Deck.standardOrder());
        for (int i = 0; i < 125_000; i++) {
            Collections.shuffle(deck, random);
            int size = 5 + i % 3; // five, six and seven cards
            List<Card> one = deck.subList(0, size);
            List<Card> two = deck.subList(size, size * 2);
            HandValue slowOne = HandEvaluator.evaluate(one);
            HandValue slowTwo = HandEvaluator.evaluate(two);
            int fastOne = FastHand.score(FastHand.codes(one), size);
            int fastTwo = FastHand.score(FastHand.codes(two), size);

            assertThat(Integer.signum(Integer.compare(fastOne, fastTwo)))
                    .as("%s against %s", one, two)
                    .isEqualTo(Integer.signum(slowOne.compareTo(slowTwo)));
            assertThat(FastHand.category(fastOne)).as("%s", one).isEqualTo(slowOne.category().ordinal());
        }
    }

    @Test
    void theCategoriesRankInTheRightOrder() {
        List<Integer> worstToBest = List.of(
                score("Ah Kd 9c 7s 3h 2d 4c"),   // high card
                score("Ah Ad 9c 7s 3h 2d 4c"),   // pair
                score("Ah Ad 9c 9s 3h 2d 4c"),   // two pair
                score("Ah Ad Ac 7s 3h 2d 4c"),   // three of a kind
                score("5h 6d 7c 8s 9h 2d Ac"),   // straight
                score("Ah Kh 9h 7h 3h 2d 4c"),   // flush
                score("Ah Ad Ac 7s 7h 2d 4c"),   // full house
                score("Ah Ad Ac As 3h 2d 4c"),   // four of a kind
                score("5h 6h 7h 8h 9h 2d Ac"));  // straight flush
        assertThat(worstToBest).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void theAcePlaysLowInAStraightAndThatStraightIsTheWeakest() {
        int wheel = score("Ah 2d 3c 4s 5h Kd Qc");
        int sixHigh = score("2d 3c 4s 5h 6d Kd Qc");

        assertThat(FastHand.category(wheel)).isEqualTo(4);
        assertThat(wheel).isLessThan(sixHigh);
    }

    @Test
    void kickersDecideBetweenHandsOfTheSameKind() {
        assertThat(score("Ah Ad Kc 7s 3h")).isGreaterThan(score("As Ac Qc 7d 3d"));
        assertThat(score("Ah Ad Kc 7s 3h")).isEqualTo(score("As Ac Kd 7d 3d"));
        // Three pairs: the two highest count, and the best remaining card is the kicker.
        assertThat(score("Ah Ad Kc Ks 3h 3d Qc")).isEqualTo(score("As Ac Kd Kh Qd 2c 2d"));
        // Two sets of three make a full house, the higher three on top.
        assertThat(FastHand.category(score("Ah Ad Ac Ks Kh Kd 2c"))).isEqualTo(6);
        assertThat(score("Ah Ad Ac Ks Kh Kd 2c")).isGreaterThan(score("Kh Kd Kc As Ah 2d 3c"));
    }
}
