package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PotInfo;
import java.util.List;

/** A street's bets were collected into the pots. */
@JsonTypeName("POTS_UPDATED")
public final class PotsUpdated extends ServerMessage {

    private final List<PotInfo> pots;

    @JsonCreator
    public PotsUpdated(@JsonProperty("pots") List<PotInfo> pots) {
        this.pots = List.copyOf(pots);
    }

    /** The main pot first, then side pots. */
    public List<PotInfo> pots() {
        return pots;
    }
}
