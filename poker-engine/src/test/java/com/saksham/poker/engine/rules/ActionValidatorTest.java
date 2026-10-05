package com.saksham.poker.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.action.AllIn;
import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.exception.InvalidActionException;
import com.saksham.poker.common.exception.InvalidAmountException;
import com.saksham.poker.common.exception.NotYourTurnException;
import org.junit.jupiter.api.Test;

class ActionValidatorTest {

    /** Nobody has bet: check, or bet 100 to 1000. */
    private static final LegalActions NO_BET = new LegalActions(true, true, 0, true, false, 100, 1000);
    /** Facing 200 with 1000 behind and 0 in: call 200, or raise to 400 up to 1000. */
    private static final LegalActions FACING_BET = new LegalActions(true, false, 200, false, true, 400, 1000);
    /** Facing a bet that can only be called or folded. */
    private static final LegalActions CALL_ONLY = new LegalActions(true, false, 200, false, false, 0, 0);

    @Test
    void nothingIsAllowedWhenItIsNotYourTurn() {
        assertThatThrownBy(() -> ActionValidator.resolve(new Fold(), LegalActions.NONE, 1000))
                .isInstanceOf(NotYourTurnException.class);
    }

    @Test
    void foldIsAlwaysAllowedOnYourTurn() throws Exception {
        assertThat(ActionValidator.resolve(new Fold(), NO_BET, 1000)).isEqualTo(new Fold());
        assertThat(ActionValidator.resolve(new Fold(), FACING_BET, 1000)).isEqualTo(new Fold());
    }

    @Test
    void checkNeedsNothingToCall() throws Exception {
        assertThat(ActionValidator.resolve(new Check(), NO_BET, 1000)).isEqualTo(new Check());
        assertThatThrownBy(() -> ActionValidator.resolve(new Check(), FACING_BET, 1000))
                .isInstanceOf(InvalidActionException.class).hasMessageContaining("200");
    }

    @Test
    void callNeedsSomethingToCall() throws Exception {
        assertThat(ActionValidator.resolve(new Call(), FACING_BET, 1000)).isEqualTo(new Call());
        assertThatThrownBy(() -> ActionValidator.resolve(new Call(), NO_BET, 1000))
                .isInstanceOf(InvalidActionException.class);
    }

    @Test
    void betMustBeInRangeAndOnlyWhenNobodyHasBet() throws Exception {
        assertThat(ActionValidator.resolve(new Bet(100), NO_BET, 1000)).isEqualTo(new Bet(100));
        assertThat(ActionValidator.resolve(new Bet(1000), NO_BET, 1000)).isEqualTo(new Bet(1000));
        assertThatThrownBy(() -> ActionValidator.resolve(new Bet(99), NO_BET, 1000))
                .isInstanceOf(InvalidAmountException.class).hasMessageContaining("100").hasMessageContaining("1000");
        assertThatThrownBy(() -> ActionValidator.resolve(new Bet(1001), NO_BET, 1000))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> ActionValidator.resolve(new Bet(500), FACING_BET, 1000))
                .isInstanceOf(InvalidActionException.class).hasMessageContaining("Raise");
    }

    @Test
    void raiseMustBeInRangeAndOnlyWhenFacingABet() throws Exception {
        assertThat(ActionValidator.resolve(new Raise(400), FACING_BET, 1000)).isEqualTo(new Raise(400));
        assertThatThrownBy(() -> ActionValidator.resolve(new Raise(399), FACING_BET, 1000))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> ActionValidator.resolve(new Raise(1001), FACING_BET, 1000))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> ActionValidator.resolve(new Raise(400), NO_BET, 1000))
                .isInstanceOf(InvalidActionException.class).hasMessageContaining("Bet");
        assertThatThrownBy(() -> ActionValidator.resolve(new Raise(400), CALL_ONLY, 1000))
                .isInstanceOf(InvalidActionException.class);
    }

    @Test
    void allInBecomesABetWhenNobodyHasBet() throws Exception {
        assertThat(ActionValidator.resolve(new AllIn(), NO_BET, 1000)).isEqualTo(new Bet(1000));
    }

    @Test
    void allInBecomesARaiseWhenItIsMoreThanACall() throws Exception {
        assertThat(ActionValidator.resolve(new AllIn(), FACING_BET, 1000)).isEqualTo(new Raise(1000));
    }

    @Test
    void allInBecomesACallWhenTheStackOnlyCoversTheCall() throws Exception {
        LegalActions shortStack = new LegalActions(true, false, 150, false, false, 0, 0);
        assertThat(ActionValidator.resolve(new AllIn(), shortStack, 150)).isEqualTo(new Call());
    }

    @Test
    void allInIsRefusedWhenItWouldBeARaiseThatIsNotAllowed() {
        assertThatThrownBy(() -> ActionValidator.resolve(new AllIn(), CALL_ONLY, 1000))
                .isInstanceOf(InvalidActionException.class);
    }
}
