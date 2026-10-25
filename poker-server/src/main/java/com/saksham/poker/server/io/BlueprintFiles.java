package com.saksham.poker.server.io;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Strategies;
import com.saksham.poker.ai.blueprint.AbstractionData;
import com.saksham.poker.ai.blueprint.BlueprintTable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the trainer's files for Expert bots: the card abstraction and the trained strategy named in
 * {@code server.properties}. Without them, or if they cannot be read, the server runs without the
 * Expert level and says why in the log.
 */
public final class BlueprintFiles {

    private static final Logger log = LoggerFactory.getLogger(BlueprintFiles.class);

    private BlueprintFiles() {
    }

    /** The Expert strategy, shared by every Expert bot, or empty if the server has no files for it. */
    public static Optional<BotStrategy> load(ServerConfig config) {
        if (config.blueprintAbstraction().isEmpty() || config.blueprintStrategy().isEmpty()) {
            log.info("No Expert bots: set blueprint.abstraction and blueprint.strategy to offer them");
            return Optional.empty();
        }
        Path abstraction = config.blueprintAbstraction().get();
        Path strategy = config.blueprintStrategy().get();
        long started = System.nanoTime();
        try {
            AbstractionData buckets = AbstractionData.read(map(abstraction));
            BlueprintTable table = BlueprintTable.read(map(strategy));
            log.info("Expert bots ready: {} information sets from {}, loaded in {} ms", table.size(), strategy,
                    (System.nanoTime() - started) / 1_000_000);
            return Optional.of(Strategies.expert(buckets, table, config.blueprintResolveIterations()));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            log.warn("No Expert bots: could not read {} and {}: {}", abstraction.toAbsolutePath(),
                    strategy.toAbsolutePath(), e.getMessage());
            return Optional.empty();
        }
    }

    /** A whole file mapped into memory, rather than copied onto the heap. */
    private static ByteBuffer map(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }
}
