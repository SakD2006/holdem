package com.saksham.poker.client.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.StorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HandTextTest {

    private static final long ENDED = Instant.parse("2026-10-05T10:01:30Z").toEpochMilli();

    private static final HandSummary WON = new HandSummary(7, "ABC234", "Friday game", 12, ENDED,
            Card.parseAll("Ah Ad"), Card.parseAll("2c 5d 9h Js 3s"), 600, 300, true);
    private static final HandSummary FOLDED = new HandSummary(8, "ABC234", "Friday game", 13, ENDED + 60_000,
            Card.parseAll("7c 2d"), List.of(), 150, -50, false);

    /** Asha folded and may see only her own cards; ravi and meera showed theirs. */
    private static final HandDetail DETAIL = new HandDetail(7, "ABC234", "Friday game", 12, 0,
            Card.parseAll("2c 5d 9h Js 3s"), 600, ENDED - 90_000, ENDED,
            List.of(new HandPlayerInfo(0, 1, "asha", List.of(), 10_000, 10_000, 0, false, false),
                    new HandPlayerInfo(1, 2, "ravi", Card.parseAll("Kh Kd"), 10_000, 9_700, -300, true, false),
                    new HandPlayerInfo(2, 3, "meera", Card.parseAll("Ah Ad"), 10_000, 10_300, 300, true, true)),
            List.of(new HandActionInfo(1, 1, "ravi", "PREFLOP", "POST_SB", 50),
                    new HandActionInfo(2, 2, "meera", "PREFLOP", "POST_BB", 100),
                    new HandActionInfo(3, 0, "asha", "PREFLOP", "FOLD", 0),
                    new HandActionInfo(4, 1, "ravi", "PREFLOP", "CALL", 50),
                    new HandActionInfo(5, 2, "meera", "PREFLOP", "CHECK", 0),
                    new HandActionInfo(6, 1, "ravi", "FLOP", "BET", 200),
                    new HandActionInfo(7, 2, "meera", "FLOP", "RAISE", 1_400),
                    new HandActionInfo(8, 1, "ravi", "RIVER", "CHECK", 0)));

    @Test
    void aHandInAListIsOneLine() {
        assertThat(HandText.line(WON, ZoneOffset.UTC))
                .isEqualTo("2026-10-05 10:01  ABC234 #12  Ah Ad  2c 5d 9h Js 3s  +300");
        // No board when the hand ended before the flop.
        assertThat(HandText.line(FOLDED, ZoneOffset.UTC))
                .isEqualTo("2026-10-05 10:02  ABC234 #13  7c 2d  -               -50");
    }

    @Test
    void timesAreShownInThePlayersTimeZone() {
        assertThat(HandText.line(WON, ZoneOffset.ofHoursMinutes(5, 30))).startsWith("2026-10-05 15:31");
    }

    @Test
    void aWholeHandIsWrittenStepByStep() {
        assertThat(HandText.lines(DETAIL, ZoneOffset.UTC)).containsExactly(
                "Hand #12 in \"Friday game\" (room ABC234), 2026-10-05 10:01",
                "Seat 1: asha (10,000) - button",
                "Seat 2: ravi (10,000) - Kh Kd",
                "Seat 3: meera (10,000) - Ah Ad",
                "",
                "Preflop",
                "ravi posts the small blind 50",
                "meera posts the big blind 100",
                "asha folds",
                "ravi calls 50",
                "meera checks",
                "",
                "Flop: 2c 5d 9h",
                "ravi bets 200",
                "meera raises, putting in 1,400",
                "",
                "River: 2c 5d 9h Js 3s",
                "ravi checks",
                "",
                "Total pot 600, board 2c 5d 9h Js 3s",
                "asha broke even",
                "ravi showed Kh Kd and lost 300",
                "meera showed Ah Ad and won 300");
    }

    @Test
    void anExportedHistoryListsEveryHandAndTotalsThem(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("hands.txt");

        HandHistoryExporter.write(file, "asha", List.of(FOLDED, WON), ZoneOffset.UTC);

        assertThat(Files.readAllLines(file)).containsExactly(
                "Hold'em hand history for asha",
                "2 hands",
                "",
                "When              Room   Hand   Cards  Board           Result",
                "2026-10-05 10:02  ABC234 #13  7c 2d  -               -50",
                "2026-10-05 10:01  ABC234 #12  Ah Ad  2c 5d 9h Js 3s  +300",
                "",
                "Hands won: 1 of 2",
                "Overall: +250");
    }

    @Test
    void exportingReplacesAnOlderFileAndCopesWithNoHands(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("hands.txt");
        Files.writeString(file, "something older and much longer than the new contents will be\n".repeat(20));

        HandHistoryExporter.write(file, "asha", List.of(), ZoneOffset.UTC);

        assertThat(Files.readString(file)).startsWith("Hold'em hand history for asha").contains("0 hands")
                .contains("Overall: 0").doesNotContain("something older");
    }

    @Test
    void aFileThatCannotBeWrittenIsExplained(@TempDir Path folder) {
        Path impossible = folder.resolve("no-such-folder").resolve("hands.txt");

        assertThatThrownBy(() -> HandHistoryExporter.write(impossible, "asha", List.of(WON), ZoneOffset.UTC))
                .isInstanceOf(StorageException.class).hasMessageContaining("hands.txt");
    }
}
