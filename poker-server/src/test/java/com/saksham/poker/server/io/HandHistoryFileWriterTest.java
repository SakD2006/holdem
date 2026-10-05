package com.saksham.poker.server.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.exception.StorageException;
import com.saksham.poker.server.db.HandRecord;
import com.saksham.poker.server.db.Hands;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandHistoryFileWriterTest {

    @TempDir
    Path folder;

    private HandHistoryFileWriter writer() {
        return new HandHistoryFileWriter(folder, ZoneOffset.UTC);
    }

    @Test
    void aHandIsWrittenAsReadableText() {
        String text = writer().format(Hands.showdown("ABC234", 12, 1, 2, 3));

        assertThat(text).isEqualTo("""
                Hold'em hand #12 - room ABC234 "Friday game" - 2026-10-05 10:01:30
                Blinds 50/100. Button on seat 0.
                Seat 0: asha (10000)
                Seat 1: ravi (10000)
                Seat 2: meera (10000)
                *** PREFLOP ***
                ravi posts the small blind 50
                meera posts the big blind 100
                asha folds
                ravi calls 50
                meera checks
                *** FLOP *** [2c 5d 9h]
                ravi bets 200
                meera calls 200
                *** TURN *** [2c 5d 9h Js]
                ravi checks
                meera checks
                *** RIVER *** [2c 5d 9h Js 3s]
                ravi checks
                meera checks
                *** SUMMARY ***
                Total pot 600. Board 2c 5d 9h Js 3s
                Seat 0: asha broke even
                Seat 1: ravi showed Kh Kd and lost 300
                Seat 2: meera showed Ah Ad and won 300

                """);
    }

    @Test
    void cardsThatWereNeverShownAreLeftOut() {
        String text = writer().format(Hands.foldedPreflop("ABC234", 3, 1, 2));

        assertThat(text).contains("asha raises to 400 and is all-in").contains("ravi folds")
                .contains("Total pot 200\n").contains("Seat 0: asha won 100\n").contains("Seat 1: ravi lost 100\n")
                .doesNotContain("7c").doesNotContain("Kh").doesNotContain("Board").doesNotContain("*** FLOP");
    }

    @Test
    void handsAreAddedToOneFilePerRoomPerDay() throws Exception {
        HandHistoryFileWriter writer = writer();

        Path first = writer.append(Hands.showdown("ABC234", 1, 1, 2, 3));
        Path second = writer.append(Hands.foldedPreflop("ABC234", 2, 1, 2));
        Path otherRoom = writer.append(Hands.foldedPreflop("DEF567", 1, 1, 2));

        assertThat(first).isEqualTo(folder.resolve("room-ABC234").resolve("2026-10-05.txt")).isEqualTo(second);
        assertThat(otherRoom).isEqualTo(folder.resolve("room-DEF567").resolve("2026-10-05.txt"));
        String text = Files.readString(first);
        assertThat(text).contains("Hold'em hand #1 - room ABC234").contains("Hold'em hand #2 - room ABC234");
        assertThat(text.indexOf("hand #1")).isLessThan(text.indexOf("hand #2"));
        assertThat(Files.readString(otherRoom)).doesNotContain("ABC234");
    }

    @Test
    void theDayIsTakenInTheServersTimeZone() {
        // 10:01 UTC on the 5th is already the 6th in Auckland.
        HandHistoryFileWriter auckland = new HandHistoryFileWriter(folder, ZoneId.of("Pacific/Auckland"));
        HandRecord hand = Hands.foldedPreflop("ABC234", 1, 1, 2);

        assertThat(auckland.fileFor(hand).getFileName().toString()).isEqualTo("2026-10-05.txt");
        assertThat(auckland.format(hand)).contains("2026-10-05 23:01:30");
        assertThat(new HandHistoryFileWriter(folder, ZoneId.of("Pacific/Kiritimati")).fileFor(hand)
                .getFileName().toString()).isEqualTo("2026-10-06.txt");
    }

    @Test
    void aFolderThatCannotBeWrittenRaisesAStorageException() throws Exception {
        // A plain file where the folder should be makes creating the room's folder fail.
        Path blocked = folder.resolve("blocked");
        Files.writeString(blocked, "not a folder");

        assertThatThrownBy(() -> new HandHistoryFileWriter(blocked, ZoneOffset.UTC)
                .append(Hands.foldedPreflop("ABC234", 1, 1, 2)))
                .isInstanceOf(StorageException.class).hasMessageContaining("hand history file");
    }
}
