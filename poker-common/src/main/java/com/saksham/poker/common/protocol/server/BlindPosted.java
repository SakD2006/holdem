package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** A player posted the small or big blind. */
@JsonTypeName("BLIND_POSTED")
public final class BlindPosted extends ServerMessage {

    private final int seat;
    private final long amount;
    private final boolean bigBlind;
    private final boolean allIn;

    @JsonCreator
    public BlindPosted(
            @JsonProperty("seat") int seat,
            @JsonProperty("amount") long amount,
            @JsonProperty("bigBlind") boolean bigBlind,
            @JsonProperty("allIn") boolean allIn) {
        this.seat = seat;
        this.amount = amount;
        this.bigBlind = bigBlind;
        this.allIn = allIn;
    }

    public int seat() {
        return seat;
    }

    /** Chips posted; less than the blind if the player could not cover it. */
    public long amount() {
        return amount;
    }

    /** True for the big blind, false for the small blind. */
    public boolean bigBlind() {
        return bigBlind;
    }

    public boolean allIn() {
        return allIn;
    }
}
