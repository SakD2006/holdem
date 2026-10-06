package com.saksham.poker.server.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.engine.rules.LegalActions;
import com.saksham.poker.server.room.RoomCommand;
import com.saksham.poker.server.room.RoomCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiControllerTest {

    private static final long BOT = 900;

    private final ScheduledExecutorService thinkers = Executors.newSingleThreadScheduledExecutor();
    private final List<RoomCommand> sent = new ArrayList<>();
    private final List<Observation> seen = new ArrayList<>();
    private Decision next = Decision.call();
    private boolean broken;

    /** A strategy that does as the test says and remembers what it was shown. */
    private final BotStrategy scripted = new BotStrategy() {
        @Override
        public String name() {
            return "scripted";
        }

        @Override
        protected Decision choose(Observation observation, Random random) {
            seen.add(observation);
            if (broken) {
                throw new IllegalStateException("a bug in the strategy");
            }
            return next;
        }
    };

    private final AiController bot = new AiController(BOT, "Ada_bot", scripted, new Random(1),
            (userId, command) -> {
                synchronized (sent) {
                    sent.add(command);
                }
            }, thinkers, 0, 0);

    @AfterEach
    void stop() {
        thinkers.shutdownNow();
    }

    /** Waits for the thinking thread to finish whatever it has been given. */
    private void settle() throws Exception {
        thinkers.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    /** Heads-up, the bot in seat 1 on the big blind with a pair of aces. */
    private void deal(long handNo) {
        bot.onEvent(new HandStarted(handNo, 0, 0, 1, 50, 100, Map.of(0, 10_000L, 1, 10_000L)));
        bot.onEvent(new BlindPosted(0, 50, false, false));
        bot.onEvent(new BlindPosted(1, 100, true, false));
        bot.onEvent(new HoleCards(1, Card.parseAll("Ah Ad")));
    }

    private static ActionRequest turn(long turnId, LegalActions legal) {
        return new ActionRequest(1, turnId, legal, 0);
    }

    private RoomCommand onlyCommand() {
        synchronized (sent) {
            assertThat(sent).hasSize(1);
            return sent.get(0);
        }
    }

    @Test
    void whenAskedItShowsItsStrategyWhatAPersonWouldSeeAndAnswersThatTurn() throws Exception {
        deal(1);
        next = Decision.raise(300);

        bot.onActionRequested(turn(41, new LegalActions(true, false, 100, false, true, 300, 10_000)));
        settle();

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).holeCards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(seen.get(0).pot()).isEqualTo(150);
        assertThat(seen.get(0).toCall()).isEqualTo(100);
        assertThat(onlyCommand()).isInstanceOf(RoomCommands.PlayerActionCmd.class);
        assertThat(onlyCommand().userId()).isEqualTo(BOT);
        assertThat(bot.isBot()).isTrue();
        assertThat(bot.isConnected()).isTrue();
    }

    @Test
    void aStrategyThatFailsNeverHoldsUpTheTable() throws Exception {
        deal(1);
        broken = true;

        bot.onActionRequested(turn(41, new LegalActions(true, false, 100, false, true, 300, 10_000)));
        settle();

        // It still answers, taking the safe way out, so the turn timer is not needed.
        assertThat(onlyCommand()).isInstanceOf(RoomCommands.PlayerActionCmd.class);
    }

    @Test
    void aBotThatLosesEverythingBuysBackInOnce() throws Exception {
        deal(1);

        bot.onEvent(new HandEnded(List.of(new PayoutInfo(0, 0, 20_000)), Map.of(0, 10_000L, 1, -10_000L),
                Map.of(0, 20_000L, 1, 0L)));

        assertThat(onlyCommand()).isInstanceOf(RoomCommands.Rebuy.class);

        // While it waits to be dealt back in, other players' hands end. It must not ask again,
        // and above all must not take a later refusal as a reason to leave.
        sent.clear();
        bot.onEvent(new HandStarted(2, 0, 0, 2, 50, 100, Map.of(0, 20_000L, 2, 10_000L)));
        bot.onEvent(new HandEnded(List.of(new PayoutInfo(0, 0, 150)), Map.of(0, 100L, 2, -100L),
                Map.of(0, 20_100L, 2, 9_900L)));
        assertThat(sent).isEmpty();
    }

    @Test
    void ifTheRoomDoesNotAllowRebuysABrokeBotGivesUpItsSeat() {
        deal(1);
        bot.onEvent(new HandEnded(List.of(new PayoutInfo(0, 0, 20_000)), Map.of(0, 10_000L, 1, -10_000L),
                Map.of(0, 20_000L, 1, 0L)));
        sent.clear();

        bot.onEvent(new ErrorMessage(ErrorCode.REBUY_NOT_ALLOWED, "This room does not allow rebuys."));

        assertThat(onlyCommand()).isInstanceOf(RoomCommands.LeaveRoom.class);
    }

    @Test
    void anUnrelatedRefusalDoesNotMakeABotLeave() {
        deal(1);

        bot.onEvent(new ErrorMessage(ErrorCode.REBUY_NOT_ALLOWED, "You can only rebuy after losing all your chips."));
        bot.onEvent(new ErrorMessage(ErrorCode.NOT_YOUR_TURN, "It is not your turn."));

        assertThat(sent).isEmpty();
        assertThat(bot.refusals()).isZero();
        bot.onEvent(new ErrorMessage(ErrorCode.INVALID_AMOUNT, "Too small."));
        assertThat(bot.refusals()).isEqualTo(1);
    }
}
