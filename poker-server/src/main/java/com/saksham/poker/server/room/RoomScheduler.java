package com.saksham.poker.server.room;

/**
 * Runs a command on the room's thread after a delay. Timers never touch a room directly: they only
 * put a command in its queue, so the room's state is still changed by one thread alone.
 */
public interface RoomScheduler {

    /** A scheduled command that can be called off. */
    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }

    /**
     * Schedules a command.
     *
     * @param delayMs how long to wait, in milliseconds
     * @return a handle to cancel it; cancelling a command that has already run does nothing
     */
    Cancellable schedule(RoomCommand command, long delayMs);
}
