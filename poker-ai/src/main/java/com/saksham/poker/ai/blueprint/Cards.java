package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.sim.FastHand;

/**
 * Card helpers shared with the trainer (`trainer/src/holdem/abstraction.rs`): starting-hand classes,
 * the numbering of hole-card pairs, and exact river strength. A card is a {@link FastHand} code,
 * rank × 4 + suit, the same numbers the trainer uses.
 */
public final class Cards {

    /** Starting-hand classes: 13 pairs, 78 suited and 78 offsuit hands. */
    public static final int PREFLOP_CLASSES = 169;
    /** Pairs of different cards. */
    public static final int COMBOS = 1326;

    private static final int[][] ALL = new int[COMBOS][];

    static {
        for (int b = 1; b < 52; b++) {
            for (int a = 0; a < b; a++) {
                ALL[comboIndex(a, b)] = new int[] {a, b};
            }
        }
    }

    private Cards() {
    }

    /** The class of a starting hand: pairs 0..13, then suited, then offsuit. */
    public static int preflopClass(int c0, int c1) {
        int a = c0 >> 2;
        int b = c1 >> 2;
        int high = Math.max(a, b);
        int low = Math.min(a, b);
        if (high == low) {
            return high;
        }
        int index = high * (high - 1) / 2 + low;
        return (c0 & 3) == (c1 & 3) ? 13 + index : 13 + 78 + index;
    }

    /** The number of a pair of different cards, 0..1326. */
    public static int comboIndex(int a, int b) {
        int low = Math.min(a, b);
        int high = Math.max(a, b);
        return high * (high - 1) / 2 + low;
    }

    /** The two cards of pair number {@code index}, lower first. Do not change the array. */
    public static int[] combo(int index) {
        return ALL[index];
    }

    /**
     * Chance of beating a random opponent hand on a complete board, ties counting half, against
     * every possible opponent hand.
     */
    public static float riverStrength(int c0, int c1, int[] board) {
        long used = 1L << c0 | 1L << c1;
        for (int c : board) {
            used |= 1L << c;
        }
        int[] seven = {c0, c1, board[0], board[1], board[2], board[3], board[4]};
        int mine = FastHand.score(seven, 7);
        int points = 0;
        int hands = 0;
        for (int a = 0; a < 52; a++) {
            if ((used & 1L << a) != 0) {
                continue;
            }
            for (int b = a + 1; b < 52; b++) {
                if ((used & 1L << b) != 0) {
                    continue;
                }
                seven[0] = a;
                seven[1] = b;
                int theirs = FastHand.score(seven, 7);
                points += mine > theirs ? 2 : mine == theirs ? 1 : 0;
                hands++;
            }
        }
        return (float) points / (float) (2 * hands);
    }

    /**
     * {@link #riverStrength} of every pair of hole cards on one board, by {@link #comboIndex}; NaN
     * for pairs that use a board card. Each hand is scored once and the hands compared pairwise.
     */
    public static float[] allRiverStrengths(int[] board) {
        long used = 0;
        for (int c : board) {
            used |= 1L << c;
        }
        int[] seven = {0, 0, board[0], board[1], board[2], board[3], board[4]};
        int[] index = new int[1081];
        long[] mask = new long[1081];
        int[] score = new int[1081];
        int n = 0;
        for (int i = 0; i < COMBOS; i++) {
            int[] hole = ALL[i];
            long m = 1L << hole[0] | 1L << hole[1];
            if ((used & m) == 0) {
                seven[0] = hole[0];
                seven[1] = hole[1];
                index[n] = i;
                mask[n] = m;
                score[n] = FastHand.score(seven, 7);
                n++;
            }
        }
        float[] out = new float[COMBOS];
        java.util.Arrays.fill(out, Float.NaN);
        for (int h = 0; h < n; h++) {
            int points = 0;
            int count = 0;
            for (int o = 0; o < n; o++) {
                if ((mask[o] & mask[h]) == 0) {
                    points += score[h] > score[o] ? 2 : score[h] == score[o] ? 1 : 0;
                    count++;
                }
            }
            out[index[h]] = (float) points / (float) (2 * count);
        }
        return out;
    }
}
