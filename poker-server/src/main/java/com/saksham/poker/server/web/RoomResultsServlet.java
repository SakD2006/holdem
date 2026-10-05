package com.saksham.poker.server.web;

import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.server.db.RoomHand;
import com.saksham.poker.server.db.RoomRecord;
import com.saksham.poker.server.room.Room;
import com.saksham.poker.server.room.RoomCodeGenerator;
import com.saksham.poker.server.web.view.RoomHandLine;
import com.saksham.poker.server.web.view.RoomLine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /rooms/{code}}: how each player did in a room, and its hands. {@code /rooms?code=} is where
 * the front page's "look up a room" box sends the visitor, and it leads to the same page.
 */
@WebServlet("/rooms/*")
public class RoomResultsServlet extends PageServlet {

    private static final long serialVersionUID = 1L;
    static final int HANDS_SHOWN = 200;

    @Override
    protected String prepare(HttpServletRequest request, HttpServletResponse response)
            throws RoomNotFoundException, IOException {
        String path = request.getPathInfo();
        boolean typed = path == null || path.equals("/");
        String asked = typed ? request.getParameter("code") : path.substring(1);
        if (asked == null || asked.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/");
            return null;
        }
        String code = RoomCodeGenerator.normalize(asked);
        RoomRecord room = (code == null ? Optional.<RoomRecord>empty() : app().roomRecords().findByCode(code))
                .orElseThrow(() -> new RoomNotFoundException("There is no room with the code \"" + asked.trim()
                        + "\". Codes are 6 letters and digits; check it with the host."));
        if (typed) {
            // Give the page its proper address, so it can be shared or bookmarked.
            response.sendRedirect(request.getContextPath() + "/rooms/" + room.code());
            return null;
        }
        List<RoomHandLine> hands = new ArrayList<>();
        for (RoomHand hand : app().hands().handsInRoom(room.id(), HANDS_SHOWN)) {
            hands.add(RoomHandLine.of(hand));
        }
        request.setAttribute("room",
                RoomLine.of(room, app().roomManager().find(room.code()).map(Room::state).orElse(null)));
        request.setAttribute("standings", app().hands().standings(room.id()));
        request.setAttribute("hands", hands);
        request.setAttribute("handsShown", HANDS_SHOWN);
        return "room-results.jsp";
    }
}
