package com.saksham.poker.common.protocol.dto;

/**
 * Chips paid to one seat from one pot.
 *
 * @param potIndex which pot, 0 being the main pot
 * @param seat the seat that was paid
 * @param amount chips paid
 */
public record PayoutInfo(int potIndex, int seat, long amount) {
}
