package com.saksham.poker.server.web;

import com.saksham.poker.common.api.AuthRequest;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.server.auth.Session;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** {@code POST /api/auth/login}: checks a username and password and starts a session. */
@WebServlet("/api/auth/login")
public class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        AuthRequest body = readJson(request, AuthRequest.class);
        Session session = app().sessions().login(body.username(), body.password());
        writeJson(response, HttpServletResponse.SC_OK, new AuthResponse(session.token(),
                new UserInfo(session.user().id(), session.user().username())));
    }
}
