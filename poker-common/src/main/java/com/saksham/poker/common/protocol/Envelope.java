package com.saksham.poker.common.protocol;

/**
 * A decoded message with its sequence number.
 *
 * @param seq the sender's counter for this connection. The server's goes up by one per message, so a
 *     client that sees a gap knows it missed something and asks for a snapshot.
 * @param message the message itself
 */
public record Envelope<M extends Message>(long seq, M message) {
}
