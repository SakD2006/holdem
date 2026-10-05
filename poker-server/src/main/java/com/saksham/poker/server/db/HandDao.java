package com.saksham.poker.server.db;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandPage;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.api.LeaderboardEntry;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.PersistenceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/** Saves finished hands and reads them back for hand history, replays and the leaderboard. */
public class HandDao extends BaseDao<HandSummary> {

    public HandDao(DataSource dataSource) {
        super(dataSource);
    }

    /** One row of a player's history: the hand joined to that player's own part in it. */
    @Override
    protected HandSummary mapRow(ResultSet row) throws SQLException {
        return new HandSummary(row.getLong("id"), row.getString("code"), row.getString("name"),
                row.getLong("hand_no"), instant(row, "ended_at").toEpochMilli(),
                Card.parseAll(row.getString("hole_cards")), Card.parseAll(row.getString("board")),
                row.getLong("total_pot"), row.getLong("net"), row.getBoolean("won"));
    }

    // ---- saving

    /**
     * Saves a hand with its players and actions in one transaction: all of it is stored, or none.
     * Saving a hand that is already stored does nothing, so a retry can never record it twice.
     *
     * @throws PersistenceException if the hand could not be saved
     */
    public void save(HandRecord hand) {
        try {
            inTransaction(connection -> {
                insert(connection, hand);
                return null;
            });
        } catch (PersistenceException e) {
            if (e.getCause() instanceof SQLException sql && isDuplicate(sql)) {
                return; // an earlier attempt got through
            }
            throw e;
        }
    }

