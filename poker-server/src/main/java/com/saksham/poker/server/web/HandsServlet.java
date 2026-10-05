package com.saksham.poker.server.web;

import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.server.db.StoredHand;
import com.saksham.poker.server.db.User;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code GET /api/hands?page=} lists the hands the logged-in player was dealt into, newest first;
 * {@code GET /api/hands/{id}} gives one hand in full, for a replay.
 */
@WebServlet("/api/hands/*")
public class HandsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;
    static final int PAGE_SIZE = 20;

    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        User user = currentUser(request);
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            int page = positiveNumber(request.getParameter("page"), 1, "page");
            writeJson(response, HttpServletResponse.SC_OK, app().hands().pageFor(user.id(), page, PAGE_SIZE));
            return;
        }
        long handId = positiveNumber(path.substring(1), -1, "hand id");
        StoredHand hand = app().hands().find(handId)
                .orElseThrow(() -> new InvalidRequestException("There is no hand with id " + handId + "."));
        // Other players' folded cards are removed here, before anything leaves the server.
        writeJson(response, HttpServletResponse.SC_OK, hand.viewFor(user.id()));
    }

    /** Reads a whole number of at least 1, or returns the fallback when nothing was given. */
    static int positiveNumber(String text, int fallback, String what) throws InvalidRequestException {
        if (text == null || text.isBlank()) {
            if (fallback > 0) {
                return fallback;
            }
            throw new InvalidRequestException("The " + what + " is missing.");
        }
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 1) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new InvalidRequestException("The " + what + " must be a whole number of 1 or more, but was \""
                + text + "\".");
    }
}
