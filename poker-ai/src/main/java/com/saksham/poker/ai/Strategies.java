package com.saksham.poker.ai;

import com.saksham.poker.common.protocol.dto.BotLevel;

/** The strategy that goes with each level a host can choose. */
public final class Strategies {

    /** Enough deals for a winning chance steady to a couple of percent, in a few milliseconds. */
    static final int MEDIUM_DEALS = 600;

    private Strategies() {
    }

    public static BotStrategy forLevel(BotLevel level) {
        return switch (level) {
            case EASY -> new RuleBasedStrategy(RuleBasedStrategy.Style.EASY);
            case MEDIUM -> new MonteCarloStrategy(MEDIUM_DEALS);
        };
    }
}
