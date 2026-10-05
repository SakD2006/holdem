package com.saksham.poker.common.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.Kick;
import com.saksham.poker.common.protocol.client.LeaveRoom;
import com.saksham.poker.common.protocol.client.PauseGame;
import com.saksham.poker.common.protocol.client.Ping;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.RequestSnapshot;
import com.saksham.poker.common.protocol.client.ResumeGame;
import com.saksham.poker.common.protocol.client.SendChat;
import com.saksham.poker.common.protocol.client.SitIn;
import com.saksham.poker.common.protocol.client.SitOut;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.client.TakeSeat;

/** A message the desktop app or a bot sends to the server. */
@JsonSubTypes({
    @JsonSubTypes.Type(JoinRoom.class),
    @JsonSubTypes.Type(TakeSeat.class),
    @JsonSubTypes.Type(LeaveRoom.class),
    @JsonSubTypes.Type(StartGame.class),
    @JsonSubTypes.Type(PauseGame.class),
    @JsonSubTypes.Type(ResumeGame.class),
    @JsonSubTypes.Type(Kick.class),
    @JsonSubTypes.Type(EndRoom.class),
    @JsonSubTypes.Type(SubmitAction.class),
    @JsonSubTypes.Type(SitOut.class),
    @JsonSubTypes.Type(SitIn.class),
    @JsonSubTypes.Type(Rebuy.class),
    @JsonSubTypes.Type(SendChat.class),
    @JsonSubTypes.Type(RequestSnapshot.class),
    @JsonSubTypes.Type(Ping.class)
})
public abstract class ClientMessage extends Message {
}
