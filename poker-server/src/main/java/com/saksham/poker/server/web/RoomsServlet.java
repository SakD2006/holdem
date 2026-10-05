package com.saksham.poker.server.web;

import com.saksham.poker.common.api.CreateRoomResponse;
import com.saksham.poker.common.exception.PokerException;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.server.db.RoomRecord;
import com.saksham.poker.server.room.RoomSettings;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@code POST /api/rooms} creates a room and answers with its code; {@code GET /api/rooms/{code}}
 * previews a room before joining.
 */
@WebServlet("/api/rooms/*")
public class RoomsServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void handlePost(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        if (codeIn(request) != null) {
            throw new RoomNotFoundException("Rooms are created at /api/rooms, without a code.");
        }
        RoomSettingsInfo body = readJson(request, RoomSettingsInfo.class);
        RoomRecord room = app().rooms().create(currentUser(request), RoomSettings.from(body));
        writeJson(response, HttpServletResponse.SC_CREATED, new CreateRoomResponse(room.code()));
    }

    @Override
    protected void handleGet(HttpServletRequest request, HttpServletResponse response)
            throws PokerException, IOException {
        String code = codeIn(request);
        if (code == null) {
            throw new RoomNotFoundException("Give the room's code in the address, as in /api/rooms/ABC234.");
        }
        writeJson(response, HttpServletResponse.SC_OK, app().rooms().preview(code));
    }

    /** The code after {@code /api/rooms/}, or null if there is none. */
    private static String codeIn(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return null;
        }
        return path.substring(1);
    }
}
