package com.saksham.poker.common.api;

import java.util.List;

/**
 * One page of a player's hand history, newest first: the answer to {@code GET /api/hands?page=}.
 *
 * @param page the page number, starting at 1
 * @param pageSize how many hands a full page holds
 * @param total how many hands the player has played in all
 * @param hands the hands on this page
 */
public record HandPage(int page, int pageSize, long total, List<HandSummary> hands) {
}
