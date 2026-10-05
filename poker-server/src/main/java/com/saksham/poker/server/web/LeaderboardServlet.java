package com.saksham.poker.server.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** {@code /leaderboard}: the biggest winners over every hand played on this server. */
@WebServlet("/leaderboard")
public class LeaderboardServlet extends PageServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected String prepare(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("entries", app().hands().leaderboard(LeaderboardApiServlet.ENTRIES));
        return "leaderboard.jsp";
    }
}
