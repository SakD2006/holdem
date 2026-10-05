package com.saksham.poker.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.ChatPosted;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.GameState;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.HostChanged;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PlayerJoined;
import com.saksham.poker.common.protocol.server.PlayerLeft;
import com.saksham.poker.common.protocol.server.Pong;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;

/** A message the server sends to the desktop app or a bot. */
@JsonSubTypes({
    @JsonSubTypes.Type(RoomSnapshot.class),
    @JsonSubTypes.Type(PlayerJoined.class),
    @JsonSubTypes.Type(PlayerLeft.class),
    @JsonSubTypes.Type(SeatUpdate.class),
    @JsonSubTypes.Type(HostChanged.class),
    @JsonSubTypes.Type(GameState.class),
    @JsonSubTypes.Type(HandStarted.class),
    @JsonSubTypes.Type(BlindPosted.class),
    @JsonSubTypes.Type(HoleCards.class),
    @JsonSubTypes.Type(ActionRequired.class),
    @JsonSubTypes.Type(PlayerActed.class),
    @JsonSubTypes.Type(StreetDealt.class),
    @JsonSubTypes.Type(BetReturned.class),
    @JsonSubTypes.Type(PotsUpdated.class),
    @JsonSubTypes.Type(Showdown.class),
    @JsonSubTypes.Type(HandEnded.class),
    @JsonSubTypes.Type(ChatPosted.class),
    @JsonSubTypes.Type(ErrorMessage.class),
    @JsonSubTypes.Type(Pong.class)
})
public abstract class ServerMessage extends Message {
}
