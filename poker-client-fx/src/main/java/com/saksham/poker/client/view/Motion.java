package com.saksham.poker.client.view;

import javafx.util.Duration;

/**
 * The timings of everything that moves at the table, in one place so the pace stays consistent, and
 * a switch to turn movement off.
 */
public final class Motion {

    /**
     * False to make every change appear at once. Used when screens are drawn to pictures, which
     * cannot wait for a card to finish turning over.
     */
    static boolean enabled = true;

    /** A card travelling from the deck or the middle of the table to its place. */
    static final Duration DEAL = Duration.millis(260);
    /** Half of a card turning over: it narrows to nothing, then widens again showing its other side. */
    static final Duration HALF_FLIP = Duration.millis(130);
    /** The gap between one card and the next when several are dealt or turned over together. */
    static final long STAGGER_MS = 130;
    /** The gap between one player's hand being turned over and the next, at showdown. */
    static final long REVEAL_GAP_MS = 380;
    /** How long after the last hand is shown the winner is announced. */
    static final long WINNER_PAUSE_MS = 450;
    /** Chips travelling between a player and the pot. */
    static final Duration CHIPS = Duration.millis(420);
    /** A folded hand sliding away. */
    static final Duration MUCK = Duration.millis(220);

    private Motion() {
    }

    /** How long after a showdown begins the winner should be shown, given how many hands are turned over. */
    static long winnerDelayMs(int handsShown) {
        if (!enabled) {
            return 0;
        }
        return handsShown == 0 ? 150 : handsShown * REVEAL_GAP_MS + WINNER_PAUSE_MS;
    }
}
