package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Stop being dealt in from the next hand, keeping the seat. */
@JsonTypeName("SIT_OUT")
public final class SitOut extends ClientMessage {

    public SitOut() {
    }
}
