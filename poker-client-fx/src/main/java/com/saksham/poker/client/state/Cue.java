package com.saksham.poker.client.state;

/** A moment at the table worth marking, with a sound say. {@link RoomState} announces them as they happen. */
public enum Cue {
    /** A new hand: hole cards go out. */
    DEAL,
    /** Community cards are dealt: the flop, the turn or the river. */
    BOARD,
    /** Somebody checked. */
    CHECK,
    /** Somebody put chips in: a call, a bet or a raise. */
    CHIPS,
    /** Somebody folded. */
    FOLD,
    /** It is this player's turn to act. */
    YOUR_TURN,
    /** The hand is over and this player won chips. */
    YOU_WIN
}
