package com.saksham.poker.server.room;

import com.saksham.poker.common.exception.PokerException;

/**
 * One thing to do to a room. Every change to a room, whether from a player, a timer or a lost
 * connection, is a command put in the room's queue and carried out by the room's own thread, one at
 * a time. The concrete commands are in {@link RoomCommands}.
 */
public abstract class RoomCommand {

    /** Carries the command out. Runs on the room's thread. */
    public abstract void execute(Room room) throws PokerException;

    /**
     * Called when {@link #execute} refuses the command. The default tells the user who sent it;
     * commands from timers have no user and say nothing.
     */
    public void failed(Room room, PokerException reason) {
        long user = userId();
        if (user >= 0) {
            room.sendError(user, reason);
        }
    }

    /** The user who asked for this, or -1 for a command the server made itself. */
    public long userId() {
        return -1;
    }
}
