package com.saksham.poker.server.io;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.StorageException;
import com.saksham.poker.server.db.HandRecord;
import com.saksham.poker.server.db.HandRecord.ActionRecord;
import com.saksham.poker.server.db.HandRecord.PlayerRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Keeps a hand history anyone can read: one text file per room per day, under
 * {@code hand-history/room-<code>/<yyyy-MM-dd>.txt}, with each finished hand added to the end.
 *
 * <p>The files show only what was public at the table. Hole cards appear only if they were turned
 * over at showdown, so a file can be passed round without giving anyone's folded hands away.
 */
public final class HandHistoryFileWriter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path folder;
    private final ZoneId zone;

    /**
     * @param folder the {@code hand-history} folder
     * @param zone the time zone for file names and the times written in them
     */
    public HandHistoryFileWriter(Path folder, ZoneId zone) {
        this.folder = folder;
        this.zone = zone;
    }

    /**
     * Adds a hand to its room's file for the day it ended.
     *
     * @return the file written to
     * @throws StorageException if the file could not be written
     */
    public Path append(HandRecord hand) {
        Path file = fileFor(hand);
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(format(hand));
            }
            return file;
        } catch (IOException e) {
            throw new StorageException("Could not write the hand history file " + file.toAbsolutePath()
                    + ". Check the folder exists and the server may write to it.", e);
        }
    }

    Path fileFor(HandRecord hand) {
        return folder.resolve("room-" + hand.roomCode()).resolve(DAY.format(hand.endedAt().atZone(zone)) + ".txt");
    }

    /** The text for one hand, ending with a blank line. */
    String format(HandRecord hand) {
        Map<Long, String> names = new HashMap<>();
        StringBuilder text = new StringBuilder();
        text.append("Hold'em hand #").append(hand.handNo()).append(" - room ").append(hand.roomCode())
                .append(" \"").append(hand.roomName()).append("\" - ")
                .append(TIME.format(hand.endedAt().atZone(zone))).append('\n');
        text.append("Blinds ").append(hand.smallBlind()).append('/').append(hand.bigBlind())
                .append(". Button on seat ").append(hand.buttonSeat()).append(".\n");
        for (PlayerRecord player : hand.players()) {
            names.put(player.userId(), player.username());
            text.append("Seat ").append(player.seat()).append(": ").append(player.username())
                    .append(" (").append(player.startStack()).append(")\n");
        }

        String street = "";
        for (ActionRecord action : hand.actions()) {
            if (!action.street().equals(street)) {
                street = action.street();
                text.append("*** ").append(street).append(" ***").append(boardAt(street, hand.board())).append('\n');
            }
            text.append(names.getOrDefault(action.userId(), "Seat " + action.seat())).append(' ')
                    .append(describe(action)).append(action.allIn() ? " and is all-in" : "").append('\n');
        }

        text.append("*** SUMMARY ***\n");
        text.append("Total pot ").append(hand.totalPot());
        if (!hand.board().isEmpty()) {
            text.append(". Board ").append(cards(hand.board()));
        }
        text.append('\n');
        for (PlayerRecord player : hand.players()) {
            text.append("Seat ").append(player.seat()).append(": ").append(player.username());
            if (player.showedDown()) {
                text.append(" showed ").append(cards(player.holeCards()));
            }
            if (player.net() > 0) {
                text.append(player.showedDown() ? " and won " : " won ").append(player.net());
            } else if (player.net() < 0) {
                text.append(player.showedDown() ? " and lost " : " lost ").append(-player.net());
            } else {
                text.append(player.showedDown() ? " and broke even" : " broke even");
            }
            text.append('\n');
        }
        return text.append('\n').toString();
    }

    private static String describe(ActionRecord action) {
        switch (action.action()) {
            case "POST_SB":
                return "posts the small blind " + action.amount();
            case "POST_BB":
                return "posts the big blind " + action.amount();
            case "FOLD":
                return "folds";
            case "CHECK":
                return "checks";
            case "CALL":
                return "calls " + action.amount();
            case "BET":
                return "bets " + action.amount();
            case "RAISE":
                return "raises to " + action.streetTotal();
            default:
                return action.action().toLowerCase() + " " + action.amount();
        }
    }

    /** The community cards on show during a street, for its heading. */
    private static String boardAt(String street, List<Card> board) {
        int shown;
        switch (street) {
            case "FLOP":
                shown = 3;
                break;
            case "TURN":
                shown = 4;
                break;
            case "RIVER":
                shown = 5;
                break;
            default:
                return "";
        }
        return board.size() < shown ? "" : " [" + cards(board.subList(0, shown)) + "]";
    }

    private static String cards(List<Card> cards) {
        StringJoiner joined = new StringJoiner(" ");
        cards.forEach(card -> joined.add(card.toString()));
        return joined.toString();
    }
}
