package com.saksham.poker.engine.pot;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Chips that a particular group of players is competing for. The first pot is the main pot; the rest
 * are side pots.
 *
 * @param amount chips in the pot
 * @param eligibleSeats seats that can win it: players who have not folded and put in their full share
 */
public record Pot(long amount, Set<Integer> eligibleSeats) {

    public Pot {
        eligibleSeats = Collections.unmodifiableSet(new TreeSet<>(eligibleSeats));
    }
}
