package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Buy back to the starting stack after losing every chip, if the room allows it. */
@JsonTypeName("REBUY")
public final class Rebuy extends ClientMessage {

    public Rebuy() {
    }
}
