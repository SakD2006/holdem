package com.saksham.poker.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    /** Few rounds, so the tests are quick. */
    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void aPasswordMatchesItsOwnHashAndNoOther() {
        String hash = hasher.hash("correct horse");

        assertThat(hasher.verify("correct horse", hash)).isTrue();
        assertThat(hasher.verify("correct horse ", hash)).isFalse();
        assertThat(hasher.verify("Correct horse", hash)).isFalse();
        assertThat(hasher.verify("", hash)).isFalse();
    }

    @Test
    void theHashRecordsHowItWasMadeAndNeverHoldsThePassword() {
        String hash = hasher.hash("correct horse");

        assertThat(hash).startsWith("pbkdf2$1000$").doesNotContain("correct horse");
        assertThat(hash.split("\\$")).hasSize(4);
    }

    @Test
    void theSamePasswordHashesDifferentlyEachTime() {
        assertThat(hasher.hash("correct horse")).isNotEqualTo(hasher.hash("correct horse"));
    }

    @Test
    void newHashesUse210000RoundsByDefault() {
        String hash = new PasswordHasher().hash("correct horse");

        assertThat(hash).startsWith("pbkdf2$210000$");
        assertThat(new PasswordHasher().verify("correct horse", hash)).isTrue();
    }

    @Test
    void aHashMadeWithOtherSettingsStillVerifies() {
        String old = new PasswordHasher(500).hash("correct horse");

        assertThat(hasher.verify("correct horse", old)).isTrue();
    }

    @Test
    void damagedOrMissingValuesNeverVerify() {
        String hash = hasher.hash("correct horse");

        assertThat(hasher.verify("correct horse", null)).isFalse();
        assertThat(hasher.verify(null, hash)).isFalse();
        assertThat(hasher.verify("correct horse", "")).isFalse();
        assertThat(hasher.verify("correct horse", "plain-text-password")).isFalse();
        assertThat(hasher.verify("correct horse", "md5$1000$abc$def")).isFalse();
        assertThat(hasher.verify("correct horse", "pbkdf2$many$abc$def")).isFalse();
        assertThat(hasher.verify("correct horse", "pbkdf2$0$abc$def")).isFalse();
        assertThat(hasher.verify("correct horse", "pbkdf2$1000$not base64!$def")).isFalse();
        assertThat(hasher.verify("correct horse", hash.substring(0, hash.length() - 4) + "AAA=")).isFalse();
    }

    @Test
    void roundsMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHasher(0));
    }
}
