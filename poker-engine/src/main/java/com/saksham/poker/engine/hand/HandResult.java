package com.saksham.poker.engine.hand;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.engine.pot.Payout;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * How a finished hand came out.
 *
 * @param board the community cards dealt, none to five
 * @param startStacks chips each seat had before the hand
 * @param endStacks chips each seat has after it
 * @param holeCards every seat's hole cards, including players who folded; private to each player
 *     unless the seat is in {@code shownSeats}
 * @param shownSeats seats whose cards were revealed at showdown; empty if the hand ended by folds
 * @param payouts chips paid out, pot by pot
 * @param totalPot all chips that were paid out
 */
public record HandResult(
        List<Card> board,
        Map<Integer, Long> startStacks,
        Map<Integer, Long> endStacks,
        Map<Integer, List<Card>> holeCards,
        Set<Integer> shownSeats,
        List<Payout> payouts,
        long totalPot) {

    public HandResult {
        board = List.copyOf(board);
        startStacks = Collections.unmodifiableMap(new TreeMap<>(startStacks));
        endStacks = Collections.unmodifiableMap(new TreeMap<>(endStacks));
        holeCards = Collections.unmodifiableMap(new TreeMap<>(holeCards));
        shownSeats = Collections.unmodifiableSet(new TreeSet<>(shownSeats));
        payouts = List.copyOf(payouts);
    }

    /** True if the hand reached a showdown, false if everyone but one player folded. */
    public boolean showdown() {
        return !shownSeats.isEmpty();
    }

    /** Chips won or lost by a seat: positive for a win, negative for a loss. */
    public long net(int seat) {
        return endStacks.get(seat) - startStacks.get(seat);
    }

    /** Seats that were paid from any pot. */
    public Set<Integer> winners() {
        Set<Integer> winners = new TreeSet<>();
        for (Payout payout : payouts) {
            winners.add(payout.seat());
        }
        return winners;
    }
}
