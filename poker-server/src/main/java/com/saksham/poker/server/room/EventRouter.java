package com.saksham.poker.server.room;

import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.engine.event.BetsCollected;
import com.saksham.poker.engine.event.BlindPosted;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.event.HandStarted;
import com.saksham.poker.engine.event.HoleCardsDealt;
import com.saksham.poker.engine.event.PlayerActed;
import com.saksham.poker.engine.event.ShowdownRevealed;
import com.saksham.poker.engine.event.StreetDealt;
import com.saksham.poker.engine.event.UncalledBetReturned;
import com.saksham.poker.engine.hand.HandResult;
import com.saksham.poker.engine.pot.Payout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Decides what each player is told about each engine event. This is the one place where hole cards
 * are kept private: every message about a hand passes through {@link #messageFor}.
 */
final class EventRouter {

    private EventRouter() {
    }

    /**
     * The message one viewer receives for an event.
     *
     * @param event what happened in the hand
     * @param viewerSeat the viewer's seat in this hand, or -1 for someone who was not dealt in
     * @param handNo the hand's number in the room
     * @return the message, or empty if this viewer is told nothing: the event is private to another
     *     seat, or it is only bookkeeping for the room
     */
    static Optional<ServerMessage> messageFor(GameEvent event, int viewerSeat, long handNo) {
        if (!event.isVisibleTo(viewerSeat)) {
            return Optional.empty();
        }
        if (event instanceof HandStarted e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.HandStarted(handNo, e.buttonSeat(),
                    e.smallBlindSeat(), e.bigBlindSeat(), e.smallBlind(), e.bigBlind(), e.stacks()));
        }
        if (event instanceof BlindPosted e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.BlindPosted(
                    e.seat(), e.amount(), e.bigBlind(), e.allIn()));
        }
        if (event instanceof HoleCardsDealt e) {
            return Optional.of(new HoleCards(e.seat(), e.cards()));
        }
        if (event instanceof PlayerActed e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.PlayerActed(
                    e.seat(), e.type(), e.amount(), e.streetBet(), e.stack(), e.allIn()));
        }
        if (event instanceof StreetDealt e) {
            return Optional.of(new com.saksham.poker.common.protocol.server.StreetDealt(
                    e.street().name(), e.cards(), e.board()));
        }
        if (event instanceof UncalledBetReturned e) {
            return Optional.of(new BetReturned(e.seat(), e.amount()));
        }
        if (event instanceof BetsCollected e) {
            return Optional.of(new PotsUpdated(HandView.potInfos(e.pots())));
        }
        if (event instanceof ShowdownRevealed e) {
            List<ShownHandInfo> hands = new ArrayList<>();
            for (ShowdownRevealed.ShownHand hand : e.hands()) {
                hands.add(new ShownHandInfo(hand.seat(), hand.cards(), hand.value().category().name()));
            }
            return Optional.of(new Showdown(hands));
        }
        if (event instanceof HandCompleted e) {
            return Optional.of(handEnded(e.result()));
        }
        // ActionRequested becomes a turn with a timer, and PotAwarded is folded into HAND_ENDED:
        // the room handles both itself.
        return Optional.empty();
    }

    private static HandEnded handEnded(HandResult result) {
        List<PayoutInfo> payouts = new ArrayList<>();
        for (Payout payout : result.payouts()) {
            payouts.add(new PayoutInfo(payout.potIndex(), payout.seat(), payout.amount()));
        }
        Map<Integer, Long> net = new TreeMap<>();
        for (int seat : result.endStacks().keySet()) {
            net.put(seat, result.net(seat));
        }
        return new HandEnded(payouts, net, result.endStacks());
    }
}
