package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.MonteCarloStrategy;
import com.saksham.poker.ai.RuleBasedStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the arena from a terminal.
 *
 * <pre>
 * ArenaMain [--hands 20000] [--bots easy,easy,solid,simulation,simulation,caller] [--seed 1] [--deals 600]
 * </pre>
 */
public final class ArenaMain {

    private ArenaMain() {
    }

    public static void main(String[] args) {
        int hands = 20_000;
        long seed = 1;
        int deals = 600;
        String line = "easy,easy,solid,solid,simulation,simulation";
        try {
            for (int i = 0; i + 1 < args.length; i += 2) {
                switch (args[i]) {
                    case "--hands" -> hands = Integer.parseInt(args[i + 1]);
                    case "--seed" -> seed = Long.parseLong(args[i + 1]);
                    case "--deals" -> deals = Integer.parseInt(args[i + 1]);
                    case "--bots" -> line = args[i + 1];
                    default -> throw new IllegalArgumentException("Unknown option " + args[i] + ".");
                }
            }
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Option " + args[args.length - 1] + " needs a value.");
            }
            List<Arena.Entrant> entrants = new ArrayList<>();
            for (String kind : line.split(",")) {
                entrants.add(new Arena.Entrant(kind.trim() + "-" + (entrants.size() + 1), strategy(kind.trim(), deals)));
            }
            int threads = Runtime.getRuntime().availableProcessors();
            long started = System.nanoTime();
            ArenaResult result = new Arena(entrants).play(hands, seed, threads);
            System.out.print(result.table());
            System.out.printf("%.1f seconds on %d threads%n", (System.nanoTime() - started) / 1e9, threads);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: ArenaMain [--hands N] [--bots easy,solid,simulation,caller,...] [--seed N] "
                    + "[--deals N]");
            System.exit(2);
        }
    }

    private static BotStrategy strategy(String kind, int deals) {
        return switch (kind) {
            case "easy" -> new RuleBasedStrategy(RuleBasedStrategy.Style.EASY);
            case "solid" -> new RuleBasedStrategy(RuleBasedStrategy.Style.SOLID);
            case "simulation" -> new MonteCarloStrategy(deals);
            case "caller" -> new AlwaysCall();
            default -> throw new IllegalArgumentException("There is no bot called \"" + kind
                    + "\". Choose from easy, solid, simulation, caller.");
        };
    }
}
