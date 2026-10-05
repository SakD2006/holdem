package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** The part of a bet nobody called went back to the player who made it. */
@JsonTypeName("BET_RETURNED")
public final class BetReturned extends ServerMessage {

    private final int seat;
    private final long amount;

    @JsonCreator
    public BetReturned(@JsonProperty("seat") int seat, @JsonProperty("amount") long amount) {
        this.seat = seat;
        this.amount = amount;
    }

    public int seat() {
        return seat;
    }

    public long amount() {
        return amount;
    }
}
