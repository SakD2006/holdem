package com.saksham.poker.client.state;

/**
 * One line of chat.
 *
 * @param userId who said it
 * @param username their name
 * @param text what they said
 */
public record ChatLine(long userId, String username, String text) {
}
