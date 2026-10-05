package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import org.junit.jupiter.api.Test;

class RoomSettingsTest {

    private static RoomSettings settings(String name, int players, long small, long big, long stack, int turn) {
        return new RoomSettings(name, players, small, big, stack, turn, true, false);
    }

    private static void assertRefused(RoomSettings settings, String mention) {
        assertThatThrownBy(settings::validate)
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining(mention);
    }

    @Test
    void sensibleSettingsAreAccepted() {
        assertThatCode(() -> settings("Friday game", 6, 50, 100, 10_000, 25).validate()).doesNotThrowAnyException();
        assertThatCode(() -> settings("x", 2, 1, 1, 1, 10).validate()).doesNotThrowAnyException();
        assertThatCode(() -> settings("x".repeat(40), 9, 1, 2, 1_000_000_000L, 60).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void theNameIsTrimmedAndMustBe1To40Characters() {
        assertThat(settings("  Friday game  ", 6, 50, 100, 10_000, 25).name()).isEqualTo("Friday game");
        assertRefused(settings("   ", 6, 50, 100, 10_000, 25), "name");
        assertRefused(settings(null, 6, 50, 100, 10_000, 25), "name");
        assertRefused(settings("x".repeat(41), 6, 50, 100, 10_000, 25), "name");
    }

    @Test
    void aRoomHas2To9Players() {
        assertRefused(settings("g", 1, 50, 100, 10_000, 25), "players");
        assertRefused(settings("g", 10, 50, 100, 10_000, 25), "players");
    }

    @Test
    void blindsAndStackMustFitTogether() {
        assertRefused(settings("g", 6, 0, 100, 10_000, 25), "small blind");
        assertRefused(settings("g", 6, 100, 50, 10_000, 25), "big blind");
        assertRefused(settings("g", 6, 50, 100, 99, 25), "starting stack");
        assertRefused(settings("g", 6, 50, 100, 1_000_000_001L, 25), "starting stack");
    }

    @Test
    void theTurnTimeIs10To60Seconds() {
        assertRefused(settings("g", 6, 50, 100, 10_000, 9), "turn time");
        assertRefused(settings("g", 6, 50, 100, 10_000, 61), "turn time");
    }

    @Test
    void settingsConvertToAndFromWhatClientsSend() {
        RoomSettingsInfo info = new RoomSettingsInfo("Friday game", 6, 50, 100, 10_000, 25, true);

        RoomSettings settings = RoomSettings.from(info);

        assertThat(settings.allowBots()).isFalse();
        assertThat(settings.toInfo()).isEqualTo(info);
    }
}
