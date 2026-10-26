package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Folds after the flop are noted by situation: whether the player had bet, and the size faced. */
class FoldProfileTest {

    private static final long RAVI = 7;

    /** A limped pot to the flop, seat 1 (Ravi) first to act; the bot sits in seat 0. */
    private static TableObserver toTheFlop() {
        TableObserver observer = new TableObserver();
        observer.accept(new SeatUpdate(new PlayerInfo(RAVI, "ravi", 1, 10_000, false, true)));
        observer.accept(new HandStarted(1, 0, 0, 1, 50, 100, Map.of(0, 10_000L, 1, 10_000L)));
        observer.accept(new HoleCards(0, Card.parseAll("As Ad")));
        observer.accept(new BlindPosted(0, 50, false, false));
        observer.accept(new BlindPosted(1, 100, true, false));
        observer.accept(new PlayerActed(0, ActionType.CALL, 50, 100, 9_900, false));
        observer.accept(new PlayerActed(1, ActionType.CHECK, 0, 100, 9_900, false));
        observer.accept(new PotsUpdated(List.of(new PotInfo(200, List.of(0, 1)))));
        observer.accept(new StreetDealt("FLOP", Card.parseAll("Kh 9d 4s"), Card.parseAll("Kh 9d 4s")));
        return observer;
    }

    @Test
    void a_fold_after_checking_to_a_half_pot_bet_is_noted_as_such() {
        TableObserver observer = toTheFlop();
        observer.accept(new PlayerActed(1, ActionType.CHECK, 0, 0, 9_900, false));
        observer.accept(new PlayerActed(0, ActionType.BET, 100, 100, 9_800, false));
        observer.accept(new PlayerActed(1, ActionType.FOLD, 0, 0, 9_900, false));
        FoldProfile folds = observer.book().tendencies(RAVI).folds();
        // A half-pot bet asks 100 to win 300: odds of a quarter, the medium size.
        assertThat(folds.seen(false, 1)).isEqualTo(1);
        assertThat(folds.rate(false, 1)).isGreaterThan(FoldProfile.TYPICAL.rate(false, 1));
        assertThat(folds.seen(true, 1)).isZero();
    }

    @Test
    void a_call_after_betting_and_being_raised_is_noted_apart() {
        TableObserver observer = toTheFlop();
        observer.accept(new PlayerActed(1, ActionType.BET, 200, 200, 9_700, false));
        observer.accept(new PlayerActed(0, ActionType.RAISE, 800, 800, 9_100, false));
        observer.accept(new PlayerActed(1, ActionType.CALL, 600, 800, 9_100, false));
        FoldProfile folds = observer.book().tendencies(RAVI).folds();
        // Facing 600 more into 1,200: odds of a third, after having bet.
        assertThat(folds.seen(true, 1)).isEqualTo(1);
        assertThat(folds.rate(true, 1)).isLessThan(FoldProfile.TYPICAL.rate(true, 1));
        assertThat(folds.seen(false, 0) + folds.seen(false, 1) + folds.seen(false, 2)).isZero();
    }
}
