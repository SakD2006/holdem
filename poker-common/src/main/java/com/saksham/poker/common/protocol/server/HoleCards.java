package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.ServerMessage;
import java.util.List;

/** Your two hole cards. Sent only to the player who holds them. */
@JsonTypeName("HOLE_CARDS")
public final class HoleCards extends ServerMessage {

    private final int seat;
    private final List<Card> cards;

    @JsonCreator
    public HoleCards(@JsonProperty("seat") int seat, @JsonProperty("cards") List<Card> cards) {
        this.seat = seat;
        this.cards = List.copyOf(cards);
    }

    public int seat() {
        return seat;
    }

    public List<Card> cards() {
        return cards;
    }
}
