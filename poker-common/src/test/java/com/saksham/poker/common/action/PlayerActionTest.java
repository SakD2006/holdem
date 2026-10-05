package com.saksham.poker.common.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerActionTest {

    @Test
    void everySubclassReportsItsOwnType() {
        List<PlayerAction> actions =
                List.of(new Fold(), new Check(), new Call(), new Bet(50), new Raise(200), new AllIn());

        Set<ActionType> types = EnumSet.noneOf(ActionType.class);
        for (PlayerAction action : actions) {
            types.add(action.type());
        }

        assertThat(types).containsExactlyInAnyOrder(ActionType.values());
    }

    @Test
    void onlyBetAndRaiseCarryAnAmount() {
        assertThat(new Bet(50).amount()).isEqualTo(50);
        assertThat(new Raise(200).amount()).isEqualTo(200);
        assertThat(new Fold().amount()).isZero();
        assertThat(new Check().amount()).isZero();
        assertThat(new Call().amount()).isZero();
        assertThat(new AllIn().amount()).isZero();
    }

    @Test
    void betAndRaiseMustBeMoreThanZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Bet(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Bet(-10));
        assertThatIllegalArgumentException().isThrownBy(() -> new Raise(0));
        assertThatIllegalArgumentException().isThrownBy(() -> new Raise(-10));
    }

    @Test
    void actionsAreEqualWhenTypeAndAmountMatch() {
        assertThat(new Fold()).isEqualTo(new Fold()).hasSameHashCodeAs(new Fold());
        assertThat(new Bet(50)).isEqualTo(new Bet(50)).hasSameHashCodeAs(new Bet(50));
        assertThat(new Bet(50)).isNotEqualTo(new Bet(60));
        assertThat(new Bet(50)).isNotEqualTo(new Raise(50));
        assertThat(new Check()).isNotEqualTo(new Call());
    }

    @Test
    void textShowsTypeAndAmount() {
        assertThat(new Fold()).hasToString("FOLD");
        assertThat(new Raise(200)).hasToString("RAISE 200");
        assertThat(new AllIn()).hasToString("ALL_IN");
    }
}
