package com.saksham.poker.server.ws;

import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.exception.ProtocolException;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.Kick;
import com.saksham.poker.common.protocol.client.LeaveRoom;
import com.saksham.poker.common.protocol.client.PauseGame;
import com.saksham.poker.common.protocol.client.Ping;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.RequestSnapshot;
import com.saksham.poker.common.protocol.client.ResumeGame;
import com.saksham.poker.common.protocol.client.SendChat;
import com.saksham.poker.common.protocol.client.SitIn;
import com.saksham.poker.common.protocol.client.SitOut;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.Pong;
import com.saksham.poker.server.bootstrap.AppContext;
import com.saksham.poker.server.db.User;
import com.saksham.poker.server.player.RemoteHumanController;
import com.saksham.poker.server.room.RoomCommand;
import com.saksham.poker.server.room.RoomCommands;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The game WebSocket, {@code /ws/game?token=<token>}. There is one instance per connection. It owns
 * nothing shared: each message is decoded and turned into a command for the player's room.
 */
@ServerEndpoint(value = "/ws/game", configurator = AuthHandshakeConfigurator.class)
public class GameEndpoint {

    private static final Logger log = LoggerFactory.getLogger(GameEndpoint.class);

    /** A connection that sends nothing for this long is taken to be dead. Clients ping to stay alive. */
    private static final long IDLE_TIMEOUT_MS = 60_000;
    private static final int MAX_MESSAGE_BYTES = 8 * 1024;

    private AppContext app;
    private User user;
    private Connection connection;
    private RemoteHumanController controller;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        user = (User) config.getUserProperties().get(AuthHandshakeConfigurator.USER_PROPERTY);
        if (user == null) {
            Connection.closeQuietly(session, Connection.CLOSE_UNAUTHORIZED, "Log in again");
            return;
        }
        app = AppContext.current();
        session.setMaxIdleTimeout(IDLE_TIMEOUT_MS);
        session.setMaxTextMessageBufferSize(MAX_MESSAGE_BYTES);
        connection = new Connection(user.id(), session, app.codec());
        controller = new RemoteHumanController(user.id(), app.connections());
        app.connections().register(connection);
    }

    @OnMessage
    public void onMessage(String text) {
        if (connection == null) {
            return;
        }
        try {
            handle(app.codec().decodeClient(text).message());
        } catch (PokerException e) {
            connection.send(new ErrorMessage(e.code(), e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Could not handle a message from user {}", user.id(), e);
        }
    }

    private void handle(ClientMessage message) throws PokerException {
        long id = user.id();
        if (message instanceof Ping) {
            connection.send(new Pong());
        } else if (message instanceof JoinRoom join) {
            app.roomManager().join(id, user.username(), join.code(), controller);
        } else {
            app.roomManager().submit(id, commandFor(id, message));
        }
    }

    private static RoomCommand commandFor(long id, ClientMessage message) throws ProtocolException {
        if (message instanceof TakeSeat m) {
            return new RoomCommands.TakeSeat(id, m.seat());
        } else if (message instanceof LeaveRoom) {
            return new RoomCommands.LeaveRoom(id);
        } else if (message instanceof StartGame) {
            return new RoomCommands.StartGame(id);
        } else if (message instanceof PauseGame) {
            return new RoomCommands.PauseGame(id);
        } else if (message instanceof ResumeGame) {
            return new RoomCommands.ResumeGame(id);
        } else if (message instanceof Kick m) {
            return new RoomCommands.KickPlayer(id, m.userId());
        } else if (message instanceof EndRoom) {
            return new RoomCommands.EndRoom(id);
        } else if (message instanceof SubmitAction m) {
            return new RoomCommands.PlayerActionCmd(id, m.turnId(), m.action(), m.amount());
        } else if (message instanceof SitOut) {
            return new RoomCommands.SitOut(id);
        } else if (message instanceof SitIn) {
            return new RoomCommands.SitIn(id);
        } else if (message instanceof Rebuy) {
            return new RoomCommands.Rebuy(id);
        } else if (message instanceof SendChat m) {
            return new RoomCommands.Chat(id, m.text());
        } else if (message instanceof RequestSnapshot) {
            return new RoomCommands.SendSnapshot(id);
        }
        throw new ProtocolException("The server does not handle " + message.type() + " messages.");
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        if (connection == null) {
            return;
        }
        connection.close(CloseReason.CloseCodes.NORMAL_CLOSURE.getCode(), "Closed");
        // If a newer connection has already replaced this one, the player has not really gone.
        if (app.connections().unregister(connection)) {
            app.roomManager().disconnected(user.id());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // A dropped connection arrives here and then at onClose, which does the tidying up.
        log.debug("Connection error for user {}", user == null ? "?" : user.id(), error);
    }
}
