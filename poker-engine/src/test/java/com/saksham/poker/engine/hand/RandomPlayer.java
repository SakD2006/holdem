package com.saksham.poker.engine.hand;

import com.saksham.poker.common.action.AllIn;
import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks a random legal action. Used by the simulation and the console demo. */
final class RandomPlayer {

    private final Random random;

    RandomPlayer(Random random) {
        this.random = random;
    }

    PlayerAction choose(LegalActions legal, long stack) {
        List<PlayerAction> choices = new ArrayList<>();
        if (legal.canCheck()) {
            add(choices, new Check(), 5);
            add(choices, new Fold(), 1);
        } else {
            add(choices, new Call(), 5);
            add(choices, new Fold(), 3);
        }
        if (legal.canBet()) {
            add(choices, new Bet(amount(legal)), 3);
        }
        if (legal.canRaise()) {
            add(choices, new Raise(amount(legal)), 3);
        }
        if (legal.canBet() || legal.canRaise() || (legal.callAmount() > 0 && legal.callAmount() >= stack)) {
            add(choices, new AllIn(), 1);
        }
        return choices.get(random.nextInt(choices.size()));
    }

    private static void add(List<PlayerAction> choices, PlayerAction action, int weight) {
        for (int i = 0; i < weight; i++) {
            choices.add(action);
        }
    }

    /** Mostly small bets, sometimes anything up to all-in. */
    private long amount(LegalActions legal) {
        long min = legal.minRaiseTo();
        long max = legal.maxRaiseTo();
        int kind = random.nextInt(10);
        if (kind < 5 || max == min) {
            return min;
        }
        long range = kind < 8 ? Math.max(1, (max - min) / 4) : max - min;
        return min + (long) (random.nextDouble() * (range + 1));
    }
}
