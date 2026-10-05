package com.saksham.poker.server.room;

import com.saksham.poker.common.protocol.dto.RoomState;

/**
 * Where a room's state is recorded for good. Calls return at once: the saving happens on another
 * thread, because a room's own thread must never wait for the database.
 */
public interface RoomStore {

    void saveState(String code, RoomState state);

    /** A store that remembers nothing, for tests. */
    RoomStore NONE = (code, state) -> { };
}
