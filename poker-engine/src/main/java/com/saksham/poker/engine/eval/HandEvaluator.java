package com.saksham.poker.engine.eval;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Rank;
import com.saksham.poker.common.card.Suit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Finds the best five-card poker hand in five, six or seven cards. */
public final class HandEvaluator {

    private static final int HAND_SIZE = 5;
    private static final int MAX_CARDS = 7;

    private HandEvaluator() {
    }

    /**
     * The value of the best five-card hand that can be made from the given cards. With seven cards
     * (two hole cards and the board) this tries all 21 ways of choosing five.
     *
     * @param cards five to seven different cards
     */
    public static HandValue evaluate(Collection<Card> cards) {
        if (cards.size() < HAND_SIZE || cards.size() > MAX_CARDS) {
            throw new IllegalArgumentException(
                    "A hand is evaluated from 5 to 7 cards, but got " + cards.size() + ".");
        }
        if (new HashSet<>(cards).size() != cards.size()) {
            throw new IllegalArgumentException("A hand cannot hold the same card twice: " + cards);
        }
        List<Card> all = List.copyOf(cards);
        int n = all.size();
        HandValue best = null;
        for (int a = 0; a < n - 4; a++) {
            for (int b = a + 1; b < n - 3; b++) {
                for (int c = b + 1; c < n - 2; c++) {
                    for (int d = c + 1; d < n - 1; d++) {
                        for (int e = d + 1; e < n; e++) {
                            HandValue value = evaluateFive(
                                    List.of(all.get(a), all.get(b), all.get(c), all.get(d), all.get(e)));
                            if (best == null || value.compareTo(best) > 0) {
                                best = value;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static HandValue evaluateFive(List<Card> five) {
        Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
        Suit firstSuit = five.get(0).suit();
        boolean flush = true;
        for (Card card : five) {
            counts.merge(card.rank(), 1, Integer::sum);
            flush &= card.suit() == firstSuit;
        }

        // Ranks held most often come first, and higher ranks before lower ones. That is exactly the
        // tie-break order for every hand except a straight: pair before side cards, trips before the
        // pair of a full house, and so on.
        List<Rank> ranks = new ArrayList<>(counts.keySet());
        ranks.sort(Comparator.<Rank, Integer>comparing(counts::get).thenComparing(Comparator.naturalOrder())
                .reversed());
        int most = counts.get(ranks.get(0));

        switch (ranks.size()) {
            case 2:
                return new HandValue(most == 4 ? HandCategory.FOUR_OF_A_KIND : HandCategory.FULL_HOUSE, ranks);
            case 3:
                return new HandValue(most == 3 ? HandCategory.THREE_OF_A_KIND : HandCategory.TWO_PAIR, ranks);
            case 4:
                return new HandValue(HandCategory.PAIR, ranks);
            default:
                break;
        }

        // Five different ranks, sorted high to low.
        Rank straightTop = straightTop(ranks);
        if (straightTop != null) {
            return new HandValue(
                    flush ? HandCategory.STRAIGHT_FLUSH : HandCategory.STRAIGHT, List.of(straightTop));
        }
        return new HandValue(flush ? HandCategory.FLUSH : HandCategory.HIGH_CARD, ranks);
    }

    /**
     * The top card of the straight formed by five different ranks sorted high to low, or null if they
     * do not form one. In the wheel (A-2-3-4-5) the ace plays low, so the top card is the five.
     */
    private static Rank straightTop(List<Rank> highToLow) {
        Rank top = highToLow.get(0);
        Rank bottom = highToLow.get(4);
        if (top.value() - bottom.value() == 4) {
            return top;
        }
        if (top == Rank.ACE && highToLow.get(1) == Rank.FIVE && bottom == Rank.TWO) {
            return Rank.FIVE;
        }
        return null;
    }
}
