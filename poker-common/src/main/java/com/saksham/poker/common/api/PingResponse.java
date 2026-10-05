package com.saksham.poker.common.api;

/**
 * The answer to {@code GET /api/ping}.
 *
 * @param status always {@code "ok"}
 * @param name the server's name, so a client knows it found a Hold'em server
 * @param version the server's version
 */
public record PingResponse(String status, String name, String version) {
}
