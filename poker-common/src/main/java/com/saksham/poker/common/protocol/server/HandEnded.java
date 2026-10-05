package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The hand is over and the pots have been paid. */
@JsonTypeName("HAND_ENDED")
public final class HandEnded extends ServerMessage {

    private final List<PayoutInfo> payouts;
    private final Map<Integer, Long> netBySeat;
    private final Map<Integer, Long> stacks;

    @JsonCreator
    public HandEnded(
            @JsonProperty("payouts") List<PayoutInfo> payouts,
            @JsonProperty("netBySeat") Map<Integer, Long> netBySeat,
            @JsonProperty("stacks") Map<Integer, Long> stacks) {
        this.payouts = List.copyOf(payouts);
        this.netBySeat = Collections.unmodifiableMap(new TreeMap<>(netBySeat));
        this.stacks = Collections.unmodifiableMap(new TreeMap<>(stacks));
    }

    public List<PayoutInfo> payouts() {
        return payouts;
    }

    /** Chips won (positive) or lost (negative) by each seat. */
    public Map<Integer, Long> netBySeat() {
        return netBySeat;
    }

    /** Chips of each seat after the hand. */
    public Map<Integer, Long> stacks() {
        return stacks;
    }
}
