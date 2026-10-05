package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** The room has a new host. */
@JsonTypeName("HOST_CHANGED")
public final class HostChanged extends ServerMessage {

    private final long hostUserId;

    @JsonCreator
    public HostChanged(@JsonProperty("hostUserId") long hostUserId) {
        this.hostUserId = hostUserId;
    }

    public long hostUserId() {
        return hostUserId;
    }
}
