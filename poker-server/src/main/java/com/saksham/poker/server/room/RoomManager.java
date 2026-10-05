package com.saksham.poker.server.room;

import com.saksham.poker.common.exception.AlreadyInRoomException;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.engine.card.DeckFactory;
import com.saksham.poker.server.db.HandRecord;
import com.saksham.poker.server.player.SeatController;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every open room, and which room each user is in. Connection threads come here to hand a command
 * to the right room; they never touch a room themselves.
 *
 * <p>Thread-safe.
 */
public final class RoomManager implements RoomListener {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    private final ConcurrentHashMap<String, RoomActor> rooms = new ConcurrentHashMap<>();
    /** The room each user is in. A user can be in only one. */
    private final ConcurrentHashMap<Long, String> roomOfUser = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timers;
    private final RoomStore store;
    private final RoomTimings timings;
    private final DeckFactory decks;
    private final Clock clock;
    private final Consumer<HandRecord> handSink;

    /**
     * @param timers the shared timer threads
     * @param store where room state changes are recorded
     * @param handSink where finished hands are handed over for saving; must not block
     * @param timings the waits rooms use
     * @param decks the decks rooms deal from
     * @param clock the time
     */
    public RoomManager(ScheduledExecutorService timers, RoomStore store, Consumer<HandRecord> handSink,
            RoomTimings timings, DeckFactory decks, Clock clock) {
        this.timers = timers;
        this.store = store;
        this.handSink = handSink;
        this.timings = timings;
        this.decks = decks;
        this.clock = clock;
    }

    /** Opens a newly created room so players can join it. */
    public void open(String code, long hostUserId, RoomSettings settings) {
        RoomActor actor = new RoomActor(code, timers);
        Room room = new Room(code, hostUserId, settings, timings, decks, clock, actor, this);
        rooms.put(code, actor);
        actor.start(room);
        room.opened();
        log.info("Room {} opened: \"{}\", {} seats, blinds {}/{}", code, settings.name(), settings.maxPlayers(),
                settings.smallBlind(), settings.bigBlind());
    }

    /** The open room with this code, if there is one. */
    public Optional<Room> find(String code) {
        RoomActor actor = rooms.get(code);
        return actor == null ? Optional.empty() : Optional.of(actor.room());
    }

    public int openRooms() {
        return rooms.size();
    }

    /**
     * Puts a user into a room, or back into the room they are already in.
     *
     * @param typedCode the code as typed; spaces and lower case are tolerated
     * @throws RoomNotFoundException if no open room has this code
     * @throws AlreadyInRoomException if the user is in a different room
     */
    public void join(long userId, String username, String typedCode, SeatController controller)
            throws PokerException {
        String code = RoomCodeGenerator.normalize(typedCode);
        RoomActor actor = code == null ? null : rooms.get(code);
        if (actor == null) {
            throw new RoomNotFoundException(
                    "There is no open room with the code \"" + typedCode + "\". Check the code with the host.");
        }
        String current = roomOfUser.putIfAbsent(userId, code);
        if (current == null) {
            if (!actor.submit(new RoomCommands.JoinRoom(userId, username, controller))) {
                // The room closed in the moment between finding it and joining it.
                roomOfUser.remove(userId, code);
                throw new RoomNotFoundException("Room " + code + " has just closed.");
            }
        } else if (current.equals(code)) {
            actor.submit(new RoomCommands.Reconnected(userId, controller));
        } else {
            throw new AlreadyInRoomException(
                    "You are already in room " + current + ". Leave it before joining another.");
        }
    }

    /**
     * Hands a command to the room the user is in.
     *
     * @throws NotInRoomException if the user is not in a room
     */
    public void submit(long userId, RoomCommand command) throws NotInRoomException {
        String code = roomOfUser.get(userId);
        RoomActor actor = code == null ? null : rooms.get(code);
        if (actor == null) {
            throw new NotInRoomException("You are not in a room. Join one with its code first.");
        }
        actor.submit(command);
    }

    /** Tells the user's room, if they are in one, that their connection was lost. */
    public void disconnected(long userId) {
        String code = roomOfUser.get(userId);
        RoomActor actor = code == null ? null : rooms.get(code);
        if (actor != null) {
            actor.submit(new RoomCommands.Disconnected(userId));
        }
    }

    /** The room a user is in, if any. */
    public Optional<String> roomOf(long userId) {
        return Optional.ofNullable(roomOfUser.get(userId));
    }

    // ---- what rooms report back

    @Override
    public void released(long userId, String code) {
        roomOfUser.remove(userId, code);
    }

    @Override
    public void stateChanged(String code, RoomState state) {
        store.saveState(code, state);
    }

    @Override
    public void handFinished(HandRecord hand) {
        handSink.accept(hand);
    }

    @Override
    public void closed(String code) {
        RoomActor actor = rooms.remove(code);
        if (actor != null) {
            actor.stop();
            log.info("Room {} closed", code);
        }
    }

    /** Stops every room. Called when the server shuts down. */
    public void shutdown() {
        for (RoomActor actor : rooms.values()) {
            actor.stop();
        }
        rooms.clear();
        roomOfUser.clear();
    }
}
