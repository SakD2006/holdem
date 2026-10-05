package com.saksham.poker.common.api;

import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.RoomState;

/**
 * What a player sees about a room before joining it: the answer to {@code GET /api/rooms/{code}}.
 *
 * @param code the room code
 * @param settings the room's settings
 * @param state whether it is waiting, playing, paused or closed
 * @param hostUsername who is hosting
 * @param seatedPlayers how many seats are taken
 */
public record RoomPreview(
        String code, RoomSettingsInfo settings, RoomState state, String hostUsername, int seatedPlayers) {
}
