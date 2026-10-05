package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Host only: start dealing again after a pause. */
@JsonTypeName("RESUME_GAME")
public final class ResumeGame extends ClientMessage {

    public ResumeGame() {
    }
}
