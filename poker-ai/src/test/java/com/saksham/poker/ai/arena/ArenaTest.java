package com.saksham.poker.ai.arena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.saksham.poker.ai.MonteCarloStrategy;
import com.saksham.poker.ai.RuleBasedStrategy;
import com.saksham.poker.ai.RuleBasedStrategy.Style;
import com.saksham.poker.ai.arena.Arena.Entrant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArenaTest {

    private static final List<Entrant> MIXED = List.of(
            new Entrant("easy", new RuleBasedStrategy(Style.EASY)),
            new Entrant("solid", new RuleBasedStrategy(Style.SOLID)),
            new Entrant("simulation", new MonteCarloStrategy(100)),
            new Entrant("caller", new AlwaysCall()));

    @Test
    void whatOneBotWinsAnotherLosesAndNoActionIsEverRefused() {
        ArenaResult result = new Arena(MIXED).play(3_000, 1, 3);

        assertThat(result.hands()).isEqualTo(3_000);
        assertThat(result.illegal()).isZero();
        double total = 0;
        for (double won : result.won()) {
            total += won;
        }
        assertThat(total).isCloseTo(0, within(1e-6));
    }

    @Test
    void theSameSeedReplaysTheSameMatch() {
        ArenaResult first = new Arena(MIXED).play(1_000, 42, 2);
        ArenaResult again = new Arena(MIXED).play(1_000, 42, 2);
        ArenaResult other = new Arena(MIXED).play(1_000, 43, 2);

        assertThat(again.won()).containsExactly(first.won());
        assertThat(other.won()).isNotEqualTo(first.won());
    }

    @Test
    void everyBotBeatsAPlayerWhoNeverFoldsByMoreThanTheMarginOfError() {
        ArenaResult result = new Arena(MIXED).play(20_000, 7, 4);

        int caller = 3;
        assertThat(result.perHundred(caller)).isLessThan(-200);
        assertThat(result.margin(caller)).isLessThan(100);
        for (int bot = 0; bot < 3; bot++) {
            assertThat(result.perHundred(bot)).as(result.names().get(bot)).isGreaterThan(result.margin(bot));
        }
    }

    @Test
    void theSimulationBotBeatsTheRuleBasedOneHeadsUp() {
        ArenaResult result = new Arena(List.of(
                new Entrant("simulation", new MonteCarloStrategy(150)),
                new Entrant("solid", new RuleBasedStrategy(Style.SOLID)))).play(40_000, 3, 4);

        // About 35 big blinds per 100 hands in long runs; here it need only be clearly ahead.
        assertThat(result.perHundred(0)).isGreaterThan(result.margin(0) + 5);
    }

    @Test
    void theResultsTableListsTheBestBotFirst() {
        ArenaResult result = new ArenaResult(List.of("one", "two"), 1_000, new double[] {-50, 50},
                new double[] {4_000, 4_000}, 0);

        assertThat(result.perHundred(1)).isEqualTo(5.0);
        assertThat(result.margin(1)).isCloseTo(12.4, within(0.1));
        String table = result.table();
        assertThat(table.indexOf("two")).isLessThan(table.indexOf("one"));
        assertThat(table).contains("1,000 hands").contains("+5.0").doesNotContain("WARNING");
    }

    @Test
    void anArenaNeedsBetweenTwoAndNineBots() {
        assertThatThrownBy(() -> new Arena(MIXED.subList(0, 1))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 to 9");
    }
}
