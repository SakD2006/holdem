package com.saksham.poker.server.web.view;

import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.db.RoomRecord;
import java.util.Date;

/**
 * A room as the web pages describe it.
 *
 * @param code the code players join with
 * @param name the room's name, as its host typed it
 * @param status "Waiting to start", "Playing", "Paused" or "Finished"
 * @param open false once the room has closed
 * @param smallBlind the small blind
 * @param bigBlind the big blind
 * @param startingStack chips each player starts with
 * @param maxPlayers seats at the table
 * @param created when the room was created
 */
public record RoomLine(String code, String name, String status, boolean open, long smallBlind, long bigBlind,
        long startingStack, int maxPlayers, Date created) {

    /**
     * @param room what the database holds
     * @param state the room's state right now if it is still running, or null if it is not
     */
    public static RoomLine of(RoomRecord room, RoomState state) {
        RoomState now = state == null ? RoomState.CLOSED : state;
        String status = switch (now) {
            case WAITING -> "Waiting to start";
            case PLAYING -> "Playing";
            case PAUSED -> "Paused";
            case CLOSED -> "Finished";
        };
        return new RoomLine(room.code(), room.settings().name(), status, now != RoomState.CLOSED,
                room.settings().smallBlind(), room.settings().bigBlind(), room.settings().startingStack(),
                room.settings().maxPlayers(), Date.from(room.createdAt()));
    }
}
