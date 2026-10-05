package com.saksham.poker.client.util;

import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.exception.StorageException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/** Saves a player's hand history as a text file they can keep or share. */
public final class HandHistoryExporter {

    private HandHistoryExporter() {
    }

    /**
     * Writes one line per hand, newest first, with a heading and a total at the end.
     *
     * @param file where to write; an existing file is replaced
     * @param username whose history it is
     * @param hands the hands, in the order to write them
     * @param zone the time zone for the times shown
     * @throws StorageException if the file cannot be written
     */
    public static void write(Path file, String username, List<HandSummary> hands, ZoneId zone) {
        long net = 0;
        long won = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("Hold'em hand history for " + username);
            writer.newLine();
            writer.write(hands.size() + (hands.size() == 1 ? " hand" : " hands"));
            writer.newLine();
            writer.newLine();
            writer.write("When              Room   Hand   Cards  Board           Result");
            writer.newLine();
            for (HandSummary hand : hands) {
                writer.write(HandText.line(hand, zone));
                writer.newLine();
                net += hand.net();
                won += hand.won() ? 1 : 0;
            }
            writer.newLine();
            writer.write("Hands won: " + won + " of " + hands.size());
            writer.newLine();
            writer.write("Overall: " + Formatters.signed(net));
            writer.newLine();
        } catch (IOException e) {
            throw new StorageException("Could not save the hand history to " + file + ". Choose another folder "
                    + "or check that the file is not open elsewhere.", e);
        }
    }
}
