package com.saksham.poker.ai.blueprint;

import java.util.Arrays;

/**
 * The betting of one hand in the blueprint's game (`trainer/src/holdem/game.rs`): heads-up, both
 * players starting with 100 big blinds (10,000 chips, blinds 50 and 100), player 0 the small blind
 * and button. The first bet or raise of a street may be half the pot, the pot or twice the pot;
 * the next two raises are pot-sized; all-in is always allowed; after three raises a player may only
 * fold, call or move all-in. "Pot" means the pot after calling.
 *
 * <p>Immutable: {@link #apply} returns a new hand. Cards are not part of it; the information-set
 * key takes the player's bucket as an argument.
 */
public final class AbstractHand {

    public static final int STACK = 10_000;
    public static final int SMALL_BLIND = 50;
    public static final int BIG_BLIND = 100;

    public static final int FOLD = 0;
    public static final int CALL = 1;
    public static final int ALL_IN = 2;
    /** {@code RAISE + i} raises by {@code SIZES[level][i]} of the pot. */
    public static final int RAISE = 3;

    private static final double[][] SIZES = {{0.5, 1.0, 2.0}, {1.0}, {1.0}};
    public static final int SHOWDOWN = 4;
    private static final int STREET_MARK = 255;

    private int street;
    private final long[] committed = new long[2];
    private final long[] streetBet = new long[2];
    private int toAct;
    private int raises;
    private long lastRaise;
    private final boolean[] acted = new boolean[2];
    private int folded = -1;
    private long pathHash;

    private AbstractHand() {
    }

    /** A new hand with the blinds posted. */
    public static AbstractHand start() {
        AbstractHand h = new AbstractHand();
        h.committed[0] = SMALL_BLIND;
        h.committed[1] = BIG_BLIND;
        h.streetBet[0] = SMALL_BLIND;
        h.streetBet[1] = BIG_BLIND;
        h.lastRaise = BIG_BLIND;
        h.pathHash = 0xCBF2_9CE4_8422_2325L;
        return h;
    }

    private AbstractHand copy() {
        AbstractHand h = new AbstractHand();
        h.street = street;
        System.arraycopy(committed, 0, h.committed, 0, 2);
        System.arraycopy(streetBet, 0, h.streetBet, 0, 2);
        h.toAct = toAct;
        h.raises = raises;
        h.lastRaise = lastRaise;
        h.acted[0] = acted[0];
        h.acted[1] = acted[1];
        h.folded = folded;
        h.pathHash = pathHash;
        return h;
    }

    /** 0 preflop, 1 flop, 2 turn, 3 river, {@link #SHOWDOWN} at the end without a fold. */
    public int street() {
        return street;
    }

    /** True when nobody is to act: a fold or a showdown. */
    public boolean terminal() {
        return folded >= 0 || street == SHOWDOWN;
    }

    /** Whose turn it is (0 small blind, 1 big blind). */
    public int toAct() {
        return toAct;
    }

    /** Who folded, or -1. */
    public int folder() {
        return folded;
    }

    public long committed(int player) {
        return committed[player];
    }

    public long streetBet(int player) {
        return streetBet[player];
    }

    public long pot() {
        return committed[0] + committed[1];
    }

    public long toCall(int player) {
        return streetBet[1 - player] - streetBet[player];
    }

    private long behind(int player) {
        return STACK - committed[player];
    }

    /**
     * What a raise action makes the acting player's total bet on this street, or -1 if that size
     * is not allowed here.
     */
    public long raiseTo(int action) {
        int me = toAct;
        long allIn = streetBet[me] + behind(me);
        if (action == ALL_IN) {
            return allIn;
        }
        if (raises >= SIZES.length || action - RAISE >= SIZES[raises].length) {
            return -1;
        }
        double fraction = SIZES[raises][action - RAISE];
        long increase = Math.max(Math.max(Math.round(fraction * (double) (pot() + toCall(me))), lastRaise), BIG_BLIND);
        long to = streetBet[1 - me] + increase;
        return to < allIn ? to : -1;
    }

    /** The legal actions, in the order the blueprint stores its probabilities. */
    public int[] legalActions() {
        int me = toAct;
        int[] out = new int[8];
        int n = 0;
        if (toCall(me) > 0) {
            out[n++] = FOLD;
        }
        out[n++] = CALL;
        if (behind(me) > toCall(me) && behind(1 - me) > 0) {
            long previous = 0;
            if (raises < SIZES.length) {
                for (int i = 0; i < SIZES[raises].length; i++) {
                    long to = raiseTo(RAISE + i);
                    if (to >= 0 && to != previous) {
                        out[n++] = RAISE + i;
                        previous = to;
                    }
                }
            }
            out[n++] = ALL_IN;
        }
        return Arrays.copyOf(out, n);
    }

    /** The hand after the player to act takes {@code action}, which must be legal. */
    public AbstractHand apply(int action) {
        AbstractHand next = copy();
        int me = toAct;
        switch (action) {
            case FOLD -> {
                next.folded = me;
                next.record(FOLD);
            }
            case CALL -> {
                long amount = Math.min(toCall(me), behind(me));
                next.committed[me] += amount;
                next.streetBet[me] += amount;
                next.acted[me] = true;
                next.record(CALL);
                boolean matched = next.streetBet[0] == next.streetBet[1] || next.behind(me) == 0;
                if (next.acted[1 - me] && matched) {
                    next.nextStreet();
                } else {
                    next.toAct = 1 - me;
                }
            }
            default -> {
                long to = raiseTo(action);
                if (to < 0) {
                    throw new IllegalArgumentException("Raise " + action + " is not allowed here.");
                }
                long increase = to - streetBet[1 - me];
                next.lastRaise = Math.max(next.lastRaise, increase);
                next.committed[me] += to - streetBet[me];
                next.streetBet[me] = to;
                next.raises++;
                next.acted[0] = false;
                next.acted[1] = false;
                next.acted[me] = true;
                next.toAct = 1 - me;
                next.record(action);
            }
        }
        return next;
    }

    private void record(int code) {
        pathHash = (pathHash ^ (code + 1L)) * 0x0000_0100_0000_01B3L;
    }

    private void nextStreet() {
        boolean someoneAllIn = behind(0) == 0 || behind(1) == 0;
        street = someoneAllIn ? SHOWDOWN : street + 1;
        streetBet[0] = 0;
        streetBet[1] = 0;
        acted[0] = false;
        acted[1] = false;
        raises = 0;
        lastRaise = BIG_BLIND;
        toAct = 1;
        if (street != SHOWDOWN) {
            record(STREET_MARK);
        }
    }

    /** The information set of whoever holds a hand in {@code bucket} at this point. */
    public long key(int bucket) {
        long z = pathHash ^ ((long) bucket << 48) ^ ((long) street << 60);
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return z ^ (z >>> 31);
    }

    /** The same point for both players: the key a river subgame uses for its decisions. */
    long nodeKey() {
        return key(0);
    }
}
