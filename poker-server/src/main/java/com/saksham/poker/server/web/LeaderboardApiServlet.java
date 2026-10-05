package com.saksham.poker.server.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** {@code GET /api/leaderboard}: the biggest winners over every hand played on this server. */
@WebServlet("/api/leaderboard")
public class LeaderboardApiServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    static final int ENTRIES = 50;

    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, app().hands().leaderboard(ENTRIES));
    }
}
