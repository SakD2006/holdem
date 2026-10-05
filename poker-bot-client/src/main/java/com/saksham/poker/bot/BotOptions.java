package com.saksham.poker.bot;

/**
 * What the bots were asked to do on the command line.
 *
 * @param server the host machine's address
 * @param port the server's port
 * @param room the code of a room to join, or null to create a new room and start the game in it
 * @param bots how many bots to run, 1 to 9
 * @param hands stop after this many hands; 0 to play until the room closes
 * @param idleSeconds give up if no hand finishes for this long
 * @param thinkMinMs the shortest time a bot waits before acting, in milliseconds
 * @param thinkMaxMs the longest time a bot waits before acting; 0 for both means act at once
 */
record BotOptions(String server, int port, String room, int bots, int hands, int idleSeconds, int thinkMinMs,
        int thinkMaxMs) {

    /** How long bots think by default: long enough to follow, short enough not to drag. */
    static final int DEFAULT_THINK_MIN_MS = 800;
    static final int DEFAULT_THINK_MAX_MS = 2_500;

    static final String USAGE = """
            Usage: --server <address> [--port 8080] [--room <code>] [--bots 3] [--hands 100] [--idle 60]
                   [--think 800-2500]

              --server  the host machine's address, such as 127.0.0.1 or 192.168.1.20
              --port    the server's port (default 8080)
              --room    the code of a room to join. Leave it out and the bots create their own room
                        and start the game themselves.
              --bots    how many bots to run, 1 to 9 (default 3)
              --hands   stop after this many hands; 0 plays until the room closes (default 100)
              --idle    give up if no hand finishes for this many seconds (default 60)
              --think   how long a bot waits before acting, in milliseconds, as min-max: a random
                        time in that range each turn, a little longer before a bet or raise
                        (default 800-2500). Use 0 for bots that act at once, as a soak run needs.
            """;

    /** Reads the options, or throws {@link IllegalArgumentException} saying which one is wrong. */
    static BotOptions parse(String[] args) {
        String server = null;
        String room = null;
        int port = 8080;
        int bots = 3;
        int hands = 100;
        int idle = 60;
        int thinkMin = DEFAULT_THINK_MIN_MS;
        int thinkMax = DEFAULT_THINK_MAX_MS;
        for (int i = 0; i < args.length; i++) {
            String name = args[i];
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("The option " + name + " needs a value.");
            }
            String value = args[++i];
            switch (name) {
                case "--server" -> server = value;
                case "--room" -> room = value.trim().toUpperCase();
                case "--port" -> port = number(name, value, 1, 65_535);
                case "--bots" -> bots = number(name, value, 1, 9);
                case "--hands" -> hands = number(name, value, 0, 1_000_000);
                case "--idle" -> idle = number(name, value, 5, 3_600);
                case "--think" -> {
                    int[] range = range(name, value);
                    thinkMin = range[0];
                    thinkMax = range[1];
                }
                default -> throw new IllegalArgumentException("Unknown option " + name + ".");
            }
        }
        if (server == null || server.isBlank()) {
            throw new IllegalArgumentException("Say where the server is with --server <address>.");
        }
        if (room == null && bots < 2) {
            throw new IllegalArgumentException("A new room needs at least 2 bots. Use --bots 2 or more, or --room.");
        }
        return new BotOptions(server.trim(), port, room, bots, hands, idle, thinkMin, thinkMax);
    }

    private static int number(String name, String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) {
                return number;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException(name + " must be a number from " + min + " to " + max
                + ", but was \"" + value + "\".");
    }

    /** Reads "800-2500" as a range, or a single number as both ends. */
    private static int[] range(String name, String value) {
        String[] parts = value.split("-", -1);
        if (parts.length == 1) {
            int both = number(name, parts[0], 0, 60_000);
            return new int[] {both, both};
        }
        if (parts.length == 2) {
            int min = number(name, parts[0], 0, 60_000);
            int max = number(name, parts[1], 0, 60_000);
            if (min <= max) {
                return new int[] {min, max};
            }
        }
        throw new IllegalArgumentException(name + " must be a time in milliseconds or a range such as 800-2500, "
                + "but was \"" + value + "\".");
    }

    String httpBase() {
        return "http://" + server + ":" + port + "/poker/api";
    }

    String webSocketUrl(String token) {
        return "ws://" + server + ":" + port + "/poker/ws/game?token=" + token;
    }
}
