package com.saksham.poker.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class JsonSupportTest {

    @Test
    void errorCodesMapToTheUsualHttpStatuses() {
        assertThat(JsonSupport.httpStatus(ErrorCode.INVALID_REQUEST)).isEqualTo(400);
        assertThat(JsonSupport.httpStatus(ErrorCode.MALFORMED_MESSAGE)).isEqualTo(400);
        assertThat(JsonSupport.httpStatus(ErrorCode.UNAUTHORIZED)).isEqualTo(401);
        assertThat(JsonSupport.httpStatus(ErrorCode.INVALID_CREDENTIALS)).isEqualTo(401);
        assertThat(JsonSupport.httpStatus(ErrorCode.NOT_HOST)).isEqualTo(403);
        assertThat(JsonSupport.httpStatus(ErrorCode.ROOM_NOT_FOUND)).isEqualTo(404);
        assertThat(JsonSupport.httpStatus(ErrorCode.USERNAME_TAKEN)).isEqualTo(409);
        assertThat(JsonSupport.httpStatus(ErrorCode.ROOM_FULL)).isEqualTo(409);
        assertThat(JsonSupport.httpStatus(ErrorCode.INTERNAL)).isEqualTo(500);
    }

    @Test
    void everyErrorCodeIsAnErrorStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(JsonSupport.httpStatus(code)).as(code.name()).isBetween(400, 500);
        }
    }

    @Test
    void theTokenIsReadFromABearerHeader() {
        assertThat(AuthFilter.bearerToken("Bearer abc123")).isEqualTo("abc123");
        assertThat(AuthFilter.bearerToken("bearer abc123 ")).isEqualTo("abc123");
        assertThat(AuthFilter.bearerToken(null)).isNull();
        assertThat(AuthFilter.bearerToken("")).isNull();
        assertThat(AuthFilter.bearerToken("Basic abc123")).isNull();
        assertThat(AuthFilter.bearerToken("abc123")).isNull();
    }
}
