package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.AdaptiveStrategy;
import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.MonteCarloStrategy;
import com.saksham.poker.ai.RuleBasedStrategy;
import com.saksham.poker.ai.blueprint.AbstractionData;
import com.saksham.poker.ai.blueprint.BlueprintStrategy;
import com.saksham.poker.ai.blueprint.BlueprintTable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        String abstractionFile = null;
        String blueprintFile = null;
        int resolve = 200;
        try {
            for (int i = 0; i + 1 < args.length; i += 2) {
                switch (args[i]) {
                    case "--hands" -> hands = Integer.parseInt(args[i + 1]);
                    case "--seed" -> seed = Long.parseLong(args[i + 1]);
                    case "--deals" -> deals = Integer.parseInt(args[i + 1]);
                    case "--bots" -> line = args[i + 1];
                    case "--abstraction" -> abstractionFile = args[i + 1];
                    case "--blueprint" -> blueprintFile = args[i + 1];
                    case "--resolve" -> resolve = Integer.parseInt(args[i + 1]);
                    default -> throw new IllegalArgumentException("Unknown option " + args[i] + ".");
                }
            }
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Option " + args[args.length - 1] + " needs a value.");
            }
            BotStrategy blueprint = null;
            if (line.contains("blueprint")) {
                if (abstractionFile == null || blueprintFile == null) {
                    throw new IllegalArgumentException("The blueprint bot needs --abstraction FILE and --blueprint FILE "
                            + "(written by the trainer).");
                }
                long loading = System.nanoTime();
                blueprint = new BlueprintStrategy(AbstractionData.read(map(abstractionFile)),
                        BlueprintTable.read(map(blueprintFile)), new AdaptiveStrategy(deals), resolve);
                System.out.printf("blueprint loaded in %.1f seconds%n", (System.nanoTime() - loading) / 1e9);
            }
            List<Arena.Entrant> entrants = new ArrayList<>();
            for (String kind : line.split(",")) {
                BotStrategy strategy = kind.trim().equals("blueprint") ? blueprint : strategy(kind.trim(), deals);
                entrants.add(new Arena.Entrant(kind.trim() + "-" + (entrants.size() + 1), strategy));
            }
            int threads = Runtime.getRuntime().availableProcessors();
            long started = System.nanoTime();
            ArenaResult result = new Arena(entrants).play(hands, seed, threads);
            System.out.print(result.table());
            System.out.printf("%.1f seconds on %d threads%n", (System.nanoTime() - started) / 1e9, threads);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: ArenaMain [--hands N] [--bots easy,solid,simulation,adaptive,blueprint,caller,maniac,"
                    + "overfolder,chameleon] [--seed N] [--deals N] [--abstraction FILE --blueprint FILE [--resolve N]]");
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Could not read a blueprint file: " + e.getMessage());
            System.exit(2);
        }
    }

    /** A whole file, mapped into memory rather than copied. */
    private static ByteBuffer map(String path) throws IOException {
        try (FileChannel channel = FileChannel.open(Path.of(path), StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    private static BotStrategy strategy(String kind, int deals) {
        return switch (kind) {
            case "easy" -> new RuleBasedStrategy(RuleBasedStrategy.Style.EASY);
            case "solid" -> new RuleBasedStrategy(RuleBasedStrategy.Style.SOLID);
            case "simulation" -> new MonteCarloStrategy(deals);
            case "adaptive" -> new AdaptiveStrategy(deals);
            case "caller" -> new AlwaysCall();
            case "maniac" -> new Maniac();
            case "overfolder" -> new OverFolder();
            case "chameleon" -> new Chameleon();
            default -> throw new IllegalArgumentException("There is no bot called \"" + kind
                    + "\". Choose from easy, solid, simulation, adaptive, blueprint, caller, maniac, overfolder, chameleon.");
        };
    }
}
