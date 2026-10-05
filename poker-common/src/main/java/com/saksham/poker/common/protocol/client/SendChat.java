package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Say something to the room. */
@JsonTypeName("CHAT")
public final class SendChat extends ClientMessage {

    private final String text;

    @JsonCreator
    public SendChat(@JsonProperty("text") String text) {
        this.text = text;
    }

    /** Up to 200 characters. */
    public String text() {
        return text;
    }
}
