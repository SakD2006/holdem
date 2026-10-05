package com.saksham.poker.server.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.saksham.poker.common.api.ApiError;
import com.saksham.poker.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writing JSON answers, shared by the servlets and the login filter. */
final class JsonSupport {

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonSupport() {
    }

    static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), body);
    }

    static void writeError(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        write(response, httpStatus(code), new ApiError(code, message));
    }

    /** The HTTP status that goes with each error code. */
    static int httpStatus(ErrorCode code) {
        switch (code) {
            case INVALID_REQUEST:
            case MALFORMED_MESSAGE:
            case INVALID_ACTION:
            case INVALID_AMOUNT:
                return HttpServletResponse.SC_BAD_REQUEST;
            case UNAUTHORIZED:
            case INVALID_CREDENTIALS:
                return HttpServletResponse.SC_UNAUTHORIZED;
            case NOT_HOST:
                return HttpServletResponse.SC_FORBIDDEN;
            case ROOM_NOT_FOUND:
                return HttpServletResponse.SC_NOT_FOUND;
            case INTERNAL:
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            default:
                // The request was fine but does not fit the current state: name taken, room full...
                return HttpServletResponse.SC_CONFLICT;
        }
    }
}
