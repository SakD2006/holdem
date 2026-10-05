package com.saksham.poker.engine.pot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PotCalculatorTest {

    private static Pot pot(long amount, Integer... eligible) {
        return new Pot(amount, Set.of(eligible));
    }

    @Test
    void noChipsMeansNoPots() {
        assertThat(PotCalculator.calculate(Map.of(1, 0L, 2, 0L), Set.of())).isEmpty();
    }

    @Test
    void equalContributionsMakeOnePot() {
        assertThat(PotCalculator.calculate(Map.of(1, 100L, 2, 100L, 3, 100L), Set.of()))
                .containsExactly(pot(300, 1, 2, 3));
    }

    @Test
    void foldedChipsStayInThePotButCannotWinIt() {
        assertThat(PotCalculator.calculate(Map.of(1, 100L, 2, 100L, 3, 40L), Set.of(3)))
                .containsExactly(pot(240, 1, 2));
    }

    @Test
    void oneShortAllInMakesASidePot() {
        // Seat 1 is all-in for 60; seats 2 and 3 put in 100.
        assertThat(PotCalculator.calculate(Map.of(1, 60L, 2, 100L, 3, 100L), Set.of()))
                .containsExactly(pot(180, 1, 2, 3), pot(80, 2, 3));
    }

    @Test
    void twoAllInsOfDifferentSizes() {
        assertThat(PotCalculator.calculate(Map.of(1, 300L, 2, 700L), Set.of()))
                .containsExactly(pot(600, 1, 2), pot(400, 2));
    }

    @Test
    void threeAllInsOfDifferentSizesPlusACaller() {
        List<Pot> pots = PotCalculator.calculate(Map.of(1, 300L, 2, 700L, 3, 1500L, 4, 1500L), Set.of());

        assertThat(pots).containsExactly(pot(1200, 1, 2, 3, 4), pot(1200, 2, 3, 4), pot(1600, 3, 4));
    }

    @Test
    void sixAllInsOfDifferentSizes() {
        Map<Integer, Long> contributed = Map.of(1, 10L, 2, 20L, 3, 30L, 4, 40L, 5, 50L, 6, 60L);

        List<Pot> pots = PotCalculator.calculate(contributed, Set.of());

        assertThat(pots).containsExactly(
                pot(60, 1, 2, 3, 4, 5, 6),
                pot(50, 2, 3, 4, 5, 6),
                pot(40, 3, 4, 5, 6),
                pot(30, 4, 5, 6),
                pot(20, 5, 6),
                pot(10, 6));
        assertThat(pots.stream().mapToLong(Pot::amount).sum()).isEqualTo(210);
    }

    @Test
    void aFoldedPlayerBetweenTwoLevelsFeedsBothPots() {
        // Seat 4 folded after putting in 500: 300 goes to the main pot and 200 to the side pot.
        assertThat(PotCalculator.calculate(Map.of(1, 300L, 2, 700L, 3, 700L, 4, 500L), Set.of(4)))
                .containsExactly(pot(1200, 1, 2, 3), pot(1000, 2, 3));
    }

    @Test
    void chipsFromAFoldedPlayerAboveEveryoneElseGoInTheLastPot() {
        assertThat(PotCalculator.calculate(Map.of(1, 100L, 2, 30L, 3, 30L), Set.of(1)))
                .containsExactly(pot(160, 2, 3));
        assertThat(PotCalculator.calculate(Map.of(1, 100L, 2, 30L, 3, 60L), Set.of(1)))
                .containsExactly(pot(90, 2, 3), pot(100, 3));
    }

    @Test
    void whenEveryContributorHasFoldedTheRemainingPlayersPlayForItAll() {
        assertThat(PotCalculator.calculate(Map.of(1, 0L, 2, 50L, 3, 100L), Set.of(2, 3)))
                .containsExactly(pot(150, 1));
    }

    @Test
    void rejectsChipsWithNobodyLeftToWinThem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PotCalculator.calculate(Map.of(1, 50L, 2, 100L), Set.of(1, 2)));
    }
}
