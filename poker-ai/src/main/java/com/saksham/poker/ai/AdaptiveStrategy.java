package com.saksham.poker.ai;

import com.saksham.poker.ai.sim.Range;

/**
 * A bot that studies its opponents. It plays as {@link MonteCarloStrategy} does against a player it
 * knows nothing about, and shifts as its notes on a player fill in: it bluffs the ones who fold,
 * bets bigger and thinner for value against the ones who call, and stops believing the bets of the
 * ones who have been caught bluffing.
 *
 * <p>The notes arrive in each {@link Observation} as {@link Tendencies}, which start at a typical
 * player and move only as evidence builds, and every adjustment here is capped. A new opponent is
 * therefore played soundly, and an opponent who changes style cannot lead the bot far astray.
 */
public final class AdaptiveStrategy extends MonteCarloStrategy {

    private static final Tendencies TYPICAL = Tendencies.TYPICAL;

    public AdaptiveStrategy(int deals) {
        super(deals);
    }

    @Override
    public String name() {
        return "adaptive";
    }

    // ---- what they might hold

    /**
     * Fits the guess at an opponent's hands to that opponent. Someone who raises one hand in three
     * has a far wider range when they raise than someone who raises one in ten; someone who has
     * been shown bluffing is less likely to have hit the board when they bet.
     */
    @Override
    protected Range rangeFor(Opponent opponent) {
        Range usual = super.rangeFor(opponent);
        Tendencies habits = opponent.habits();
        double loose = opponent.preflopRaises() >= 1
                ? habits.raisesPreflop() / TYPICAL.raisesPreflop()
                : habits.entersPot() / TYPICAL.entersPot();
        double width = usual.width() * clamp(loose, 0.4, 2.5);
        double honesty = 1 + (TYPICAL.bluffs() - habits.bluffs()) * 1.5
                + (TYPICAL.aggression() - habits.aggression()) * 0.8;
        return new Range(width, usual.connected() * clamp(honesty, 0.35, 1.3));
    }

    // ---- bluffing at the right time

    /**
     * Bluffs when the arithmetic says it pays. A bet of 0.7 of the pot shows a profit if everyone
     * folds more than about two times in five; the bot's notes say how often each opponent folds.
     * It bluffs more as that margin grows, more on later streets where fewer bets can follow, and
     * not at all into three or more players.
     */
    @Override
    protected double bluffChance(Observation seen, double chance) {
        if (seen.opponentCount() > 2 || chance >= 0.35) {
            return super.bluffChance(seen, chance);
        }
        double everyoneFolds = everyoneFolds(seen);
        double needed = BLUFF_SIZE / (1 + BLUFF_SIZE);
        double edge = everyoneFolds - needed - 0.04;
        if (edge <= 0) {
            return seen.opponentCount() == 1 && everyoneFolds > 0.3 ? super.bluffChance(seen, chance) : 0;
        }
        double street = seen.river() ? 1.0 : seen.board().size() == 4 ? 0.8 : 0.55;
        return clamp((0.15 + edge * 2.5) * street, 0, 0.65);
    }

    @Override
    protected double continuationChance(Observation seen) {
        return clamp(0.55 + (everyoneFolds(seen) - TYPICAL.foldsToBet()) * 1.4, 0.15, 0.9);
    }

    // ---- betting for value

    /** Against players who call too much, weaker hands are worth betting; against folders, fewer are. */
    @Override
    protected double valueBar(Observation seen) {
        double calling = TYPICAL.foldsToBet() - averageFoldsToBet(seen);
        return super.valueBar(seen) - clamp(calling * 0.3, -0.06, 0.08);
    }

    /** Charge the callers more; ask the folders for less, so that they pay something. */
    @Override
    protected double valueSize(Observation seen, double chance) {
        double usual = super.valueSize(seen, chance);
        double calling = TYPICAL.foldsToBet() - averageFoldsToBet(seen);
        return clamp(usual + calling * 1.2, 0.4, 1.1);
    }

    // ---- calling

    /**
     * Calls lighter against a bettor who bets a lot and has been caught bluffing, and tighter
     * against one whose bets have always been real.
     */
    @Override
    protected double callMargin(Observation seen) {
        Tendencies bettor = TYPICAL;
        for (Opponent opponent : seen.opponents()) {
            if (opponent.betThisStreet()) {
                bettor = opponent.habits();
            }
        }
        double trust = (TYPICAL.bluffs() - bettor.bluffs()) * 0.2 + (TYPICAL.aggression() - bettor.aggression()) * 0.15;
        return super.callMargin(seen) + clamp(trust, -0.07, 0.08);
    }

    // ---- before the flop

    /** Opens more hands when the players still in tend to give up to a raise. */
    @Override
    protected double openShare(Observation seen) {
        double folds = 0;
        for (Opponent opponent : seen.opponents()) {
            folds += opponent.habits().foldsToRaisePreflop();
        }
        folds /= seen.opponents().size();
        return super.openShare(seen) * clamp(1 + (folds - TYPICAL.foldsToRaisePreflop()) * 1.5, 0.8, 1.5);
    }

    // ---- helpers

    /** The chance that every opponent still in folds to a bet, going by each one's record. */
    private static double everyoneFolds(Observation seen) {
        double all = 1;
        for (Opponent opponent : seen.opponents()) {
            all *= opponent.habits().foldsToBet();
        }
        return all;
    }

    private static double averageFoldsToBet(Observation seen) {
        double sum = 0;
        for (Opponent opponent : seen.opponents()) {
            sum += opponent.habits().foldsToBet();
        }
        return sum / seen.opponents().size();
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
