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

/** {@code POST /api/auth/register}: creates an account and logs it in. */
@WebServlet("/api/auth/register")
public class RegisterServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        AuthRequest body = readJson(request, AuthRequest.class);
        Session session = app().sessions().register(body.username(), body.password());
        writeJson(response, HttpServletResponse.SC_CREATED, new AuthResponse(session.token(),
                new UserInfo(session.user().id(), session.user().username())));
    }
}
