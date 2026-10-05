package com.saksham.poker.client.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.client.util.SoundPlayer.Sound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SoundPlayerTest {

    private static int sample(byte[] bytes, int index) {
        return (short) ((bytes[2 * index] & 0xff) | (bytes[2 * index + 1] << 8));
    }

    @ParameterizedTest
    @EnumSource(Sound.class)
    void everySoundIsShortAudibleAndNeverDistorted(Sound sound) {
        byte[] bytes = SoundPlayer.samples(sound);
        int frames = bytes.length / 2;
        int peak = 0;
        for (int i = 0; i < frames; i++) {
            peak = Math.max(peak, Math.abs(sample(bytes, i)));
        }

        assertThat(bytes.length % 2).isZero();
        assertThat(frames / SoundPlayer.RATE).as("seconds").isBetween(0.1f, 1.0f);
        assertThat(peak).as("loud enough to hear").isGreaterThan(3_000);
        // Well short of the largest value a sample can hold, where a sound starts to crackle.
        assertThat(peak).as("quiet enough not to clip").isLessThan(30_000);
    }

    @ParameterizedTest
    @EnumSource(Sound.class)
    void everySoundStartsAndEndsInSilenceSoItDoesNotClick(Sound sound) {
        byte[] bytes = SoundPlayer.samples(sound);
        int last = bytes.length / 2 - 1;

        assertThat(Math.abs(sample(bytes, 0))).isLessThan(400);
        assertThat(Math.abs(sample(bytes, last))).isLessThan(400);
    }

    @Test
    void aSoundIsTheSameEveryTimeItIsMade() {
        assertThat(SoundPlayer.samples(Sound.DEAL)).isEqualTo(SoundPlayer.samples(Sound.DEAL));
    }

    @Test
    void soundIsOffUntilTheAppTurnsItOn() {
        assertThat(SoundPlayer.enabled()).isFalse();
        SoundPlayer.play(Sound.CHECK); // does nothing, and must not throw
    }
}
