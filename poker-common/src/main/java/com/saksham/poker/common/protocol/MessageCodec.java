package com.saksham.poker.common.protocol;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saksham.poker.common.exception.ProtocolException;
import java.nio.charset.StandardCharsets;

/**
 * Turns messages into JSON text and back. The server, the desktop app and the bots all use it, so
 * they cannot disagree about the format.
 *
 * <p>On the wire a message is an envelope: {@code {"type":"JOIN_ROOM","seq":7,"payload":{"code":"ABC234"}}}.
 *
 * <p>Thread-safe.
 */
public final class MessageCodec {

    /** The largest message, in bytes of UTF-8, that may be sent or will be accepted. */
    public static final int MAX_BYTES = 8 * 1024;

    private static final String TYPE = "type";
    private static final String SEQ = "seq";
    private static final String PAYLOAD = "payload";

    private final ObjectMapper mapper = JsonMapper.builder()
            // Messages expose their data as record-style methods, so read and write fields directly.
            .visibility(PropertyAccessor.GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.SETTER, Visibility.NONE)
            .visibility(PropertyAccessor.FIELD, Visibility.ANY)
            // A message with a field left out is malformed; an extra field from a newer version is not.
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Writes a message as JSON text.
     *
     * @param message the message to send
     * @param seq its sequence number on this connection
     * @throws ProtocolException if the result would be larger than {@link #MAX_BYTES}
     */
    public String encode(Message message, long seq) throws ProtocolException {
        ObjectNode payload = mapper.valueToTree(message);
        payload.remove(TYPE);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put(TYPE, message.type());
        envelope.put(SEQ, seq);
        envelope.set(PAYLOAD, payload);
        String text = envelope.toString();
        requireSize(text, "send");
        return text;
    }

    /** Reads a message sent by a client. */
    public Envelope<ClientMessage> decodeClient(String text) throws ProtocolException {
        return decode(text, ClientMessage.class);
    }

    /** Reads a message sent by the server. */
    public Envelope<ServerMessage> decodeServer(String text) throws ProtocolException {
        return decode(text, ServerMessage.class);
    }

    private <M extends Message> Envelope<M> decode(String text, Class<M> family) throws ProtocolException {
        if (text == null) {
            throw new ProtocolException("The message is empty.");
        }
        requireSize(text, "accept");
        try {
            JsonNode envelope = mapper.readTree(text);
            if (envelope == null || !envelope.isObject()) {
                throw new ProtocolException("A message must be a JSON object with a \"type\".");
            }
            JsonNode type = envelope.get(TYPE);
            if (type == null || !type.isTextual()) {
                throw new ProtocolException("The message has no \"type\".");
            }
            JsonNode seq = envelope.get(SEQ);
            if (seq != null && !seq.isIntegralNumber()) {
                throw new ProtocolException("The message's \"seq\" must be a whole number.");
            }
            JsonNode payload = envelope.get(PAYLOAD);
            if (payload != null && !payload.isNull() && !payload.isObject()) {
                throw new ProtocolException("The message's \"payload\" must be a JSON object.");
            }
            // The payload may be left out when a message has no fields.
            ObjectNode typed = payload == null || payload.isNull()
                    ? mapper.createObjectNode()
                    : ((ObjectNode) payload).deepCopy();
            typed.set(TYPE, type);
            return new Envelope<>(seq == null ? 0 : seq.asLong(), mapper.treeToValue(typed, family));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ProtocolException("The message could not be read: " + firstLine(e.getMessage()), e);
        }
    }

    private static void requireSize(String text, String verb) throws ProtocolException {
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw new ProtocolException(
                    "The message is " + bytes + " bytes, more than the " + MAX_BYTES + " this connection will "
                            + verb + ".");
        }
    }

    /** Jackson's messages run to several lines of source location; the first says what is wrong. */
    private static String firstLine(String message) {
        if (message == null) {
            return "it is not valid.";
        }
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
