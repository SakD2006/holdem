package com.saksham.poker.engine.event;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.engine.hand.Street;

/** A player folded, checked, called, bet or raised. An all-in appears as the bet, call or raise it was. */
public final class PlayerActed extends GameEvent {

    private final int seat;
    private final Street street;
    private final ActionType type;
    private final long amount;
    private final long streetBet;
    private final long stack;
    private final boolean allIn;

    public PlayerActed(int seat, Street street, ActionType type, long amount, long streetBet,
            long stack, boolean allIn) {
        this.seat = seat;
        this.street = street;
        this.type = type;
        this.amount = amount;
        this.streetBet = streetBet;
        this.stack = stack;
        this.allIn = allIn;
    }

    public int seat() {
        return seat;
    }

    public Street street() {
        return street;
    }

    /** FOLD, CHECK, CALL, BET or RAISE; never ALL_IN. */
    public ActionType type() {
        return type;
    }

    /** Chips this action added to the player's bet. */
    public long amount() {
        return amount;
    }

    /** The player's total bet on this street after the action. */
    public long streetBet() {
        return streetBet;
    }

    /** The player's chips behind after the action. */
    public long stack() {
        return stack;
    }

    public boolean allIn() {
        return allIn;
    }

    @Override
    public String describe() {
        String text;
        switch (type) {
            case FOLD:
                text = "folds";
                break;
            case CHECK:
                text = "checks";
                break;
            case CALL:
                text = "calls " + amount;
                break;
            case BET:
                text = "bets " + amount;
                break;
            default:
                text = "raises to " + streetBet;
                break;
        }
        return "Seat " + seat + " " + text + (allIn ? " and is all-in" : "");
    }
}
