package com.saksham.poker.client.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ServerAddressTest {

    @Test
    void aBareAddressUsesTheUsualPort() {
        ServerAddress address = ServerAddress.parse(" 192.168.1.20 ");

        assertThat(address).isEqualTo(new ServerAddress("192.168.1.20", 8080));
        assertThat(address.display()).isEqualTo("192.168.1.20");
        assertThat(address.apiBase()).isEqualTo("http://192.168.1.20:8080/poker/api");
        assertThat(address.gameSocketUrl("abc")).isEqualTo("ws://192.168.1.20:8080/poker/ws/game?token=abc");
    }

    @Test
    void aPortCanBeGiven() {
        ServerAddress address = ServerAddress.parse("gamebox.local:9090");

        assertThat(address).isEqualTo(new ServerAddress("gamebox.local", 9090));
        assertThat(address.display()).isEqualTo("gamebox.local:9090");
    }

    @Test
    void theWholeAddressFromTheServerLogCanBePastedIn() {
        assertThat(ServerAddress.parse("http://192.168.1.20:8080/poker")).isEqualTo(new ServerAddress("192.168.1.20", 8080));
        assertThat(ServerAddress.parse("http://192.168.1.20/poker/")).isEqualTo(new ServerAddress("192.168.1.20", 8080));
    }

    @Test
    void mistakesAreExplained() {
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse("")).withMessageContaining("address");
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse(null));
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse("192.168.1.20:eighty"))
                .withMessageContaining("port").withMessageContaining("eighty");
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse("192.168.1.20:99999"));
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse("my computer"))
                .withMessageContaining("no spaces");
        assertThatIllegalArgumentException().isThrownBy(() -> ServerAddress.parse(":8080"));
    }
}
