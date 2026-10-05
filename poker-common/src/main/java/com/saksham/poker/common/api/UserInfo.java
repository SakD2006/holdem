package com.saksham.poker.common.api;

/**
 * A player's account, as other programs see it.
 *
 * @param id the account's id
 * @param username the name to show
 */
public record UserInfo(long id, String username) {
}
