package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Check the connection is alive; answered with PONG. */
@JsonTypeName("PING")
public final class Ping extends ClientMessage {

    public Ping() {
    }
}
