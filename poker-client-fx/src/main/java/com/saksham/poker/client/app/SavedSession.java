package com.saksham.poker.client.app;

import java.io.Serializable;

/**
 * A remembered login, so the player need not type their password every time. It holds the login
 * token, never the password.
 *
 * @param server the server address the login is for
 * @param userId the account's id
 * @param username the account's name
 * @param token the login token; the server forgets it after 7 days or on logging out
 */
public record SavedSession(String server, long userId, String username, String token) implements Serializable {

    private static final long serialVersionUID = 1L;
}
