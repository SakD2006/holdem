package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.AlreadyInRoomException;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.card.DeckFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Rooms on their real threads: the manager, the actors and the shared timers. */
class RoomManagerTest {

    private static final RoomSettings SETTINGS = new RoomSettings("Test", 6, 50, 100, 10_000, 10, true, false);
    /** No waits, so hands follow each other as fast as the players answer. */
    private static final RoomTimings NO_WAITS = new RoomTimings(0, 0, 60_000, 30 * 60_000L);

    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
    private final List<RoomState> savedStates = new CopyOnWriteArrayList<>();
    private final RoomManager manager = new RoomManager(timers, (code, state) -> savedStates.add(state),
            NO_WAITS, seededDecks(7), Clock.systemUTC());

    @AfterEach
    void stop() {
        manager.shutdown();
        timers.shutdownNow();
    }

    /** Shuffled decks that are the same on every run. Used from one room's thread only. */
    private static DeckFactory seededDecks(long seed) {
        Random random = new Random(seed);
        return () -> {
            List<Card> cards = new ArrayList<>(Deck.standardOrder());
            Collections.shuffle(cards, random);
            return new Deck(cards);
        };
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            Thread.sleep(5);
        }
    }

    // ---- finding and joining rooms

    @Test
    void anOpenRoomCanBeFoundAndJoinedByItsCodeHoweverItIsTyped() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        AutoPlayer asha = new AutoPlayer(1, 0, manager, 1);

        manager.join(1, "asha", "  abc234 ", asha);
        await("asha has her snapshot", () -> asha.lastSnapshot != null);

        assertThat(manager.find("ABC234")).isPresent();
        assertThat(manager.openRooms()).isEqualTo(1);
        assertThat(manager.roomOf(1)).contains("ABC234");
        assertThat(asha.lastSnapshot.code()).isEqualTo("ABC234");
        assertThat(asha.lastSnapshot.hostUserId()).isEqualTo(1);
    }

    @Test
    void joiningARoomThatDoesNotExistIsRefused() {
        AutoPlayer asha = new AutoPlayer(1, 0, manager, 1);

        assertThatThrownBy(() -> manager.join(1, "asha", "ZZZ999", asha)).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> manager.join(1, "asha", "nonsense", asha))
                .isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> manager.join(1, "asha", null, asha)).isInstanceOf(RoomNotFoundException.class);
        assertThat(manager.roomOf(1)).isEmpty();
    }

    @Test
    void aUserCanBeInOnlyOneRoomUntilTheyLeaveIt() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        manager.open("DEF567", 2, SETTINGS);
        AutoPlayer asha = new AutoPlayer(1, 0, manager, 1);
        manager.join(1, "asha", "ABC234", asha);
        await("asha is in the first room", () -> asha.lastSnapshot != null);

        assertThatThrownBy(() -> manager.join(1, "asha", "DEF567", asha))
                .isInstanceOf(AlreadyInRoomException.class).hasMessageContaining("ABC234");

        manager.submit(1, new RoomCommands.LeaveRoom(1));
        await("asha has left", () -> manager.roomOf(1).isEmpty());
        // She was alone, so the first room closed behind her.
        await("the empty room closed", () -> manager.find("ABC234").isEmpty());
        assertThat(savedStates).contains(RoomState.CLOSED);

        manager.join(1, "asha", "DEF567", asha);
        await("asha is in the second room", () -> manager.roomOf(1).equals(java.util.Optional.of("DEF567")));
    }

    @Test
    void joiningTheSameRoomAgainIsAReconnectAndSendsAFreshSnapshot() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        AutoPlayer asha = new AutoPlayer(1, 0, manager, 1);
        manager.join(1, "asha", "ABC234", asha);
        manager.submit(1, new RoomCommands.TakeSeat(1, 3));
        await("asha is seated", () -> manager.find("ABC234").orElseThrow().seatedCount() == 1);
        manager.disconnected(1);

        manager.join(1, "asha", "ABC234", asha);
        await("asha has a second snapshot", () -> asha.snapshots.get() == 2);

        assertThat(asha.lastSnapshot.yourSeat()).isEqualTo(3);
        assertThat(asha.lastSnapshot.players()).extracting(PlayerInfo::connected).containsExactly(true);
    }

    @Test
    void aFullRoomReleasesTheUserItTurnedAway() throws Exception {
        manager.open("ABC234", 1, new RoomSettings("Small", 2, 50, 100, 10_000, 10, true, false));
        manager.open("DEF567", 1, SETTINGS);
        AutoPlayer third = new AutoPlayer(3, 0, manager, 3);
        manager.join(1, "asha", "ABC234", new AutoPlayer(1, 0, manager, 1));
        manager.join(2, "ravi", "ABC234", new AutoPlayer(2, 1, manager, 2));

        manager.join(3, "meera", "ABC234", third);
        await("meera is told the room is full", () -> third.errors.contains(ErrorCode.ROOM_FULL));
        await("meera is free again", () -> manager.roomOf(3).isEmpty());

        manager.join(3, "meera", "DEF567", third);
        await("meera is in the other room", () -> third.lastSnapshot != null);
    }

    @Test
    void commandsFromSomeoneInNoRoomAreRefused() {
        assertThatThrownBy(() -> manager.submit(9, new RoomCommands.SitOut(9))).isInstanceOf(NotInRoomException.class);
        manager.disconnected(9); // nothing to tell; must not fail
    }

    // ---- many threads at once

    @Test
    void aRoomStaysConsistentWhileTwentyThreadsSendCommandsAtIt() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        List<AutoPlayer> players = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            AutoPlayer player = new AutoPlayer(i + 1, i, manager, 100 + i);
            players.add(player);
            manager.join(player.userId(), "bot" + i, "ABC234", player);
            manager.submit(player.userId(), new RoomCommands.TakeSeat(player.userId(), i));
        }
        await("everyone is seated", () -> manager.find("ABC234").orElseThrow().seatedCount() == 6);
        manager.submit(1, new RoomCommands.StartGame(1));
        AutoPlayer host = players.get(0);

        // Twenty threads pester the room with chat, snapshots and sitting out and in, while the six
        // players play hands as fast as they can.
        int threads = 20;
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        for (int t = 0; t < threads; t++) {
            Random random = new Random(t);
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 400; i++) {
                        long user = 1 + random.nextInt(6);
                        switch (random.nextInt(4)) {
                            case 0 -> manager.submit(user, new RoomCommands.Chat(user, "message " + i));
                            case 1 -> manager.submit(user, new RoomCommands.SendSnapshot(user));
                            case 2 -> manager.submit(user, new RoomCommands.SitOut(user));
                            default -> manager.submit(user, new RoomCommands.SitIn(user));
                        }
                        if (i % 20 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            }, "pester-" + t);
            thread.start();
        }
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        assertThat(failures).isEmpty();

        // Everyone sits back in, and the game must still be going.
        for (AutoPlayer player : players) {
            manager.submit(player.userId(), new RoomCommands.SitIn(player.userId()));
        }
        int handsSoFar = host.handsEnded.get();
        await("another 200 hands are played", () -> host.handsEnded.get() >= handsSoFar + 200);

        // Stop dealing, let the room settle, and take stock.
        manager.submit(1, new RoomCommands.PauseGame(1));
        RoomActorProbe.awaitQuiet(manager, "ABC234", host);
        int before = host.snapshots.get();
        manager.submit(1, new RoomCommands.SendSnapshot(1));
        await("the final snapshot arrives", () -> host.snapshots.get() > before);

        int rebuys = 0;
        for (AutoPlayer player : players) {
            assertThat(player.violations).as("user " + player.userId()).isEmpty();
            // The only refusals are the ones the pestering asks for: chat sent too fast, and
            // sitting in or rebuying at a moment when it is not allowed.
            assertThat(player.errors).as("user " + player.userId())
                    .isSubsetOf(ErrorCode.INVALID_REQUEST, ErrorCode.REBUY_NOT_ALLOWED);
            rebuys += player.rebuys.get();
        }
        assertThat(host.lastSnapshot.state()).isEqualTo(RoomState.PAUSED);
        assertThat(host.lastSnapshot.hand()).isNull();
        long chips = host.lastSnapshot.players().stream().mapToLong(PlayerInfo::stack).sum();
        // Chips only ever enter the room as a starting stack: six at the start, one per rebuy since.
        assertThat(chips % 10_000).isZero();
        assertThat(chips).isBetween(60_000L, 60_000L + rebuys * 10_000L);
        assertThat(host.handsEnded.get()).isGreaterThan(200);
    }
}
