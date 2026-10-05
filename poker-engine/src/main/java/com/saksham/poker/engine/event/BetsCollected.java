package com.saksham.poker.engine.event;

import com.saksham.poker.engine.pot.Pot;
import java.util.List;

/** The bets of a finished street were moved into the pots. */
public final class BetsCollected extends GameEvent {

    private final List<Pot> pots;

    public BetsCollected(List<Pot> pots) {
        this.pots = List.copyOf(pots);
    }

    /** Every pot after collecting: the main pot first, then side pots. */
    public List<Pot> pots() {
        return pots;
    }

    @Override
    public String describe() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < pots.size(); i++) {
            text.append(i == 0 ? "Main pot " : ", side pot ").append(pots.get(i).amount());
        }
        return text.toString();
    }
}
