package com.saksham.poker.server.db;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.server.db.HandRecord.ActionRecord;
import com.saksham.poker.server.db.HandRecord.PlayerRecord;
import java.time.Instant;
import java.util.List;

/** Ready-made hand records for tests. */
public final class Hands {

    public static final Instant STARTED = Instant.parse("2026-10-05T10:00:00Z");
    public static final Instant ENDED = Instant.parse("2026-10-05T10:01:30Z");

    private Hands() {
    }

    /**
     * A three-player hand: seat 0 folds before the flop, seat 1 bets the flop and seat 2 calls, and
     * seat 2 wins at showdown. Seat 0's cards were never shown.
     */
    public static HandRecord showdown(String roomCode, long handNo, long user0, long user1, long user2) {
        return new HandRecord(roomCode, "Friday game", handNo, 50, 100, 0, Card.parseAll("2c 5d 9h Js 3s"), 600,
                STARTED, ENDED,
                List.of(new PlayerRecord(user0, "asha", 0, Card.parseAll("7c 2d"), 10_000, 10_000, 0, false, false),
                        new PlayerRecord(user1, "ravi", 1, Card.parseAll("Kh Kd"), 10_000, 9_700, -300, true, false),
                        new PlayerRecord(user2, "meera", 2, Card.parseAll("Ah Ad"), 10_000, 10_300, 300, true, true)),
                List.of(new ActionRecord(1, user1, 1, "PREFLOP", "POST_SB", 50, 50, false),
                        new ActionRecord(2, user2, 2, "PREFLOP", "POST_BB", 100, 100, false),
                        new ActionRecord(3, user0, 0, "PREFLOP", "FOLD", 0, 0, false),
                        new ActionRecord(4, user1, 1, "PREFLOP", "CALL", 50, 100, false),
                        new ActionRecord(5, user2, 2, "PREFLOP", "CHECK", 0, 100, false),
                        new ActionRecord(6, user1, 1, "FLOP", "BET", 200, 200, false),
                        new ActionRecord(7, user2, 2, "FLOP", "CALL", 200, 200, false),
                        new ActionRecord(8, user1, 1, "TURN", "CHECK", 0, 0, false),
                        new ActionRecord(9, user2, 2, "TURN", "CHECK", 0, 0, false),
                        new ActionRecord(10, user1, 1, "RIVER", "CHECK", 0, 0, false),
                        new ActionRecord(11, user2, 2, "RIVER", "CHECK", 0, 0, false)));
    }

    /** A two-player hand that ends before the flop: seat 0 raises all-in and seat 1 folds. */
    public static HandRecord foldedPreflop(String roomCode, long handNo, long user0, long user1) {
        return new HandRecord(roomCode, "Friday game", handNo, 50, 100, 0, List.of(), 200, STARTED, ENDED,
                List.of(new PlayerRecord(user0, "asha", 0, Card.parseAll("7c 2d"), 400, 500, 100, false, true),
                        new PlayerRecord(user1, "ravi", 1, Card.parseAll("Kh Kd"), 10_000, 9_900, -100, false, false)),
                List.of(new ActionRecord(1, user0, 0, "PREFLOP", "POST_SB", 50, 50, false),
                        new ActionRecord(2, user1, 1, "PREFLOP", "POST_BB", 100, 100, false),
                        new ActionRecord(3, user0, 0, "PREFLOP", "RAISE", 350, 400, true),
                        new ActionRecord(4, user1, 1, "PREFLOP", "FOLD", 0, 100, false)));
    }
}
