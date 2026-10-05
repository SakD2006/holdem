package com.saksham.poker.engine.pot;

/**
 * Chips paid to one seat from one pot.
 *
 * @param potIndex which pot, 0 being the main pot
 * @param seat the seat that was paid
 * @param amount chips paid
 */
public record Payout(int potIndex, int seat, long amount) {
}
