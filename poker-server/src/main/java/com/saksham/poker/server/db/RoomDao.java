package com.saksham.poker.server.db;

import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.room.RoomSettings;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/** Reads and writes rooms. */
public class RoomDao extends BaseDao<RoomRecord> {

    private static final String COLUMNS = "id, code, name, host_user_id, max_players, small_blind, big_blind, "
            + "starting_stack, turn_seconds, rebuy_allowed, status, created_at, closed_at";

    public RoomDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected RoomRecord mapRow(ResultSet row) throws SQLException {
        RoomSettings settings = new RoomSettings(row.getString("name"), row.getInt("max_players"),
                row.getLong("small_blind"), row.getLong("big_blind"), row.getLong("starting_stack"),
                row.getInt("turn_seconds"), row.getBoolean("rebuy_allowed"), false);
        return new RoomRecord(row.getLong("id"), row.getString("code"), row.getLong("host_user_id"), settings,
                RoomState.valueOf(row.getString("status")), instant(row, "created_at"), instant(row, "closed_at"));
    }

    /**
     * Adds a room in the {@code WAITING} state.
     *
     * @return the new room, or empty if the code has been used before; the caller tries another code
     */
    public Optional<RoomRecord> create(String code, long hostUserId, RoomSettings settings) {
        String sql = "INSERT INTO rooms (code, name, host_user_id, max_players, small_blind, big_blind, "
                + "starting_stack, turn_seconds, rebuy_allowed, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, settings.name());
            statement.setLong(3, hostUserId);
            statement.setInt(4, settings.maxPlayers());
            statement.setLong(5, settings.smallBlind());
            statement.setLong(6, settings.bigBlind());
            statement.setLong(7, settings.startingStack());
            statement.setInt(8, settings.turnSeconds());
            statement.setBoolean(9, settings.rebuyAllowed());
            statement.setString(10, RoomState.WAITING.name());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return Optional.of(mapRow(row));
            }
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return Optional.empty();
            }
            throw new PersistenceException("Could not save the new room: " + e.getMessage(), e);
        }
    }

    public Optional<RoomRecord> findByCode(String code) {
        return findOne("SELECT " + COLUMNS + " FROM rooms WHERE code = ?", code);
    }

    /** Records a change of state; closing a room also records when. */
    public void updateState(String code, RoomState state, Instant now) {
        if (state == RoomState.CLOSED) {
            update("UPDATE rooms SET status = ?, closed_at = ? WHERE code = ?", state.name(), now, code);
        } else {
            update("UPDATE rooms SET status = ? WHERE code = ?", state.name(), code);
        }
    }

    /**
     * Closes every room that is not closed. Rooms live in the server's memory, so any still open in
     * the database when the server starts were lost when it last stopped.
     *
     * @return how many were closed
     */
    public int closeAllOpen(Instant now) {
        return update("UPDATE rooms SET status = ?, closed_at = ? WHERE status <> ?",
                RoomState.CLOSED.name(), now, RoomState.CLOSED.name());
    }
}
