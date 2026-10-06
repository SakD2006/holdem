package com.saksham.poker.ai;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Suit;
import com.saksham.poker.engine.eval.HandCategory;
import com.saksham.poker.engine.eval.HandEvaluator;
import com.saksham.poker.engine.eval.HandValue;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Rules of thumb for how good a hand is. Quick to work out, and good enough for a simple bot. */
public final class HandStrength {

    private HandStrength() {
    }

    // =====================================================================================
    // Before the flop
    // =====================================================================================

    /**
     * Scores two hole cards with the Chen formula, a well-known shortcut: from about -1 for the
     * worst hands (7-2 offsuit) to 20 for a pair of aces. Roughly, 10 and up is a strong hand, 7 to 9
     * is playable, and under 5 is not.
     */
    public static int chen(Card first, Card second) {
        int high = Math.max(first.rank().value(), second.rank().value());
        int low = Math.min(first.rank().value(), second.rank().value());
        double score = highCardPoints(high);
        if (high == low) {
            return (int) Math.max(5, score * 2);
        }
        if (first.suit() == second.suit()) {
            score += 2;
        }
        int gap = high - low - 1;
        score -= switch (gap) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            default -> 5;
        };
        // Low cards close together can make straights at both ends.
        if (gap <= 1 && high < 12) {
            score += 1;
        }
        return (int) Math.ceil(score);
    }

    private static double highCardPoints(int value) {
        return switch (value) {
            case 14 -> 10;
            case 13 -> 8;
            case 12 -> 7;
            case 11 -> 6;
            default -> value / 2.0;
        };
    }

    // =====================================================================================
    // After the flop
    // =====================================================================================

    /**
     * How strong the hand already is, from 0 (nothing) to 1 (cannot realistically lose). It judges
     * the hand against the board: a pair that is on the board for everyone is worth little, while
     * top pair or better is worth betting.
     *
     * @param hole the player's two cards
     * @param board three, four or five community cards
     */
    public static double made(List<Card> hole, List<Card> board) {
        List<Card> all = new ArrayList<>(hole);
        all.addAll(board);
        HandValue mine = HandEvaluator.evaluate(all);
        // On the river, a hand no better than the board itself beats nobody.
        if (board.size() == 5 && HandEvaluator.evaluate(board).compareTo(mine) == 0) {
            return 0.15;
        }
        int[] boardCount = counts(board);
        int[] allCount = counts(all);
        int h1 = hole.get(0).rank().value();
        int h2 = hole.get(1).rank().value();
        int topBoard = highest(boardCount, 1);
        boolean boardPaired = highest(boardCount, 2) > 0;

        switch (mine.category()) {
            case STRAIGHT_FLUSH:
            case FOUR_OF_A_KIND:
                return usesHole(allCount, boardCount, h1, h2, 4) || mine.category() == HandCategory.STRAIGHT_FLUSH
                        ? 1.0 : 0.4;
            case FULL_HOUSE:
                return highest(boardCount, 3) > 0 && h1 != h2 ? 0.75 : 0.96;
            case FLUSH: {
                Suit suit = flushSuit(all);
                int best = 0;
                for (Card card : hole) {
                    if (card.suit() == suit) {
                        best = Math.max(best, card.rank().value());
                    }
                }
                if (best == 0) {
                    return 0.3; // the board's flush: anyone with one card of the suit beats us
                }
                return best >= 13 ? 0.95 : best >= 10 ? 0.9 : 0.84;
            }
            case STRAIGHT:
                return boardPaired ? 0.84 : 0.88;
            case THREE_OF_A_KIND: {
                int rank = highest(allCount, 3);
                if (boardCount[rank] == 3) {
                    return 0.25 + kicker(Math.max(h1, h2)); // trips on the board: only the kicker is ours
                }
                return h1 == h2 ? 0.86 : 0.8;
            }
            case TWO_PAIR: {
                int mineInPairs = (allCount[h1] == 2 && boardCount[h1] < 2 ? 1 : 0)
                        + (h1 != h2 && allCount[h2] == 2 && boardCount[h2] < 2 ? 1 : 0);
                if (h1 == h2 && boardCount[h1] == 0) {
                    return onePair(h1, h1, boardCount, topBoard) + 0.04; // pocket pair plus the board's pair
                }
                if (mineInPairs == 2 && !boardPaired) {
                    return 0.78;
                }
                if (mineInPairs >= 1) {
                    int paired = allCount[h1] == 2 && boardCount[h1] < 2 ? h1 : h2;
                    return onePair(paired, paired == h1 ? h2 : h1, boardCount, topBoard) + 0.04;
                }
                return 0.2 + kicker(Math.max(h1, h2));
            }
            case PAIR: {
                if (h1 == h2) {
                    return onePair(h1, h1, boardCount, topBoard);
                }
                if (boardCount[h1] == 1) {
                    return onePair(h1, h2, boardCount, topBoard);
                }
                if (boardCount[h2] == 1) {
                    return onePair(h2, h1, boardCount, topBoard);
                }
                return 0.18 + kicker(Math.max(h1, h2)); // the pair is on the board
            }
            default: {
                double value = 0.08 + kicker(Math.max(h1, h2));
                if (h1 > topBoard && h2 > topBoard) {
                    value += 0.06;
                }
                return value;
            }
        }
    }

    /** One pair made with a hole card: an overpair, top pair, middle pair or a small one. */
    private static double onePair(int pairRank, int otherHole, int[] boardCount, int topBoard) {
        if (pairRank > topBoard) {
            return 0.7; // a pocket pair above every board card
        }
        if (pairRank == topBoard) {
            return 0.6 + (otherHole >= 13 ? 0.06 : otherHole >= 11 ? 0.03 : otherHole <= 8 ? -0.05 : 0);
        }
        int higher = 0;
        for (int rank = pairRank + 1; rank <= 14; rank++) {
            if (boardCount[rank] > 0) {
                higher++;
            }
        }
        return higher == 1 ? 0.45 : 0.34;
    }

    /** A little extra for a high side card. */
    private static double kicker(int rank) {
        return rank == 14 ? 0.05 : rank == 13 ? 0.03 : rank >= 11 ? 0.01 : 0;
    }

    /**
     * Cards still in the deck that would turn a drawing hand into a strong one: 9 for four cards to a
     * flush, 8 for a straight open at both ends, 4 for a straight with one gap. 0 on the river, when
     * no cards are left to come.
     */
    public static int outs(List<Card> hole, List<Card> board) {
        if (board.size() >= 5 || board.size() < 3) {
            return 0;
        }
        List<Card> all = new ArrayList<>(hole);
        all.addAll(board);
        int outs = 0;
        Map<Suit, Integer> suits = new EnumMap<>(Suit.class);
        for (Card card : all) {
            suits.merge(card.suit(), 1, Integer::sum);
        }
        for (Map.Entry<Suit, Integer> entry : suits.entrySet()) {
            boolean holdsOne = hole.get(0).suit() == entry.getKey() || hole.get(1).suit() == entry.getKey();
            if (entry.getValue() == 4 && holdsOne) {
                outs += 9;
            }
        }
        boolean[] present = new boolean[15];
        for (Card card : all) {
            present[card.rank().value()] = true;
        }
        present[1] = present[14]; // an ace also plays low
        if (!hasStraight(present)) {
            int ranksThatComplete = 0;
            for (int rank = 2; rank <= 14; rank++) {
                if (present[rank]) {
                    continue;
                }
                present[rank] = true;
                if (rank == 14) {
                    present[1] = true;
                }
                if (hasStraight(present)) {
                    ranksThatComplete++;
                }
                present[rank] = false;
                if (rank == 14) {
                    present[1] = false;
                }
            }
            outs += 4 * Math.min(2, ranksThatComplete);
        }
        return Math.min(15, outs);
    }

    /** The chance that a draw with this many outs arrives by the river. */
    public static double drawChance(int outs, int cardsToCome) {
        if (outs <= 0 || cardsToCome <= 0) {
            return 0;
        }
        double miss = 1;
        int unseen = cardsToCome == 2 ? 47 : 46;
        for (int i = 0; i < cardsToCome; i++) {
            miss *= (double) (unseen - i - outs) / (unseen - i);
        }
        return 1 - miss;
    }

    // ---- small helpers

    /** How many cards of each rank there are, indexed by rank value 2 to 14. */
    private static int[] counts(List<Card> cards) {
        int[] count = new int[15];
        for (Card card : cards) {
            count[card.rank().value()]++;
        }
        return count;
    }

    /** The highest rank that appears at least this many times, or 0. */
    private static int highest(int[] count, int times) {
        for (int rank = 14; rank >= 2; rank--) {
            if (count[rank] >= times) {
                return rank;
            }
        }
        return 0;
    }

    private static boolean usesHole(int[] allCount, int[] boardCount, int h1, int h2, int times) {
        return (allCount[h1] >= times && boardCount[h1] < times) || (allCount[h2] >= times && boardCount[h2] < times);
    }

    private static Suit flushSuit(List<Card> cards) {
        Map<Suit, Integer> suits = new EnumMap<>(Suit.class);
        for (Card card : cards) {
            suits.merge(card.suit(), 1, Integer::sum);
        }
        Suit best = cards.get(0).suit();
        for (Map.Entry<Suit, Integer> entry : suits.entrySet()) {
            if (entry.getValue() > suits.get(best)) {
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean hasStraight(boolean[] present) {
        int run = 0;
        for (int rank = 1; rank <= 14; rank++) {
            run = present[rank] ? run + 1 : 0;
            if (run >= 5) {
                return true;
            }
        }
        return false;
    }
}
