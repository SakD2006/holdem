package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.sim.FastHand;

/**
 * The trainer's `CategoryBuckets`: the 169 starting hands before the flop, and after it the
 * category of the best hand so far (high card up to straight flush). Needs no tables; for tests.
 */
public final class CategoryBuckets implements CardBuckets {

    @Override
    public int bucket(int street, int h0, int h1, int[] board) {
        if (street == 0) {
            return Cards.preflopClass(h0, h1);
        }
        int shown = street + 2;
        int[] cards = new int[2 + shown];
        cards[0] = h0;
        cards[1] = h1;
        System.arraycopy(board, 0, cards, 2, shown);
        return FastHand.category(FastHand.score(cards, cards.length));
    }

    @Override
    public int[] rangeBuckets(int street, int[] board) {
        int shown = street == 0 ? 0 : street + 2;
        long used = 0;
        for (int i = 0; i < shown; i++) {
            used |= 1L << board[i];
        }
        int[] out = new int[Cards.COMBOS];
        for (int c = 0; c < Cards.COMBOS; c++) {
            int[] hole = Cards.combo(c);
            if ((used & (1L << hole[0] | 1L << hole[1])) == 0) {
                out[c] = bucket(street, hole[0], hole[1], board);
            }
        }
        return out;
    }
}
