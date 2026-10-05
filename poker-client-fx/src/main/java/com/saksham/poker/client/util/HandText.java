package com.saksham.poker.client.util;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.card.Card;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Writes hands out as text, for the history screen and for the exported file. */
public final class HandText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private HandText() {
    }

    /** One line for a hand in a list: when, where, your cards, the board and how you did. */
    public static String line(HandSummary hand, ZoneId zone) {
        return when(hand.endedAtMs(), zone) + "  " + hand.roomCode() + " #" + hand.handNo() + "  "
                + pad(cards(hand.yourCards()), 5) + "  " + pad(hand.board().isEmpty() ? "-" : cards(hand.board()), 14)
                + "  " + Formatters.signed(hand.net());
    }

    /** A whole hand, step by step, as lines of text. Cards the viewer may not see are left out. */
    public static List<String> lines(HandDetail hand, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        lines.add("Hand #" + hand.handNo() + " in \"" + hand.roomName() + "\" (room " + hand.roomCode() + "), "
                + when(hand.endedAtMs(), zone));
        for (HandPlayerInfo player : hand.players()) {
            lines.add(Formatters.seat(player.seat()) + ": " + player.username() + " (" + Formatters.chips(
                    player.startStack()) + ")" + (player.seat() == hand.buttonSeat() ? " - button" : "")
                    + (player.holeCards().isEmpty() ? "" : " - " + cards(player.holeCards())));
        }
        String street = "";
        for (HandActionInfo action : hand.actions()) {
            if (!action.street().equals(street)) {
                street = action.street();
                lines.add("");
                lines.add(streetHeading(street, hand.board()));
            }
            lines.add(action.username() + " " + describe(action));
        }
        lines.add("");
        lines.add("Total pot " + Formatters.chips(hand.totalPot())
                + (hand.board().isEmpty() ? "" : ", board " + cards(hand.board())));
        for (HandPlayerInfo player : hand.players()) {
            String result = player.net() > 0 ? "won " + Formatters.chips(player.net())
                    : player.net() < 0 ? "lost " + Formatters.chips(-player.net()) : "broke even";
            lines.add(player.username() + (player.showedDown() ? " showed " + cards(player.holeCards()) + " and " : " ")
                    + result);
        }
        return lines;
    }

    private static String streetHeading(String street, List<Card> board) {
        int shown = switch (street) {
            case "FLOP" -> 3;
            case "TURN" -> 4;
            case "RIVER" -> 5;
            default -> 0;
        };
        String name = street.charAt(0) + street.substring(1).toLowerCase();
        return shown == 0 || board.size() < shown ? name : name + ": " + cards(board.subList(0, shown));
    }

    private static String describe(HandActionInfo action) {
        return switch (action.action()) {
            case "POST_SB" -> "posts the small blind " + Formatters.chips(action.amount());
            case "POST_BB" -> "posts the big blind " + Formatters.chips(action.amount());
            case "FOLD" -> "folds";
            case "CHECK" -> "checks";
            case "CALL" -> "calls " + Formatters.chips(action.amount());
            case "BET" -> "bets " + Formatters.chips(action.amount());
            case "RAISE" -> "raises, putting in " + Formatters.chips(action.amount());
            default -> action.action().toLowerCase() + " " + Formatters.chips(action.amount());
        };
    }

    static String when(long epochMs, ZoneId zone) {
        return TIME.format(Instant.ofEpochMilli(epochMs).atZone(zone));
    }

    static String cards(List<Card> cards) {
        StringJoiner joined = new StringJoiner(" ");
        cards.forEach(card -> joined.add(card.toString()));
        return joined.toString();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }
}
