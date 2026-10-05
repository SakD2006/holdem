package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** The answer to PING. */
@JsonTypeName("PONG")
public final class Pong extends ServerMessage {

    public Pong() {
    }
}
