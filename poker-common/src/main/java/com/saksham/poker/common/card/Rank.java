package com.saksham.poker.common.card;

/** Card ranks from lowest to highest, so {@code compareTo} orders them by strength. */
public enum Rank {
    TWO('2'),
    THREE('3'),
    FOUR('4'),
    FIVE('5'),
    SIX('6'),
    SEVEN('7'),
    EIGHT('8'),
    NINE('9'),
    TEN('T'),
    JACK('J'),
    QUEEN('Q'),
    KING('K'),
    ACE('A');

    private final char symbol;

    Rank(char symbol) {
        this.symbol = symbol;
    }

    /** The character used in card text, such as {@code A} in {@code "Ah"}. */
    public char symbol() {
        return symbol;
    }

    /** 2 for a two up to 14 for an ace. */
    public int value() {
        return ordinal() + 2;
    }

    public static Rank fromSymbol(char symbol) {
        for (Rank rank : values()) {
            if (rank.symbol == symbol) {
                return rank;
            }
        }
        throw new IllegalArgumentException(
                "Unknown rank '" + symbol + "'. Use one of 2 3 4 5 6 7 8 9 T J Q K A.");
    }
}
