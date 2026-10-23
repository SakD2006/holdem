package com.saksham.poker.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * A bot's memory of the players it has met: for each one, counts of what they did, kept from hand to
 * hand for as long as the bot sits at the table. Everything recorded here was visible to the whole
 * table. Cards are remembered only if they were turned over at a showdown.
 *
 * <p>Not thread-safe: written and read by whoever owns the bot's {@link TableObserver}.
 */
public final class OpponentBook {

    /**
     * How strongly the typical player is assumed before any evidence: each figure starts as if this
     * many typical decisions had already been seen. Common events earn trust quickly; a shown-down
     * bluff is rare, so it takes fewer of them to count.
     */
    private static final double PREFLOP_WEIGHT = 12;
    private static final double BET_WEIGHT = 8;
    private static final double SHOWDOWN_WEIGHT = 4;

    /** What has been counted for one player. */
    static final class Page {
        int hands;
        int enteredPot;
        int raisedPreflop;
        int facedPreflopRaise;
        int foldedToPreflopRaise;
        int facedBet;
        int foldedToBet;
        int postflopActions;
        int postflopBets;
        int shownAfterBetting;
        int shownBluffs;
    }

    private final Map<Long, Page> pages = new HashMap<>();

    Page page(long playerId) {
        return pages.computeIfAbsent(playerId, id -> new Page());
    }

    /** How many players the bot has notes on. */
    public int players() {
        return pages.size();
    }

    /** What is known of a player's habits; the typical player if they have never been seen. */
    public Tendencies tendencies(long playerId) {
        Page page = pages.get(playerId);
        if (page == null) {
            return Tendencies.TYPICAL;
        }
        Tendencies typical = Tendencies.TYPICAL;
        return new Tendencies(
                blend(typical.entersPot(), PREFLOP_WEIGHT, page.enteredPot, page.hands),
                blend(typical.raisesPreflop(), PREFLOP_WEIGHT, page.raisedPreflop, page.hands),
                blend(typical.foldsToRaisePreflop(), PREFLOP_WEIGHT, page.foldedToPreflopRaise, page.facedPreflopRaise),
                blend(typical.foldsToBet(), BET_WEIGHT, page.foldedToBet, page.facedBet),
                blend(typical.aggression(), BET_WEIGHT, page.postflopBets, page.postflopActions),
                blend(typical.bluffs(), SHOWDOWN_WEIGHT, page.shownBluffs, page.shownAfterBetting),
                page.hands);
    }

    /**
     * A share that starts at the typical value and moves toward what was actually seen as the number
     * of observations grows.
     */
    private static double blend(double typical, double weight, int times, int outOf) {
        return (typical * weight + times) / (weight + outOf);
    }
}
