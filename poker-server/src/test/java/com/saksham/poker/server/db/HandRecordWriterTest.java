package com.saksham.poker.server.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.server.io.HandHistoryFileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The background writer, with the database replaced by a list or by something that fails. */
class HandRecordWriterTest {

    @TempDir
    Path data;

    private final List<HandRecord> stored = new CopyOnWriteArrayList<>();

    private HandRecordWriter writer(HandRecordWriter.HandStore store) {
        return new HandRecordWriter(store, new HandHistoryFileWriter(data.resolve("hand-history"), ZoneOffset.UTC),
                data.resolve("failed-hands"), 3, 0);
    }

    private List<Path> failedFiles() throws Exception {
        Path folder = data.resolve("failed-hands");
        if (!Files.exists(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.sorted().toList();
        }
    }

    @Test
    void submittedHandsAreSavedInOrderAndAddedToTheHistoryFile() throws Exception {
        HandRecordWriter writer = writer(stored::add);
        for (int handNo = 1; handNo <= 50; handNo++) {
            writer.submit(Hands.foldedPreflop("ABC234", handNo, 1, 2));
        }
        writer.close();

        assertThat(stored).hasSize(50);
        assertThat(stored).extracting(HandRecord::handNo).isSorted();
        assertThat(writer.saved()).isEqualTo(50);
        assertThat(writer.failed()).isZero();
        assertThat(writer.waiting()).isZero();
        assertThat(failedFiles()).isEmpty();
        String history = Files.readString(data.resolve("hand-history/room-ABC234/2026-10-05.txt"));
        assertThat(history).contains("Hold'em hand #1 ").contains("Hold'em hand #50 ");
    }

    @Test
    void submittingReturnsAtOnceEvenWhileTheDatabaseIsSlow() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HandRecordWriter writer = writer(hand -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stored.add(hand);
        });

        long before = System.nanoTime();
        for (int handNo = 1; handNo <= 20; handNo++) {
            writer.submit(Hands.foldedPreflop("ABC234", handNo, 1, 2));
        }
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        // The producer was never held up, though nothing has been saved yet.
        assertThat(tookMs).isLessThan(1_000);
        assertThat(stored).isEmpty();

        release.countDown();
        writer.close();
        assertThat(stored).hasSize(20);
    }

    @Test
    void aHandTheDatabaseRefusesIsRetriedThenWrittenToAFile() throws Exception {
        AtomicInteger tries = new AtomicInteger();
        HandRecordWriter writer = writer(hand -> {
            tries.incrementAndGet();
            throw new PersistenceException("the database is down");
        });

        writer.submit(Hands.showdown("ABC234", 12, 1, 2, 3));
        writer.close();

        assertThat(tries.get()).isEqualTo(3);
        assertThat(writer.saved()).isZero();
        assertThat(writer.failed()).isEqualTo(1);
        List<Path> files = failedFiles();
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString()).startsWith("room-ABC234-hand-000012-").endsWith(".json");
        // The file has everything needed to load the hand later, including the private cards.
        String json = Files.readString(files.get(0));
        assertThat(json).contains("\"roomCode\" : \"ABC234\"").contains("\"handNo\" : 12")
                .contains("\"startedAt\" : \"2026-10-05T10:00:00Z\"").contains("\"username\" : \"meera\"")
                .contains("\"holeCards\" : [ \"7c\", \"2d\" ]").contains("\"action\" : \"POST_SB\"");
        // The readable history does not depend on the database.
        assertThat(Files.exists(data.resolve("hand-history/room-ABC234/2026-10-05.txt"))).isTrue();
    }

    @Test
    void aSaveThatFailsOnceSucceedsOnTheRetry() throws Exception {
        AtomicInteger tries = new AtomicInteger();
        HandRecordWriter writer = writer(hand -> {
            if (tries.incrementAndGet() == 1) {
                throw new PersistenceException("a hiccup");
            }
            stored.add(hand);
        });

        writer.submit(Hands.foldedPreflop("ABC234", 1, 1, 2));
        writer.close();

        assertThat(tries.get()).isEqualTo(2);
        assertThat(stored).hasSize(1);
        assertThat(writer.failed()).isZero();
        assertThat(failedFiles()).isEmpty();
    }

    @Test
    void oneFailedHandDoesNotStopTheNextFromBeingSaved() throws Exception {
        HandRecordWriter writer = writer(hand -> {
            if (hand.handNo() == 2) {
                throw new PersistenceException("only hand 2 fails");
            }
            stored.add(hand);
        });

        for (int handNo = 1; handNo <= 3; handNo++) {
            writer.submit(Hands.foldedPreflop("ABC234", handNo, 1, 2));
        }
        writer.close();

        assertThat(stored).extracting(HandRecord::handNo).containsExactly(1L, 3L);
        assertThat(failedFiles()).hasSize(1);
        assertThat(failedFiles().get(0).getFileName().toString()).contains("hand-000002-");
    }

    @Test
    void aHistoryFileThatCannotBeWrittenDoesNotStopTheHandBeingSaved() throws Exception {
        Files.writeString(data.resolve("hand-history"), "a file where the folder should be");
        HandRecordWriter writer = writer(stored::add);

        writer.submit(Hands.foldedPreflop("ABC234", 1, 1, 2));
        writer.close();

        assertThat(stored).hasSize(1);
    }

    @Test
    void closingSavesWhatIsWaitingAndRefusesAnythingLater() throws Exception {
        HandRecordWriter writer = writer(stored::add);
        for (int handNo = 1; handNo <= 200; handNo++) {
            writer.submit(Hands.foldedPreflop("ABC234", handNo, 1, 2));
        }

        writer.close();
        writer.submit(Hands.foldedPreflop("ABC234", 201, 1, 2));

        assertThat(stored).hasSize(200);
        assertThat(writer.waiting()).isLessThanOrEqualTo(1);
    }
}
