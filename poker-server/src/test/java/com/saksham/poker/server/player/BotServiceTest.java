package com.saksham.poker.server.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.Strategies;
import com.saksham.poker.common.protocol.dto.BotLevel;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BotServiceTest {

    @Test
    void everyLevelHasNamesAStrategyAndALabel() {
        for (BotLevel level : BotLevel.values()) {
            assertThat(BotService.NAMES).containsKey(level);
            assertThat(Strategies.forLevel(level)).isNotNull();
            assertThat(level.label()).isNotBlank();
        }
    }

    @Test
    void namesAreValidUsernamesAndNeverSharedBetweenLevels() {
        Set<String> seen = new HashSet<>();
        for (BotLevel level : BotLevel.values()) {
            for (int number = 0; number < 40; number++) {
                String name = BotService.nameFor(level, number);
                assertThat(name).matches("[A-Za-z0-9_]{3,24}");
                assertThat(seen.add(name)).as(name).isTrue();
            }
        }
        assertThat(BotService.nameFor(BotLevel.EASY, 0)).isEqualTo("Ada_bot");
        assertThat(BotService.nameFor(BotLevel.EASY, 6)).isEqualTo("Ada_bot2");
        assertThat(BotService.nameFor(BotLevel.MEDIUM, 0)).isEqualTo("Gauss_bot");
    }
}
