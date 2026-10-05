package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import java.util.List;

/** The players still in the hand showed their cards. */
@JsonTypeName("SHOWDOWN")
public final class Showdown extends ServerMessage {

    private final List<ShownHandInfo> hands;

    @JsonCreator
    public Showdown(@JsonProperty("hands") List<ShownHandInfo> hands) {
        this.hands = List.copyOf(hands);
    }

    /** In the order they were shown. */
    public List<ShownHandInfo> hands() {
        return hands;
    }
}
