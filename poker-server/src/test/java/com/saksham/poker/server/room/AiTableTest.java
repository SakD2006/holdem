package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.RuleBasedStrategy;
import com.saksham.poker.ai.RuleBasedStrategy.Style;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.engine.card.SecureDeckFactory;
import com.saksham.poker.server.db.HandRecord;
import com.saksham.poker.server.player.AiController;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Real computer players at a real table: rooms on their own threads, bots thinking on theirs, and
 * hundreds of hands played as fast as they will go.
 */
class AiTableTest {

    private static final RoomSettings SETTINGS = new RoomSettings("Bots", 6, 50, 100, 10_000, 25, true, false);
    private static final RoomTimings NO_WAITS = new RoomTimings(0, 0, 60_000, 30 * 60_000L);

    private final ScheduledExecutorService timers = Executors.newScheduledThreadPool(2);
    private final ScheduledExecutorService thinkers = Executors.newScheduledThreadPool(2);
    private final List<HandRecord> hands = new CopyOnWriteArrayList<>();
    private final RoomManager manager = new RoomManager(timers, (code, state) -> { }, hands::add, NO_WAITS,
            new SecureDeckFactory(), Clock.systemUTC());

    @AfterEach
    void stop() {
        manager.shutdown();
        timers.shutdownNow();
        thinkers.shutdownNow();
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            Thread.sleep(5);
        }
    }

    /** Counts what a strategy chose, so the test can tell the bots really played poker. */
    private static final class Counting extends BotStrategy {
        private final BotStrategy inner;
        final Map<ActionType, AtomicInteger> chosen = new EnumMap<>(ActionType.class);

        Counting(BotStrategy inner) {
            this.inner = inner;
            for (ActionType type : ActionType.values()) {
                chosen.put(type, new AtomicInteger());
            }
        }

        @Override
        public String name() {
            return inner.name();
        }

        @Override
        protected Decision choose(Observation seen, Random random) {
            Decision decision = inner.decide(seen, random);
            chosen.get(decision.type()).incrementAndGet();
            return decision;
        }

        int count(ActionType type) {
            return chosen.get(type).get();
        }
    }

    private void submit(long userId, RoomCommand command) {
        try {
            manager.submit(userId, command);
        } catch (NotInRoomException e) {
            // the room has closed: the test is over
        }
    }

    @Test
    void fiveBotsAndAPersonPlayHundredsOfHandsWithNothingRefusedAndNoChipsLost() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        AutoPlayer person = new AutoPlayer(1, 0, manager, 11);
        manager.join(1, "asha", "ABC234", person);
        manager.submit(1, new RoomCommands.TakeSeat(1, 0));

        List<AiController> bots = new ArrayList<>();
        List<Counting> strategies = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Counting strategy = new Counting(new RuleBasedStrategy(i % 2 == 0 ? Style.EASY : Style.SOLID));
            AiController bot = new AiController(100 + i, "bot" + i, strategy, new Random(500 + i), this::submit,
                    thinkers, 0, 0);
            strategies.add(strategy);
            bots.add(bot);
            assertThat(manager.addBot(1, bot.userId(), "bot" + i, bot)).isTrue();
        }
        await("everyone is seated", () -> manager.find("ABC234").orElseThrow().seatedCount() == 6);
        assertThat(manager.addBot(1, 100, "bot0", bots.get(0))).as("a bot already playing is not offered twice")
                .isFalse();

        manager.submit(1, new RoomCommands.StartGame(1));
        await("400 hands are played", () -> person.handsEnded.get() >= 400);
        manager.submit(1, new RoomCommands.PauseGame(1));

        assertThat(person.violations).as("no leaked cards, no chips made or lost").isEmpty();
        // Bots that went broke bought back in and stayed: nobody wandered off.
        assertThat(manager.find("ABC234").orElseThrow().seatedCount()).isEqualTo(6);
        assertThat(person.errors).isEmpty();
        for (AiController bot : bots) {
            assertThat(bot.refusals()).as("every bot action was legal").isZero();
        }
        // They played real poker: every kind of action was used, and folding was the most common.
        int folds = 0;
        int calls = 0;
        int raises = 0;
        int checks = 0;
        for (Counting strategy : strategies) {
            folds += strategy.count(ActionType.FOLD);
            calls += strategy.count(ActionType.CALL);
            checks += strategy.count(ActionType.CHECK);
            raises += strategy.count(ActionType.BET) + strategy.count(ActionType.RAISE);
        }
        assertThat(folds).isGreaterThan(calls);
        assertThat(calls).isPositive();
        assertThat(checks).isPositive();
        assertThat(raises).isGreaterThan(100);
        // Every hand has all its chips accounted for.
        assertThat(hands).hasSizeGreaterThanOrEqualTo(400);
        for (HandRecord hand : hands) {
            assertThat(hand.players().stream().mapToLong(HandRecord.PlayerRecord::net).sum()).isZero();
        }
    }

    @Test
    void whenThePersonLeavesTheBotsGoToo() throws Exception {
        manager.open("ABC234", 1, SETTINGS);
        AutoPlayer person = new AutoPlayer(1, 0, manager, 11);
        manager.join(1, "asha", "ABC234", person);
        manager.submit(1, new RoomCommands.TakeSeat(1, 0));
        AiController bot = new AiController(100, "bot", new RuleBasedStrategy(Style.EASY), new Random(1),
                this::submit, thinkers, 0, 0);
        manager.addBot(1, 100, "bot", bot);
        await("both are seated", () -> manager.find("ABC234").orElseThrow().seatedCount() == 2);
        manager.submit(1, new RoomCommands.StartGame(1));
        await("a few hands are played", () -> person.handsEnded.get() >= 5);

        manager.submit(1, new RoomCommands.LeaveRoom(1));

        await("the room closes", () -> manager.find("ABC234").isEmpty());
        assertThat(manager.roomOf(100)).as("the bot's account is free for another room").isEmpty();
        assertThat(manager.openRooms()).isZero();
    }
}
