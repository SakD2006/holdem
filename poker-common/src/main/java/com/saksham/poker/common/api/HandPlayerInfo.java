package com.saksham.poker.common.api;

import com.saksham.poker.common.card.Card;
import java.util.List;

/**
 * A player's part in a finished hand.
 *
 * @param seat their seat
 * @param userId their account
 * @param username their name
 * @param holeCards their hole cards, or empty if the viewer may not see them: a player's cards are
 *     shown only to that player, unless they were revealed at showdown
 * @param startStack chips before the hand
 * @param endStack chips after it
 * @param net chips won (positive) or lost (negative)
 * @param showedDown true if their cards were revealed at showdown
 * @param won true if they were paid from any pot
 */
public record HandPlayerInfo(int seat, long userId, String username, List<Card> holeCards, long startStack,
        long endStack, long net, boolean showedDown, boolean won) {
}
