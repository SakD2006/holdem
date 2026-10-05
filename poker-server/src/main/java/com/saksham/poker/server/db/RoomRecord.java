package com.saksham.poker.server.db;

import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.room.RoomSettings;
import java.time.Instant;

/**
 * A row of the {@code rooms} table.
 *
 * @param id the room's id, which hands refer to
 * @param code the 6-character code players join with
 * @param hostUserId who created the room
 * @param settings the settings chosen at creation
 * @param state where the room is in its life
 * @param createdAt when it was created
 * @param closedAt when it closed, or null while it is open
 */
public record RoomRecord(long id, String code, long hostUserId, RoomSettings settings, RoomState state,
        Instant createdAt, Instant closedAt) {
}
