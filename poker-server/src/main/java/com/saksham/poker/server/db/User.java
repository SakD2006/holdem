package com.saksham.poker.server.db;

import java.time.Instant;

/**
 * A row of the {@code users} table.
 *
 * @param id the account's id
 * @param username the name the player chose, as they typed it
 * @param passwordHash the password as stored by {@code PasswordHasher}; never the password itself
 * @param createdAt when the account was registered
 * @param lastLoginAt when the player last logged in, or null if never
 */
public record User(long id, String username, String passwordHash, Instant createdAt, Instant lastLoginAt) {
}
