package com.saksham.poker.common.protocol.server;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;

/** A request was refused, or something went wrong. */
@JsonTypeName("ERROR")
public final class ErrorMessage extends ServerMessage {

    private final ErrorCode code;
    private final String message;

    @JsonCreator
    public ErrorMessage(@JsonProperty("code") ErrorCode code, @JsonProperty("message") String message) {
        this.code = code;
        this.message = message;
    }

    public ErrorCode code() {
        return code;
    }

    /** What went wrong and how to fix it, in plain words. */
    public String message() {
        return message;
    }
}
