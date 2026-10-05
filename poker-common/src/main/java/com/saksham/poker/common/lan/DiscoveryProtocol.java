package com.saksham.poker.common.lan;

import java.util.Optional;

/**
 * How "Find server" works (SPEC §4.6). A desktop app broadcasts {@value #REQUEST} over UDP to
 * everyone on the local network; each server that hears it answers
 * {@code HOLDEM_SERVER <ip> <port>}, the address the app should then connect to.
 */
public final class DiscoveryProtocol {

    /** The UDP port servers listen on unless told otherwise. */
    public static final int DEFAULT_PORT = 8888;
    /** What an app sends to ask "is there a server here?". */
    public static final String REQUEST = "HOLDEM_DISCOVER";

    private static final String REPLY_WORD = "HOLDEM_SERVER";

    private DiscoveryProtocol() {
    }

    /**
     * A server's answer.
     *
     * @param ip the server's address on the local network
     * @param port the port its game runs on
     */
    public record Reply(String ip, int port) {
    }

    /** The text of a server's answer. */
    public static String reply(String ip, int port) {
        return REPLY_WORD + " " + ip + " " + port;
    }

    /** True if the text is an app asking for servers. */
    public static boolean isRequest(String text) {
        return text != null && REQUEST.equals(text.trim());
    }

    /**
     * Reads a server's answer. Anything else on the network that happens to arrive on the same port
     * is not an answer, and gives empty.
     */
    public static Optional<Reply> parseReply(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String[] words = text.trim().split(" ");
        if (words.length != 3 || !REPLY_WORD.equals(words[0]) || words[1].isEmpty()) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(words[2]);
            if (port >= 1 && port <= 65_535) {
                return Optional.of(new Reply(words[1], port));
            }
        } catch (NumberFormatException e) {
            // not an answer
        }
        return Optional.empty();
    }
}
