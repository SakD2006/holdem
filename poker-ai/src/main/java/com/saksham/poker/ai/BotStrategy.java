package com.saksham.poker.ai;

import java.util.Random;

/**
 * How a computer player chooses what to do. Each kind of bot is a subclass; whatever runs the bot
 * holds only this type and never needs to know which kind it has.
 */
public abstract class BotStrategy {

    /** A short name for logs and results tables, such as "rules". */
    public abstract String name();

    /**
     * Chooses an action. It may ask for anything: the caller passes the answer through
     * {@link Decision#madeLegal} before it reaches the table.
     *
     * @param seen what the bot knows
     * @param random the only source of chance a strategy may use, so a game can be replayed exactly
     */
    protected abstract Decision choose(Observation seen, Random random);

    /** Chooses an action the rules allow. */
    public final Decision decide(Observation seen, Random random) {
        return choose(seen, random).madeLegal(seen);
    }
}
