package com.saksham.poker.server.db;

import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.common.protocol.dto.BotLevel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Reads and creates the accounts of computer players, which live in the users table. */
public class BotAccountDao extends BaseDao<BotAccount> {

    /** Stored where a password hash would be. It is not a hash, so no password can ever match it. */
    static final String NO_LOGIN = "bot:no-login";

    public BotAccountDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected BotAccount mapRow(ResultSet row) throws SQLException {
        return new BotAccount(row.getLong("id"), row.getString("username"), BotLevel.valueOf(row.getString("bot_level")));
    }

    /** Every bot account of a level, oldest first. */
    public List<BotAccount> findByLevel(BotLevel level) {
        return findAll("SELECT id, username, bot_level FROM users WHERE bot_level = ? ORDER BY id", level.name());
    }

    /**
     * Creates a bot account.
     *
     * @return the account, or empty if the name is taken, by a person or by another bot
     */
    public Optional<BotAccount> create(String username, BotLevel level) {
        String sql = "INSERT INTO users (username, password_hash, bot_level) VALUES (?, ?, ?) "
                + "RETURNING id, username, bot_level";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, NO_LOGIN);
            statement.setString(3, level.name());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return Optional.of(mapRow(row));
            }
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return Optional.empty();
            }
            throw new PersistenceException("Could not create the bot account " + username + ": " + e.getMessage(), e);
        }
    }
}
