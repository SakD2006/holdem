package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Enter a room by its code. */
@JsonTypeName("JOIN_ROOM")
public final class JoinRoom extends ClientMessage {

    private final String code;

    @JsonCreator
    public JoinRoom(@JsonProperty("code") String code) {
        this.code = code;
    }

    /** The 6-character room code. */
    public String code() {
        return code;
    }
}
