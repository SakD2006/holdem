package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Host only: start dealing hands. */
@JsonTypeName("START_GAME")
public final class StartGame extends ClientMessage {

    public StartGame() {
    }
}
