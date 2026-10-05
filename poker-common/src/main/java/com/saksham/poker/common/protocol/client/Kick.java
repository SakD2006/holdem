package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Host only: remove a player while the room is waiting to start. */
@JsonTypeName("KICK")
public final class Kick extends ClientMessage {

    private final long userId;

    @JsonCreator
    public Kick(@JsonProperty("userId") long userId) {
        this.userId = userId;
    }

    public long userId() {
        return userId;
    }
}
