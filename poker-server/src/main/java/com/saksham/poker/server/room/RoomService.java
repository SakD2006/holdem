package com.saksham.poker.server.room;

import com.saksham.poker.common.api.RoomPreview;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.PersistenceException;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.db.RoomDao;
import com.saksham.poker.server.db.RoomRecord;
import com.saksham.poker.server.db.User;
import com.saksham.poker.server.db.UserDao;
import java.util.Optional;

/** Creates rooms and looks them up by code. */
public final class RoomService {

    /** With 887 million possible codes a clash is rare; this many in a row means something is wrong. */
    private static final int MAX_CODE_ATTEMPTS = 20;

    private final RoomDao rooms;
    private final UserDao users;
    private final RoomCodeGenerator codes;
    private final RoomManager manager;

    public RoomService(RoomDao rooms, UserDao users, RoomCodeGenerator codes, RoomManager manager) {
        this.rooms = rooms;
        this.users = users;
        this.codes = codes;
        this.manager = manager;
    }

    /**
     * Creates a room hosted by the given user and opens it for players to join.
     *
     * @return the new room, with its code
     * @throws InvalidRequestException if a setting is out of range
     */
    public RoomRecord create(User host, RoomSettings settings) throws InvalidRequestException {
        settings.validate();
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            // A code is never reused: the database refuses one that any room, open or closed, has had.
            Optional<RoomRecord> room = rooms.create(codes.next(), host.id(), settings);
            if (room.isPresent()) {
                manager.open(room.get().code(), host.id(), settings);
                return room.get();
            }
        }
        throw new PersistenceException("Could not find an unused room code after " + MAX_CODE_ATTEMPTS + " tries.");
    }

    /**
     * What a player is shown before joining.
     *
     * @param typedCode the code as the player typed it; spaces and lower case are tolerated
     * @throws RoomNotFoundException if no room has this code
     */
    public RoomPreview preview(String typedCode) throws RoomNotFoundException {
        String code = RoomCodeGenerator.normalize(typedCode);
        Optional<RoomRecord> found = code == null ? Optional.empty() : rooms.findByCode(code);
        if (found.isEmpty()) {
            throw new RoomNotFoundException(
                    "There is no room with the code \"" + typedCode + "\". Check the code with the host.");
        }
        RoomRecord room = found.get();
        String host = users.findById(room.hostUserId()).map(User::username).orElse("");
        // The database knows a room's settings; only the live room knows who is sitting in it now.
        Optional<Room> live = manager.find(room.code());
        return new RoomPreview(room.code(), room.settings().toInfo(),
                live.map(Room::state).orElse(RoomState.CLOSED), host, live.map(Room::seatedCount).orElse(0));
    }
}
