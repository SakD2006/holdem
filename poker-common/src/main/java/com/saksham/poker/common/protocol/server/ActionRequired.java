package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;

/** It is a player's turn. Everyone receives it, to show whose turn it is and the timer. */
@JsonTypeName("ACTION_REQUIRED")
public final class ActionRequired extends ServerMessage {

    private final int seat;
    private final long turnId;
    private final boolean canCheck;
    private final long callAmount;
    private final boolean canBet;
    private final boolean canRaise;
    private final long minRaiseTo;
    private final long maxRaiseTo;
    private final long deadlineEpochMs;

    @JsonCreator
    public ActionRequired(
            @JsonProperty("seat") int seat,
            @JsonProperty("turnId") long turnId,
            @JsonProperty("canCheck") boolean canCheck,
            @JsonProperty("callAmount") long callAmount,
            @JsonProperty("canBet") boolean canBet,
            @JsonProperty("canRaise") boolean canRaise,
            @JsonProperty("minRaiseTo") long minRaiseTo,
            @JsonProperty("maxRaiseTo") long maxRaiseTo,
            @JsonProperty("deadlineEpochMs") long deadlineEpochMs) {
        this.seat = seat;
        this.turnId = turnId;
        this.canCheck = canCheck;
        this.callAmount = callAmount;
        this.canBet = canBet;
        this.canRaise = canRaise;
        this.minRaiseTo = minRaiseTo;
        this.maxRaiseTo = maxRaiseTo;
        this.deadlineEpochMs = deadlineEpochMs;
    }

    public int seat() {
        return seat;
    }

    /** Send this back in ACTION. */
    public long turnId() {
        return turnId;
    }

    public boolean canCheck() {
        return canCheck;
    }

    /** Chips a call would add; 0 when there is nothing to call. */
    public long callAmount() {
        return callAmount;
    }

    /** True when the player may open the betting with BET. */
    public boolean canBet() {
        return canBet;
    }

    /** True when the player may RAISE a bet. */
    public boolean canRaise() {
        return canRaise;
    }

    /** Smallest bet, or smallest total to raise to; 0 if neither is allowed. */
    public long minRaiseTo() {
        return minRaiseTo;
    }

    /** Largest bet or total to raise to, which is all-in; 0 if neither is allowed. */
    public long maxRaiseTo() {
        return maxRaiseTo;
    }

    /** When the turn times out, in milliseconds since 1970 UTC. */
    public long deadlineEpochMs() {
        return deadlineEpochMs;
    }
}
