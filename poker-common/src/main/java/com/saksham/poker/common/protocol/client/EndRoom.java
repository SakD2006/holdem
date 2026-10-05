package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Host only: close the room for everyone. */
@JsonTypeName("END_ROOM")
public final class EndRoom extends ClientMessage {

    public EndRoom() {
    }
}
