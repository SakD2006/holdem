package com.saksham.poker.common.protocol;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * One message on the game WebSocket. Each kind of message is a subclass, named on the wire by its
 * {@code type} (SPEC §5). Messages are immutable.
 *
 * <p>The same type name can mean different things in each direction ({@code CHAT} is "say this"
 * going up and "someone said this" coming down), so the two directions are separate families:
 * {@link ClientMessage} and {@link ServerMessage}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public abstract class Message {

    /** The name of this kind of message on the wire, such as {@code JOIN_ROOM}. */
    public final String type() {
        return getClass().getAnnotation(JsonTypeName.class).value();
    }

    @Override
    public String toString() {
        return type();
    }
}
