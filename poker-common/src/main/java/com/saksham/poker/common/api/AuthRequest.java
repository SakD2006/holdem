package com.saksham.poker.common.api;

/**
 * The body of {@code POST /api/auth/register} and {@code POST /api/auth/login}.
 *
 * @param username 3 to 24 letters, digits or underscores
 * @param password 6 to 72 characters
 */
public record AuthRequest(String username, String password) {
}
