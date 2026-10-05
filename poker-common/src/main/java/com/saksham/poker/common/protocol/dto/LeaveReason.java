package com.saksham.poker.common.protocol.dto;

/** Why a player is no longer in a room. */
public enum LeaveReason {
    /** They chose to leave. */
    LEFT,
    /** The host removed them. */
    KICKED,
    /** They lost their connection and did not come back in time. */
    DISCONNECTED
}
