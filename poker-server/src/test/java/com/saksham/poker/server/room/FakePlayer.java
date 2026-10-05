package com.saksham.poker.server.room;

import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.server.player.ActionRequest;
import com.saksham.poker.server.player.SeatController;
import java.util.ArrayList;
import java.util.List;

/** A player with no network: it records everything the room tells it. */
final class FakePlayer extends SeatController {

    final String username;
    final List<ServerMessage> received = new ArrayList<>();
    /** The turn this player has been asked to take and has not yet answered; null otherwise. */
    ActionRequest pendingRequest;
    /** The seat this player took, so it can tell when its own action has gone through. */
    int seat = -1;

    FakePlayer(long userId, String username) {
        super(userId);
        this.username = username;
    }

    @Override
    public void onActionRequested(ActionRequest request) {
        received.add(request.toMessage());
        pendingRequest = request;
    }

    @Override
    public void onEvent(ServerMessage event) {
        received.add(event);
        if (event instanceof HandEnded || (event instanceof PlayerActed acted && acted.seat() == seat)) {
            pendingRequest = null;
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    /** Every message of one kind received so far, in order. */
    <T extends ServerMessage> List<T> all(Class<T> type) {
        List<T> matching = new ArrayList<>();
        for (ServerMessage message : received) {
            if (type.isInstance(message)) {
                matching.add(type.cast(message));
            }
        }
        return matching;
    }

    /** The most recent message of one kind. */
    <T extends ServerMessage> T last(Class<T> type) {
        List<T> matching = all(type);
        if (matching.isEmpty()) {
            throw new AssertionError(username + " has received no " + type.getSimpleName() + ": " + received);
        }
        return matching.get(matching.size() - 1);
    }

    /** The code of the most recent error, or null if there has been none. */
    ErrorCode lastError() {
        List<ErrorMessage> errors = all(ErrorMessage.class);
        return errors.isEmpty() ? null : errors.get(errors.size() - 1).code();
    }

    void forget() {
        received.clear();
    }
}
