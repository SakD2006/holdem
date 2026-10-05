package com.saksham.poker.server.room;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one thread that owns a room. Any thread may {@link #submit} a command; the actor's thread
 * takes them from the queue and runs them one at a time, so the room itself needs no locks.
 *
 * <p>The actor is also the room's {@link RoomScheduler}: a delayed command waits on the shared
 * timer threads and is then queued like any other.
 */
public final class RoomActor implements RoomScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomActor.class);

    private final String code;
    private final LinkedBlockingQueue<RoomCommand> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService timers;
    private final ExecutorService executor;
    private volatile Room room;
    private volatile boolean running = true;

    /**
     * @param code the room's code, used to name the thread
     * @param timers the shared timer threads
     */
    public RoomActor(String code, ScheduledExecutorService timers) {
        this.code = code;
        this.timers = timers;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "room-" + code);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts running commands for the room. */
    public void start(Room toRun) {
        this.room = toRun;
        executor.execute(this::loop);
    }

    private void loop() {
        while (running) {
            try {
                room.run(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // Room.run already guards each command; this is the last line of defence.
                log.error("Room {}: unexpected failure in the room thread", code, e);
            }
        }
    }

    /**
     * Queues a command.
     *
     * @return false if the actor has stopped, in which case the command was dropped
     */
    public boolean submit(RoomCommand command) {
        if (!running) {
            return false;
        }
        queue.add(command);
        return true;
    }

    @Override
    public Cancellable schedule(RoomCommand command, long delayMs) {
        if (!running) {
            return () -> { };
        }
        ScheduledFuture<?> future = timers.schedule(() -> submit(command), Math.max(0, delayMs),
                TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    public Room room() {
        return room;
    }

    /** Commands waiting to run. */
    public int queued() {
        return queue.size();
    }

    /** Stops the thread. Commands still queued are dropped. */
    public void stop() {
        running = false;
        executor.shutdownNow();
    }
}
