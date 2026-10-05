package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Host only: stop dealing after the current hand. */
@JsonTypeName("PAUSE_GAME")
public final class PauseGame extends ClientMessage {

    public PauseGame() {
    }
}
