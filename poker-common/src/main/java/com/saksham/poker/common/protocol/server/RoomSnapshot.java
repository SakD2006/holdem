package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.HandInfo;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import java.util.List;

/** Everything a player needs to draw the room from nothing: sent on joining, on reconnecting and on request. */
@JsonTypeName("ROOM_SNAPSHOT")
public final class RoomSnapshot extends ServerMessage {

    private final String code;
    private final RoomSettingsInfo settings;
    private final RoomState state;
    private final long hostUserId;
    private final List<PlayerInfo> players;
    private final HandInfo hand;
    private final long yourUserId;
    private final int yourSeat;
    private final List<Card> yourCards;

    @JsonCreator
    public RoomSnapshot(
            @JsonProperty("code") String code,
            @JsonProperty("settings") RoomSettingsInfo settings,
            @JsonProperty("state") RoomState state,
            @JsonProperty("hostUserId") long hostUserId,
            @JsonProperty("players") List<PlayerInfo> players,
            @JsonProperty("hand") HandInfo hand,
            @JsonProperty("yourUserId") long yourUserId,
            @JsonProperty("yourSeat") int yourSeat,
            @JsonProperty("yourCards") List<Card> yourCards) {
        this.code = code;
        this.settings = settings;
        this.state = state;
        this.hostUserId = hostUserId;
        this.players = List.copyOf(players);
        this.hand = hand;
        this.yourUserId = yourUserId;
        this.yourSeat = yourSeat;
        this.yourCards = List.copyOf(yourCards);
    }

    public String code() {
        return code;
    }

    public RoomSettingsInfo settings() {
        return settings;
    }

    public RoomState state() {
        return state;
    }

    public long hostUserId() {
        return hostUserId;
    }

    /** Everyone in the room, seated or not. */
    public List<PlayerInfo> players() {
        return players;
    }

    /** The hand being played, or null between hands. */
    public HandInfo hand() {
        return hand;
    }

    public long yourUserId() {
        return yourUserId;
    }

    /** Your seat, or -1 if you have not sat down. */
    public int yourSeat() {
        return yourSeat;
    }

    /** Your hole cards in the current hand; empty if you have none. */
    public List<Card> yourCards() {
        return yourCards;
    }
}
