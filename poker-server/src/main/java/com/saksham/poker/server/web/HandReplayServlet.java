package com.saksham.poker.server.web;

import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.server.web.view.CardView;
import com.saksham.poker.server.web.view.HandReplay;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;

/** {@code /hands/{id}}: one finished hand, street by street. */
@WebServlet("/hands/*")
public class HandReplayServlet extends PageServlet {

    private static final long serialVersionUID = 1L;
    /** Nobody is logged in on the web pages, so a visitor is shown what the whole table saw. */
    private static final long ANYONE = -1;

    @Override
    protected String prepare(HttpServletRequest request, HttpServletResponse response)
            throws InvalidRequestException {
        String path = request.getPathInfo();
        long handId;
        try {
            handId = Long.parseLong(path == null ? "" : path.substring(1));
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("A hand's address ends in its number, such as /hands/12.");
        }
        // Hole cards that were never shown at the table are removed before the page sees the hand.
        HandDetail hand = app().hands().find(handId)
                .orElseThrow(() -> new InvalidRequestException("There is no hand number " + handId + "."))
                .viewFor(ANYONE);
        request.setAttribute("hand", hand);
        request.setAttribute("board", CardView.of(hand.board()));
        request.setAttribute("ended", new Date(hand.endedAtMs()));
        request.setAttribute("replay", HandReplay.from(hand));
        return "hand-replay.jsp";
    }
}
