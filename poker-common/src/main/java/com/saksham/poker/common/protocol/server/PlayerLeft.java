package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.LeaveReason;

/** Someone is no longer in the room. */
@JsonTypeName("PLAYER_LEFT")
public final class PlayerLeft extends ServerMessage {

    private final long userId;
    private final LeaveReason reason;

    @JsonCreator
    public PlayerLeft(@JsonProperty("userId") long userId, @JsonProperty("reason") LeaveReason reason) {
        this.userId = userId;
        this.reason = reason;
    }

    public long userId() {
        return userId;
    }

    public LeaveReason reason() {
        return reason;
    }
}
