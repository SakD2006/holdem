package com.saksham.poker.engine.event;

import com.saksham.poker.common.card.Card;
import java.util.List;
import java.util.StringJoiner;

/**
 * Something that happened in a hand. The engine returns events in the order they happened; the
 * caller decides who is sent each one, what to store and when to pause.
 */
public abstract class GameEvent {

    /** One line for a hand log, such as "Seat 3 raises to 300". */
    public abstract String describe();

    /** Whether the player in this seat may see the event. Only hole cards are private. */
    public boolean isVisibleTo(int seat) {
        return true;
    }

    @Override
    public String toString() {
        return describe();
    }

    /** Cards as text with spaces between them, such as "Ah Kd 7c". */
    protected static String cards(List<Card> cards) {
        StringJoiner text = new StringJoiner(" ");
        for (Card card : cards) {
            text.add(card.toString());
        }
        return text.toString();
    }
}
