package com.saksham.poker.server.player;

import com.saksham.poker.common.protocol.ServerMessage;

/**
 * Whoever sits in a seat, as a room sees them. A room never knows whether it is talking to a person
 * over a network connection or, later, to a computer player: it only tells the controller what
 * happened and when it is that seat's turn.
 *
 * <p>Methods are called on the room's own thread and must return quickly without blocking.
 */
public abstract class SeatController {

    protected final long userId;

    protected SeatController(long userId) {
        this.userId = userId;
    }

    public long userId() {
        return userId;
    }

    /**
     * It is this player's turn. The controller must eventually answer by submitting a player-action
     * command to the room; if it does not, the room's turn timer acts for it.
     */
    public abstract void onActionRequested(ActionRequest request);

    /**
     * Something happened that this player is allowed to see. Messages are already filtered: another
     * player's hole cards never arrive here.
     */
    public abstract void onEvent(ServerMessage event);

    /** Whether the player can currently be reached. */
    public abstract boolean isConnected();
}
