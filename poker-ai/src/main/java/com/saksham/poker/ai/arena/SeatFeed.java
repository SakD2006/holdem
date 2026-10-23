package com.saksham.poker.ai.arena;

import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.engine.event.BetsCollected;
import com.saksham.poker.engine.event.BlindPosted;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.event.HandStarted;
import com.saksham.poker.engine.event.HoleCardsDealt;
import com.saksham.poker.engine.event.PlayerActed;
import com.saksham.poker.engine.event.StreetDealt;
import com.saksham.poker.engine.event.UncalledBetReturned;
import com.saksham.poker.engine.hand.HandResult;
import com.saksham.poker.engine.pot.Payout;
import com.saksham.poker.engine.pot.Pot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns an engine event into the message one seat would be sent, so a bot in the arena is fed
 * exactly as it is at a real table. The server's own router does this job for real games; this is
 * the arena's copy of the part a bot uses, and a server test checks the two agree. Like the server,
 * it shows a seat nothing the engine marks as private to another.
 */
public final class SeatFeed {

    private SeatFeed() {
    }

    /** The message this seat is sent for an event, or empty if it is told nothing. */
    public static Optional<ServerMessage> messageFor(GameEvent event, int seat, long handNo) {
        if (!event.isVisibleTo(seat)) {
            return Optional.empty();
        }
        if (event instanceof HandStarted e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.HandStarted(handNo, e.buttonSeat(),
                    e.smallBlindSeat(), e.bigBlindSeat(), e.smallBlind(), e.bigBlind(), e.stacks()));
        }
        if (event instanceof BlindPosted e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.BlindPosted(e.seat(), e.amount(),
                    e.bigBlind(), e.allIn()));
        }
        if (event instanceof HoleCardsDealt e) {
            return Optional.of(new HoleCards(e.seat(), e.cards()));
        }
        if (event instanceof PlayerActed e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.PlayerActed(e.seat(), e.type(),
                    e.amount(), e.streetBet(), e.stack(), e.allIn()));
        }
        if (event instanceof StreetDealt e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.StreetDealt(e.street().name(), e.cards(),
                    e.board()));
        }
        if (event instanceof UncalledBetReturned e) {
            return Optional.of(new BetReturned(e.seat(), e.amount()));
        }
        if (event instanceof BetsCollected e) {
            List<PotInfo> pots = new ArrayList<>();
            for (Pot pot : e.pots()) {
                pots.add(new PotInfo(pot.amount(), new ArrayList<>(pot.eligibleSeats())));
            }
            return Optional.of(new PotsUpdated(pots));
        }
        if (event instanceof HandCompleted e) {
            HandResult result = e.result();
            List<PayoutInfo> payouts = new ArrayList<>();
            for (Payout payout : result.payouts()) {
                payouts.add(new PayoutInfo(payout.potIndex(), payout.seat(), payout.amount()));
            }
            Map<Integer, Long> net = new TreeMap<>();
            for (int each : result.endStacks().keySet()) {
                net.put(each, result.net(each));
            }
            return Optional.of(new HandEnded(payouts, net, result.endStacks()));
        }
        return Optional.empty();
    }
}
