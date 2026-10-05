package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** A new hand has begun. */
@JsonTypeName("HAND_STARTED")
public final class HandStarted extends ServerMessage {

    private final long handNo;
    private final int buttonSeat;
    private final int smallBlindSeat;
    private final int bigBlindSeat;
    private final long smallBlind;
    private final long bigBlind;
    private final Map<Integer, Long> stacks;

    @JsonCreator
    public HandStarted(
            @JsonProperty("handNo") long handNo,
            @JsonProperty("buttonSeat") int buttonSeat,
            @JsonProperty("smallBlindSeat") int smallBlindSeat,
            @JsonProperty("bigBlindSeat") int bigBlindSeat,
            @JsonProperty("smallBlind") long smallBlind,
            @JsonProperty("bigBlind") long bigBlind,
            @JsonProperty("stacks") Map<Integer, Long> stacks) {
        this.handNo = handNo;
        this.buttonSeat = buttonSeat;
        this.smallBlindSeat = smallBlindSeat;
        this.bigBlindSeat = bigBlindSeat;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.stacks = Collections.unmodifiableMap(new TreeMap<>(stacks));
    }

    /** The hand's number in this room, starting at 1. */
    public long handNo() {
        return handNo;
    }

    public int buttonSeat() {
        return buttonSeat;
    }

    public int smallBlindSeat() {
        return smallBlindSeat;
    }

    public int bigBlindSeat() {
        return bigBlindSeat;
    }

    public long smallBlind() {
        return smallBlind;
    }

    public long bigBlind() {
        return bigBlind;
    }

    /** Chips of each seat dealt in, before blinds. */
    public Map<Integer, Long> stacks() {
        return stacks;
    }
}
