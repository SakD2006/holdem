package com.saksham.poker.server.db;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.card.Card;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand as read back from the database, with every player's hole cards. It must never be sent to a
 * client as it is: {@link #viewFor} produces the version one viewer is allowed to see.
 *
 * @param id the hand's id
 * @param roomCode the room it was played in
 * @param roomName that room's name
 * @param handNo the hand's number within the room
 * @param buttonSeat the seat that had the dealer button
 * @param board the community cards that were dealt
 * @param totalPot all chips paid out
 * @param startedAtMs when it began, in milliseconds since 1970 UTC
 * @param endedAtMs when it ended
 * @param players everyone dealt in, by seat, hole cards included
 * @param actions every blind and action, in order
 */
public record StoredHand(long id, String roomCode, String roomName, long handNo, int buttonSeat, List<Card> board,
        long totalPot, long startedAtMs, long endedAtMs, List<HandPlayerInfo> players,
        List<HandActionInfo> actions) {

    /**
     * The hand as one viewer may see it. A player's hole cards are shown to that player, and to
     * everyone if they were revealed at showdown; otherwise they are left out.
     *
     * @param viewerUserId who is asking; -1 for someone with no account, who sees only shown cards
     */
    public HandDetail viewFor(long viewerUserId) {
        List<HandPlayerInfo> visible = new ArrayList<>();
        for (HandPlayerInfo player : players) {
            boolean maySee = player.showedDown() || player.userId() == viewerUserId;
            visible.add(maySee ? player : new HandPlayerInfo(player.seat(), player.userId(), player.username(),
                    List.of(), player.startStack(), player.endStack(), player.net(), player.showedDown(),
                    player.won()));
        }
        return new HandDetail(id, roomCode, roomName, handNo, buttonSeat, board, totalPot, startedAtMs, endedAtMs,
                visible, actions);
    }
}
