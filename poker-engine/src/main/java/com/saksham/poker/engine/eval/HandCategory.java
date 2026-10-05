package com.saksham.poker.engine.eval;

/** The kinds of five-card poker hand, from weakest to strongest. */
public enum HandCategory {
    HIGH_CARD,
    PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    /** Includes the royal flush, which is only the highest straight flush. */
    STRAIGHT_FLUSH
}
