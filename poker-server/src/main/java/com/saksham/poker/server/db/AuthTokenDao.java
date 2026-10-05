package com.saksham.poker.server.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Reads and writes login tokens. Tokens are stored hashed, so this class only ever sees the hash.
 * A row is read as the id of the user the token belongs to.
 */
public class AuthTokenDao extends BaseDao<Long> {

    public AuthTokenDao(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected Long mapRow(ResultSet row) throws SQLException {
        return row.getLong("user_id");
    }

    /**
     * Records a login: stores the token and notes when the user logged in. Both happen in one
     * transaction, so a login is never half-recorded.
     */
    public void recordLogin(String tokenHash, long userId, Instant now, Instant expiresAt) {
        inTransaction(connection -> {
            update(connection, "INSERT INTO auth_tokens (token, user_id, expires_at) VALUES (?, ?, ?)",
                    tokenHash, userId, expiresAt);
            update(connection, "UPDATE users SET last_login_at = ? WHERE id = ?",
                    now, userId);
            return null;
        });
    }

    /** The user a token belongs to, if the token exists and has not expired. */
    public Optional<Long> findUserId(String tokenHash, Instant now) {
        return findOne("SELECT user_id FROM auth_tokens WHERE token = ? AND expires_at > ?", tokenHash, now);
    }

    /** Removes a token, logging that session out. */
    public void delete(String tokenHash) {
        update("DELETE FROM auth_tokens WHERE token = ?", tokenHash);
    }

    /**
     * Removes every token that has expired.
     *
     * @return how many were removed
     */
    public int deleteExpired(Instant now) {
        return update("DELETE FROM auth_tokens WHERE expires_at <= ?", now);
    }
}
