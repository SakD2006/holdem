package com.saksham.poker.server.db;

import com.saksham.poker.common.protocol.dto.BotLevel;

/**
 * The account of a computer player.
 *
 * @param id its user id, which hands refer to like any player's
 * @param username the name shown at the table
 * @param level how well it plays
 */
public record BotAccount(long id, String username, BotLevel level) {
}
