package com.saksham.poker.ai;

import com.saksham.poker.ai.blueprint.AdaptiveBlueprintStrategy;
import com.saksham.poker.ai.blueprint.BlueprintTable;
import com.saksham.poker.ai.blueprint.CardBuckets;
import com.saksham.poker.common.protocol.dto.BotLevel;

/** The strategy that goes with each level a host can choose. */
public final class Strategies {

    /** Enough deals for a winning chance steady to a couple of percent, in a few milliseconds. */
    static final int MEDIUM_DEALS = 600;
    static final int HARD_DEALS = 800;

    private Strategies() {
    }

    /**
     * The strategy for a level.
     *
     * @throws IllegalArgumentException for {@link BotLevel#EXPERT}, which needs trained files: see
     *     {@link #expert}
     */
    public static BotStrategy forLevel(BotLevel level) {
        return switch (level) {
            case EASY -> new RuleBasedStrategy(RuleBasedStrategy.Style.EASY);
            case MEDIUM -> new MonteCarloStrategy(MEDIUM_DEALS);
            case HARD -> new AdaptiveStrategy(HARD_DEALS);
            case EXPERT -> throw new IllegalArgumentException("The Expert level plays a trained blueprint; use expert().");
        };
    }

    /**
     * The Expert level: a trained blueprint, heads-up, with the river re-solved and its play bent,
     * within bounds, toward what each opponent's habits give away; the Hard level's play when more
     * than two players are dealt in. Stateless, so one can serve every Expert bot (what it knows of
     * an opponent lives in each bot's own {@link TableObserver}).
     *
     * @param resolveIterations iterations of river re-solving, or 0 to play the river buckets
     */
    public static BotStrategy expert(CardBuckets buckets, BlueprintTable blueprint, int resolveIterations) {
        return new AdaptiveBlueprintStrategy(buckets, blueprint, new AdaptiveStrategy(HARD_DEALS), resolveIterations);
    }
}
