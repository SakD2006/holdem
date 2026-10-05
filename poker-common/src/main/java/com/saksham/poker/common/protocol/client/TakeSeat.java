package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Sit down at a free seat in the room. */
@JsonTypeName("TAKE_SEAT")
public final class TakeSeat extends ClientMessage {

    private final int seat;

    @JsonCreator
    public TakeSeat(@JsonProperty("seat") int seat) {
        this.seat = seat;
    }

    public int seat() {
        return seat;
    }
}
