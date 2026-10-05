package com.saksham.poker.server.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.saksham.poker.common.exception.StorageException;
import com.saksham.poker.server.io.HandHistoryFileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saves finished hands on its own thread. Rooms are the producers: they {@link #submit} a record and
 * carry on at once. This thread is the single consumer: it takes records from the queue one at a
 * time, adds each to the readable hand history, and stores it in the database.
 *
 * <p>If the database refuses a hand, the save is tried again a few times. If it still fails, the
 * hand is written as a JSON file under {@code failed-hands/}, so a database outage never loses a
 * hand; the file holds everything needed to load it later.
 */
public final class HandRecordWriter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HandRecordWriter.class);

    /** Where finished hands are stored for good. In the server this is {@link HandDao#save}. */
    @FunctionalInterface
    public interface HandStore {
        void save(HandRecord hand);
    }

    /** A marker put in the queue to tell the thread there is nothing more to come. */
    private static final HandRecord STOP = new HandRecord("", "", 0, 0, 0, 0, List.of(), 0,
            Instant.EPOCH, Instant.EPOCH, List.of(), List.of());

    private final BlockingQueue<HandRecord> queue = new LinkedBlockingQueue<>();
    private final HandStore store;
    private final HandHistoryFileWriter history;
    private final Path failedFolder;
    private final int attempts;
    private final long retryDelayMs;
    private final Thread thread;
    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final AtomicLong saved = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile boolean accepting = true;

    /**
     * Starts the writer thread.
     *
     * @param store where hands are saved
     * @param history the readable hand history
     * @param failedFolder where a hand goes if it cannot be saved
     * @param attempts how many times to try saving a hand before giving up on the database
     * @param retryDelayMs how long to wait before each further try
     */
    public HandRecordWriter(HandStore store, HandHistoryFileWriter history, Path failedFolder, int attempts,
            long retryDelayMs) {
        this.store = store;
        this.history = history;
        this.failedFolder = failedFolder;
        this.attempts = Math.max(1, attempts);
        this.retryDelayMs = retryDelayMs;
        this.thread = new Thread(this::consume, "hand-writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Hands a finished hand over for saving. Never blocks, so a room's thread may call it. */
    public void submit(HandRecord hand) {
        if (accepting) {
            queue.add(hand);
        } else {
            log.warn("Hand {} of room {} finished after the server began shutting down and was not saved",
                    hand.handNo(), hand.roomCode());
        }
    }

    private void consume() {
        try {
            while (true) {
                HandRecord hand = queue.take();
                if (hand == STOP) {
                    return;
                }
                write(hand);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void write(HandRecord hand) throws InterruptedException {
        try {
            history.append(hand);
        } catch (RuntimeException e) {
            log.error("Could not add hand {} of room {} to the hand history file", hand.handNo(), hand.roomCode(), e);
        }

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                store.save(hand);
                saved.incrementAndGet();
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Saving hand {} of room {} failed (try {} of {}): {}", hand.handNo(), hand.roomCode(),
                        attempt, attempts, e.getMessage());
                if (attempt < attempts) {
                    Thread.sleep(retryDelayMs);
                }
            }
        }
        failed.incrementAndGet();
        try {
            Path file = writeFailed(hand);
            log.error("Hand {} of room {} could not be saved to the database after {} tries. It was written to "
                    + "{} instead.", hand.handNo(), hand.roomCode(), attempts, file.toAbsolutePath(), lastFailure);
        } catch (RuntimeException e) {
            log.error("Hand {} of room {} could not be saved to the database or to a file and is lost",
                    hand.handNo(), hand.roomCode(), e);
        }
    }

    private Path writeFailed(HandRecord hand) {
        Path file = failedFolder.resolve(String.format("room-%s-hand-%06d-%d.json", hand.roomCode(), hand.handNo(),
                hand.endedAt().toEpochMilli()));
        try {
            Files.createDirectories(failedFolder);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                json.writeValue(writer, hand);
            }
            return file;
        } catch (IOException e) {
            throw new StorageException("Could not write " + file.toAbsolutePath(), e);
        }
    }

    /** Hands saved to the database since the writer started. */
    public long saved() {
        return saved.get();
    }

    /** Hands that could not be saved to the database and went to a file instead. */
    public long failed() {
        return failed.get();
    }

    /** Hands waiting to be saved. */
    public int waiting() {
        return queue.size();
    }

    /**
     * Stops taking new hands, saves the ones already waiting, then stops the thread. Waits up to
     * thirty seconds; call it before closing the database.
     */
    @Override
    public void close() {
        accepting = false;
        queue.add(STOP);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(30));
            if (thread.isAlive()) {
                log.error("{} hand(s) were still waiting to be saved when the server stopped", queue.size());
                thread.interrupt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
