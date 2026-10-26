package com.saksham.poker.ai.blueprint;

import java.util.Arrays;

/**
 * Maps a real bet onto the blueprint's few bet sizes. Sizes are compared as fractions of the pot
 * after calling. A bet between two sizes the blueprint knows goes to one of them at random, with
 * the pseudo-harmonic mapping (Ganzfried and Sandholm, 2013): of sizes {@code A < x < B}, it goes to
 * {@code A} with probability {@code (B − x)(1 + A) / ((B − A)(1 + x))}. Bets below the smallest size
 * or above the largest go to that size.
 */
public final class ActionTranslator {

    private ActionTranslator() {
    }

    /**
     * The blueprint action for a bet or raise that makes the actor's street total {@code to}
     * (in the blueprint's chips), or {@link AbstractHand#CALL} if the blueprint allows no raise here.
     *
     * @param allIn true if the real action put the player all-in
     * @param draw a number in [0, 1) choosing between two neighbouring sizes
     */
    public static int raise(AbstractHand hand, double to, boolean allIn, double draw) {
        int actor = hand.toAct();
        int[] legal = hand.legalActions();
        int[] raises = Arrays.stream(legal).filter(a -> a != AbstractHand.FOLD && a != AbstractHand.CALL).toArray();
        if (raises.length == 0) {
            return AbstractHand.CALL;
        }
        if (allIn && contains(legal, AbstractHand.ALL_IN)) {
            return AbstractHand.ALL_IN;
        }
        double base = hand.streetBet(1 - actor);
        double pot = hand.pot() + hand.toCall(actor);
        double x = (to - base) / pot;
        // Raise actions come out of legalActions smallest first, with all-in last and largest.
        double[] sizes = new double[raises.length];
        for (int i = 0; i < raises.length; i++) {
            sizes[i] = (hand.raiseTo(raises[i]) - base) / pot;
        }
        if (x <= sizes[0]) {
            return raises[0];
        }
        for (int i = 1; i < sizes.length; i++) {
            if (x <= sizes[i]) {
                double a = sizes[i - 1];
                double b = sizes[i];
                double toSmaller = (b - x) * (1 + a) / ((b - a) * (1 + x));
                return draw < toSmaller ? raises[i - 1] : raises[i];
            }
        }
        return raises[raises.length - 1];
    }

    private static boolean contains(int[] actions, int action) {
        for (int a : actions) {
            if (a == action) {
                return true;
            }
        }
        return false;
    }
}
