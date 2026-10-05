package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PlayerInfo;

/** Someone entered the room. */
@JsonTypeName("PLAYER_JOINED")
public final class PlayerJoined extends ServerMessage {

    private final PlayerInfo player;

    @JsonCreator
    public PlayerJoined(@JsonProperty("player") PlayerInfo player) {
        this.player = player;
    }

    public PlayerInfo player() {
        return player;
    }
}
