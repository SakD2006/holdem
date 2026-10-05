package com.saksham.poker.common.protocol.dto;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * A hand turned over at showdown.
 *
 * @param seat the player's seat
 * @param cards their two hole cards
 * @param category the kind of hand they made, such as {@code TWO_PAIR}
 */
public record ShownHandInfo(int seat, List<Card> cards, String category) {

    public ShownHandInfo {
        cards = List.copyOf(cards);
    }
}
