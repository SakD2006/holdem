package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An opponent who changes style to punish a bot that has learned its habits: for a stretch it folds
 * to every bet, inviting bluffs, then for a stretch it calls everything. A bot that adapts too far
 * or too trustingly loses to this; one that adapts within limits does not.
 */
public final class Chameleon extends BotStrategy {

    /** Roughly how many decisions each style lasts. */
    private static final int STRETCH = 1_500;

    private final BotStrategy folder = new OverFolder();
    private final BotStrategy caller = new AlwaysCall();
    private final AtomicInteger decisions = new AtomicInteger();

    @Override
    public String name() {
        return "chameleon";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        boolean folding = decisions.getAndIncrement() / STRETCH % 2 == 0;
        return (folding ? folder : caller).decide(seen, random);
    }
}
