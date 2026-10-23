package com.saksham.poker.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** What a bot remembers about the players it meets, built only from what the table could see. */
class OpponentBookTest {

    private static final long RAVI = 42;
    private final TableObserver observer = new TableObserver();
    private long handNo;

    /** We are seat 0 on the button; ravi is seat 1 and posts the big blind. */
    private void deal() {
        observer.accept(new SeatUpdate(new PlayerInfo(RAVI, "ravi", 1, 10_000, false, true)));
        observer.accept(new HandStarted(++handNo, 0, 0, 1, 50, 100, Map.of(0, 10_000L, 1, 10_000L)));
        observer.accept(new BlindPosted(0, 50, false, false));
        observer.accept(new BlindPosted(1, 100, true, false));
        observer.accept(new HoleCards(0, Card.parseAll("Ah Kd")));
    }

    private void toTheFlop() {
        observer.accept(new PlayerActed(0, ActionType.CALL, 50, 100, 9_900, false));
        observer.accept(new PlayerActed(1, ActionType.CHECK, 0, 100, 9_900, false));
        observer.accept(new PotsUpdated(List.of(new PotInfo(200, List.of(0, 1)))));
        observer.accept(new StreetDealt("FLOP", Card.parseAll("2c 7d 9h"), Card.parseAll("2c 7d 9h")));
    }

    private void endHand() {
        observer.accept(new HandEnded(List.of(new PayoutInfo(0, 0, 200)), Map.of(0, 100L, 1, -100L),
                Map.of(0, 10_100L, 1, 9_900L)));
    }

    private Tendencies ravi() {
        return observer.book().tendencies(RAVI);
    }

    @Test
    void aPlayerNeverSeenIsAssumedToBeTypical() {
        assertThat(observer.book().tendencies(999)).isEqualTo(Tendencies.TYPICAL);
        assertThat(observer.book().players()).isZero();
    }

    @Test
    void aFewHandsBarelyMoveTheEstimateAndManyHandsMoveItALot() {
        for (int i = 0; i < 3; i++) {
            deal();
            toTheFlop();
            observer.accept(new PlayerActed(0, ActionType.BET, 100, 100, 9_800, false));
            observer.accept(new PlayerActed(1, ActionType.FOLD, 0, 0, 9_900, false));
            endHand();
        }
        double afterThree = ravi().foldsToBet();
        assertThat(afterThree).as("three folds are not proof").isBetween(0.5, 0.65);

        for (int i = 0; i < 60; i++) {
            deal();
            toTheFlop();
            observer.accept(new PlayerActed(0, ActionType.BET, 100, 100, 9_800, false));
            observer.accept(new PlayerActed(1, ActionType.FOLD, 0, 0, 9_900, false));
            endHand();
        }
        assertThat(ravi().foldsToBet()).as("sixty-three in a row are").isGreaterThan(0.9);
        assertThat(ravi().handsSeen()).isEqualTo(63);
    }

    @Test
    void enteringThePotAndRaisingAreCountedOncePerHand() {
        for (int i = 0; i < 40; i++) {
            deal();
            observer.accept(new PlayerActed(0, ActionType.RAISE, 250, 300, 9_700, false));
            // ravi re-raises, we raise again, he calls: one hand entered, one hand raised
            observer.accept(new PlayerActed(1, ActionType.RAISE, 800, 900, 9_100, false));
            observer.accept(new PlayerActed(0, ActionType.RAISE, 1_800, 2_100, 7_900, false));
            observer.accept(new PlayerActed(1, ActionType.CALL, 1_200, 2_100, 7_900, false));
            endHand();
        }
        Tendencies seen = ravi();
        assertThat(seen.entersPot()).isCloseTo((0.3 * 12 + 40) / 52, within(1e-9));
        assertThat(seen.raisesPreflop()).isCloseTo((0.18 * 12 + 40) / 52, within(1e-9));
        assertThat(seen.foldsToRaisePreflop()).as("he faced a raise twice a hand and never folded").isLessThan(0.1);
    }

    @Test
    void checkingTheBigBlindIsNotEnteringThePotByChoice() {
        for (int i = 0; i < 40; i++) {
            deal();
            toTheFlop();
            endHand();
        }
        assertThat(ravi().entersPot()).isLessThan(0.1);
        assertThat(ravi().raisesPreflop()).isLessThan(0.1);
    }

