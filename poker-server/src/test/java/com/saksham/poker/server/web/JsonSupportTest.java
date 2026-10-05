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
    void pageAndIdNumbersMustBeWholeAndPositive() throws Exception {
        assertThat(HandsServlet.positiveNumber("3", 1, "page")).isEqualTo(3);
        assertThat(HandsServlet.positiveNumber(" 12 ", -1, "hand id")).isEqualTo(12);
        // A missing page means the first page; a missing id is an error.
        assertThat(HandsServlet.positiveNumber(null, 1, "page")).isEqualTo(1);
        assertThat(HandsServlet.positiveNumber("", 1, "page")).isEqualTo(1);
        for (String bad : new String[] {"0", "-2", "two", "1.5", "99999999999"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> HandsServlet.positiveNumber(bad, 1, "page"))
                    .as(bad).isInstanceOf(com.saksham.poker.common.exception.InvalidRequestException.class)
                    .hasMessageContaining("page").hasMessageContaining(bad);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> HandsServlet.positiveNumber(null, -1, "hand id"))
                .isInstanceOf(com.saksham.poker.common.exception.InvalidRequestException.class);
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
