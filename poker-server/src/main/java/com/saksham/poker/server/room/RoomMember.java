package com.saksham.poker.server.room;

import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.server.player.SeatController;

/** One person in a room, seated or not. Only the room's thread reads or changes it. */
final class RoomMember {

    final long userId;
    final String username;
    SeatController controller;
    /** A computer player. Fixed when it joins. */
    final boolean bot;

    /** Seat number, or {@link PlayerInfo#NO_SEAT}. */
    int seat = PlayerInfo.NO_SEAT;
    /** Chips when not in a hand. During a hand the live figure is in the room's {@link HandView}. */
    long stack;
    /** Not dealt in from the next hand. */
    boolean sittingOut;
    /** Dealt in only once the big blind reaches this seat. */
    boolean waitingForBigBlind;
    /** For the rest of the current hand, check or fold at once instead of waiting for the player. */
    boolean autoAct;
    boolean connected = true;
    /** Goes up on every disconnect and reconnect, so an old "grace expired" timer can be told apart. */
    long connectionGeneration;
    long lastChatAtMs = Long.MIN_VALUE / 2;

    RoomMember(long userId, String username, SeatController controller) {
        this.userId = userId;
        this.username = username;
        this.controller = controller;
        this.bot = controller.isBot();
    }

    boolean seated() {
        return seat != PlayerInfo.NO_SEAT;
    }

    /** Can be dealt into a hand: seated, sitting in, and has chips. */
    boolean readyToPlay() {
        return seated() && !sittingOut && stack > 0;
    }
}
