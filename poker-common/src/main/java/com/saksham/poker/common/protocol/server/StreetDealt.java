package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.ServerMessage;
import java.util.List;

/** The flop, turn or river was dealt. */
@JsonTypeName("STREET_DEALT")
public final class StreetDealt extends ServerMessage {

    private final String street;
    private final List<Card> cards;
    private final List<Card> board;

    @JsonCreator
    public StreetDealt(
            @JsonProperty("street") String street,
            @JsonProperty("cards") List<Card> cards,
            @JsonProperty("board") List<Card> board) {
        this.street = street;
        this.cards = List.copyOf(cards);
        this.board = List.copyOf(board);
    }

    /** FLOP, TURN or RIVER. */
    public String street() {
        return street;
    }

    /** The cards just dealt. */
    public List<Card> cards() {
        return cards;
    }

    /** All community cards so far. */
    public List<Card> board() {
        return board;
    }
}
