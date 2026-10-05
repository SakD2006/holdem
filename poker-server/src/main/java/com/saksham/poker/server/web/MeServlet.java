package com.saksham.poker.server.web;

import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.server.db.User;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code GET /api/me}: who the request's token belongs to. The desktop app calls it at startup to
 * find out whether a remembered login is still good.
 */
@WebServlet("/api/me")
public class MeServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = currentUser(request);
        writeJson(response, HttpServletResponse.SC_OK, new UserInfo(user.id(), user.username()));
    }
}
