package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Be dealt in again; the player waits for the big blind. */
@JsonTypeName("SIT_IN")
public final class SitIn extends ClientMessage {

    public SitIn() {
    }
}
