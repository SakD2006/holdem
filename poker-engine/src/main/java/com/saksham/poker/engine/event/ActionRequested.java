package com.saksham.poker.engine.event;

import com.saksham.poker.engine.rules.LegalActions;

/** It is a player's turn to act. */
public final class ActionRequested extends GameEvent {

    private final int seat;
    private final LegalActions legal;

    public ActionRequested(int seat, LegalActions legal) {
        this.seat = seat;
        this.legal = legal;
    }

    public int seat() {
        return seat;
    }

    public LegalActions legal() {
        return legal;
    }

    @Override
    public String describe() {
        StringBuilder text = new StringBuilder("Seat " + seat + " to act:");
        text.append(legal.canCheck() ? " check" : " call " + legal.callAmount());
        if (legal.canBet() || legal.canRaise()) {
            text.append(legal.canBet() ? ", bet " : ", raise to ")
                    .append(legal.minRaiseTo()).append("-").append(legal.maxRaiseTo());
        }
        return text.append(", or fold").toString();
    }
}
