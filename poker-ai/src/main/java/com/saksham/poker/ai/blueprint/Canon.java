package com.saksham.poker.ai.blueprint;

import java.util.Arrays;

/**
 * Canonical keys for deals that differ only by suit names (`trainer/src/holdem/canon.rs`): the
 * smallest packed form over all 24 renamings of the suits. The flop and turn bucket tables are
 * indexed by these keys.
 */
public final class Canon {

    private static final int[][] PERMUTATIONS = permutations();

    private Canon() {
    }

    private static int[][] permutations() {
        int[][] out = new int[24][];
        int n = 0;
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                for (int c = 0; c < 4; c++) {
                    int d = 6 - a - b - c;
                    if (a != b && a != c && b != c && d >= 0 && d < 4 && d != a && d != b && d != c) {
                        out[n++] = new int[] {a, b, c, d};
                    }
                }
            }
        }
        return out;
    }

    /** The key of hole cards on the first {@code n} cards of {@code board} (0 to 5). */
    public static long key(int h0, int h1, int[] board, int n) {
        long best = -1; // compared unsigned: the largest possible value
        int[] renamed = new int[n];
        for (int[] perm : PERMUTATIONS) {
            for (int i = 0; i < n; i++) {
                renamed[i] = rename(board[i], perm);
            }
            Arrays.sort(renamed);
            int a = rename(h0, perm);
            int b = rename(h1, perm);
            long key = (long) Math.min(a, b) << 6 | Math.max(a, b);
            for (int i = 0; i < n; i++) {
                key = key << 6 | renamed[i];
            }
            if (Long.compareUnsigned(key, best) < 0) {
                best = key;
            }
        }
        return best;
    }

    private static int rename(int card, int[] perm) {
        return (card & ~3) | perm[card & 3];
    }
}