    private static void insert(Connection connection, HandRecord hand) throws SQLException {
        long handId;
        String insertHand = "INSERT INTO hands (room_id, hand_no, button_seat, board, total_pot, started_at, "
                + "ended_at) SELECT id, ?, ?, ?, ?, ?, ? FROM rooms WHERE code = ? RETURNING id";
        try (PreparedStatement statement = connection.prepareStatement(insertHand)) {
            statement.setLong(1, hand.handNo());
            statement.setInt(2, hand.buttonSeat());
            statement.setString(3, Card.formatAll(hand.board()));
            statement.setLong(4, hand.totalPot());
            statement.setObject(5, hand.startedAt().atOffset(ZoneOffset.UTC));
            statement.setObject(6, hand.endedAt().atOffset(ZoneOffset.UTC));
            statement.setString(7, hand.roomCode());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("There is no room with code " + hand.roomCode() + " to save the hand under");
                }
                handId = row.getLong(1);
            }
        }
        String insertPlayer = "INSERT INTO hand_players (hand_id, user_id, seat, hole_cards, start_stack, "
                + "end_stack, net, showed_down, won) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertPlayer)) {
            for (HandRecord.PlayerRecord player : hand.players()) {
                statement.setLong(1, handId);
                statement.setLong(2, player.userId());
                statement.setInt(3, player.seat());
                statement.setString(4, Card.formatAll(player.holeCards()));
                statement.setLong(5, player.startStack());
                statement.setLong(6, player.endStack());
                statement.setLong(7, player.net());
                statement.setBoolean(8, player.showedDown());
                statement.setBoolean(9, player.won());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        String insertAction = "INSERT INTO hand_actions (hand_id, seq, user_id, street, action, amount) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertAction)) {
            for (HandRecord.ActionRecord action : hand.actions()) {
                statement.setLong(1, handId);
                statement.setInt(2, action.seq());
                statement.setLong(3, action.userId());
                statement.setString(4, action.street());
                statement.setString(5, action.action());
                statement.setLong(6, action.amount());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // ---- reading

    /**
     * One page of the hands a user was dealt into, newest first.
     *
     * @param page the page number, starting at 1
     * @param pageSize hands per page
     */
    public HandPage pageFor(long userId, int page, int pageSize) {
        long total = query("SELECT COUNT(*) FROM hand_players WHERE user_id = ?", row -> row.getLong(1), userId)
                .get(0);
        List<HandSummary> hands = findAll(
                "SELECT h.id, r.code, r.name, h.hand_no, h.ended_at, h.board, h.total_pot, "
                        + "p.hole_cards, p.net, p.won "
                        + "FROM hand_players p JOIN hands h ON h.id = p.hand_id JOIN rooms r ON r.id = h.room_id "
                        + "WHERE p.user_id = ? ORDER BY h.ended_at DESC, h.id DESC LIMIT ? OFFSET ?",
                userId, pageSize, (long) (page - 1) * pageSize);
        return new HandPage(page, pageSize, total, hands);
    }

    /** A whole hand, with every player's hole cards. Filter it with {@link StoredHand#viewFor}. */
    public Optional<StoredHand> find(long handId) {
        return withConnection(connection -> {
            List<HandPlayerInfo> players = query(connection,
                    "SELECT p.seat, p.user_id, u.username, p.hole_cards, p.start_stack, p.end_stack, p.net, "
                            + "p.showed_down, p.won FROM hand_players p JOIN users u ON u.id = p.user_id "
                            + "WHERE p.hand_id = ? ORDER BY p.seat",
                    row -> new HandPlayerInfo(row.getInt("seat"), row.getLong("user_id"), row.getString("username"),
                            Card.parseAll(row.getString("hole_cards")), row.getLong("start_stack"),
                            row.getLong("end_stack"), row.getLong("net"), row.getBoolean("showed_down"),
                            row.getBoolean("won")),
                    handId);
            Map<Long, HandPlayerInfo> byUser = new HashMap<>();
            players.forEach(player -> byUser.put(player.userId(), player));
            List<HandActionInfo> actions = query(connection,
                    "SELECT seq, user_id, street, action, amount FROM hand_actions WHERE hand_id = ? ORDER BY seq",
                    row -> {
                        HandPlayerInfo actor = byUser.get(row.getLong("user_id"));
                        return new HandActionInfo(row.getInt("seq"), actor == null ? -1 : actor.seat(),
                                actor == null ? "" : actor.username(), row.getString("street"),
                                row.getString("action"), row.getLong("amount"));
                    },
                    handId);
            List<StoredHand> hands = query(connection,
                    "SELECT h.id, r.code, r.name, h.hand_no, h.button_seat, h.board, h.total_pot, h.started_at, "
                            + "h.ended_at FROM hands h JOIN rooms r ON r.id = h.room_id WHERE h.id = ?",
                    row -> new StoredHand(row.getLong("id"), row.getString("code"), row.getString("name"),
                            row.getLong("hand_no"), row.getInt("button_seat"),
                            Card.parseAll(row.getString("board")), row.getLong("total_pot"),
                            instant(row, "started_at").toEpochMilli(), instant(row, "ended_at").toEpochMilli(),
                            players, actions),
                    handId);
            return hands.isEmpty() ? Optional.empty() : Optional.of(hands.get(0));
        });
    }

    /**
     * The biggest winners over every hand played. Players level on chips share a rank.
     *
     * @param limit the most entries to return
     */
    public List<LeaderboardEntry> leaderboard(int limit) {
        return query("SELECT RANK() OVER (ORDER BY SUM(p.net) DESC) AS place, u.username, "
                        + "SUM(p.net) AS total_net, COUNT(*) AS hands_played, SUM(p.won::int) AS hands_won "
                        + "FROM hand_players p JOIN users u ON u.id = p.user_id "
                        + "GROUP BY u.id, u.username ORDER BY total_net DESC, u.username LIMIT ?",
                row -> new LeaderboardEntry(row.getInt("place"), row.getString("username"),
                        row.getLong("total_net"), row.getLong("hands_played"), row.getLong("hands_won")),
                limit);
    }

    /** How many hands have been played on this server. */
    public long count() {
        return query("SELECT COUNT(*) FROM hands", row -> row.getLong(1)).get(0);
    }

    /** How each player did in one room over all its hands, biggest winner first. */
    public List<RoomStanding> standings(long roomId) {
        return query("SELECT u.username, COUNT(*) AS hands_played, SUM(p.won::int) AS hands_won, "
                        + "SUM(p.net) AS total_net "
                        + "FROM hand_players p JOIN hands h ON h.id = p.hand_id JOIN users u ON u.id = p.user_id "
                        + "WHERE h.room_id = ? GROUP BY u.id, u.username ORDER BY total_net DESC, u.username",
                row -> new RoomStanding(row.getString("username"), row.getLong("hands_played"),
                        row.getLong("hands_won"), row.getLong("total_net")),
                roomId);
    }

    /**
     * The hands played in one room, newest first, each with the names of whoever won it.
     *
     * @param limit the most hands to return
     */
    public List<RoomHand> handsInRoom(long roomId, int limit) {
        return query("SELECT h.id, h.hand_no, h.board, h.total_pot, h.ended_at, "
                        + "COALESCE(STRING_AGG(u.username, ', ' ORDER BY p.seat) FILTER (WHERE p.won), '') AS winners "
                        + "FROM hands h JOIN hand_players p ON p.hand_id = h.id JOIN users u ON u.id = p.user_id "
                        + "WHERE h.room_id = ? GROUP BY h.id ORDER BY h.hand_no DESC LIMIT ?",
                row -> new RoomHand(row.getLong("id"), row.getLong("hand_no"),
                        Card.parseAll(row.getString("board")), row.getLong("total_pot"), instant(row, "ended_at"),
                        row.getString("winners")),
                roomId, limit);
    }
}
