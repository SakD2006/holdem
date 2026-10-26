package com.saksham.poker.ai.blueprint;

/**
 * Which bucket a hand falls in on each street. {@link AbstractionData} is the trainer's real
 * abstraction; {@link CategoryBuckets} is a cheap stand-in for tests.
 */
public interface CardBuckets {

    /**
     * The bucket of hole cards {@code h0, h1} on {@code street} (0 to 3), with at least that
     * street's board cards in {@code board}.
     */
    int bucket(int street, int h0, int h1, int[] board);

    /**
     * The bucket of every pair of hole cards (by {@link Cards#comboIndex}) on {@code street}; pairs
     * that use a board card get 0.
     */
    int[] rangeBuckets(int street, int[] board);
}
