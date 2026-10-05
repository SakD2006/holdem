package com.saksham.poker.engine.eval;

import com.saksham.poker.common.card.Rank;
import java.util.List;
import java.util.Objects;

/**
 * How strong a five-card hand is. Compare two values to find the winner; equal values split the pot.
 *
 * @param category the kind of hand
 * @param kickers the ranks that break ties inside the category, most important first: for example
 *     the pair then the three side cards, or only the top card of a straight
 */
public record HandValue(HandCategory category, List<Rank> kickers) implements Comparable<HandValue> {

    public HandValue {
        Objects.requireNonNull(category, "category");
        kickers = List.copyOf(kickers);
    }

    /** Positive when this hand beats {@code other}, negative when it loses, 0 for a tie. */
    @Override
    public int compareTo(HandValue other) {
        int byCategory = category.compareTo(other.category);
        if (byCategory != 0) {
            return byCategory;
        }
        for (int i = 0; i < Math.min(kickers.size(), other.kickers.size()); i++) {
            int byRank = kickers.get(i).compareTo(other.kickers.get(i));
            if (byRank != 0) {
                return byRank;
            }
        }
        return Integer.compare(kickers.size(), other.kickers.size());
    }
}
