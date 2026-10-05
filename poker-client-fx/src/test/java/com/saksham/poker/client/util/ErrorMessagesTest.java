package com.saksham.poker.client.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.error.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorMessagesTest {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAPlainSentenceOfItsOwn(ErrorCode code) {
        String text = ErrorMessages.plain(code);

        assertThat(text).isNotBlank().endsWith(".");
        // Words for a player, not the name of the code.
        assertThat(text).doesNotContain(code.name()).doesNotContain("_");
    }

    @Test
    void noTwoCodesShareASentence() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(seen.add(ErrorMessages.plain(code))).as(code.name()).isTrue();
        }
    }

    @Test
    void theServersOwnSentenceIsPreferredBecauseItKnowsTheDetails() {
        assertThat(ErrorMessages.text(ErrorCode.INVALID_AMOUNT, "The smallest raise is to 400."))
                .isEqualTo("The smallest raise is to 400.");
        assertThat(ErrorMessages.text(ErrorCode.INVALID_AMOUNT, null))
                .isEqualTo(ErrorMessages.plain(ErrorCode.INVALID_AMOUNT));
        assertThat(ErrorMessages.text(ErrorCode.ROOM_FULL, "  ")).isEqualTo(ErrorMessages.plain(ErrorCode.ROOM_FULL));
    }

    @Test
    void technicalDetailsAreNeverShownToThePlayer() {
        assertThat(ErrorMessages.text(ErrorCode.MALFORMED_MESSAGE, "Unrecognized field \"amt\" at line 1"))
                .isEqualTo(ErrorMessages.plain(ErrorCode.MALFORMED_MESSAGE));
        assertThat(ErrorMessages.text(ErrorCode.INTERNAL, "NullPointerException"))
                .isEqualTo(ErrorMessages.plain(ErrorCode.INTERNAL));
    }

    @Test
    void aFailureWithNoCodeStillSaysSomething() {
        assertThat(ErrorMessages.text(null, "Could not reach the server.")).isEqualTo("Could not reach the server.");
        assertThat(ErrorMessages.text(null, null)).isNotBlank();
    }
}
