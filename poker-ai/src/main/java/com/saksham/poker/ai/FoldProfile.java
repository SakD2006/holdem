package com.saksham.poker.ai;

import java.util.Arrays;

/**
 * How often a player folds to a bet after the flop, by situation. One overall rate mixes things
 * that have nothing in common: a player who folds a weak hand after checking may never fold after
 * betting, and a player who weighs pot odds folds to big bets and calls small ones.
 *
 * <p>A situation is whether the player had already bet or raised on that street, and the size of
 * the bet they faced, by the pot odds it offered them: small (the call is under a quarter of the
 * pot after calling, a bet up to half the pot), medium (up to two fifths, a bet up to the pot) or
 * large.
 *
 * @param rates fold rate in each situation (see {@link #cell}); copied
 * @param seen how many times each situation was seen; copied
 */
public record FoldProfile(double[] rates, int[] seen) {

    /** Bet sizes told apart. */
    public static final int SIZES = 3;
    /** Situations: {@code 2 × SIZES}. */
    public static final int CELLS = 2 * SIZES;

    /**
     * A typical casual player: folds more to bigger bets, and much less once they have shown
     * strength by betting themselves.
     */
    public static final FoldProfile TYPICAL =
            new FoldProfile(new double[] {0.40, 0.50, 0.60, 0.15, 0.25, 0.35}, new int[CELLS]);

    public FoldProfile {
        if (rates.length != CELLS || seen.length != CELLS) {
            throw new IllegalArgumentException("A fold profile has " + CELLS + " situations.");
        }
        rates = rates.clone();
        seen = seen.clone();
    }

    /** The size class of a bet that offers these pot odds (the call as a share of the pot after it). */
    public static int size(double potOdds) {
        return potOdds < 0.25 ? 0 : potOdds < 0.4 ? 1 : 2;
    }

    /** The index of a situation. */
    public static int cell(boolean afterBetting, int size) {
        return (afterBetting ? SIZES : 0) + size;
    }

    public double rate(boolean afterBetting, int size) {
        return rates[cell(afterBetting, size)];
    }

    public int seen(boolean afterBetting, int size) {
        return seen[cell(afterBetting, size)];
    }

    @Override
    public double[] rates() {
        return rates.clone();
    }

    @Override
    public int[] seen() {
        return seen.clone();
    }

    /** Equal when every rate and count is equal (a record would compare the arrays' identity). */
    @Override
    public boolean equals(Object other) {
        return other instanceof FoldProfile o && Arrays.equals(rates, o.rates) && Arrays.equals(seen, o.seen);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(rates) + Arrays.hashCode(seen);
    }

    @Override
    public String toString() {
        return "FoldProfile[checked " + rounded(0) + ", after betting " + rounded(SIZES) + ", seen "
                + Arrays.toString(seen) + "]";
    }

    private String rounded(int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < from + SIZES; i++) {
            out.append(i == from ? "" : "/").append(Math.round(rates[i] * 100));
        }
        return out.toString();
    }
}
