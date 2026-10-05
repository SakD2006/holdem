package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Ask for the full room state again, after a gap in sequence numbers. */
@JsonTypeName("REQUEST_SNAPSHOT")
public final class RequestSnapshot extends ClientMessage {

    public RequestSnapshot() {
    }
}
