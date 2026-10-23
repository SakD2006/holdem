package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import java.util.Random;

/**
 * An opponent who bets and raises most of the time whatever the cards. Hard to play against without
 * a read, easy with one: its bets mean nothing, so the answer is to call it down with decent hands.
 */
public final class Maniac extends BotStrategy {

    @Override
    public String name() {
        return "maniac";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        double roll = random.nextDouble();
        if (roll < 0.65) {
            long to = seen.highestBet() + Math.max(seen.bigBlind(), Math.round((seen.pot() + seen.toCall()) * 0.8));
            return Decision.raise(to);
        }
        return roll < 0.92 ? Decision.call() : Decision.fold();
    }
}
