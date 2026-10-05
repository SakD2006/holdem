package com.saksham.poker.common.protocol.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.protocol.ClientMessage;

/** Act on your turn. */
@JsonTypeName("ACTION")
public final class SubmitAction extends ClientMessage {

    private final long turnId;
    private final ActionType action;
    private final long amount;

    @JsonCreator
    public SubmitAction(
            @JsonProperty("turnId") long turnId,
            @JsonProperty("action") ActionType action,
            @JsonProperty("amount") long amount) {
        this.turnId = turnId;
        this.action = action;
        this.amount = amount;
    }

    /** The turn being answered, copied from ACTION_REQUIRED. An answer to an old turn is ignored. */
    public long turnId() {
        return turnId;
    }

    /** FOLD, CHECK, CALL, BET, RAISE or ALL_IN. */
    public ActionType action() {
        return action;
    }

    /** The bet size for BET, the total to raise to for RAISE, and 0 otherwise. */
    public long amount() {
        return amount;
    }
}
