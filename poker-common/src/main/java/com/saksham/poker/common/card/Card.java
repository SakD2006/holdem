package com.saksham.poker.common.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One playing card. Its text form is rank then suit, such as {@code "Ah"} or {@code "Tc"}; in JSON a
 * card is that text.
 */
public record Card(Rank rank, Suit suit) {

    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }

    /** Reads one card from text such as {@code "Ah"}. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Card parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() != 2) {
            throw new IllegalArgumentException(
                    "A card is two characters such as \"Ah\", but got \"" + text + "\".");
        }
        return new Card(Rank.fromSymbol(text.charAt(0)), Suit.fromSymbol(text.charAt(1)));
    }

    /** Reads several cards, with or without spaces between them: {@code "Ah Kd"} or {@code "AhKd"}. */
    public static List<Card> parseAll(String text) {
        Objects.requireNonNull(text, "text");
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Cards are two characters each such as \"Ah Kd\", but got \"" + text + "\".");
        }
        List<Card> cards = new ArrayList<>(compact.length() / 2);
        for (int i = 0; i < compact.length(); i += 2) {
            cards.add(parse(compact.substring(i, i + 2)));
        }
        return List.copyOf(cards);
    }

    /** Writes cards with no separator, such as {@code "AhKd"}. */
    public static String formatAll(List<Card> cards) {
        StringBuilder text = new StringBuilder(cards.size() * 2);
        for (Card card : cards) {
            text.append(card);
        }
        return text.toString();
    }

    @JsonValue
    @Override
    public String toString() {
        return "" + rank.symbol() + suit.symbol();
    }
}
