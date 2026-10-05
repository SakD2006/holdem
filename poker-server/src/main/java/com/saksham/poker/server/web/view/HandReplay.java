package com.saksham.poker.server.web.view;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.card.Card;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A finished hand laid out for reading: who sat where, then what happened on each street.
 *
 * @param players everyone dealt in, by seat
 * @param streets the streets that were reached, in order
 */
public record HandReplay(List<PlayerLine> players, List<Street> streets) {

    /**
     * @param seat their seat, counting from 1 as players do
     * @param username their name
     * @param cards their hole cards, or empty if they were never shown
     * @param button true for the dealer
     * @param startStack chips before the hand
     * @param endStack chips after it
     * @param net chips won or lost
     * @param won true if they were paid from a pot
     */
    public record PlayerLine(int seat, String username, List<CardView> cards, boolean button, long startStack,
            long endStack, long net, boolean won) {
    }

    /**
     * @param name "Before the flop", "Flop", "Turn" or "River"
     * @param cards the community cards dealt on this street
     * @param steps what each player did, in order
     */
    public record Street(String name, List<CardView> cards, List<Step> steps) {
    }

    /**
     * @param username who acted
     * @param text what they did, such as "raises to 300"
     */
    public record Step(String username, String text) {
    }

    private static final String[] STREETS = {"PREFLOP", "FLOP", "TURN", "RIVER"};
    private static final String[] NAMES = {"Before the flop", "Flop", "Turn", "River"};
    /** How many community cards are on the table once each street has been dealt. */
    private static final int[] BOARD_SIZE = {0, 3, 4, 5};

    /**
     * Lays out a hand. Give it the hand as the viewer may see it ({@code StoredHand.viewFor}): this
     * class shows whatever hole cards it is given.
     */
    public static HandReplay from(HandDetail hand) {
        List<PlayerLine> players = new ArrayList<>();
        for (HandPlayerInfo player : hand.players()) {
            players.add(new PlayerLine(player.seat() + 1, player.username(), CardView.of(player.holeCards()),
                    player.seat() == hand.buttonSeat(), player.startStack(), player.endStack(), player.net(),
                    player.won()));
        }
        List<Street> streets = new ArrayList<>();
        List<Card> board = hand.board();
        for (int i = 0; i < STREETS.length; i++) {
            if (board.size() < BOARD_SIZE[i]) {
                break; // the hand ended before this street
            }
            List<Card> dealt = i == 0 ? List.of() : board.subList(BOARD_SIZE[i - 1], BOARD_SIZE[i]);
            streets.add(new Street(NAMES[i], CardView.of(dealt), steps(hand.actions(), STREETS[i])));
        }
        return new HandReplay(players, streets);
    }

    private static List<Step> steps(List<HandActionInfo> actions, String street) {
        // What each player has put in on this street so far, to turn "raised by" into "raises to".
        Map<Integer, Long> put = new HashMap<>();
        List<Step> steps = new ArrayList<>();
        for (HandActionInfo action : actions) {
            if (!street.equals(action.street())) {
                continue;
            }
            long total = put.merge(action.seat(), action.amount(), Long::sum);
            steps.add(new Step(action.username(), describe(action, total)));
        }
        return steps;
    }

    private static String describe(HandActionInfo action, long streetTotal) {
        return switch (action.action()) {
            case "POST_SB" -> "posts the small blind, " + chips(action.amount());
            case "POST_BB" -> "posts the big blind, " + chips(action.amount());
            case "FOLD" -> "folds";
            case "CHECK" -> "checks";
            case "CALL" -> "calls " + chips(action.amount());
            case "BET" -> "bets " + chips(action.amount());
            case "RAISE" -> "raises to " + chips(streetTotal);
            default -> action.action().toLowerCase() + " " + chips(action.amount());
        };
    }

    private static String chips(long amount) {
        return String.format("%,d", amount);
    }
}
