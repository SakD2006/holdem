package com.saksham.poker.ai;

import java.util.Random;

/**
 * A bot that plays by rules of thumb: a table of starting hands before the flop, and afterwards a
 * judgement of how strong its hand is against the price of staying in. It does not look ahead or
 * study its opponents. How loosely and how boldly it plays is set by its {@link Style}.
 */
public final class RuleBasedStrategy extends BotStrategy {

    /**
     * A bot's temperament.
     *
     * @param loose how far it relaxes its starting-hand standards, in Chen points; 0 is disciplined
     * @param sticky how much worse than the right price it will still call, as a share of the pot
     * @param bluff how often it bets with nothing when a bluff is possible, 0 to 1
     * @param bold how often it raises a strong hand instead of just calling, 0 to 1
     */
    public record Style(int loose, double sticky, double bluff, double bold) {

        /** A beginner's habits: plays too many hands, calls too much, rarely raises. */
        public static final Style EASY = new Style(2, 0.08, 0.05, 0.45);
        /** Sound, straightforward play. */
        public static final Style SOLID = new Style(0, 0.0, 0.1, 0.7);
    }

    private final Style style;

    public RuleBasedStrategy(Style style) {
        this.style = style;
    }

    @Override
    public String name() {
        return "rules";
    }

    @Override
    protected Decision choose(Observation seen, Random random) {
        return seen.preflop() ? preflop(seen, random) : postflop(seen, random);
    }

    // =====================================================================================
    // Before the flop: play by the strength of the two cards and by position
    // =====================================================================================

    private Decision preflop(Observation seen, Random random) {
        int score = HandStrength.chen(seen.holeCards().get(0), seen.holeCards().get(1)) + style.loose();
        // Fewer players and a later seat both mean weaker hands are worth playing.
        int open = seen.latePosition() || seen.playersDealt() <= 3 ? 7 : 8;
        long stackInBlinds = (seen.stack() + seen.streetBet()) / seen.bigBlind();
        long highestBet = seen.streetBet() + seen.toCall();
        boolean raisedAlready = highestBet > seen.bigBlind();

        if (!raisedAlready) {
            if (score >= open) {
                long limpers = Math.max(0, seen.pot() / seen.bigBlind() - 1);
                return raiseOrShove(seen, (3 + limpers) * seen.bigBlind(), score);
            }
            if (seen.canCheck()) {
                return Decision.check();
            }
            // Completing the small blind with something that can improve. A loose player also limps in
            // from any seat, which a disciplined one does not: a hand is worth a raise or it is not.
            boolean cheap = seen.toCall() <= seen.bigBlind();
            boolean invested = seen.streetBet() > 0;
            boolean worthIt = invested ? score >= 5 : style.loose() > 0 && score >= 7;
            return cheap && worthIt ? Decision.call() : Decision.fold();
        }

        // Someone has raised. Only good hands continue, and only the best raise again.
        boolean bigPrice = seen.toCall() * 3 > seen.stack() + seen.streetBet();
        if (score >= 12 || (score >= 10 && stackInBlinds <= 15)) {
            if (bigPrice || seen.raisesThisStreet() >= 2) {
                return seen.canRaise() && score >= 14 ? Decision.raise(seen.maxRaiseTo()) : Decision.call();
            }
            return random.nextDouble() < style.bold() ? raiseOrShove(seen, highestBet * 3, score) : Decision.call();
        }
        if (bigPrice) {
            return Decision.fold();
        }
        if (score >= 8 && seen.toCall() <= 4 * seen.bigBlind()) {
            return Decision.call();
        }
        if (score >= 6 && seen.toCall() <= 2 * seen.bigBlind() && seen.playersInHand() >= 3) {
            return Decision.call(); // a cheap look with a hand that plays well against several players
        }
        return Decision.fold();
    }

    /** Raises to a size, or moves all-in when the raise would commit most of the stack anyway. */
    private static Decision raiseOrShove(Observation seen, long to, int score) {
        long everything = seen.maxRaiseTo();
        if (to * 2 >= everything) {
            // Too short to raise and fold: commit with a real hand, otherwise just call or check.
            return score >= 10 ? Decision.raise(everything) : Decision.call();
        }
        return Decision.raise(to);
    }

    // =====================================================================================
    // After the flop: bet good hands, call when the price is right, fold the rest
    // =====================================================================================

    private Decision postflop(Observation seen, Random random) {
        double made = HandStrength.made(seen.holeCards(), seen.board());
        int outs = HandStrength.outs(seen.holeCards(), seen.board());
        double improve = HandStrength.drawChance(outs, 5 - seen.board().size());
        // Each extra opponent makes it likelier that somebody holds better.
        double strength = made - 0.04 * (seen.opponents() - 1);
        double winChance = strength + (1 - strength) * improve;

        if (seen.toCall() == 0) {
            if (strength >= 0.58) {
                return Decision.bet(share(seen, strength >= 0.8 ? 0.75 : 0.6));
            }
            if (outs >= 8 && random.nextDouble() < 0.4) {
                return Decision.bet(share(seen, 0.5)); // betting a draw: it can win now or get there later
            }
            if (seen.opponents() <= 2 && random.nextDouble() < style.bluff()) {
                return Decision.bet(share(seen, 0.5));
            }
            return Decision.check();
        }

        double price = seen.potOdds();
        boolean bigPrice = seen.toCall() * 2 > seen.stack();
        if (strength >= 0.84) {
            if (seen.canRaise() && random.nextDouble() < style.bold()) {
                return Decision.raise(seen.streetBet() + seen.toCall() + share(seen, 0.9));
            }
            return Decision.call();
        }
        if (strength >= 0.6) {
            // A good one-pair hand calls a normal bet but gives up to a very large one.
            return bigPrice && strength < 0.68 ? Decision.fold() : Decision.call();
        }
        if (winChance + style.sticky() >= price + 0.1 && !bigPrice) {
            return Decision.call();
        }
        if (improve >= price && !seen.river()) {
            return Decision.call(); // the draw alone is worth the price
        }
        return Decision.fold();
    }

    /** A bet of this share of the pot, counting the call if there is one. */
    private static long share(Observation seen, double ofPot) {
        return Math.max(seen.bigBlind(), Math.round((seen.pot() + seen.toCall()) * ofPot));
    }
}
