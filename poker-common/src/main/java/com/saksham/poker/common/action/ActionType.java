package com.saksham.poker.common.action;

/** The kinds of action a player can take on their turn. */
public enum ActionType {
    FOLD,
    CHECK,
    CALL,
    BET,
    RAISE,
    /** A convenience: the engine turns it into the matching BET, CALL or RAISE. */
    ALL_IN
}
