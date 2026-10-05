package com.saksham.poker.common.card;

/** The four suits. Suits are never ranked against each other in Hold'em. */
public enum Suit {
    CLUBS('c'),
    DIAMONDS('d'),
    HEARTS('h'),
    SPADES('s');

    private final char symbol;

    Suit(char symbol) {
        this.symbol = symbol;
    }

    /** The lower-case letter used in card text, such as {@code h} in {@code "Ah"}. */
    public char symbol() {
        return symbol;
    }

    public static Suit fromSymbol(char symbol) {
        for (Suit suit : values()) {
            if (suit.symbol == symbol) {
                return suit;
            }
        }
        throw new IllegalArgumentException("Unknown suit '" + symbol + "'. Use one of c, d, h, s.");
    }
}
