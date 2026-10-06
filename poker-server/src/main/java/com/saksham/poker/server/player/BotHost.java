package com.saksham.poker.server.player;

import com.saksham.poker.server.room.RoomCommand;

/**
 * How a computer player sends its commands to the room it sits in: the same commands a person's app
 * causes, through the same queue.
 */
public interface BotHost {

    /** Queues a command for the room this user is in. Does nothing if the user is in no room. */
    void submit(long userId, RoomCommand command);
}
