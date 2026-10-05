package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.RoomState;

/** The game started, paused, resumed or the room closed. */
@JsonTypeName("GAME_STATE")
public final class GameState extends ServerMessage {

    private final RoomState state;

    @JsonCreator
    public GameState(@JsonProperty("state") RoomState state) {
        this.state = state;
    }

    /** The state the room is in now. */
    public RoomState state() {
        return state;
    }
}
