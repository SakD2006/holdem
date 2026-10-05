package com.saksham.poker.server.web;

import com.saksham.poker.server.bootstrap.AppContext;
import com.saksham.poker.server.db.RoomRecord;
import com.saksham.poker.server.lan.NetworkInfo;
import com.saksham.poker.server.room.Room;
import com.saksham.poker.server.web.view.RoomLine;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/** The front page: is the server up, what address do players type, and the latest rooms. */
@WebServlet("")
public class HomeServlet extends PageServlet {

    private static final long serialVersionUID = 1L;
    private static final int ROOMS_SHOWN = 10;

    @Override
    protected String prepare(HttpServletRequest request, HttpServletResponse response) {
        AppContext app = app();
        List<RoomLine> rooms = new ArrayList<>();
        for (RoomRecord room : app.roomRecords().recent(ROOMS_SHOWN)) {
            rooms.add(RoomLine.of(room, app.roomManager().find(room.code()).map(Room::state).orElse(null)));
        }
        request.setAttribute("version", AppContext.SERVER_VERSION);
        request.setAttribute("addresses", NetworkInfo.lanAddresses());
        request.setAttribute("port", app.config().httpPort());
        request.setAttribute("openRooms", app.roomManager().openRooms());
        request.setAttribute("handsPlayed", app.hands().count());
        request.setAttribute("rooms", rooms);
        return "home.jsp";
    }
}
