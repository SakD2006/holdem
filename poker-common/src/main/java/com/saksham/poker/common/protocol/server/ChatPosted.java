package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** Someone said something in the room. */
@JsonTypeName("CHAT")
public final class ChatPosted extends ServerMessage {

    private final long userId;
    private final String username;
    private final String text;

    @JsonCreator
    public ChatPosted(
            @JsonProperty("userId") long userId,
            @JsonProperty("username") String username,
            @JsonProperty("text") String text) {
        this.userId = userId;
        this.username = username;
        this.text = text;
    }

    public long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String text() {
        return text;
    }
}
