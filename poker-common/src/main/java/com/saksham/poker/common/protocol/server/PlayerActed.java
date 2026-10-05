package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.protocol.ServerMessage;

/** A player folded, checked, called, bet or raised. */
@JsonTypeName("PLAYER_ACTED")
public final class PlayerActed extends ServerMessage {

    private final int seat;
    private final ActionType action;
    private final long amount;
    private final long streetBet;
    private final long stack;
    private final boolean allIn;

    @JsonCreator
    public PlayerActed(
            @JsonProperty("seat") int seat,
            @JsonProperty("action") ActionType action,
            @JsonProperty("amount") long amount,
            @JsonProperty("streetBet") long streetBet,
            @JsonProperty("stack") long stack,
            @JsonProperty("allIn") boolean allIn) {
        this.seat = seat;
        this.action = action;
        this.amount = amount;
        this.streetBet = streetBet;
        this.stack = stack;
        this.allIn = allIn;
    }

    public int seat() {
        return seat;
    }

    /** FOLD, CHECK, CALL, BET or RAISE; an all-in arrives as what it was. */
    public ActionType action() {
        return action;
    }

    /** Chips this action added. */
    public long amount() {
        return amount;
    }

    /** The player's total bet on this street afterwards. */
    public long streetBet() {
        return streetBet;
    }

    /** The player's chips behind afterwards. */
    public long stack() {
        return stack;
    }

    public boolean allIn() {
        return allIn;
    }
}
