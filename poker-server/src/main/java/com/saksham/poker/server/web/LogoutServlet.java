package com.saksham.poker.server.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** {@code POST /api/auth/logout}: ends the session the request's token belongs to. */
@WebServlet("/api/auth/logout")
public class LogoutServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handlePost(HttpServletRequest request, HttpServletResponse response) {
        app().sessions().logout((String) request.getAttribute(AuthFilter.TOKEN_ATTRIBUTE));
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
