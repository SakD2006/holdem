package com.saksham.poker.common.api;

/**
 * The answer to a successful register or login.
 *
 * @param token sent as {@code Authorization: Bearer <token>} on later requests, and in the WebSocket
 *     address; valid for 7 days
 * @param user the signed-in account
 */
public record AuthResponse(String token, UserInfo user) {
}
