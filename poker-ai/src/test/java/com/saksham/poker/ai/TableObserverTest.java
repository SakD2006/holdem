package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.ChatPosted;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TableObserverTest {

    private final TableObserver observer = new TableObserver();

    /** Three players, seats 1, 3 and 5; button on 1, blinds 50 and 100 from seats 3 and 5; we are seat 1. */
    private void dealThreeHanded() {
        observer.accept(new HandStarted(7, 1, 3, 5, 50, 100, Map.of(1, 10_000L, 3, 8_000L, 5, 6_000L)));
        observer.accept(new BlindPosted(3, 50, false, false));
        observer.accept(new BlindPosted(5, 100, true, false));
        observer.accept(new HoleCards(1, Card.parseAll("Ah Kd")));
    }

    private Observation askedToAct(long toCall) {
        return observer.observe(toCall == 0, toCall, toCall == 0, toCall > 0, 200, 10_000);
    }

    @Test
    void theFirstTurnShowsTheBlindsInThePotAndTheBotOnTheButton() {
        dealThreeHanded();

        Observation seen = askedToAct(100);

        assertThat(observer.inHand()).isTrue();
        assertThat(observer.seat()).isEqualTo(1);
        assertThat(seen.holeCards()).isEqualTo(Card.parseAll("Ah Kd"));
        assertThat(seen.board()).isEmpty();
        assertThat(seen.preflop()).isTrue();
        assertThat(seen.pot()).isEqualTo(150);
        assertThat(seen.toCall()).isEqualTo(100);
        assertThat(seen.stack()).isEqualTo(10_000);
        assertThat(seen.streetBet()).isZero();
        assertThat(seen.bigBlind()).isEqualTo(100);
        assertThat(seen.playersDealt()).isEqualTo(3);
        assertThat(seen.playersInHand()).isEqualTo(3);
        assertThat(seen.seatsAfterButton()).isZero();
        assertThat(seen.latePosition()).isTrue();
        assertThat(seen.raisesThisStreet()).isZero();
        assertThat(seen.potOdds()).isEqualTo(100.0 / 250);
    }

    @Test
    void actionsMoveChipsCountRaisesAndRemoveFolders() {
        dealThreeHanded();
        observer.accept(new PlayerActed(1, ActionType.RAISE, 300, 300, 9_700, false));
        observer.accept(new PlayerActed(3, ActionType.FOLD, 0, 50, 7_950, false));
        observer.accept(new PlayerActed(5, ActionType.RAISE, 800, 900, 5_100, false));

        Observation seen = askedToAct(600);

        assertThat(seen.pot()).isEqualTo(300 + 50 + 900);
        assertThat(seen.stack()).isEqualTo(9_700);
        assertThat(seen.streetBet()).isEqualTo(300);
        assertThat(seen.playersInHand()).isEqualTo(2);
        assertThat(seen.opponents()).isEqualTo(1);
        assertThat(seen.raisesThisStreet()).isEqualTo(2);
    }

    @Test
    void aNewStreetSweepsTheBetsIntoThePotAndStartsTheRaiseCountAgain() {
        dealThreeHanded();
        observer.accept(new PlayerActed(1, ActionType.RAISE, 300, 300, 9_700, false));
        observer.accept(new PlayerActed(3, ActionType.FOLD, 0, 50, 7_950, false));
        observer.accept(new PlayerActed(5, ActionType.CALL, 200, 300, 5_700, false));
        observer.accept(new PotsUpdated(List.of(new PotInfo(650, List.of(1, 5)))));
        observer.accept(new StreetDealt("FLOP", Card.parseAll("2c 5d 9h"), Card.parseAll("2c 5d 9h")));

        Observation seen = askedToAct(0);

        assertThat(seen.board()).isEqualTo(Card.parseAll("2c 5d 9h"));
        assertThat(seen.preflop()).isFalse();
        assertThat(seen.pot()).isEqualTo(650);
        assertThat(seen.streetBet()).isZero();
        assertThat(seen.raisesThisStreet()).isZero();
        assertThat(seen.potOdds()).isZero();

        observer.accept(new PlayerActed(5, ActionType.BET, 400, 400, 5_300, false));
        assertThat(askedToAct(400).pot()).isEqualTo(1_050);
    }

    @Test
    void anUncalledBetGoesBackOutOfThePot() {
        dealThreeHanded();
        observer.accept(new PlayerActed(1, ActionType.RAISE, 2_000, 2_000, 8_000, false));
        observer.accept(new BetReturned(1, 1_900));

        Observation seen = askedToAct(0);

        assertThat(seen.pot()).isEqualTo(100 + 50 + 100);
        assertThat(seen.stack()).isEqualTo(9_900);
    }

    @Test
    void positionIsCountedClockwiseFromTheButton() {
        observer.accept(new HandStarted(1, 5, 1, 3, 50, 100, Map.of(1, 1_000L, 3, 1_000L, 5, 1_000L)));
        observer.accept(new HoleCards(3, Card.parseAll("2c 2d")));

        Observation seen = askedToAct(0);

        // Button is seat 5, then seat 1, then seat 3: two seats after the button.
        assertThat(seen.seatsAfterButton()).isEqualTo(2);
        assertThat(seen.latePosition()).as("the last seat before the button").isTrue();
    }

    @Test
    void theEndOfAHandRecordsTheStackAndTheNextHandStartsClean() {
        dealThreeHanded();
        observer.accept(new HandEnded(List.of(new PayoutInfo(0, 5, 250)), Map.of(1, -100L, 3, -50L, 5, 150L),
                Map.of(1, 0L, 3, 7_950L, 5, 6_150L)));

        assertThat(observer.inHand()).isFalse();
        assertThat(observer.playedLastHand()).isTrue();
        assertThat(observer.stackAfterLastHand()).isZero();

        observer.accept(new HandStarted(8, 3, 5, 1, 50, 100, Map.of(3, 7_950L, 5, 6_150L)));
        assertThat(observer.inHand()).as("not dealt in: no hole cards arrived").isFalse();
        assertThat(askedToAct(0).holeCards()).isEmpty();
        assertThat(askedToAct(0).pot()).isZero();

        // That hand ends without us: it says nothing new about our chips.
        observer.accept(new HandEnded(List.of(new PayoutInfo(0, 5, 150)), Map.of(3, -50L, 5, 50L),
                Map.of(3, 7_900L, 5, 6_200L)));
        assertThat(observer.playedLastHand()).isFalse();
    }

    @Test
    void messagesThatAreNotAboutTheHandChangeNothing() {
        dealThreeHanded();
        Observation before = askedToAct(100);

        observer.accept(new ChatPosted(9, "ravi", "nice hand"));

        assertThat(askedToAct(100)).isEqualTo(before);
        assertThat(new TableObserver().stackAfterLastHand()).isEqualTo(-1);
    }
}
