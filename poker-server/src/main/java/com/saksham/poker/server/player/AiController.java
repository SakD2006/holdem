package com.saksham.poker.server.player;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.TableObserver;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.engine.rules.LegalActions;
import com.saksham.poker.server.room.RoomCommands;
import java.util.Random;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A computer player in a seat. The room treats it exactly as it treats a person: it sends the seat
 * the same filtered messages, asks it to act when its turn comes, and receives its answer as an
 * ordinary command on the room's queue. The bot therefore knows only what a person in that seat
 * would know, and can do only what a person could do.
 *
 * <p>The room calls {@link #onEvent} and {@link #onActionRequested} on its own thread, which must
 * not be held up, so the thinking is done on another thread after a short, human-looking pause.
 */
public final class AiController extends SeatController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final String name;
    private final BotStrategy strategy;
    private final Random random;
    private final BotHost host;
    private final ScheduledExecutorService thinkers;
    private final long thinkMinMs;
    private final long thinkMaxMs;
    /** The bot's picture of the hand. Touched only on the room's thread. */
    private final TableObserver observer = new TableObserver();
    private final AtomicInteger refusals = new AtomicInteger();
    /** True from asking to buy back in until the bot is next dealt cards. Room thread only. */
    private boolean askedToRebuy;

    /**
     * @param userId the bot's account
     * @param name its name, for the log
     * @param strategy how it chooses its actions
     * @param random its source of chance
     * @param host where it sends its commands
     * @param thinkers the threads bots do their thinking on
     * @param thinkMinMs the shortest pause before acting, in milliseconds
     * @param thinkMaxMs the longest
     */
    public AiController(long userId, String name, BotStrategy strategy, Random random, BotHost host,
            ScheduledExecutorService thinkers, long thinkMinMs, long thinkMaxMs) {
        super(userId);
        this.name = name;
        this.strategy = strategy;
        this.random = random;
        this.host = host;
        this.thinkers = thinkers;
        this.thinkMinMs = Math.max(0, thinkMinMs);
        this.thinkMaxMs = Math.max(this.thinkMinMs, thinkMaxMs);
    }

    @Override
    public void onEvent(ServerMessage event) {
        observer.accept(event);
        if (event instanceof HandEnded) {
            if (observer.playedLastHand() && observer.stackAfterLastHand() == 0) {
                // Out of chips: buy back in, as a person would. If the room does not allow it, the
                // refusal comes back below and the bot gives up its seat.
                askedToRebuy = true;
                host.submit(userId, new RoomCommands.Rebuy(userId));
            }
        } else if (event instanceof HoleCards) {
            askedToRebuy = false; // dealt in again: the rebuy went through
        } else if (event instanceof ErrorMessage error) {
            if (error.code() == ErrorCode.REBUY_NOT_ALLOWED && askedToRebuy) {
                askedToRebuy = false;
                host.submit(userId, new RoomCommands.LeaveRoom(userId));
            } else if (error.code() == ErrorCode.INVALID_ACTION || error.code() == ErrorCode.INVALID_AMOUNT) {
                // Should never happen: every decision is made legal before it is sent. The room's
                // turn timer will act for the bot, but this is a fault worth knowing about.
                refusals.incrementAndGet();
                log.warn("Bot {} had an action refused: {}", name, error.message());
            }
        }
    }

    @Override
    public void onActionRequested(ActionRequest request) {
        LegalActions legal = request.legal();
        // Taken now, on the room's thread, and never changed: safe to hand to another thread.
        Observation seen = observer.observe(legal.canCheck(), legal.callAmount(), legal.canBet(), legal.canRaise(),
                legal.minRaiseTo(), legal.maxRaiseTo());
        long pause = thinkMinMs + (thinkMaxMs > thinkMinMs ? random.nextLong(thinkMaxMs - thinkMinMs + 1) : 0);
        try {
            thinkers.schedule(() -> act(request.turnId(), seen), pause, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The server is shutting down. The room's turn timer covers the turn.
            log.debug("Bot {} could not think: the server is stopping", name);
        }
    }

    /** Runs on a thinking thread: chooses, then answers the turn. */
    private void act(long turnId, Observation seen) {
        Decision decision;
        try {
            decision = strategy.decide(seen, random);
        } catch (RuntimeException e) {
            // A bot must never hold a table up: on any fault it takes the safe way out.
            log.error("Bot {} could not decide; checking or folding instead. It saw {}", name, seen, e);
            decision = seen.canCheck() ? Decision.check() : Decision.fold();
        }
        ActionType type = decision.type();
        host.submit(userId, new RoomCommands.PlayerActionCmd(userId, turnId, type, decision.amount()));
    }

    /** How many of this bot's actions the room has refused as against the rules. It should stay 0. */
    public int refusals() {
        return refusals.get();
    }

    /** A bot is always there. */
    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isBot() {
        return true;
    }
}
