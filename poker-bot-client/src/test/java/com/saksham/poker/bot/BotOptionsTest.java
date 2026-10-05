package com.saksham.poker.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BotOptionsTest {

    private static BotOptions parse(String... args) {
        return BotOptions.parse(args);
    }

    @Test
    void onlyTheServerIsRequired() {
        BotOptions options = parse("--server", "192.168.1.20");

        assertThat(options.server()).isEqualTo("192.168.1.20");
        assertThat(options.port()).isEqualTo(8080);
        assertThat(options.room()).isNull();
        assertThat(options.bots()).isEqualTo(3);
        assertThat(options.hands()).isEqualTo(100);
        // By default bots pause before acting, so a person playing with them can follow.
        assertThat(options.thinkMinMs()).isEqualTo(800);
        assertThat(options.thinkMaxMs()).isEqualTo(2_500);
        assertThat(options.httpBase()).isEqualTo("http://192.168.1.20:8080/poker/api");
        assertThat(options.webSocketUrl("abc")).isEqualTo("ws://192.168.1.20:8080/poker/ws/game?token=abc");
    }

    @Test
    void everyOptionCanBeGiven() {
        BotOptions options = parse("--server", "127.0.0.1", "--port", "9090", "--room", "abc234", "--bots", "6",
                "--hands", "500", "--idle", "30");

        assertThat(options.port()).isEqualTo(9090);
        assertThat(options.room()).isEqualTo("ABC234");
        assertThat(options.bots()).isEqualTo(6);
        assertThat(options.hands()).isEqualTo(500);
        assertThat(options.idleSeconds()).isEqualTo(30);
    }

    @Test
    void thinkingTimeCanBeARangeOneNumberOrOff() {
        BotOptions range = parse("--server", "x", "--think", "300-900");
        assertThat(range.thinkMinMs()).isEqualTo(300);
        assertThat(range.thinkMaxMs()).isEqualTo(900);

        BotOptions fixed = parse("--server", "x", "--think", "1500");
        assertThat(fixed.thinkMinMs()).isEqualTo(1_500);
        assertThat(fixed.thinkMaxMs()).isEqualTo(1_500);

        BotOptions off = parse("--server", "x", "--think", "0");
        assertThat(off.thinkMinMs()).isZero();
        assertThat(off.thinkMaxMs()).isZero();

        for (String bad : new String[] {"900-300", "fast", "1-2-3", "-5", "100-", "999999"}) {
            assertThatIllegalArgumentException().as(bad)
                    .isThrownBy(() -> parse("--server", "x", "--think", bad)).withMessageContaining("--think");
        }
    }

    @Test
    void mistakesAreNamed() {
        assertThatIllegalArgumentException().isThrownBy(() -> parse()).withMessageContaining("--server");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("--server")).withMessageContaining("needs a value");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("--server", "x", "--speed", "9"))
                .withMessageContaining("--speed");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("--server", "x", "--bots", "ten"))
                .withMessageContaining("--bots").withMessageContaining("ten");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("--server", "x", "--bots", "10"))
                .withMessageContaining("1 to 9");
    }

    @Test
    void aNewRoomNeedsTwoBotsButJoiningARoomCanUseOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> parse("--server", "x", "--bots", "1"))
                .withMessageContaining("at least 2");
        assertThat(parse("--server", "x", "--bots", "1", "--room", "ABC234").bots()).isEqualTo(1);
    }
}
