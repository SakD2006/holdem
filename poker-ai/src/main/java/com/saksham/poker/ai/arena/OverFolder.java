package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.HandStrength;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.RuleBasedStrategy;
import java.util.Random;

/**
 * An opponent who plays sensibly before the flop and then gives up to any bet without a strong
 * hand. Common among cautious beginners. The answer is to bet at it often.
 */
public final class OverFolder extends BotStrategy {

    private final BotStrategy sensible = new RuleBasedStrategy(RuleBasedStrategy.Style.SOLID);

    @Override
    public String name() {
        return "overfolder";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        if (seen.preflop()) {
            return sensible.decide(seen, random);
        }
        double made = HandStrength.made(seen.holeCards(), seen.board());
        if (seen.toCall() > 0) {
            return made >= 0.62 ? Decision.call() : Decision.fold();
        }
        return made >= 0.7 ? Decision.bet(Math.round(seen.pot() * 0.5)) : Decision.check();
    }
}
