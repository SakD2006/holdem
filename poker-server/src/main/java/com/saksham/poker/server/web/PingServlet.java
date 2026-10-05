package com.saksham.poker.server.web;

import com.saksham.poker.common.api.PingResponse;
import com.saksham.poker.server.bootstrap.AppContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Answers "is a Hold'em server here?". The desktop app's "Test connection" button calls it. */
@WebServlet("/api/ping")
public class PingServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK,
                new PingResponse("ok", AppContext.SERVER_NAME, AppContext.SERVER_VERSION));
    }
}
