package com.saksham.poker.client.net;

/**
 * Where the Hold'em server is.
 *
 * @param host the host machine's name or IP address
 * @param port the server's port
 */
public record ServerAddress(String host, int port) {

    public static final int DEFAULT_PORT = 8080;

    /**
     * Reads what a player typed: {@code 192.168.1.20}, {@code 192.168.1.20:9090}, or a whole address
     * copied from the server's log such as {@code http://192.168.1.20:8080/poker}.
     *
     * @throws IllegalArgumentException saying what is wrong, in words fit to show the player
     */
    public static ServerAddress parse(String typed) {
        String text = typed == null ? "" : typed.trim();
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            text = text.substring(scheme + 3);
        }
        int path = text.indexOf('/');
        if (path >= 0) {
            text = text.substring(0, path);
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Type the host computer's address, such as 192.168.1.20.");
        }
        int colon = text.lastIndexOf(':');
        if (colon < 0) {
            return new ServerAddress(requireHost(text), DEFAULT_PORT);
        }
        String portText = text.substring(colon + 1);
        try {
            int port = Integer.parseInt(portText);
            if (port >= 1 && port <= 65_535) {
                return new ServerAddress(requireHost(text.substring(0, colon)), port);
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException(
                "The port after the colon must be a number from 1 to 65535, but was \"" + portText + "\".");
    }

    private static String requireHost(String host) {
        if (host.isEmpty() || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "\"" + host + "\" is not an address. Use something like 192.168.1.20, with no spaces.");
        }
        return host;
    }

    /** The address as a player would type it; the port is left off when it is the usual one. */
    public String display() {
        return port == DEFAULT_PORT ? host : host + ":" + port;
    }

    public String apiBase() {
        return "http://" + host + ":" + port + "/poker/api";
    }

    public String gameSocketUrl(String token) {
        return "ws://" + host + ":" + port + "/poker/ws/game?token=" + token;
    }
}
