package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.dto.BotLevel;

/** Host only: seat a computer player in the first free seat. */
@JsonTypeName("ADD_BOT")
public final class AddBot extends ClientMessage {

    private final BotLevel level;

    @JsonCreator
    public AddBot(@JsonProperty("level") BotLevel level) {
        this.level = level;
    }

    /** How well the bot should play; null if the sender named a level this server does not know. */
    public BotLevel level() {
        return level;
    }
}
