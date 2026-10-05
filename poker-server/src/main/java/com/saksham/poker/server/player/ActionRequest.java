package com.saksham.poker.server.player;

import com.saksham.poker.common.protocol.dto.TurnInfo;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.engine.rules.LegalActions;

/**
 * A request for a player to act.
 *
 * @param seat the seat being asked
 * @param turnId identifies this turn; an answer must carry it, and an answer to an older turn is ignored
 * @param legal what the player may do
 * @param deadlineEpochMs when the turn times out, in milliseconds since 1970 UTC
 */
public record ActionRequest(int seat, long turnId, LegalActions legal, long deadlineEpochMs) {

    /** The message that tells clients about this turn. */
    public ActionRequired toMessage() {
        return new ActionRequired(seat, turnId, legal.canCheck(), legal.callAmount(), legal.canBet(),
                legal.canRaise(), legal.minRaiseTo(), legal.maxRaiseTo(), deadlineEpochMs);
    }

    /** The same turn, as it appears inside a snapshot. */
    public TurnInfo toTurnInfo() {
        return new TurnInfo(seat, turnId, legal.canCheck(), legal.callAmount(), legal.canBet(),
                legal.canRaise(), legal.minRaiseTo(), legal.maxRaiseTo(), deadlineEpochMs);
    }
}
