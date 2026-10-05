package com.saksham.poker.engine.hand;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.common.exception.InvalidActionException;
import com.saksham.poker.common.exception.NotYourTurnException;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.eval.HandEvaluator;
import com.saksham.poker.engine.eval.HandValue;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.BetsCollected;
import com.saksham.poker.engine.event.BlindPosted;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.event.HandStarted;
import com.saksham.poker.engine.event.HoleCardsDealt;
import com.saksham.poker.engine.event.PlayerActed;
import com.saksham.poker.engine.event.PotAwarded;
import com.saksham.poker.engine.event.ShowdownRevealed;
import com.saksham.poker.engine.event.ShowdownRevealed.ShownHand;
import com.saksham.poker.engine.event.StreetDealt;
import com.saksham.poker.engine.event.UncalledBetReturned;
import com.saksham.poker.engine.pot.Payout;
import com.saksham.poker.engine.pot.Pot;
import com.saksham.poker.engine.pot.PotCalculator;
import com.saksham.poker.engine.rules.ActionValidator;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One hand of No-Limit Texas Hold'em, from the blinds to the last pot being paid.
 *
 * <p>Call {@link #start()} once, then {@link #apply} for each player action until
 * {@link #isComplete()}. Every call returns the events it caused, in order. The hand does nothing
 * else: it does not send, log, wait or save, and its outcome depends only on the deck and the
 * actions it is given.
 *
 * <p>Not thread-safe; one thread must own a hand.
 */
public final class HoldemHand {

    private final HandConfig config;
    private final Deck deck;
    /** Every seat in the hand, in clockwise (ascending seat number) order. */
    private final List<SeatState> seats = new ArrayList<>();
    private final Map<Integer, SeatState> bySeat = new HashMap<>();
    private final List<Card> board = new ArrayList<>();
    private final long totalChips;

    private Street street = Street.PREFLOP;
    private BettingRound round;
    private int smallBlindSeat = -1;
    private int bigBlindSeat = -1;
    private int seatToAct = -1;
    private int riverAggressor = -1;
    private boolean started;
    private HandResult result;

    public HoldemHand(HandConfig config, Deck deck) {
        this.config = config;
        this.deck = deck;
        long total = 0;
        for (Map.Entry<Integer, Long> entry : config.stacks().entrySet()) {
            SeatState seat = new SeatState(entry.getKey(), entry.getValue());
            seats.add(seat);
            bySeat.put(seat.seat(), seat);
            total += entry.getValue();
        }
        this.totalChips = total;
    }

    // ---- playing the hand

    /** Posts the blinds, deals the hole cards and asks the first player to act. */
    public List<GameEvent> start() {
        if (started) {
            throw new IllegalStateException("The hand has already started.");
        }
        started = true;
        List<GameEvent> events = new ArrayList<>();

        // Heads-up the button is the small blind; otherwise the blinds are the next two seats.
        List<SeatState> fromLeftOfButton = clockwiseAfter(config.buttonSeat());
        SeatState smallBlind = seats.size() == 2 ? bySeat.get(config.buttonSeat()) : fromLeftOfButton.get(0);
        SeatState bigBlind = seats.size() == 2 ? fromLeftOfButton.get(0) : fromLeftOfButton.get(1);
        smallBlindSeat = smallBlind.seat();
        bigBlindSeat = bigBlind.seat();
        events.add(new HandStarted(config.buttonSeat(), smallBlindSeat, bigBlindSeat,
                config.smallBlind(), config.bigBlind(), config.stacks()));

        postBlind(smallBlind, config.smallBlind(), false, events);
        postBlind(bigBlind, config.bigBlind(), true, events);

        // One card at a time, starting left of the button, twice round.
        Map<Integer, List<Card>> dealt = new HashMap<>();
        for (int card = 0; card < 2; card++) {
            for (SeatState seat : fromLeftOfButton) {
                dealt.computeIfAbsent(seat.seat(), key -> new ArrayList<>()).add(deck.deal());
            }
        }
        for (SeatState seat : fromLeftOfButton) {
            seat.deal(dealt.get(seat.seat()));
            events.add(new HoleCardsDealt(seat.seat(), seat.holeCards()));
        }

        // A short big blind does not lower the price: everyone still has to match the full blind.
        round = new BettingRound(seats, config.bigBlind(), config.bigBlind());
        advance(bigBlindSeat, events);
        return events;
    }

    /**
     * Carries out a player's action.
     *
     * @throws NotYourTurnException if it is not this seat's turn
     * @throws InvalidActionException if the hand is over, or the action is not allowed now
     * @throws com.saksham.poker.common.exception.InvalidAmountException if the bet or raise is
     *     outside the legal range
     */
    public List<GameEvent> apply(int seatNumber, PlayerAction action) throws GameRuleException {
        requireStarted();
        if (isComplete()) {
            throw new InvalidActionException("The hand is over. Wait for the next one.");
        }
        if (seatNumber != seatToAct) {
            throw new NotYourTurnException(
                    "It is seat " + seatToAct + "'s turn, not seat " + seatNumber + "'s.");
        }
        SeatState seat = bySeat.get(seatNumber);
        LegalActions legal = round.legalActions(seat);
        PlayerAction resolved = ActionValidator.resolve(action, legal, seat.stack());

        List<GameEvent> events = new ArrayList<>();
        Street actedOn = street;
        long paid = round.apply(seat, resolved, legal);
        events.add(new PlayerActed(seatNumber, actedOn, resolved.type(), paid, seat.streetBet(),
                seat.stack(), seat.allIn()));
        advance(seatNumber, events);
        return events;
    }

    /**
     * Folds a player whether or not it is their turn, for a player who has left the room or must be
     * removed. Does nothing if the seat has already folded or the hand is over.
     */
    public List<GameEvent> forceFold(int seatNumber) {
        requireStarted();
        SeatState seat = bySeat.get(seatNumber);
        if (seat == null) {
            throw new IllegalArgumentException("Seat " + seatNumber + " is not in this hand.");
        }
        if (isComplete() || seat.folded()) {
            return List.of();
        }
        List<GameEvent> events = new ArrayList<>();
        int waitingOn = seatToAct;
        LegalActions before = legalActionsFor(waitingOn);

        seat.fold();
        events.add(new PlayerActed(seatNumber, street, ActionType.FOLD, 0, seat.streetBet(), seat.stack(), false));

        if (seatNumber == waitingOn || playersInHand() == 1 || !round.needsToAct(bySeat.get(waitingOn))) {
            advance(waitingOn, events);
        } else {
            // Still the same player's turn. Tell them again only if their options changed.
            LegalActions after = legalActionsFor(waitingOn);
            if (!after.equals(before)) {
                events.add(new ActionRequested(waitingOn, after));
            }
        }
        return events;
    }

    /** Moves the hand on after the seat {@code lastActor} has acted or folded. */
    private void advance(int lastActor, List<GameEvent> events) {
        seatToAct = -1;
        if (playersInHand() == 1) {
            finishByFolds(events);
            return;
        }
        int next = round.nextToAct(lastActor);
        if (next >= 0) {
            seatToAct = next;
            events.add(new ActionRequested(next, round.legalActions(bySeat.get(next))));
            return;
        }
        endStreet(events);
    }

    /**
     * Closes the betting on the current street and deals on. When fewer than two players can still
     * bet, the next street has no betting either, so this keeps going to the showdown.
     */
    private void endStreet(List<GameEvent> events) {
        while (true) {
            if (street == Street.RIVER) {
                riverAggressor = round.lastAggressor();
            }
            collectBets(events);
            if (street == Street.RIVER) {
                showdown(events);
                return;
            }
            dealNextStreet(events);
            round = new BettingRound(seats, config.bigBlind(), 0);
            // After the flop the first player left of the button acts first.
            int next = round.nextToAct(config.buttonSeat());
            if (next >= 0) {
                seatToAct = next;
                events.add(new ActionRequested(next, round.legalActions(bySeat.get(next))));
                return;
            }
        }
    }

    private void postBlind(SeatState seat, long blind, boolean isBigBlind, List<GameEvent> events) {
        long amount = Math.min(blind, seat.stack());
        seat.pay(amount);
        events.add(new BlindPosted(seat.seat(), amount, isBigBlind, seat.allIn()));
    }

    private void dealNextStreet(List<GameEvent> events) {
        deck.burn();
        int count = street == Street.PREFLOP ? 3 : 1;
        List<Card> dealt = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dealt.add(deck.deal());
        }
        board.addAll(dealt);
        street = Street.values()[street.ordinal() + 1];
        events.add(new StreetDealt(street, dealt, board));
    }

    /** Hands back any uncalled bet, then moves the street's bets into the pots. */
    private void collectBets(List<GameEvent> events) {
        boolean anyBets = false;
        SeatState top = null;
        long second = 0;
        for (SeatState seat : seats) {
            anyBets |= seat.streetBet() > 0;
            if (top == null || seat.streetBet() > top.streetBet()) {
                second = top == null ? 0 : top.streetBet();
                top = seat;
            } else {
                second = Math.max(second, seat.streetBet());
            }
        }
        // Chips bet by a player who then folded stay in the pot, so only a live bet is handed back.
        if (top != null && !top.folded() && top.streetBet() > second) {
            long uncalled = top.streetBet() - second;
            top.refund(uncalled);
            events.add(new UncalledBetReturned(top.seat(), uncalled));
        }
        for (SeatState seat : seats) {
            seat.startStreet();
        }
        if (anyBets) {
            events.add(new BetsCollected(pots()));
        }
    }

    /** Everyone else has folded: the last player takes the pot without showing cards. */
    private void finishByFolds(List<GameEvent> events) {
        collectBets(events);
        SeatState winner = null;
        long pot = 0;
        for (SeatState seat : seats) {
            pot += seat.totalCommitted();
            if (!seat.folded()) {
                winner = seat;
            }
        }
        winner.win(pot);
        List<Payout> payouts = List.of(new Payout(0, winner.seat(), pot));
        events.add(new PotAwarded(0, pot, payouts));
        complete(Set.of(), payouts, pot, events);
    }

    private void showdown(List<GameEvent> events) {
        street = Street.SHOWDOWN;

        // The last player to bet or raise on the river shows first; with no river bet, the first
        // player left of the button. Then clockwise.
        List<SeatState> showOrder = new ArrayList<>();
        SeatState aggressor = bySeat.get(riverAggressor);
        int startAfter = aggressor != null && !aggressor.folded()
                ? previousSeat(riverAggressor)
                : config.buttonSeat();
        for (SeatState seat : clockwiseAfter(startAfter)) {
            if (!seat.folded()) {
                showOrder.add(seat);
            }
        }

        Map<Integer, HandValue> values = new HashMap<>();
        List<ShownHand> shown = new ArrayList<>();
        for (SeatState seat : showOrder) {
            List<Card> seven = new ArrayList<>(seat.holeCards());
            seven.addAll(board);
            HandValue value = HandEvaluator.evaluate(seven);
            values.put(seat.seat(), value);
            shown.add(new ShownHand(seat.seat(), seat.holeCards(), value));
        }
        events.add(new ShowdownRevealed(shown));

        List<Payout> allPayouts = new ArrayList<>();
        long totalPot = 0;
        List<Pot> pots = pots();
        for (int index = 0; index < pots.size(); index++) {
            Pot pot = pots.get(index);
            HandValue best = null;
            for (int seat : pot.eligibleSeats()) {
                if (best == null || values.get(seat).compareTo(best) > 0) {
                    best = values.get(seat);
                }
            }
            // Winners in clockwise order from the left of the button: odd chips go out in that order.
            List<SeatState> winners = new ArrayList<>();
            for (SeatState seat : clockwiseAfter(config.buttonSeat())) {
                if (pot.eligibleSeats().contains(seat.seat()) && values.get(seat.seat()).compareTo(best) == 0) {
                    winners.add(seat);
                }
            }
            long share = pot.amount() / winners.size();
            long oddChips = pot.amount() % winners.size();
            List<Payout> payouts = new ArrayList<>();
            for (int i = 0; i < winners.size(); i++) {
                long amount = share + (i < oddChips ? 1 : 0);
                winners.get(i).win(amount);
                payouts.add(new Payout(index, winners.get(i).seat(), amount));
            }
            events.add(new PotAwarded(index, pot.amount(), payouts));
            allPayouts.addAll(payouts);
            totalPot += pot.amount();
        }
        complete(values.keySet(), allPayouts, totalPot, events);
    }

    private void complete(Set<Integer> shownSeats, List<Payout> payouts, long totalPot, List<GameEvent> events) {
        seatToAct = -1;
        Map<Integer, Long> startStacks = new TreeMap<>();
        Map<Integer, Long> endStacks = new TreeMap<>();
        Map<Integer, List<Card>> holeCards = new TreeMap<>();
        long chipsNow = 0;
        for (SeatState seat : seats) {
            startStacks.put(seat.seat(), seat.startStack());
            endStacks.put(seat.seat(), seat.stack());
            holeCards.put(seat.seat(), seat.holeCards());
            chipsNow += seat.stack();
        }
        // SPEC 2.9: a hand never creates or destroys chips.
        if (chipsNow != totalChips) {
            throw new IllegalStateException(
                    "Chips were not conserved: the hand started with " + totalChips + " and ended with "
                            + chipsNow + ".");
        }
        result = new HandResult(board, startStacks, endStacks, holeCards, shownSeats, payouts, totalPot);
        events.add(new HandCompleted(result));
    }

    // ---- reading the hand

    /** What this seat may do now; {@link LegalActions#NONE} unless it is their turn. */
    public LegalActions legalActionsFor(int seatNumber) {
        if (seatNumber < 0 || seatNumber != seatToAct) {
            return LegalActions.NONE;
        }
        return round.legalActions(bySeat.get(seatNumber));
    }

    public boolean isComplete() {
        return result != null;
    }

    /** The outcome, once the hand is complete. */
    public HandResult result() {
        if (result == null) {
            throw new IllegalStateException("The hand is not over yet.");
        }
        return result;
    }

    public HandConfig config() {
        return config;
    }

    public Street street() {
        return street;
    }

    /** The community cards dealt so far. */
    public List<Card> board() {
        return Collections.unmodifiableList(board);
    }

    /** The seat whose turn it is, or -1 when nobody is being waited on. */
    public int seatToAct() {
        return seatToAct;
    }

    public int smallBlindSeat() {
        return smallBlindSeat;
    }

    public int bigBlindSeat() {
        return bigBlindSeat;
    }

    /** Every seat in the hand, clockwise. */
    public List<SeatState> seats() {
        return Collections.unmodifiableList(seats);
    }

    /** One seat's state, or null if that seat is not in the hand. */
    public SeatState seat(int seatNumber) {
        return bySeat.get(seatNumber);
    }

    /** The highest bet on the current street, 0 if nobody has bet or the hand is over. */
    public long currentBet() {
        return round == null || isComplete() ? 0 : round.currentBet();
    }

    /** The pots made from bets already collected; bets on the current street are not in them yet. */
    public List<Pot> pots() {
        Map<Integer, Long> collected = new TreeMap<>();
        Set<Integer> folded = new TreeSet<>();
        for (SeatState seat : seats) {
            collected.put(seat.seat(), seat.totalCommitted() - seat.streetBet());
            if (seat.folded()) {
                folded.add(seat.seat());
            }
        }
        return PotCalculator.calculate(collected, folded);
    }

    // ---- helpers

    private void requireStarted() {
        if (!started) {
            throw new IllegalStateException("The hand has not started yet. Call start() first.");
        }
    }

    private int playersInHand() {
        int count = 0;
        for (SeatState seat : seats) {
            if (!seat.folded()) {
                count++;
            }
        }
        return count;
    }

    /** Every seat, starting with the one after {@code seatNumber} and ending with that seat itself. */
    private List<SeatState> clockwiseAfter(int seatNumber) {
        int start = 0;
        for (int i = 0; i < seats.size(); i++) {
            if (seats.get(i).seat() > seatNumber) {
                start = i;
                break;
            }
        }
        List<SeatState> ordered = new ArrayList<>(seats.size());
        for (int i = 0; i < seats.size(); i++) {
            ordered.add(seats.get((start + i) % seats.size()));
        }
        return ordered;
    }

    /** The seat just before {@code seatNumber}, going anticlockwise. */
    private int previousSeat(int seatNumber) {
        List<SeatState> ordered = clockwiseAfter(seatNumber);
        return ordered.get(ordered.size() - 2).seat();
    }
}
