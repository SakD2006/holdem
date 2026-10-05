package com.saksham.poker.common.api;

/**
 * The answer to {@code POST /api/rooms}.
 *
 * @param code the 6-character code other players type to join
 */
public record CreateRoomResponse(String code) {
}
