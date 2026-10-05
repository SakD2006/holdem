package com.saksham.poker.common.protocol.dto;

import java.util.List;

/**
 * A pot and who can win it.
 *
 * @param amount chips in the pot
 * @param eligibleSeats seats that can win it, in ascending order
 */
public record PotInfo(long amount, List<Integer> eligibleSeats) {

    public PotInfo {
        eligibleSeats = List.copyOf(eligibleSeats);
    }
}
