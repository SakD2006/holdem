package com.saksham.poker.server.auth;

import com.saksham.poker.server.db.User;

/**
 * A fresh login.
 *
 * @param token the secret the client sends with later requests; shown to the client once and stored
 *     only as a hash
 * @param user the account that logged in
 */
public record Session(String token, User user) {
}
