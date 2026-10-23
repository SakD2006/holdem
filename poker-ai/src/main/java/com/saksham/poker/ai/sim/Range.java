package com.saksham.poker.ai.sim;

/**
 * A guess at what an opponent might be holding, from how they have played the hand so far.
 *
 * @param width the share of all starting hands they could have, counting from the best: 0.2 means
 *     "one of the best fifth". Someone who raised before the flop has a narrow range; someone who
 *     got in for free could have anything.
 * @param connected how strongly their hand is likely to have hit the board, 0 to 1. A player who
 *     has just bet usually has a pair or a draw; 0 means no such hint.
 */
public record Range(double width, double connected) {

    /** Any two cards. */
    public static final Range ANY = new Range(1, 0);

    public Range {
        width = Math.max(0.01, Math.min(1, width));
        connected = Math.max(0, Math.min(0.95, connected));
    }
}
