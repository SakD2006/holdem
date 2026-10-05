package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PlayerInfo;

/** A player's seat, stack or status changed: sat down, sat out, came back, rebought, lost or regained their connection. */
@JsonTypeName("SEAT_UPDATE")
public final class SeatUpdate extends ServerMessage {

    private final PlayerInfo player;

    @JsonCreator
    public SeatUpdate(@JsonProperty("player") PlayerInfo player) {
        this.player = player;
    }

    /** The player as they are now. */
    public PlayerInfo player() {
        return player;
    }
}
