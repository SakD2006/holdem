package com.saksham.poker.ai;

/**
 * How one opponent tends to play, as far as a bot has been able to tell. Every figure is a share
 * from 0 to 1 and starts at what a typical player does; it moves away from that only as hands are
 * seen, so a handful of hands never convinces a bot of anything.
 *
 * @param entersPot how often they put chips in before the flop by choice
 * @param raisesPreflop how often they raise before the flop
 * @param foldsToRaisePreflop how often they give up before the flop when someone has raised
 * @param foldsToBet how often they fold after the flop when bet into
 * @param aggression of their actions after the flop, the share that are bets or raises
 * @param bluffs when they bet after the flop and the hand is shown, how often it was weak
 * @param handsSeen how many hands the bot has watched them play
 */
public record Tendencies(double entersPot, double raisesPreflop, double foldsToRaisePreflop, double foldsToBet,
        double aggression, double bluffs, int handsSeen) {

    /** A typical player at a casual table. Used for anyone not yet seen. */
    public static final Tendencies TYPICAL = new Tendencies(0.3, 0.18, 0.6, 0.45, 0.35, 0.3, 0);
}
