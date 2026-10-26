package com.saksham.poker.ai.blueprint;

import com.saksham.poker.ai.sim.FastHand;
import java.util.Random;

/**
 * How often a hand beats each hand of an opponent's range at showdown, over the board cards still
 * to come (the trainer's `win_chances` in `lbr.rs`). Exact on the river and the turn; on the flop
 * and before it, a sample of run-outs.
 */
final class RangeEquity {

    /** Random run-outs before the turn: with no board, and on the flop. */
    private static final int PREFLOP_RUNOUTS = 40;
    private static final int FLOP_RUNOUTS = 60;

    private RangeEquity() {
    }

    /**
     * For every opponent hand (by {@link Cards#comboIndex}) with weight, the chance {@code hole}
     * beats it, ties counting half; 0 for hands without weight.
     *
     * @param shown how many cards of {@code board} are out (0, 3, 4 or 5)
     */
    static double[] winChances(int[] hole, int[] board, int shown, double[] weights, Random random) {
        long used = 1L << hole[0] | 1L << hole[1];
        for (int i = 0; i < shown; i++) {
            used |= 1L << board[i];
        }
        int[][] runouts;
        if (shown == 5) {
            runouts = new int[][] {{}};
        } else if (shown == 4) {
            runouts = new int[52 - Long.bitCount(used)][];
            int n = 0;
            for (int c = 0; c < 52; c++) {
                if ((used & 1L << c) == 0) {
                    runouts[n++] = new int[] {c};
                }
            }
        } else {
            runouts = new int[shown == 0 ? PREFLOP_RUNOUTS : FLOP_RUNOUTS][];
            for (int r = 0; r < runouts.length; r++) {
                long taken = used;
                runouts[r] = new int[5 - shown];
                for (int k = 0; k < runouts[r].length; k++) {
                    int c;
                    do {
                        c = random.nextInt(52);
                    } while ((taken & 1L << c) != 0);
                    taken |= 1L << c;
                    runouts[r][k] = c;
                }
            }
        }
        double[] points = new double[Cards.COMBOS];
        int[] counted = new int[Cards.COMBOS];
        int[] seven = new int[7];
        for (int[] runout : runouts) {
            long boardMask = 0;
            for (int i = 0; i < shown; i++) {
                seven[2 + i] = board[i];
                boardMask |= 1L << board[i];
            }
            for (int i = 0; i < runout.length; i++) {
                seven[2 + shown + i] = runout[i];
                boardMask |= 1L << runout[i];
            }
            seven[0] = hole[0];
            seven[1] = hole[1];
            int mine = FastHand.score(seven, 7);
            for (int c = 0; c < Cards.COMBOS; c++) {
                int[] theirs = Cards.combo(c);
                if (weights[c] == 0 || (boardMask & (1L << theirs[0] | 1L << theirs[1])) != 0
                        || theirs[0] == hole[0] || theirs[0] == hole[1] || theirs[1] == hole[0] || theirs[1] == hole[1]) {
                    continue;
                }
                seven[0] = theirs[0];
                seven[1] = theirs[1];
                int score = FastHand.score(seven, 7);
                points[c] += mine > score ? 1 : mine == score ? 0.5 : 0;
                counted[c]++;
            }
        }
        double[] out = new double[Cards.COMBOS];
        for (int c = 0; c < Cards.COMBOS; c++) {
            out[c] = counted[c] > 0 ? points[c] / counted[c] : 0;
        }
        return out;
    }
}