    @Test
    void aShownBluffIsRememberedAndAShownValueBetIsNot() {
        // He bets the river and shows seven-high: a bluff.
        deal();
        toTheFlop();
        observer.accept(new StreetDealt("TURN", Card.parseAll("Ks"), Card.parseAll("2c 7d 9h Ks")));
        observer.accept(new StreetDealt("RIVER", Card.parseAll("Qc"), Card.parseAll("2c 7d 9h Ks Qc")));
        observer.accept(new PlayerActed(1, ActionType.BET, 150, 150, 9_750, false));
        observer.accept(new PlayerActed(0, ActionType.CALL, 150, 150, 9_750, false));
        observer.accept(new Showdown(List.of(new ShownHandInfo(1, Card.parseAll("5s 4s"), "HIGH_CARD"),
                new ShownHandInfo(0, Card.parseAll("Ah Kd"), "PAIR"))));
        endHand();
        double afterBluff = ravi().bluffs();
        assertThat(afterBluff).isGreaterThan(Tendencies.TYPICAL.bluffs());

        // He bets the river and shows a set: his bet was honest.
        deal();
        toTheFlop();
        observer.accept(new StreetDealt("TURN", Card.parseAll("Ks"), Card.parseAll("2c 7d 9h Ks")));
        observer.accept(new StreetDealt("RIVER", Card.parseAll("Qc"), Card.parseAll("2c 7d 9h Ks Qc")));
        observer.accept(new PlayerActed(1, ActionType.BET, 150, 150, 9_750, false));
        observer.accept(new PlayerActed(0, ActionType.CALL, 150, 150, 9_750, false));
        observer.accept(new Showdown(List.of(new ShownHandInfo(1, Card.parseAll("9s 9d"), "THREE_OF_A_KIND"),
                new ShownHandInfo(0, Card.parseAll("Ah Kd"), "PAIR"))));
        endHand();
        assertThat(ravi().bluffs()).isLessThan(afterBluff);
    }

    @Test
    void aHandShownWithoutAnyBettingSaysNothingAboutBluffing() {
        deal();
        toTheFlop();
        observer.accept(new StreetDealt("TURN", Card.parseAll("Ks"), Card.parseAll("2c 7d 9h Ks")));
        observer.accept(new StreetDealt("RIVER", Card.parseAll("Qc"), Card.parseAll("2c 7d 9h Ks Qc")));
        observer.accept(new Showdown(List.of(new ShownHandInfo(1, Card.parseAll("5s 4s"), "HIGH_CARD"))));
        endHand();

        assertThat(ravi().bluffs()).isEqualTo(Tendencies.TYPICAL.bluffs());
    }

    @Test
    void notesFollowAPlayerWhoChangesSeatAndAreNotGivenToWhoeverSitsThereNext() {
        for (int i = 0; i < 30; i++) {
            deal();
            toTheFlop();
            observer.accept(new PlayerActed(0, ActionType.BET, 100, 100, 9_800, false));
            observer.accept(new PlayerActed(1, ActionType.FOLD, 0, 0, 9_900, false));
            endHand();
        }
        double learned = ravi().foldsToBet();

        // ravi moves to seat 4 and meera sits in seat 1.
        observer.accept(new SeatUpdate(new PlayerInfo(RAVI, "ravi", 4, 9_900, false, true)));
        observer.accept(new SeatUpdate(new PlayerInfo(77, "meera", 1, 10_000, false, true)));
        observer.accept(new HandStarted(++handNo, 0, 1, 4, 50, 100, Map.of(0, 10_000L, 1, 10_000L, 4, 9_900L)));
        observer.accept(new HoleCards(0, Card.parseAll("Ah Kd")));
        Observation seen = observer.observe(false, 100, false, true, 200, 10_000);

        assertThat(seen.opponents()).extracting(Opponent::seat).containsExactly(1, 4);
        assertThat(seen.opponents().get(0).habits().foldsToBet()).as("meera is new").isEqualTo(0.45);
        assertThat(seen.opponents().get(1).habits().foldsToBet()).as("ravi's record came with him").isEqualTo(learned);
    }

    @Test
    void theBotsOwnActionsAndCardsAreNotPartOfAnyoneElsesRecord() {
        deal();
        toTheFlop();
        observer.accept(new PlayerActed(0, ActionType.BET, 100, 100, 9_800, false));
        observer.accept(new PlayerActed(1, ActionType.CALL, 100, 100, 9_800, false));
        endHand();

        Tendencies seen = ravi();
        assertThat(seen.aggression()).as("he checked and called: no bets of his own").isLessThan(0.35);
        assertThat(seen.foldsToBet()).as("he faced one bet and called it").isLessThan(0.45);
    }
}
