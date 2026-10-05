package com.saksham.poker.engine.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.saksham.poker.common.card.Card;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeckTest {

    private static List<Card> dealAll(Deck deck) {
        List<Card> dealt = new ArrayList<>();
        while (deck.remaining() > 0) {
            dealt.add(deck.deal());
        }
        return dealt;
    }

    @Test
    void standardOrderHasAll52CardsOnce() {
        assertThat(new HashSet<>(Deck.standardOrder())).hasSize(52);
        assertThat(Deck.standardOrder()).hasSize(52).startsWith(Card.parse("2c")).endsWith(Card.parse("As"));
    }

    @Test
    void dealsCardsInTheGivenOrder() {
        Deck deck = new Deck(Deck.standardOrder());

        assertThat(deck.deal()).isEqualTo(Card.parse("2c"));
        assertThat(deck.deal()).isEqualTo(Card.parse("3c"));
        assertThat(deck.remaining()).isEqualTo(50);
    }

    @Test
    void burningDiscardsTheTopCard() {
        Deck deck = new Deck(Deck.standardOrder());

        deck.burn();

        assertThat(deck.remaining()).isEqualTo(51);
        assertThat(deck.deal()).isEqualTo(Card.parse("3c"));
    }

    @Test
    void cannotDealOrBurnFromAnEmptyDeck() {
        Deck deck = new Deck(Deck.standardOrder());
        dealAll(deck);

        assertThatIllegalStateException().isThrownBy(deck::deal);
        assertThatIllegalStateException().isThrownBy(deck::burn);
    }

    @Test
    void rejectsADeckThatIsNotAll52Cards() {
        List<Card> short51 = Deck.standardOrder().subList(0, 51);
        List<Card> repeated = new ArrayList<>(short51);
        repeated.add(Card.parse("2c"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Deck(short51));
        assertThatIllegalArgumentException().isThrownBy(() -> new Deck(repeated));
    }

    @Test
    void secureFactoryShufflesAFullDeckDifferentlyEachTime() {
        DeckFactory factory = new SecureDeckFactory();

        List<Card> first = dealAll(factory.create());
        List<Card> second = dealAll(factory.create());

        assertThat(new HashSet<>(first)).hasSize(52);
        assertThat(new HashSet<>(second)).hasSize(52);
        // Two shuffles matching, or a shuffle leaving the deck untouched, has a chance of 1 in 52!.
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(Deck.standardOrder());
    }

    @Test
    void stackedFactoryDealsTheChosenCardsFirstThenTheRest() {
        DeckFactory factory = StackedDeckFactory.of("Ah Kd 2c");

        List<Card> dealt = dealAll(factory.create());

        assertThat(dealt).startsWith(Card.parse("Ah"), Card.parse("Kd"), Card.parse("2c"));
        assertThat(dealt.get(3)).isEqualTo(Card.parse("3c"));
        Set<Card> unique = new HashSet<>(dealt);
        assertThat(unique).hasSize(52);
    }

    @Test
    void stackedFactoryGivesTheSameOrderEveryTime() {
        DeckFactory factory = StackedDeckFactory.of("Ah Kd");

        assertThat(dealAll(factory.create())).isEqualTo(dealAll(factory.create()));
    }

    @Test
    void stackedFactoryRejectsARepeatedCard() {
        assertThatIllegalArgumentException().isThrownBy(() -> StackedDeckFactory.of("Ah Kd Ah"));
    }
}
