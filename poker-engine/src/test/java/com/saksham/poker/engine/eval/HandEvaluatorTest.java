package com.saksham.poker.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Rank;
import com.saksham.poker.engine.card.Deck;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HandEvaluatorTest {

    private static HandValue eval(String cards) {
        return HandEvaluator.evaluate(Card.parseAll(cards));
    }

    private static void assertHand(String cards, HandCategory category, Rank... kickers) {
        HandValue value = eval(cards);
        assertThat(value.category()).as(cards).isEqualTo(category);
        assertThat(value.kickers()).as(cards).containsExactly(kickers);
    }

    private static void assertBeats(String winner, String loser) {
        assertThat(eval(winner)).as(winner + " beats " + loser).isGreaterThan(eval(loser));
        assertThat(eval(loser)).as(loser + " loses to " + winner).isLessThan(eval(winner));
    }

    private static void assertTies(String one, String other) {
        assertThat(eval(one)).as(one + " ties " + other).isEqualByComparingTo(eval(other));
        assertThat(eval(one)).isEqualTo(eval(other));
    }

    // ---- every category, with the ranks that break ties

    @Test
    void highCard() {
        assertHand("Ah Kd 9c 7s 3h", HandCategory.HIGH_CARD,
                Rank.ACE, Rank.KING, Rank.NINE, Rank.SEVEN, Rank.THREE);
    }

    @Test
    void pair() {
        assertHand("9h 9d Ac 7s 3h", HandCategory.PAIR, Rank.NINE, Rank.ACE, Rank.SEVEN, Rank.THREE);
    }

    @Test
    void twoPair() {
        assertHand("9h 9d 4c 4s Kh", HandCategory.TWO_PAIR, Rank.NINE, Rank.FOUR, Rank.KING);
    }

    @Test
    void threeOfAKind() {
        assertHand("9h 9d 9c 4s Kh", HandCategory.THREE_OF_A_KIND, Rank.NINE, Rank.KING, Rank.FOUR);
    }

    @Test
    void straight() {
        assertHand("9h 8d 7c 6s 5h", HandCategory.STRAIGHT, Rank.NINE);
        assertHand("Ah Kd Qc Js Th", HandCategory.STRAIGHT, Rank.ACE);
    }

    @Test
    void flush() {
        assertHand("Ah Jh 9h 7h 3h", HandCategory.FLUSH,
                Rank.ACE, Rank.JACK, Rank.NINE, Rank.SEVEN, Rank.THREE);
    }

    @Test
    void fullHouse() {
        assertHand("9h 9d 9c 4s 4h", HandCategory.FULL_HOUSE, Rank.NINE, Rank.FOUR);
        assertHand("4h 4d 4c 9s 9h", HandCategory.FULL_HOUSE, Rank.FOUR, Rank.NINE);
    }

    @Test
    void fourOfAKind() {
        assertHand("9h 9d 9c 9s Kh", HandCategory.FOUR_OF_A_KIND, Rank.NINE, Rank.KING);
    }

    @Test
    void straightFlush() {
        assertHand("9h 8h 7h 6h 5h", HandCategory.STRAIGHT_FLUSH, Rank.NINE);
    }

    @Test
    void royalFlushIsTheTopStraightFlush() {
        assertHand("Ah Kh Qh Jh Th", HandCategory.STRAIGHT_FLUSH, Rank.ACE);
        assertBeats("Ah Kh Qh Jh Th", "Kd Qd Jd Td 9d");
    }

    // ---- order of the categories

    @Test
    void eachCategoryBeatsTheOneBelowIt() {
        List<String> weakestFirst = List.of(
                "Ah Kd Qc Js 9h", // best high card
                "2h 2d 3c 4s 5h", // worst pair
                "3h 3d 2c 2s 4h", // worst two pair
                "2h 2d 2c 3s 4h", // worst trips
                "Ah 2d 3c 4s 5h", // worst straight
                "2h 3h 4h 5h 7h", // worst flush
                "2h 2d 2c 3s 3h", // worst full house
                "2h 2d 2c 2s 3h", // worst quads
                "Ah 2h 3h 4h 5h"); // worst straight flush
        for (int i = 1; i < weakestFirst.size(); i++) {
            assertBeats(weakestFirst.get(i), weakestFirst.get(i - 1));
        }
        assertThat(HandCategory.values()).hasSize(weakestFirst.size());
    }

    // ---- the wheel

    @Test
    void wheelIsAFiveHighStraight() {
        assertHand("Ah 2d 3c 4s 5h", HandCategory.STRAIGHT, Rank.FIVE);
        assertHand("5h 4d 3c 2s Ah", HandCategory.STRAIGHT, Rank.FIVE);
    }

    @Test
    void wheelLosesToASixHighStraight() {
        assertBeats("2h 3d 4c 5s 6h", "Ah 2d 3c 4s 5h");
    }

    @Test
    void wheelStraightFlushIsFiveHigh() {
        assertHand("Ah 2h 3h 4h 5h", HandCategory.STRAIGHT_FLUSH, Rank.FIVE);
        assertBeats("2d 3d 4d 5d 6d", "Ah 2h 3h 4h 5h");
    }

    @Test
    void aceCannotSitInTheMiddleOfAStraight() {
        assertHand("Qh Kd Ac 2s 3h", HandCategory.HIGH_CARD,
                Rank.ACE, Rank.KING, Rank.QUEEN, Rank.THREE, Rank.TWO);
        assertHand("Kh Ad 2c 3s 4h", HandCategory.HIGH_CARD,
                Rank.ACE, Rank.KING, Rank.FOUR, Rank.THREE, Rank.TWO);
    }

    // ---- kickers

    @Test
    void highCardIsDecidedCardByCard() {
        assertBeats("Ah Kd 9c 7s 3h", "Ad Qh Jc Ts 8h");
        assertBeats("Ah Kd 9c 7s 4h", "Ad Kh 9d 7c 3s");
        assertTies("Ah Kd 9c 7s 3h", "Ad Kh 9d 7c 3s");
    }

    @Test
    void pairIsDecidedByThePairThenTheSideCards() {
        assertBeats("Th Td 2c 3s 4h", "9h 9d Ac Ks Qh");
        assertBeats("9h 9d Ac 7s 3h", "9c 9s Kc Qs Jh");
        assertBeats("9h 9d Ac 7s 4h", "9c 9s Ad 7c 3h");
        assertTies("9h 9d Ac 7s 3h", "9c 9s Ad 7c 3d");
    }

    @Test
    void twoPairIsDecidedByTheHighPairThenTheLowPairThenTheSideCard() {
        assertBeats("Kh Kd 2c 2s 3h", "Qh Qd Jc Js Ah");
        assertBeats("Kh Kd 5c 5s 3h", "Kc Ks 4c 4s Ah");
        assertBeats("Kh Kd 5c 5s Ah", "Kc Ks 5d 5h Qh");
        assertTies("Kh Kd 5c 5s Ah", "Kc Ks 5d 5h Ad");
    }

    @Test
    void threeOfAKindIsDecidedByTheTripsThenTheSideCards() {
        assertBeats("9h 9d 9c 2s 3h", "8h 8d 8c As Kh");
        assertBeats("9h 9d 9c As 3h", "9s 9d 9c Ks Qh");
    }

    @Test
    void flushIsDecidedCardByCard() {
        assertBeats("Ah Jh 9h 7h 3h", "Kd Qd Jd 9d 8d");
        assertBeats("Ah Jh 9h 7h 4h", "Ad Jd 9d 7d 3d");
        assertTies("Ah Jh 9h 7h 3h", "Ad Jd 9d 7d 3d");
    }

    @Test
    void fullHouseIsDecidedByTheTripsThenThePair() {
        assertBeats("3h 3d 3c 2s 2h", "2d 2c 2s Ah Ad");
        assertBeats("9h 9d 9c 5s 5h", "9h 9d 9c 4s 4h");
    }

    @Test
    void fourOfAKindIsDecidedByTheQuadsThenTheSideCard() {
        assertBeats("3h 3d 3c 3s 2h", "2h 2d 2c 2s Ah");
        assertBeats("9h 9d 9c 9s Ah", "9h 9d 9c 9s Kh");
    }

    @Test
    void suitsNeverBreakATie() {
        assertTies("As Ks Qs Js 9s", "Ah Kh Qh Jh 9h");
        assertTies("Ah Kd Qc Js Th", "Ad Kc Qs Jh Tc");
    }

    // ---- choosing the best five from seven

    @Test
    void picksTheBestFiveOfSeven() {
        // Flush beats the straight that is also there.
        assertHand("9h 8h 7h 6d 5c 2h Kh", HandCategory.FLUSH,
                Rank.KING, Rank.NINE, Rank.EIGHT, Rank.SEVEN, Rank.TWO);
        // Three pairs: the top two pairs and the best remaining card.
        assertHand("Ah Ad Kc Ks Qh Qd 2c", HandCategory.TWO_PAIR, Rank.ACE, Rank.KING, Rank.QUEEN);
        // Two sets of trips make a full house, higher trips first.
        assertHand("9h 9d 9c 4s 4h 4d Kc", HandCategory.FULL_HOUSE, Rank.NINE, Rank.FOUR);
        // Trips and two pairs: the higher pair fills the house.
        assertHand("9h 9d 9c Ks Kh 4d 4c", HandCategory.FULL_HOUSE, Rank.NINE, Rank.KING);
        // Six cards to a straight: the higher end is used.
        assertHand("4h 5d 6c 7s 8h 9d Kc", HandCategory.STRAIGHT, Rank.NINE);
        // Six cards of a suit: the five highest are used.
        assertHand("2h 4h 6h 8h Th Qh Kd", HandCategory.FLUSH,
                Rank.QUEEN, Rank.TEN, Rank.EIGHT, Rank.SIX, Rank.FOUR);
        // Quads with a pair: the side card is the best single card, not the pair's rank by default.
        assertHand("9h 9d 9c 9s 4h 4d Ac", HandCategory.FOUR_OF_A_KIND, Rank.NINE, Rank.ACE);
        // A straight flush hiding among a higher plain flush card.
        assertHand("5h 6h 7h 8h 9h Ah Kd", HandCategory.STRAIGHT_FLUSH, Rank.NINE);
        // The wheel is used when no higher straight exists.
        assertHand("Ah 2d 3c 4s 5h Kd Kc", HandCategory.STRAIGHT, Rank.FIVE);
    }

    @Test
    void evaluatesSixCards() {
        assertHand("Ah Ad Kc Ks Qh 2c", HandCategory.TWO_PAIR, Rank.ACE, Rank.KING, Rank.QUEEN);
    }

    // ---- board plays: both players' best five is the board

    @Test
    void bothPlayersTieWhenTheBoardIsTheBestHand() {
        String board = "Ah Kd Qc Js Th";
        assertTies("2c 3d " + board, "4h 5s " + board);
        assertHand("2c 3d " + board, HandCategory.STRAIGHT, Rank.ACE);
    }

    @Test
    void bothPlayersTieWhenTheirHoleCardsAreTooLowToPlay() {
        String board = "Ah Ad Kc Ks Qh";
        assertTies("2c 3d " + board, "Jh 4s " + board);
    }

    @Test
    void aHoleCardThatBeatsTheBoardKickerWins() {
        String board = "Ah Ad Kc Ks 5h";
        assertBeats("Qc 2d " + board, "Jh 4s " + board);
    }

    // ---- bad input

    @Test
    void rejectsTooFewOrTooManyCards() {
        assertThatIllegalArgumentException().isThrownBy(() -> eval("Ah Kd Qc Js"));
        assertThatIllegalArgumentException().isThrownBy(() -> eval("Ah Kd Qc Js Th 9c 8d 7s"));
    }

    @Test
    void rejectsARepeatedCard() {
        assertThatIllegalArgumentException().isThrownBy(() -> eval("Ah Ah Qc Js Th"));
    }

    // ---- cross-check against the reference evaluator

    @Test
    void agreesWithTheReferenceEvaluatorOnRandomHands() {
        Random random = new Random(20261005L);
        List<Card> deck = new ArrayList<>(Deck.standardOrder());
        int[] seen = new int[HandCategory.values().length];

        for (int i = 0; i < 20_000; i++) {
            Collections.shuffle(deck, random);
            int size = 5 + i % 3;
            List<Card> first = deck.subList(0, size);
            List<Card> second = deck.subList(size, size * 2);

            HandValue firstValue = HandEvaluator.evaluate(first);
            HandValue secondValue = HandEvaluator.evaluate(second);
            List<Integer> firstReference = ReferenceEvaluator.evaluate(first);
            List<Integer> secondReference = ReferenceEvaluator.evaluate(second);

            assertThat(asNumbers(firstValue)).as("%s", first).isEqualTo(firstReference);
            assertThat(asNumbers(secondValue)).as("%s", second).isEqualTo(secondReference);
            assertThat(Integer.signum(firstValue.compareTo(secondValue)))
                    .as("%s against %s", first, second)
                    .isEqualTo(Integer.signum(compare(firstReference, secondReference)));
            seen[firstValue.category().ordinal()]++;
        }

        // The common categories must all have come up, or the check proves little.
        for (HandCategory category : List.of(HandCategory.HIGH_CARD, HandCategory.PAIR,
                HandCategory.TWO_PAIR, HandCategory.THREE_OF_A_KIND, HandCategory.STRAIGHT,
                HandCategory.FLUSH, HandCategory.FULL_HOUSE)) {
            assertThat(seen[category.ordinal()]).as(category.name()).isPositive();
        }
    }

    @Test
    void agreesWithTheReferenceEvaluatorOnHandsBuiltToBeStrong() {
        // Random deals rarely make quads or straight flushes, so build boards around them.
        Random random = new Random(7L);
        List<String> cores = List.of(
                "9h 9d 9c 9s", "Ah Ad Ac As", "2h 2d 2c 2s",
                "5h 6h 7h 8h 9h", "Ah 2h 3h 4h 5h", "Ts Js Qs Ks As",
                "4d 5d 6d 7d", "Ac 2c 3c 4c", "Jh Qh Kh Ah");
        for (String core : cores) {
            List<Card> fixed = Card.parseAll(core);
            List<Card> rest = new ArrayList<>(Deck.standardOrder());
            rest.removeAll(fixed);
            for (int i = 0; i < 500; i++) {
                Collections.shuffle(rest, random);
                List<Card> hand = new ArrayList<>(fixed);
                hand.addAll(rest.subList(0, 7 - fixed.size()));
                assertThat(asNumbers(HandEvaluator.evaluate(hand))).as("%s", hand)
                        .isEqualTo(ReferenceEvaluator.evaluate(hand));
            }
        }
    }

    private static List<Integer> asNumbers(HandValue value) {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(value.category().ordinal());
        for (Rank rank : value.kickers()) {
            numbers.add(rank.value());
        }
        return numbers;
    }

    private static int compare(List<Integer> one, List<Integer> other) {
        for (int i = 0; i < Math.min(one.size(), other.size()); i++) {
            int difference = Integer.compare(one.get(i), other.get(i));
            if (difference != 0) {
                return difference;
            }
        }
        return Integer.compare(one.size(), other.size());
    }
}
