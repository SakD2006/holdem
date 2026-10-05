package com.saksham.poker.server.room;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.HandSeatInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
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
import com.saksham.poker.engine.hand.Street;
import com.saksham.poker.engine.pot.Pot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The hand as the players have been shown it so far.
 *
 * <p>The engine plays ahead: when everyone is all-in it deals the whole board in one go, while the
 * room reveals it card by card with pauses. A snapshot must show only what has been revealed, so it
 * is built from this view, which is updated as each event is delivered, never from the engine.
 */
final class HandView {

    private static final class SeatView {
        long stack;
        long streetBet;
        boolean folded;
        List<Card> holeCards = List.of();
    }

    private final Map<Integer, SeatView> seats = new TreeMap<>();
    private int buttonSeat;
    private int smallBlindSeat;
    private int bigBlindSeat;
    private Street street = Street.PREFLOP;
    private List<Card> board = List.of();
    private List<PotInfo> pots = List.of();

    /** Updates the view for an event that is being delivered. */
    void apply(GameEvent event) {
        if (event instanceof HandStarted started) {
            buttonSeat = started.buttonSeat();
            smallBlindSeat = started.smallBlindSeat();
            bigBlindSeat = started.bigBlindSeat();
            started.stacks().forEach((seat, stack) -> {
                SeatView view = new SeatView();
                view.stack = stack;
                seats.put(seat, view);
            });
        } else if (event instanceof BlindPosted blind) {
            SeatView seat = seats.get(blind.seat());
            seat.stack -= blind.amount();
            seat.streetBet += blind.amount();
        } else if (event instanceof HoleCardsDealt dealt) {
            seats.get(dealt.seat()).holeCards = dealt.cards();
        } else if (event instanceof PlayerActed acted) {
            SeatView seat = seats.get(acted.seat());
            seat.stack = acted.stack();
            seat.streetBet = acted.streetBet();
            seat.folded |= acted.type() == ActionType.FOLD;
        } else if (event instanceof UncalledBetReturned returned) {
            SeatView seat = seats.get(returned.seat());
            seat.stack += returned.amount();
            seat.streetBet -= returned.amount();
        } else if (event instanceof BetsCollected collected) {
            pots = potInfos(collected.pots());
            seats.values().forEach(seat -> seat.streetBet = 0);
        } else if (event instanceof StreetDealt dealt) {
            street = dealt.street();
            board = dealt.board();
            // A street with no betting collects nothing, so clear any bets here too.
            seats.values().forEach(seat -> seat.streetBet = 0);
        } else if (event instanceof ShowdownRevealed) {
            street = Street.SHOWDOWN;
        } else if (event instanceof HandCompleted completed) {
            completed.result().endStacks().forEach((seat, stack) -> seats.get(seat).stack = stack);
        }
    }

    static List<PotInfo> potInfos(List<Pot> pots) {
        List<PotInfo> infos = new ArrayList<>();
        for (Pot pot : pots) {
            infos.add(new PotInfo(pot.amount(), new ArrayList<>(pot.eligibleSeats())));
        }
        return infos;
    }

    boolean hasSeat(int seat) {
        return seats.containsKey(seat);
    }

    long stack(int seat) {
        return seats.get(seat).stack;
    }

    List<Card> holeCards(int seat) {
        SeatView view = seats.get(seat);
        return view == null ? List.of() : view.holeCards;
    }

    int buttonSeat() {
        return buttonSeat;
    }

    int smallBlindSeat() {
        return smallBlindSeat;
    }

    int bigBlindSeat() {
        return bigBlindSeat;
    }

    String street() {
        return street.name();
    }

    List<Card> board() {
        return board;
    }

    List<PotInfo> pots() {
        return pots;
    }

    List<HandSeatInfo> seatInfos() {
        List<HandSeatInfo> infos = new ArrayList<>();
        seats.forEach((seat, view) -> infos.add(new HandSeatInfo(seat, view.stack, view.streetBet, view.folded,
                !view.folded && view.stack == 0)));
        return infos;
    }
}
