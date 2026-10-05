package com.saksham.poker.client.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormattersTest {

    @Test
    void chipsHaveThousandsSeparators() {
        assertThat(Formatters.chips(0)).isEqualTo("0");
        assertThat(Formatters.chips(950)).isEqualTo("950");
        assertThat(Formatters.chips(12_500)).isEqualTo("12,500");
        assertThat(Formatters.chips(1_000_000)).isEqualTo("1,000,000");
    }

    @Test
    void winsAndLossesCarryTheirSign() {
        assertThat(Formatters.signed(300)).isEqualTo("+300");
        assertThat(Formatters.signed(-1_250)).isEqualTo("-1,250");
        assertThat(Formatters.signed(0)).isEqualTo("0");
    }

    @Test
    void seatsAreCountedFromOneForPlayers() {
        assertThat(Formatters.seat(0)).isEqualTo("Seat 1");
        assertThat(Formatters.seat(8)).isEqualTo("Seat 9");
    }
}
