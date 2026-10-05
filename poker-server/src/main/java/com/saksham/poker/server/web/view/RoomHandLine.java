package com.saksham.poker.server.web.view;

import com.saksham.poker.server.db.RoomHand;
import java.util.Date;
import java.util.List;

/**
 * One hand in a room's list.
 *
 * @param id the hand's id, for the link to its replay
 * @param handNo the hand's number within the room
 * @param board the community cards
 * @param totalPot all chips paid out
 * @param winners the names of whoever was paid
 * @param ended when it ended
 */
public record RoomHandLine(long id, long handNo, List<CardView> board, long totalPot, String winners, Date ended) {

    public static RoomHandLine of(RoomHand hand) {
        return new RoomHandLine(hand.id(), hand.handNo(), CardView.of(hand.board()), hand.totalPot(),
                hand.winners(), Date.from(hand.endedAt()));
    }
}
