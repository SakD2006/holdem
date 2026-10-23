package com.saksham.poker.ai.arena;

import java.util.List;

/**
 * What happened in the arena: for each bot, how much it won.
 *
 * @param names the bots, in the order they were entered
 * @param hands how many hands were played
 * @param won each bot's total winnings, in big blinds; these add up to zero
 * @param wonSquared the sum of the squares of each bot's result per hand, for the margin of error
 * @param illegal how many actions the engine refused; it should be 0
 */
public record ArenaResult(List<String> names, int hands, double[] won, double[] wonSquared, int illegal) {

    static ArenaResult empty(List<String> names) {
        return new ArenaResult(names, 0, new double[names.size()], new double[names.size()], 0);
    }

    ArenaResult plus(ArenaResult other) {
        double[] total = won.clone();
        double[] totalSquared = wonSquared.clone();
        for (int i = 0; i < total.length; i++) {
            total[i] += other.won[i];
            totalSquared[i] += other.wonSquared[i];
        }
        return new ArenaResult(names, hands + other.hands, total, totalSquared, illegal + other.illegal);
    }

    /** Big blinds won per 100 hands: the usual measure of how good a player is. */
    public double perHundred(int bot) {
        return hands == 0 ? 0 : won[bot] / hands * 100;
    }

    /**
     * How far the true figure might be from {@link #perHundred}, 19 times in 20. Poker is a game of
     * luck in the short run: a result smaller than its margin could have gone either way.
     */
    public double margin(int bot) {
        if (hands < 2) {
            return Double.POSITIVE_INFINITY;
        }
        double mean = won[bot] / hands;
        double variance = Math.max(0, wonSquared[bot] / hands - mean * mean);
        return 1.96 * Math.sqrt(variance / hands) * 100;
    }

    /** A table for a person to read, best bot first. */
    public String table() {
        Integer[] order = new Integer[names.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(perHundred(b), perHundred(a)));
        StringBuilder text = new StringBuilder();
        text.append(String.format("%,d hands%n", hands));
        text.append(String.format("%-14s %14s %10s%n", "bot", "bb per 100", "+/-"));
        for (int bot : order) {
            text.append(String.format("%-14s %+14.1f %10.1f%n", names.get(bot), perHundred(bot), margin(bot)));
        }
        if (illegal > 0) {
            text.append(String.format("WARNING: %d illegal action(s) were refused and folded.%n", illegal));
        }
        return text.toString();
    }
}
