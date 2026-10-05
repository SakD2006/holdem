package com.saksham.poker.server.room;

import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.db.HandRecord;

/** What a room tells the rest of the server. Called on the room's thread; must not block. */
public interface RoomListener {

    /** A user is no longer in the room, so they are free to join another. */
    void released(long userId, String code);

    /** The room moved to a new state. */
    void stateChanged(String code, RoomState state);

    /** A hand finished. The record is complete and is never changed afterwards. */
    void handFinished(HandRecord hand);

    /** The room has closed for good and will accept no more commands. */
    void closed(String code);
}
