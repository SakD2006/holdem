package com.saksham.poker.server.room;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.engine.card.DeckFactory;
import com.saksham.poker.server.db.HandRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A room with no threads, no network and no real clock. Commands run at once on the test's thread,
 * and timers only fire when the test says so, which makes every scenario repeatable.
 */
final class RoomHarness implements RoomScheduler, RoomListener {

    /** A timer the room asked for. */
    static final class Scheduled {
        final RoomCommand command;
        final long delayMs;
        boolean cancelled;
        boolean fired;

        Scheduled(RoomCommand command, long delayMs) {
            this.command = command;
            this.delayMs = delayMs;
        }
    }

    static final long HOST = 1;

    final List<Scheduled> timers = new ArrayList<>();
    final List<Long> released = new ArrayList<>();
    final List<RoomState> states = new ArrayList<>();
    final List<HandRecord> hands = new ArrayList<>();
    final Map<Long, FakePlayer> players = new LinkedHashMap<>();
    boolean closed;
    long nowMs = Instant.parse("2026-10-05T10:00:00Z").toEpochMilli();
    final Room room;

    RoomHarness(RoomSettings settings, RoomTimings timings, DeckFactory decks) {
        Clock clock = new Clock() {
            @Override
            public long millis() {
                return nowMs;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMs);
            }

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
        };
        room = new Room("ABC234", HOST, settings, timings, decks, clock, this, this);
        room.opened();
    }

    // ---- what the room asks of the outside world

    @Override
    public Cancellable schedule(RoomCommand command, long delayMs) {
        Scheduled scheduled = new Scheduled(command, delayMs);
        timers.add(scheduled);
        return () -> scheduled.cancelled = true;
    }

    @Override
    public void released(long userId, String code) {
        released.add(userId);
    }

    @Override
    public void stateChanged(String code, RoomState state) {
        states.add(state);
    }

    @Override
    public void handFinished(HandRecord hand) {
        hands.add(hand);
    }

    @Override
    public void closed(String code) {
        closed = true;
    }

    // ---- driving the room

    void run(RoomCommand command) {
        room.run(command);
    }

    /** A user enters the room. The first to join with id {@link #HOST} is the host. */
    FakePlayer join(long userId, String username) {
        FakePlayer player = new FakePlayer(userId, username);
        players.put(userId, player);
        run(new RoomCommands.JoinRoom(userId, username, player));
        return player;
    }

    /** Joins and sits down. */
    FakePlayer joinAndSit(long userId, String username, int seat) {
        FakePlayer player = join(userId, username);
        sit(player, seat);
        return player;
    }

    void sit(FakePlayer player, int seat) {
        player.seat = seat;
        run(new RoomCommands.TakeSeat(player.userId(), seat));
    }

    void start() {
        run(new RoomCommands.StartGame(HOST));
    }

    /** Answers the player's current turn. */
    void act(FakePlayer player, ActionType type, long amount) {
        long turnId = player.pendingRequest == null ? -1 : player.pendingRequest.turnId();
        run(new RoomCommands.PlayerActionCmd(player.userId(), turnId, type, amount));
    }

    void act(FakePlayer player, ActionType type) {
        act(player, type, 0);
    }

    /** The player the room is waiting on, or null if it is waiting on nobody. */
    FakePlayer toAct() {
        // A player who left mid-turn never hears that their turn ended, so go by the newest turn.
        FakePlayer latest = null;
        for (FakePlayer player : players.values()) {
            if (player.pendingRequest != null
                    && (latest == null || player.pendingRequest.turnId() > latest.pendingRequest.turnId())) {
                latest = player;
            }
        }
        return latest;
    }

    /** Everyone checks or calls until the hand is over. */
    void checkDown() {
        for (FakePlayer player = toAct(); player != null; player = toAct()) {
            act(player, player.pendingRequest.legal().canCheck() ? ActionType.CHECK : ActionType.CALL);
        }
    }

    // ---- timers

    /** The newest timer of this kind that is still waiting, or null. */
    Scheduled waiting(Class<? extends RoomCommand> type) {
        for (int i = timers.size() - 1; i >= 0; i--) {
            Scheduled scheduled = timers.get(i);
            if (type.isInstance(scheduled.command) && !scheduled.cancelled && !scheduled.fired) {
                return scheduled;
            }
        }
        return null;
    }

    /** Lets the newest waiting timer of this kind go off. */
    void fire(Class<? extends RoomCommand> type) {
        Scheduled scheduled = waiting(type);
        if (scheduled == null) {
            throw new AssertionError("No " + type.getSimpleName() + " timer is waiting");
        }
        fire(scheduled);
    }

    /** Lets a particular timer go off, even one that was cancelled, as a late timer would. */
    void fire(Scheduled scheduled) {
        scheduled.fired = true;
        run(scheduled.command);
    }

    void forgetMessages() {
        players.values().forEach(FakePlayer::forget);
    }
}
