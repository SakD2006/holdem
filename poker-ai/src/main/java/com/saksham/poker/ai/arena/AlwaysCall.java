package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import java.util.Random;

/**
 * The simplest opponent there is: never folds, never raises. A "calling station". Any bot worth the
 * name must beat it soundly, which makes it a useful yardstick.
 */
public final class AlwaysCall extends BotStrategy {

    @Override
    public String name() {
        return "caller";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        return Decision.call();
    }
}
