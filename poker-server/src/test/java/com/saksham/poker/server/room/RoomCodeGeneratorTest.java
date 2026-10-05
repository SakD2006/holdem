package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    private final RoomCodeGenerator generator = new RoomCodeGenerator();

    @Test
    void codesAreSixCharactersFromTheUnambiguousAlphabet() {
        assertThat(RoomCodeGenerator.ALPHABET).hasSize(31).doesNotContain("0", "O", "1", "I", "L");
        for (int i = 0; i < 2_000; i++) {
            assertThat(generator.next()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
        }
    }

    @Test
    void codesAlmostNeverRepeat() {
        // 20,000 codes out of 887 million: a handful of repeats at most would be expected by chance.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen.size()).isGreaterThan(19_990);
    }

    @Test
    void everyCharacterOfTheAlphabetIsUsed() {
        Set<Character> used = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            for (char c : generator.next().toCharArray()) {
                used.add(c);
            }
        }
        assertThat(used).hasSize(RoomCodeGenerator.ALPHABET.length());
    }

    @Test
    void aTypedCodeIsTrimmedAndUpperCased() {
        assertThat(RoomCodeGenerator.normalize("abc234")).isEqualTo("ABC234");
        assertThat(RoomCodeGenerator.normalize("  AbC234 ")).isEqualTo("ABC234");
    }

    @Test
    void textThatCannotBeACodeIsRejected() {
        assertThat(RoomCodeGenerator.normalize(null)).isNull();
        assertThat(RoomCodeGenerator.normalize("")).isNull();
        assertThat(RoomCodeGenerator.normalize("ABC23")).isNull();
        assertThat(RoomCodeGenerator.normalize("ABC2345")).isNull();
        assertThat(RoomCodeGenerator.normalize("ABC 34")).isNull();
        assertThat(RoomCodeGenerator.normalize("ABC10O")).isNull();
    }
}
