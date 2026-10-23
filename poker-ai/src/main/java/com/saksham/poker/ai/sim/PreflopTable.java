package com.saksham.poker.ai.sim;

import java.util.Arrays;
import java.util.Random;

/**
 * How good each pair of hole cards is, as a place in the queue of all 1,326 possible starting
 * hands: 0 for the very best (a pair of aces) up to nearly 1 for the worst. A bot that wants to
 * "play the best fifth of hands" plays those with a place under 0.2.
 *
 * <p>The order is worked out once, when the class is first used, by simulation: each kind of hand
 * is played out against a random hand many times and the kinds are sorted by how often they win.
 * A fixed seed makes it come out the same on every run.
 */
public final class PreflopTable {

    private static final int DEALS_PER_KIND = 3_000;
    /** place[a][b] for two card numbers. */
    private static final float[][] PLACE = new float[FastHand.CARDS][FastHand.CARDS];

    static {
        build();
    }

    private PreflopTable() {
    }

    /** The place of a starting hand among all starting hands: 0 is best. */
    public static double place(int first, int second) {
        return PLACE[first][second];
    }

    private static void build() {
        // The 169 kinds of hand: 13 pairs, 78 suited and 78 offsuit combinations of two ranks.
        // kind = high * 13 + low for suited, low * 13 + high for offsuit, rank * 14 for a pair.
        double[] winRate = new double[169];
        int[] combos = new int[169];
        Random random = new Random(20261006);
        int[] mine = new int[7];
        int[] theirs = new int[7];
        boolean[] used = new boolean[FastHand.CARDS];
        for (int high = 0; high < 13; high++) {
            for (int low = 0; low <= high; low++) {
                for (int suited = 0; suited < 2; suited++) {
                    if (high == low && suited == 1) {
                        continue;
                    }
                    int a = high * 4;                               // a club
                    int b = low * 4 + (suited == 1 ? 0 : 1);        // a club too, or a diamond
                    double won = 0;
                    for (int deal = 0; deal < DEALS_PER_KIND; deal++) {
                        Arrays.fill(used, false);
                        used[a] = true;
                        used[b] = true;
                        mine[0] = a;
                        mine[1] = b;
                        theirs[0] = draw(used, random);
                        theirs[1] = draw(used, random);
                        for (int i = 2; i < 7; i++) {
                            int card = draw(used, random);
                            mine[i] = card;
                            theirs[i] = card;
                        }
                        int ours = FastHand.score(mine, 7);
                        int other = FastHand.score(theirs, 7);
                        won += ours > other ? 1 : ours == other ? 0.5 : 0;
                    }
                    int kind = kind(high, low, suited == 1);
                    winRate[kind] = won / DEALS_PER_KIND;
                    combos[kind] = high == low ? 6 : suited == 1 ? 4 : 12;
                }
            }
        }
        // A kind's place is the share of all hands that belong to stronger kinds.
        Integer[] order = new Integer[169];
        for (int i = 0; i < 169; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Double.compare(winRate[y], winRate[x]));
        float[] placeOfKind = new float[169];
        int better = 0;
        for (int kind : order) {
            placeOfKind[kind] = better / 1326f;
            better += combos[kind];
        }
        for (int a = 0; a < FastHand.CARDS; a++) {
            for (int b = 0; b < FastHand.CARDS; b++) {
                if (a != b) {
                    int high = Math.max(FastHand.rank(a), FastHand.rank(b));
                    int low = Math.min(FastHand.rank(a), FastHand.rank(b));
                    PLACE[a][b] = placeOfKind[kind(high, low, FastHand.suit(a) == FastHand.suit(b))];
                }
            }
        }
    }

    private static int kind(int high, int low, boolean suited) {
        return suited ? high * 13 + low : low * 13 + high;
    }

    private static int draw(boolean[] used, Random random) {
        while (true) {
            int card = random.nextInt(FastHand.CARDS);
            if (!used[card]) {
                used[card] = true;
                return card;
            }
        }
    }
}
