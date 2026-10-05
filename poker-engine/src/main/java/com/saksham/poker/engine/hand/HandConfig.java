package com.saksham.poker.engine.hand;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Everything fixed before a hand starts.
 *
 * <p>Seats are numbered; "clockwise" means towards the next higher seat number, wrapping round to the
 * lowest. Only the players being dealt into this hand are listed.
 *
 * @param smallBlind the small blind, more than 0
 * @param bigBlind the big blind, at least the small blind
 * @param buttonSeat the seat with the dealer button; must be one of the seats in the hand
 * @param stacks chips each seat starts the hand with, all more than 0; two to nine seats
 */
public record HandConfig(long smallBlind, long bigBlind, int buttonSeat, SortedMap<Integer, Long> stacks) {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 9;

    public HandConfig {
        stacks = Collections.unmodifiableSortedMap(new TreeMap<>(stacks));
        if (smallBlind <= 0 || bigBlind < smallBlind) {
            throw new IllegalArgumentException(
                    "Blinds must be more than 0 with the big blind at least the small blind, but were "
                            + smallBlind + "/" + bigBlind + ".");
        }
        if (stacks.size() < MIN_PLAYERS || stacks.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException(
                    "A hand needs 2 to 9 players, but got " + stacks.size() + ".");
        }
        if (!stacks.containsKey(buttonSeat)) {
            throw new IllegalArgumentException(
                    "The button is on seat " + buttonSeat + ", which is not in the hand: " + stacks.keySet());
        }
        for (Map.Entry<Integer, Long> entry : stacks.entrySet()) {
            if (entry.getValue() <= 0) {
                throw new IllegalArgumentException(
                        "Seat " + entry.getKey() + " has no chips, so it cannot be dealt in.");
            }
        }
    }

    public HandConfig(long smallBlind, long bigBlind, int buttonSeat, Map<Integer, Long> stacks) {
        this(smallBlind, bigBlind, buttonSeat, (SortedMap<Integer, Long>) new TreeMap<>(stacks));
    }
}
