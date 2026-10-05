package com.saksham.poker.engine.event;

import com.saksham.poker.engine.pot.Payout;
import java.util.List;

/** A pot was paid to its winner, or split between the players who tied for it. */
public final class PotAwarded extends GameEvent {

    private final int potIndex;
    private final long amount;
    private final List<Payout> payouts;

    public PotAwarded(int potIndex, long amount, List<Payout> payouts) {
        this.potIndex = potIndex;
        this.amount = amount;
        this.payouts = List.copyOf(payouts);
    }

    /** Which pot: 0 is the main pot. */
    public int potIndex() {
        return potIndex;
    }

    public long amount() {
        return amount;
    }

    public List<Payout> payouts() {
        return payouts;
    }

    @Override
    public String describe() {
        String pot = potIndex == 0 ? "the main pot" : "side pot " + potIndex;
        StringBuilder text = new StringBuilder();
        for (Payout payout : payouts) {
            text.append(text.length() == 0 ? "" : "; ")
                    .append("Seat ").append(payout.seat()).append(" wins ").append(payout.amount())
                    .append(" from ").append(pot);
        }
        return text.toString();
    }
}
