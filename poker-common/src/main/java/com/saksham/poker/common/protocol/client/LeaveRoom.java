package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ClientMessage;

/** Leave the room. In the middle of a hand this folds the hand. */
@JsonTypeName("LEAVE_ROOM")
public final class LeaveRoom extends ClientMessage {

    public LeaveRoom() {
    }
}
