package com.saksham.poker.engine.pot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Splits the chips put in during a hand into the main pot and side pots. */
public final class PotCalculator {

    private PotCalculator() {
    }

    /**
     * Builds the pots. A player who is all-in for less than the others can only win as much from
     * each opponent as they put in themselves; the extra goes into side pots for the players who
     * matched it. Chips from players who folded stay in the pots they went into.
     *
     * @param contributed chips each seat has put in this hand
     * @param folded seats that have folded
     * @return the main pot first, then side pots in the order they were formed; empty if no chips
     *     are in
     */
    public static List<Pot> calculate(Map<Integer, Long> contributed, Set<Integer> folded) {
        // Each different amount put in by a player still in the hand closes one pot.
        TreeSet<Long> levels = new TreeSet<>();
        long total = 0;
        for (Map.Entry<Integer, Long> entry : contributed.entrySet()) {
            total += entry.getValue();
            if (entry.getValue() > 0 && !folded.contains(entry.getKey())) {
                levels.add(entry.getValue());
            }
        }
        if (total == 0) {
            return List.of();
        }
        if (levels.isEmpty()) {
            // Everyone who put chips in has folded, so whoever is left plays for all of it.
            Set<Integer> remaining = new TreeSet<>(contributed.keySet());
            remaining.removeAll(folded);
            if (remaining.isEmpty()) {
                throw new IllegalArgumentException("There are chips in the pot but every player has folded.");
            }
            return List.of(new Pot(total, remaining));
        }

        List<Pot> pots = new ArrayList<>();
        long previous = 0;
        long counted = 0;
        for (long level : levels) {
            long amount = 0;
            Set<Integer> eligible = new TreeSet<>();
            for (Map.Entry<Integer, Long> entry : contributed.entrySet()) {
                long chips = entry.getValue();
                amount += Math.min(chips, level) - Math.min(chips, previous);
                if (chips >= level && !folded.contains(entry.getKey())) {
                    eligible.add(entry.getKey());
                }
            }
            pots.add(new Pot(amount, eligible));
            counted += amount;
            previous = level;
        }

        // A folded player can have put in more than anyone still in. Those chips stay in play, in
        // the last pot.
        if (counted < total) {
            Pot last = pots.remove(pots.size() - 1);
            pots.add(new Pot(last.amount() + total - counted, last.eligibleSeats()));
        }
        return List.copyOf(pots);
    }
}
