package com.saksham.poker.ai.sim;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * A hand evaluator built for speed. It gives every five-to-seven-card hand a single number, and a
 * bigger number is a better hand. The engine's own evaluator is the authority on who wins a real
 * pot; this one agrees with it (a test checks a quarter of a million hands) and is used only inside
 * simulations, where it runs millions of times.
 *
 * <p>A card is a number from 0 to 51: four times its rank (0 for a two up to 12 for an ace) plus
 * its suit (0 to 3).
 */
public final class FastHand {

    public static final int CARDS = 52;

    private static final int STRAIGHT_FLUSH = 8;
    private static final int FOUR = 7;
    private static final int FULL_HOUSE = 6;
    private static final int FLUSH = 5;
    private static final int STRAIGHT = 4;
    private static final int THREE = 3;
    private static final int TWO_PAIR = 2;
    private static final int PAIR = 1;

    private FastHand() {
    }

    /** The number for a card. */
    public static int code(Card card) {
        return (card.rank().value() - 2) * 4 + card.suit().ordinal();
    }

    public static int[] codes(List<Card> cards) {
        int[] codes = new int[cards.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = code(cards.get(i));
        }
        return codes;
    }

    public static int rank(int code) {
        return code >> 2;
    }

    public static int suit(int code) {
        return code & 3;
    }

    /** The category of a score: 0 for high card up to 8 for a straight flush. */
    public static int category(int score) {
        return score >>> 20;
    }

    /**
     * Scores the best five-card hand among the given cards.
     *
     * @param cards card numbers; only the first {@code count} are read
     * @param count how many cards, 5 to 7
     */
    public static int score(int[] cards, int count) {
        int ranks = 0;      // one bit per rank present
        int s0 = 0;         // ranks present in each suit
        int s1 = 0;
        int s2 = 0;
        int s3 = 0;
        // Four bits per rank: how many cards of that rank. 13 ranks fit in a long.
        long counts = 0;
        for (int i = 0; i < count; i++) {
            int rank = cards[i] >> 2;
            int bit = 1 << rank;
            ranks |= bit;
            switch (cards[i] & 3) {
                case 0 -> s0 |= bit;
                case 1 -> s1 |= bit;
                case 2 -> s2 |= bit;
                default -> s3 |= bit;
            }
            counts += 1L << (rank * 4);
        }

        int flush = Integer.bitCount(s0) >= 5 ? s0 : Integer.bitCount(s1) >= 5 ? s1
                : Integer.bitCount(s2) >= 5 ? s2 : Integer.bitCount(s3) >= 5 ? s3 : 0;
        if (flush != 0) {
            int high = straightHigh(flush);
            if (high >= 0) {
                return STRAIGHT_FLUSH << 20 | high;
            }
        }

        int quads = -1;
        int trips = -1;
        int secondTrips = -1;
        int pair = -1;
        int secondPair = -1;
        for (int rank = 12; rank >= 0; rank--) {
            int n = (int) (counts >>> (rank * 4)) & 15;
            if (n == 4) {
                quads = rank;
            } else if (n == 3) {
                if (trips < 0) {
                    trips = rank;
                } else if (secondTrips < 0) {
                    secondTrips = rank;
                }
            } else if (n == 2) {
                if (pair < 0) {
                    pair = rank;
                } else if (secondPair < 0) {
                    secondPair = rank;
                }
            }
        }

        if (quads >= 0) {
            return FOUR << 20 | quads << 4 | top(ranks & ~(1 << quads), 1);
        }
        if (trips >= 0 && (pair >= 0 || secondTrips >= 0)) {
            return FULL_HOUSE << 20 | trips << 4 | Math.max(pair, secondTrips);
        }
        if (flush != 0) {
            return FLUSH << 20 | top(flush, 5);
        }
        int high = straightHigh(ranks);
        if (high >= 0) {
            return STRAIGHT << 20 | high;
        }
        if (trips >= 0) {
            return THREE << 20 | trips << 8 | top(ranks & ~(1 << trips), 2);
        }
        if (secondPair >= 0) {
            return TWO_PAIR << 20 | pair << 8 | secondPair << 4 | top(ranks & ~(1 << pair) & ~(1 << secondPair), 1);
        }
        if (pair >= 0) {
            return PAIR << 20 | pair << 12 | top(ranks & ~(1 << pair), 3);
        }
        return top(ranks, 5);
    }

    /** The top rank of the best straight in a set of ranks, or -1. The ace also plays low. */
    private static int straightHigh(int ranks) {
        for (int high = 12; high >= 4; high--) {
            if ((ranks >>> (high - 4) & 0x1F) == 0x1F) {
                return high;
            }
        }
        return (ranks & 0x100F) == 0x100F ? 3 : -1;
    }

    /** The highest {@code n} ranks of a set, packed four bits each, highest first. */
    private static int top(int ranks, int n) {
        int packed = 0;
        for (int rank = 12; rank >= 0 && n > 0; rank--) {
            if ((ranks & 1 << rank) != 0) {
                packed = packed << 4 | rank;
                n--;
            }
        }
        return packed << (4 * n); // fewer ranks than asked for: pad, so lengths compare fairly
    }
}
