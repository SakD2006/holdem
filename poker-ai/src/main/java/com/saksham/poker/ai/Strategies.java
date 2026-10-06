package com.saksham.poker.ai;

import com.saksham.poker.common.protocol.dto.BotLevel;

/** The strategy that goes with each level a host can choose. */
public final class Strategies {

    private Strategies() {
    }

    public static BotStrategy forLevel(BotLevel level) {
        return switch (level) {
            case EASY -> new RuleBasedStrategy(RuleBasedStrategy.Style.EASY);
        };
    }
}
