package com.saksham.poker.common.action;

/**
 * What a player chooses to do on their turn. Subclasses are immutable; two actions are equal when
 * they have the same type and amount.
 */
public abstract class PlayerAction {

    private final long amount;

    protected PlayerAction(long amount) {
        this.amount = amount;
    }

    public abstract ActionType type();

    /**
     * The chips named by the action: the bet size for a {@link Bet}, the total to raise to for a
     * {@link Raise}, and 0 for every other action.
     */
    public long amount() {
        return amount;
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof PlayerAction that && type() == that.type() && amount == that.amount;
    }

    @Override
    public final int hashCode() {
        return 31 * type().hashCode() + Long.hashCode(amount);
    }

    @Override
    public String toString() {
        return amount == 0 ? type().name() : type().name() + " " + amount;
    }
}
