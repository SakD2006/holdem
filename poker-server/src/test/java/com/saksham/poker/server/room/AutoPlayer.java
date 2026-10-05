package com.saksham.poker.server.room;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.NotInRoomException;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.engine.rules.LegalActions;
import com.saksham.poker.server.player.ActionRequest;
import com.saksham.poker.server.player.SeatController;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A player that answers every turn at once with a random legal action, rebuys when it goes broke,
 * and notes anything that should never happen. It talks to its room only through the
 * {@link RoomManager}, as a real connection does.
 */
final class AutoPlayer extends SeatController {

    private final RoomManager manager;
    private final Random random;
    private final int seat;

    final AtomicInteger handsEnded = new AtomicInteger();
    final AtomicInteger rebuys = new AtomicInteger();
    final AtomicInteger snapshots = new AtomicInteger();
    /** Things that must never happen: another player's cards, or a hand that created chips. */
    final List<String> violations = new CopyOnWriteArrayList<>();
    final List<ErrorCode> errors = new CopyOnWriteArrayList<>();
    volatile RoomSnapshot lastSnapshot;

    AutoPlayer(long userId, int seat, RoomManager manager, long seed) {
        super(userId);
        this.seat = seat;
        this.manager = manager;
        this.random = new Random(seed);
    }

    @Override
    public void onActionRequested(ActionRequest request) {
        LegalActions legal = request.legal();
        ActionType type;
        long amount = 0;
        int roll = random.nextInt(100);
        if (roll < 12) {
            type = legal.canCheck() ? ActionType.CHECK : ActionType.FOLD;
        } else if (roll < 75 || !(legal.canBet() || legal.canRaise())) {
            type = legal.canCheck() ? ActionType.CHECK : ActionType.CALL;
        } else if (roll < 95) {
            type = legal.canBet() ? ActionType.BET : ActionType.RAISE;
            amount = legal.minRaiseTo();
        } else {
            type = ActionType.ALL_IN;
        }
        submit(new RoomCommands.PlayerActionCmd(userId, request.turnId(), type, amount));
    }

    @Override
    public void onEvent(ServerMessage event) {
        if (event instanceof HoleCards cards && cards.seat() != seat) {
            violations.add("user " + userId + " was sent the hole cards of seat " + cards.seat());
        } else if (event instanceof HandEnded ended) {
            long net = ended.netBySeat().values().stream().mapToLong(Long::longValue).sum();
            if (net != 0) {
                violations.add("a hand ended with chips not adding up: net " + net);
            }
            handsEnded.incrementAndGet();
            Long mine = ended.stacks().get(seat);
            if (mine != null && mine == 0) {
                // Broke: buy back in. The room settles the hand before it reads this request.
                rebuys.incrementAndGet();
                submit(new RoomCommands.Rebuy(userId));
            }
        } else if (event instanceof ErrorMessage error) {
            errors.add(error.code());
        } else if (event instanceof RoomSnapshot snapshot) {
            lastSnapshot = snapshot;
            snapshots.incrementAndGet();
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    private void submit(RoomCommand command) {
        try {
            manager.submit(userId, command);
        } catch (NotInRoomException e) {
            violations.add("user " + userId + " was dropped from the room");
        }
    }
}
