package com.saksham.poker.common.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CardTest {

    @Test
    void parsesRankThenSuit() {
        assertThat(Card.parse("Ah")).isEqualTo(new Card(Rank.ACE, Suit.HEARTS));
        assertThat(Card.parse("Tc")).isEqualTo(new Card(Rank.TEN, Suit.CLUBS));
        assertThat(Card.parse("2s")).isEqualTo(new Card(Rank.TWO, Suit.SPADES));
        assertThat(Card.parse("Kd")).isEqualTo(new Card(Rank.KING, Suit.DIAMONDS));
    }

    @Test
    void everyCardSurvivesFormatThenParse() {
        Set<String> texts = new HashSet<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                Card card = new Card(rank, suit);
                assertThat(Card.parse(card.toString())).isEqualTo(card);
                texts.add(card.toString());
            }
        }
        assertThat(texts).hasSize(52);
    }

    @Test
    void rejectsTextThatIsNotACard() {
        for (String bad : List.of("", "A", "Ahh", "1h", "Ax", "ah", "AH", "hA", "10h")) {
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> Card.parse(bad));
        }
    }

    @Test
    void parsesSeveralCardsWithOrWithoutSpaces() {
        List<Card> expected = List.of(Card.parse("Ah"), Card.parse("Kd"), Card.parse("7c"));
        assertThat(Card.parseAll("Ah Kd 7c")).isEqualTo(expected);
        assertThat(Card.parseAll("AhKd7c")).isEqualTo(expected);
        assertThat(Card.parseAll("  Ah  Kd\t7c ")).isEqualTo(expected);
        assertThat(Card.parseAll("")).isEmpty();
    }

    @Test
    void rejectsSeveralCardsWithALeftoverCharacter() {
        assertThatIllegalArgumentException().isThrownBy(() -> Card.parseAll("Ah K"));
    }

    @Test
    void formatsSeveralCardsWithoutSeparators() {
        assertThat(Card.formatAll(Card.parseAll("Ah Kd"))).isEqualTo("AhKd");
        assertThat(Card.formatAll(List.of())).isEmpty();
    }

    @Test
    void ranksAreOrderedFromTwoToAce() {
        assertThat(Rank.TWO.value()).isEqualTo(2);
        assertThat(Rank.TEN.value()).isEqualTo(10);
        assertThat(Rank.ACE.value()).isEqualTo(14);
        assertThat(Rank.ACE).isGreaterThan(Rank.KING);
        assertThat(Rank.THREE).isGreaterThan(Rank.TWO);
    }
}
