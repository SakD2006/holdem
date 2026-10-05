package com.saksham.poker.engine.eval;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Suit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A second evaluator, written a different way, to check {@link HandEvaluator} against. It never picks
 * five cards: it looks at all the cards at once and searches for each kind of hand from the strongest
 * down. It works on plain numbers (2 to 14) and shares no code with the real evaluator.
 *
 * <p>The answer is a list of numbers: the category (0 = high card up to 8 = straight flush) followed
 * by the tie-break ranks, most important first.
 */
final class ReferenceEvaluator {

    private ReferenceEvaluator() {
    }

    static List<Integer> evaluate(Collection<Card> cards) {
        int[] rankCount = new int[15];
        for (Card card : cards) {
            rankCount[card.rank().ordinal() + 2]++;
        }

        // Straight flush: the best straight inside any suit holding five or more cards.
        int[] flushRanks = null;
        for (Suit suit : Suit.values()) {
            int[] present = new int[15];
            int held = 0;
            for (Card card : cards) {
                if (card.suit() == suit) {
                    present[card.rank().ordinal() + 2] = 1;
                    held++;
                }
            }
            if (held >= 5) {
                flushRanks = present;
            }
        }
        if (flushRanks != null) {
            int top = highestStraight(flushRanks);
            if (top > 0) {
                return List.of(8, top);
            }
        }

        int quads = highestWithCount(rankCount, 4, 0, 0);
        if (quads > 0) {
            return answer(6 + 1, List.of(quads), rankCount, 1);
        }

        int trips = highestWithAtLeast(rankCount, 3, 0);
        if (trips > 0) {
            int pair = highestWithAtLeast(rankCount, 2, trips);
            if (pair > 0) {
                return List.of(6, trips, pair);
            }
        }

        if (flushRanks != null) {
            List<Integer> answer = new ArrayList<>(List.of(5));
            for (int rank = 14; rank >= 2 && answer.size() < 6; rank--) {
                if (flushRanks[rank] == 1) {
                    answer.add(rank);
                }
            }
            return answer;
        }

        int straightTop = highestStraight(rankCount);
        if (straightTop > 0) {
            return List.of(4, straightTop);
        }

        if (trips > 0) {
            return answer(3, List.of(trips), rankCount, 2);
        }

        int highPair = highestWithCount(rankCount, 2, 0, 0);
        if (highPair > 0) {
            int lowPair = highestWithCount(rankCount, 2, highPair, 0);
            if (lowPair > 0) {
                return answer(2, List.of(highPair, lowPair), rankCount, 1);
            }
            return answer(1, List.of(highPair), rankCount, 3);
        }

        return answer(0, List.of(), rankCount, 5);
    }

    /** Category, then the made ranks, then the highest {@code sideCards} ranks not already used. */
    private static List<Integer> answer(int category, List<Integer> made, int[] rankCount, int sideCards) {
        List<Integer> answer = new ArrayList<>();
        answer.add(category);
        answer.addAll(made);
        int added = 0;
        for (int rank = 14; rank >= 2 && added < sideCards; rank--) {
            if (rankCount[rank] > 0 && !made.contains(rank)) {
                answer.add(rank);
                added++;
            }
        }
        return answer;
    }

    private static int highestWithCount(int[] rankCount, int count, int skipA, int skipB) {
        for (int rank = 14; rank >= 2; rank--) {
            if (rankCount[rank] == count && rank != skipA && rank != skipB) {
                return rank;
            }
        }
        return 0;
    }

    private static int highestWithAtLeast(int[] rankCount, int count, int skip) {
        for (int rank = 14; rank >= 2; rank--) {
            if (rankCount[rank] >= count && rank != skip) {
                return rank;
            }
        }
        return 0;
    }

    /** The top rank of the best run of five, counting the ace as 1 for the wheel; 0 if there is none. */
    private static int highestStraight(int[] present) {
        for (int top = 14; top >= 5; top--) {
            boolean run = true;
            for (int rank = top; rank > top - 5; rank--) {
                int index = rank == 1 ? 14 : rank;
                if (present[index] == 0) {
                    run = false;
                    break;
                }
            }
            if (run) {
                return top;
            }
        }
        return 0;
    }
}
