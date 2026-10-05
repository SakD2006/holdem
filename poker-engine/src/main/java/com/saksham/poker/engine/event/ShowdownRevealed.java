package com.saksham.poker.engine.event;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.engine.eval.HandValue;
import java.util.List;

/** The players still in the hand turned their cards over. */
public final class ShowdownRevealed extends GameEvent {

    /**
     * One player's cards at showdown.
     *
     * @param seat the player's seat
     * @param cards their two hole cards
     * @param value the best five-card hand they make with the board
     */
    public record ShownHand(int seat, List<Card> cards, HandValue value) {

        public ShownHand {
            cards = List.copyOf(cards);
        }
    }

    private final List<ShownHand> hands;

    public ShowdownRevealed(List<ShownHand> hands) {
        this.hands = List.copyOf(hands);
    }

    /** The hands in the order they are shown. */
    public List<ShownHand> hands() {
        return hands;
    }

    @Override
    public String describe() {
        StringBuilder text = new StringBuilder("Showdown.");
        for (ShownHand hand : hands) {
            text.append(" Seat ").append(hand.seat()).append(" shows ").append(cards(hand.cards()))
                    .append(" (").append(hand.value().category().name().toLowerCase().replace('_', ' '))
                    .append(").");
        }
        return text.toString();
    }
}
