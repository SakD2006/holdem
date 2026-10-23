package com.saksham.poker.ai;

/**
 * What a bot has seen an opponent do in the current hand. Everything here was visible to the whole
 * table.
 *
 * @param seat their seat
 * @param stack their chips not yet bet
 * @param streetBet what they have put in on this street
 * @param preflopRaises how many times they raised before the flop
 * @param calledPreflopRaise whether they called someone else's raise before the flop
 * @param betThisStreet whether they have bet or raised on the current street
 * @param postflopBets how many times they have bet or raised since the flop, on any street
 * @param habits how this player has tended to play in earlier hands
 */
public record Opponent(int seat, long stack, long streetBet, int preflopRaises, boolean calledPreflopRaise,
        boolean betThisStreet, int postflopBets, Tendencies habits) {

    /** An opponent seen in this hand but with no history. */
    public Opponent(int seat, long stack, long streetBet, int preflopRaises, boolean calledPreflopRaise,
            boolean betThisStreet, int postflopBets) {
        this(seat, stack, streetBet, preflopRaises, calledPreflopRaise, betThisStreet, postflopBets,
                Tendencies.TYPICAL);
    }

    /** An opponent about whom nothing is known. */
    public static Opponent unknown() {
        return new Opponent(-1, 0, 0, 0, false, false, 0);
    }
}
