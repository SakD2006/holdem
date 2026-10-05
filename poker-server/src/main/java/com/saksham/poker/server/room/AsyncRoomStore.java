package com.saksham.poker.server.room;

import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.db.RoomDao;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Saves room state changes to the database on a single background thread, in the order they happened. */
public final class AsyncRoomStore implements RoomStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncRoomStore.class);

    private final RoomDao rooms;
    private final Clock clock;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "room-store");
        thread.setDaemon(true);
        return thread;
    });

    public AsyncRoomStore(RoomDao rooms, Clock clock) {
        this.rooms = rooms;
        this.clock = clock;
    }

    @Override
    public void saveState(String code, RoomState state) {
        writer.execute(() -> {
            try {
                rooms.updateState(code, state, clock.instant());
            } catch (RuntimeException e) {
                // The game goes on; only the record of the room's state is behind.
                log.error("Could not save that room {} is now {}", code, state, e);
            }
        });
    }

    /** Finishes the saves already queued, waiting up to five seconds. */
    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Some room state changes were not saved before shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
