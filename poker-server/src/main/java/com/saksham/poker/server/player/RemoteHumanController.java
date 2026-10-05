package com.saksham.poker.server.player;

import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.server.ws.ConnectionRegistry;

/**
 * A person playing from the desktop app. Everything is forwarded to whichever connection that user
 * has open right now, so the controller keeps working when the player reconnects.
 */
public final class RemoteHumanController extends SeatController {

    private final ConnectionRegistry connections;

    public RemoteHumanController(long userId, ConnectionRegistry connections) {
        super(userId);
        this.connections = connections;
    }

    @Override
    public void onActionRequested(ActionRequest request) {
        connections.send(userId, request.toMessage());
    }

    @Override
    public void onEvent(ServerMessage event) {
        connections.send(userId, event);
    }

    @Override
    public boolean isConnected() {
        return connections.isConnected(userId);
    }
}
