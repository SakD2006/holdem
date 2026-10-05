package com.saksham.poker.engine.hand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.action.AllIn;
import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.common.exception.InvalidActionException;
import com.saksham.poker.common.exception.InvalidAmountException;
import com.saksham.poker.common.exception.NotYourTurnException;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.eval.HandCategory;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.BetsCollected;
import com.saksham.poker.engine.event.BlindPosted;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.event.HandStarted;
import com.saksham.poker.engine.event.HoleCardsDealt;
import com.saksham.poker.engine.event.PlayerActed;
import com.saksham.poker.engine.event.PotAwarded;
import com.saksham.poker.engine.event.ShowdownRevealed;
import com.saksham.poker.engine.event.StreetDealt;
import com.saksham.poker.engine.event.UncalledBetReturned;
import com.saksham.poker.engine.pot.Payout;
import com.saksham.poker.engine.pot.Pot;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scripted hands with chosen cards. Blinds are 50/100 unless a test says otherwise. */
class HoldemHandTest {

    private static final String BOARD = "2c 5d 9h Js 3s";

    private static HandFixture headsUp(long stack1, long stack2) {
        return new HandFixture().button(1)
                .seat(1, stack1, "Ah Ad").seat(2, stack2, "Kc Kd").board(BOARD).start();
    }

    private static HandFixture threeHanded() {
        return new HandFixture().button(1)
                .seat(1, 1000, "Kh Kd").seat(2, 1000, "Qh Qd").seat(3, 1000, "Ah Ad").board(BOARD).start();
    }

    /** Both players check until the street, or the hand, is over. */
    private static void checkStreet(HandFixture table, int first, int second) throws GameRuleException {
        table.act(first, new Check());
        table.act(second, new Check());
    }

    // ---- heads-up order

    @Test
    void headsUpTheButtonPostsTheSmallBlindActsFirstPreflopAndLastAfter() throws Exception {
        HandFixture table = headsUp(1000, 1000);

        HandStarted started = table.last(HandStarted.class);
        assertThat(started.buttonSeat()).isEqualTo(1);
        assertThat(started.smallBlindSeat()).isEqualTo(1);
        assertThat(started.bigBlindSeat()).isEqualTo(2);
        List<BlindPosted> blinds = table.events(BlindPosted.class);
        assertThat(blinds).extracting(BlindPosted::seat).containsExactly(1, 2);
        assertThat(blinds).extracting(BlindPosted::amount).containsExactly(50L, 100L);
        assertThat(blinds).extracting(BlindPosted::bigBlind).containsExactly(false, true);

        // Preflop the button acts first and must add 50 to call.
        assertThat(table.hand().seatToAct()).isEqualTo(1);
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 50, false, true, 200, 1000));
        table.act(1, new Call());

        // The big blind has the option to check or raise.
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, true, 0, false, true, 200, 1000));
        table.act(2, new Check());

        // After the flop the big blind acts first and the button last.
        assertThat(table.hand().street()).isEqualTo(Street.FLOP);
        assertThat(table.hand().seatToAct()).isEqualTo(2);
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, true, 0, true, false, 100, 900));
        table.act(2, new Check());
        assertThat(table.hand().seatToAct()).isEqualTo(1);
        table.act(1, new Check());

        assertThat(table.hand().street()).isEqualTo(Street.TURN);
        assertThat(table.hand().seatToAct()).isEqualTo(2);
    }

    // ---- blind positions with three or more players

    @Test
    void withThreePlayersTheBlindsAreLeftOfTheButtonAndTheNextSeatActsFirst() {
        HandFixture table = new HandFixture().button(8).seat(2, 1000).seat(5, 1000).seat(8, 1000).start();

        assertThat(table.hand().smallBlindSeat()).isEqualTo(2);
        assertThat(table.hand().bigBlindSeat()).isEqualTo(5);
        assertThat(table.hand().seatToAct()).isEqualTo(8);
    }

    @Test
    void blindsWrapRoundTheTable() {
        HandFixture table = new HandFixture().button(2).seat(1, 1000).seat(2, 1000).seat(3, 1000).start();

        assertThat(table.hand().smallBlindSeat()).isEqualTo(3);
        assertThat(table.hand().bigBlindSeat()).isEqualTo(1);
        assertThat(table.hand().seatToAct()).isEqualTo(2);
    }

    @Test
    void holeCardsAreDealtOneAtATimeStartingLeftOfTheButton() {
        HandFixture table = threeHanded();

        List<HoleCardsDealt> dealt = table.events(HoleCardsDealt.class);
        assertThat(dealt).extracting(HoleCardsDealt::seat).containsExactly(2, 3, 1);
        assertThat(table.hand().seat(1).holeCards()).isEqualTo(Card.parseAll("Kh Kd"));
        assertThat(table.hand().seat(3).holeCards()).isEqualTo(Card.parseAll("Ah Ad"));
    }

    @Test
    void holeCardsAreVisibleOnlyToTheirOwner() {
        HandFixture table = threeHanded();

        for (GameEvent event : table.events()) {
            if (event instanceof HoleCardsDealt dealt) {
                for (int seat = 1; seat <= 3; seat++) {
                    assertThat(event.isVisibleTo(seat)).isEqualTo(seat == dealt.seat());
                }
            } else {
                assertThat(event.isVisibleTo(1)).isTrue();
                assertThat(event.isVisibleTo(99)).isTrue();
            }
        }
    }

    // ---- checked to showdown

    @Test
    void aHandCheckedDownGoesToShowdownAndTheBestHandWins() throws Exception {
        HandFixture table = headsUp(1000, 1000);
        table.act(1, new Call());
        table.act(2, new Check());
        checkStreet(table, 2, 1);
        checkStreet(table, 2, 1);
        checkStreet(table, 2, 1);

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.hand().street()).isEqualTo(Street.SHOWDOWN);

        List<StreetDealt> streets = table.events(StreetDealt.class);
        assertThat(streets).extracting(StreetDealt::street)
                .containsExactly(Street.FLOP, Street.TURN, Street.RIVER);
        assertThat(streets.get(0).cards()).isEqualTo(Card.parseAll("2c 5d 9h"));
        assertThat(streets.get(1).cards()).isEqualTo(Card.parseAll("Js"));
        assertThat(streets.get(2).board()).isEqualTo(Card.parseAll(BOARD));

        // Bets were only made preflop, so they are collected once.
        assertThat(table.events(BetsCollected.class)).hasSize(1);
        assertThat(table.last(BetsCollected.class).pots()).containsExactly(new Pot(200, Set.of(1, 2)));

        // No river bet: the first player left of the button shows first.
        ShowdownRevealed showdown = table.last(ShowdownRevealed.class);
        assertThat(showdown.hands()).extracting(ShowdownRevealed.ShownHand::seat).containsExactly(2, 1);
        assertThat(showdown.hands().get(1).cards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(showdown.hands().get(1).value().category()).isEqualTo(HandCategory.PAIR);

        PotAwarded awarded = table.last(PotAwarded.class);
        assertThat(awarded.amount()).isEqualTo(200);
        assertThat(awarded.payouts()).containsExactly(new Payout(0, 1, 200));

        HandResult result = table.hand().result();
        assertThat(result.showdown()).isTrue();
        assertThat(result.shownSeats()).containsExactly(1, 2);
        assertThat(result.endStacks()).isEqualTo(Map.of(1, 1100L, 2, 900L));
        assertThat(result.net(1)).isEqualTo(100);
        assertThat(result.net(2)).isEqualTo(-100);
        assertThat(result.winners()).containsExactly(1);
        assertThat(result.totalPot()).isEqualTo(200);
        assertThat(result.board()).isEqualTo(Card.parseAll(BOARD));
        assertThat(table.events().get(table.events().size() - 1)).isInstanceOf(HandCompleted.class);
    }

    // ---- everyone folds

    @Test
    void whenEveryoneFoldsToTheBigBlindItWinsWithoutShowing() throws Exception {
        HandFixture table = threeHanded();

        table.act(1, new Fold());
        table.act(2, new Fold());

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.events(ShowdownRevealed.class)).isEmpty();
        assertThat(table.events(StreetDealt.class)).isEmpty();
        UncalledBetReturned returned = table.last(UncalledBetReturned.class);
        assertThat(returned.seat()).isEqualTo(3);
        assertThat(returned.amount()).isEqualTo(50);
        assertThat(table.last(PotAwarded.class).payouts()).containsExactly(new Payout(0, 3, 100));

        HandResult result = table.hand().result();
        assertThat(result.showdown()).isFalse();
        assertThat(result.shownSeats()).isEmpty();
        assertThat(result.endStacks()).isEqualTo(Map.of(1, 1000L, 2, 950L, 3, 1050L));
        // Hole cards are still recorded for every seat, for each player's own history.
        assertThat(result.holeCards()).containsOnlyKeys(1, 2, 3);
        assertThat(result.board()).isEmpty();
    }

    // ---- uncalled bets

    @Test
    void aBetThatNobodyCallsIsReturned() throws Exception {
        HandFixture table = headsUp(1000, 1000);
        table.act(1, new Call());
        table.act(2, new Check());

        table.act(2, new Bet(300));
        table.act(1, new Fold());

        assertThat(table.last(UncalledBetReturned.class).seat()).isEqualTo(2);
        assertThat(table.last(UncalledBetReturned.class).amount()).isEqualTo(300);
        assertThat(table.last(PotAwarded.class).amount()).isEqualTo(200);
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 900L, 2, 1100L));
    }

    @Test
    void onlyTheUncalledPartOfARaiseIsReturned() throws Exception {
        HandFixture table = headsUp(2000, 2000);
        table.act(1, new Call());
        table.act(2, new Check());

        table.act(2, new Bet(300));
        table.act(1, new Raise(900));
        table.act(2, new Fold());

        assertThat(table.last(UncalledBetReturned.class).seat()).isEqualTo(1);
        assertThat(table.last(UncalledBetReturned.class).amount()).isEqualTo(600);
        assertThat(table.last(PotAwarded.class).amount()).isEqualTo(800);
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 2400L, 2, 1600L));
    }

    @Test
    void anAllInBiggerThanTheCallersStackGetsTheDifferenceBackAndTheBoardRunsOut() throws Exception {
        HandFixture table = headsUp(1000, 400);

        List<GameEvent> shove = table.act(1, new AllIn());
        PlayerActed acted = HandFixture.of(shove, PlayerActed.class).get(0);
        assertThat(acted.type()).isEqualTo(ActionType.RAISE);
        assertThat(acted.streetBet()).isEqualTo(1000);
        assertThat(acted.allIn()).isTrue();

        // The short stack can only call for what it has, or fold.
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, false, 300, false, false, 0, 0));
        List<GameEvent> call = table.act(2, new Call());

        // Nobody can bet any more: the rest of the board is dealt without asking anyone to act.
        assertThat(HandFixture.of(call, ActionRequested.class)).isEmpty();
        assertThat(HandFixture.of(call, StreetDealt.class)).hasSize(3);
        assertThat(HandFixture.of(call, UncalledBetReturned.class).get(0).amount()).isEqualTo(600);
        assertThat(table.last(PotAwarded.class).payouts()).containsExactly(new Payout(0, 1, 800));
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 1400L, 2, 0L));
    }

    // ---- side pots

    @Test
    void allInsOfThreeSizesMakeAMainPotAndTwoSidePotsWonByDifferentPlayers() throws Exception {
        HandFixture table = new HandFixture().button(1)
                .seat(1, 300, "Ah Ad")   // shortest stack, best hand
                .seat(2, 700, "Kh Kd")   // small blind
                .seat(3, 1500, "Qh Qd")  // big blind
                .seat(4, 2000, "7c 8d")  // covers everyone, worst hand
                .board(BOARD).start();

        assertThat(table.hand().seatToAct()).isEqualTo(4);
        table.act(4, new AllIn());
        table.act(1, new Call());
        table.act(2, new Call());
        List<GameEvent> last = table.act(3, new Call());

        assertThat(HandFixture.of(last, UncalledBetReturned.class).get(0).seat()).isEqualTo(4);
        assertThat(HandFixture.of(last, UncalledBetReturned.class).get(0).amount()).isEqualTo(500);
        assertThat(table.last(BetsCollected.class).pots()).containsExactly(
                new Pot(1200, Set.of(1, 2, 3, 4)),
                new Pot(1200, Set.of(2, 3, 4)),
                new Pot(1600, Set.of(3, 4)));

        List<PotAwarded> awarded = table.events(PotAwarded.class);
        assertThat(awarded).hasSize(3);
        assertThat(awarded.get(0).payouts()).containsExactly(new Payout(0, 1, 1200));
        assertThat(awarded.get(1).payouts()).containsExactly(new Payout(1, 2, 1200));
        assertThat(awarded.get(2).payouts()).containsExactly(new Payout(2, 3, 1600));
        assertThat(table.hand().result().endStacks())
                .isEqualTo(Map.of(1, 1200L, 2, 1200L, 3, 1600L, 4, 500L));
        assertThat(table.hand().result().totalPot()).isEqualTo(4000);
    }

    // ---- the incomplete all-in raise

    /** Three players see a flop; seat 3 has 250 left, less than a full raise over a 200 bet. */
    private static HandFixture flopWithAShortStack() throws GameRuleException {
        HandFixture table = new HandFixture().button(1)
                .seat(1, 2000).seat(2, 2000).seat(3, 350).start();
        table.act(1, new Call());
        table.act(2, new Call());
        table.act(3, new Check());
        table.act(2, new Bet(200));
        table.act(3, new AllIn());
        return table;
    }

    @Test
    void anAllInRaiseForLessThanAFullRaiseIsAllowed() throws Exception {
        HandFixture table = flopWithAShortStack();

        PlayerActed shortAllIn = table.last(PlayerActed.class);
        assertThat(shortAllIn.type()).isEqualTo(ActionType.RAISE);
        assertThat(shortAllIn.streetBet()).isEqualTo(250);
        assertThat(shortAllIn.allIn()).isTrue();

        // Seat 1 has not acted yet, so it may still raise, by a full raise over the 250.
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 250, false, true, 450, 1900));
    }

    @Test
    void anIncompleteAllInDoesNotReopenTheBettingForAPlayerWhoAlreadyActed() throws Exception {
        HandFixture table = flopWithAShortStack();
        table.act(1, new Call());

        // Seat 2 bet 200 and now faces 250: it may only call the extra 50 or fold.
        assertThat(table.hand().seatToAct()).isEqualTo(2);
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, false, 50, false, false, 0, 0));
        assertThatThrownBy(() -> table.act(2, new Raise(650))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> table.act(2, new AllIn())).isInstanceOf(InvalidActionException.class);
        assertThat(table.hand().seatToAct()).isEqualTo(2);

        table.act(2, new Call());

        assertThat(table.hand().street()).isEqualTo(Street.TURN);
        assertThat(table.last(BetsCollected.class).pots()).containsExactly(new Pot(1050, Set.of(1, 2, 3)));
    }

    @Test
    void aFullRaiseAfterTheIncompleteAllInReopensTheBetting() throws Exception {
        HandFixture table = flopWithAShortStack();

        table.act(1, new Raise(450));

        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, false, 250, false, true, 650, 1900));
    }

    @Test
    void twoShortAllInsThatTogetherMakeAFullRaiseReopenTheBetting() throws Exception {
        HandFixture table = new HandFixture().button(1)
                .seat(1, 5000).seat(2, 5000).seat(3, 250).seat(4, 320).start();
        table.act(4, new Call());
        table.act(1, new Call());
        table.act(2, new Call());
        table.act(3, new Check());

        table.act(2, new Bet(100));
        table.act(3, new AllIn());   // to 150: 50 more, not a full raise
        table.act(4, new AllIn());   // to 220: 70 more, not a full raise
        table.act(1, new Call());

        // Seat 2 bet 100 and now faces 220. That is 120 more, a full raise in total.
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, false, 120, false, true, 320, 4900));
    }

    @Test
    void anAllInBetSmallerThanTheBigBlindDoesNotLetACheckerRaise() throws Exception {
        HandFixture table = new HandFixture().button(1)
                .seat(1, 2000).seat(2, 2000).seat(3, 140).start();
        table.act(1, new Call());
        table.act(2, new Call());
        table.act(3, new Check());
        table.act(2, new Check());

        // Seat 3 has 40 left: its only bet is all-in for less than the big blind.
        assertThat(table.hand().legalActionsFor(3))
                .isEqualTo(new LegalActions(true, true, 0, true, false, 40, 40));
        table.act(3, new AllIn());
        assertThat(table.last(PlayerActed.class).type()).isEqualTo(ActionType.BET);

        // Seat 1 has not acted: it may raise, by at least the big blind.
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 40, false, true, 140, 1900));
        table.act(1, new Call());

        // Seat 2 already checked and the bet is under a full bet: call or fold only.
        assertThat(table.hand().legalActionsFor(2))
                .isEqualTo(new LegalActions(true, false, 40, false, false, 0, 0));
    }

    // ---- split pots

    @Test
    void aSplitPotGivesTheOddChipToTheFirstWinnerLeftOfTheButton() throws Exception {
        // The board is a straight that both remaining players play.
        HandFixture table = new HandFixture().blinds(25, 50).button(1)
                .seat(1, 1000, "2c 3d").seat(2, 1000, "7c 8d").seat(3, 1000, "4h 5s")
                .board("Ah Kd Qc Js Th").start();
        table.act(1, new Call());
        table.act(2, new Fold());
        table.act(3, new Check());
        checkStreet(table, 3, 1);
        checkStreet(table, 3, 1);
        checkStreet(table, 3, 1);

        // 125 chips between two players: 62 each, and the odd chip to seat 3, not the button.
        PotAwarded awarded = table.last(PotAwarded.class);
        assertThat(awarded.amount()).isEqualTo(125);
        assertThat(awarded.payouts()).containsExactly(new Payout(0, 3, 63), new Payout(0, 1, 62));
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 1012L, 2, 975L, 3, 1013L));
        assertThat(table.hand().result().winners()).containsExactly(1, 3);
    }

    // ---- short stacks on the blinds

    @Test
    void aBigBlindWhoCannotCoverItIsAllInAndOthersStillPayTheFullBlind() throws Exception {
        HandFixture table = new HandFixture().button(1)
                .seat(1, 1000, "Kh Kd").seat(2, 1000, "Qh Qd").seat(3, 60, "Ah Ad").board(BOARD).start();

        BlindPosted bigBlind = table.events(BlindPosted.class).get(1);
        assertThat(bigBlind.seat()).isEqualTo(3);
        assertThat(bigBlind.amount()).isEqualTo(60);
        assertThat(bigBlind.allIn()).isTrue();

        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 100, false, true, 200, 1000));
        table.act(1, new Call());
        table.act(2, new Call());

        assertThat(table.events(UncalledBetReturned.class)).isEmpty();
        assertThat(table.last(BetsCollected.class).pots())
                .containsExactly(new Pot(180, Set.of(1, 2, 3)), new Pot(80, Set.of(1, 2)));

        checkStreet(table, 2, 1);
        checkStreet(table, 2, 1);
        checkStreet(table, 2, 1);

        // With no river bet the hands are shown clockwise from the left of the button.
        assertThat(table.last(ShowdownRevealed.class).hands())
                .extracting(ShowdownRevealed.ShownHand::seat).containsExactly(2, 3, 1);
        List<PotAwarded> awarded = table.events(PotAwarded.class);
        assertThat(awarded.get(0).payouts()).containsExactly(new Payout(0, 3, 180));
        assertThat(awarded.get(1).payouts()).containsExactly(new Payout(1, 1, 80));
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 980L, 2, 900L, 3, 180L));
    }

    @Test
    void aSmallBlindWhoCannotCoverItHeadsUpIsAllInAndTheHandRunsOutAtOnce() {
        HandFixture table = headsUp(30, 1000);

        assertThat(table.events(BlindPosted.class).get(0).amount()).isEqualTo(30);
        assertThat(table.events(BlindPosted.class).get(0).allIn()).isTrue();
        assertThat(table.events(ActionRequested.class)).isEmpty();
        assertThat(table.last(UncalledBetReturned.class).seat()).isEqualTo(2);
        assertThat(table.last(UncalledBetReturned.class).amount()).isEqualTo(70);
        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 60L, 2, 970L));
    }

    // ---- raise sizes

    @Test
    void aRaiseMustBeAtLeastAsBigAsTheLastRaise() throws Exception {
        HandFixture table = headsUp(1000, 1000);

        assertThatThrownBy(() -> table.act(1, new Raise(150))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> table.act(1, new Raise(1001))).isInstanceOf(InvalidAmountException.class);
        // A refused action changes nothing.
        assertThat(table.hand().seatToAct()).isEqualTo(1);
        assertThat(table.stack(1)).isEqualTo(950);

        table.act(1, new Raise(200));   // raises by 100
        assertThat(table.hand().legalActionsFor(2).minRaiseTo()).isEqualTo(300);

        table.act(2, new Raise(500));   // raises by 300
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 300, false, true, 800, 1000));
    }

    @Test
    void theBigBlindMayRaiseAfterEveryoneJustCalls() throws Exception {
        HandFixture table = threeHanded();
        table.act(1, new Call());
        table.act(2, new Call());

        assertThat(table.hand().legalActionsFor(3))
                .isEqualTo(new LegalActions(true, true, 0, false, true, 200, 1000));
        table.act(3, new Raise(300));

        assertThat(table.hand().seatToAct()).isEqualTo(1);
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 200, false, true, 500, 1000));
    }

    // ---- refused actions

    @Test
    void actionsThatDoNotFitTheSituationAreRefused() throws Exception {
        HandFixture table = headsUp(1000, 1000);

        // Preflop the button faces the big blind.
        assertThatThrownBy(() -> table.act(1, new Check())).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> table.act(1, new Bet(200))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> table.act(2, new Fold())).isInstanceOf(NotYourTurnException.class);
        assertThatThrownBy(() -> table.act(7, new Fold())).isInstanceOf(NotYourTurnException.class);

        table.act(1, new Call());
        table.act(2, new Check());

        // On the flop nobody has bet.
        assertThatThrownBy(() -> table.act(2, new Call())).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> table.act(2, new Raise(300))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> table.act(2, new Bet(99))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> table.act(2, new Bet(901))).isInstanceOf(InvalidAmountException.class);

        table.act(2, new Bet(100));
        table.act(1, new Fold());

        assertThatThrownBy(() -> table.act(2, new Check())).isInstanceOf(InvalidActionException.class);
    }

    @Test
    void legalActionsAreOnlyGivenToThePlayerWhoseTurnItIs() {
        HandFixture table = headsUp(1000, 1000);

        assertThat(table.hand().legalActionsFor(2)).isEqualTo(LegalActions.NONE);
        assertThat(table.hand().legalActionsFor(7)).isEqualTo(LegalActions.NONE);
        assertThat(table.hand().legalActionsFor(1).canAct()).isTrue();
    }

    // ---- all-in turned into the right action

    @Test
    void allInBecomesABetOrACallDependingOnTheSituation() throws Exception {
        HandFixture table = headsUp(1000, 1000);
        table.act(1, new Call());
        table.act(2, new Check());

        table.act(2, new AllIn());
        PlayerActed bet = table.last(PlayerActed.class);
        assertThat(bet.type()).isEqualTo(ActionType.BET);
        assertThat(bet.amount()).isEqualTo(900);
        assertThat(bet.allIn()).isTrue();

        table.act(1, new AllIn());
        PlayerActed call = table.last(PlayerActed.class);
        assertThat(call.type()).isEqualTo(ActionType.CALL);
        assertThat(call.amount()).isEqualTo(900);

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 2000L, 2, 0L));
    }

    // ---- showdown order

    @Test
    void theLastPlayerToRaiseOnTheRiverShowsFirst() throws Exception {
        HandFixture table = headsUp(2000, 2000);
        table.act(1, new Call());
        table.act(2, new Check());
        checkStreet(table, 2, 1);
        checkStreet(table, 2, 1);

        table.act(2, new Bet(100));
        table.act(1, new Raise(300));
        table.act(2, new Call());

        assertThat(table.last(ShowdownRevealed.class).hands())
                .extracting(ShowdownRevealed.ShownHand::seat).containsExactly(1, 2);
    }

    // ---- forced folds

    @Test
    void aPlayerCanBeFoldedOutOfTurnWithoutDisturbingTheCurrentTurn() {
        HandFixture table = threeHanded();

        List<GameEvent> events = table.forceFold(2);

        assertThat(events).hasSize(1);
        PlayerActed folded = (PlayerActed) events.get(0);
        assertThat(folded.seat()).isEqualTo(2);
        assertThat(folded.type()).isEqualTo(ActionType.FOLD);
        assertThat(table.hand().seatToAct()).isEqualTo(1);
        assertThat(table.hand().seat(2).folded()).isTrue();

        // Folding the same seat again does nothing.
        assertThat(table.forceFold(2)).isEmpty();
    }

    @Test
    void foldingThePlayerWhoseTurnItIsMovesTheHandOn() {
        HandFixture table = threeHanded();
        table.forceFold(2);

        table.forceFold(1);

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 1000L, 2, 950L, 3, 1050L));
        assertThat(table.forceFold(3)).isEmpty();
    }

    @Test
    void aForcedFoldThatLeavesOnePlayerEndsTheHandAndTheFoldedBlindStaysInThePot() {
        HandFixture table = headsUp(1000, 1000);

        table.forceFold(2);

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.events(UncalledBetReturned.class)).isEmpty();
        assertThat(table.last(PotAwarded.class).payouts()).containsExactly(new Payout(0, 1, 150));
        assertThat(table.hand().result().endStacks()).isEqualTo(Map.of(1, 1100L, 2, 900L));
    }

    @Test
    void aForcedFoldTellsTheCurrentPlayerAgainWhenTheirOptionsChange() throws Exception {
        // Seat 3 is all-in on the big blind, so once seat 2 folds nobody could call a raise.
        HandFixture table = new HandFixture().button(1)
                .seat(1, 1000, "Kh Kd").seat(2, 1000, "Qh Qd").seat(3, 100, "Ah Ad").board(BOARD).start();
        assertThat(table.hand().legalActionsFor(1).canRaise()).isTrue();

        List<GameEvent> events = table.forceFold(2);

        assertThat(HandFixture.of(events, ActionRequested.class)).hasSize(1);
        assertThat(table.hand().seatToAct()).isEqualTo(1);
        assertThat(table.hand().legalActionsFor(1))
                .isEqualTo(new LegalActions(true, false, 100, false, false, 0, 0));

        table.act(1, new Call());

        assertThat(table.hand().isComplete()).isTrue();
        assertThat(table.last(PotAwarded.class).payouts()).containsExactly(new Payout(0, 3, 250));
    }

    @Test
    void forcedFoldRejectsASeatThatIsNotInTheHand() {
        HandFixture table = headsUp(1000, 1000);

        assertThatIllegalArgumentException().isThrownBy(() -> table.forceFold(7));
    }

    // ---- reading the hand while it is played

    @Test
    void theHandReportsStacksBetsAndPotsAsItGoes() throws Exception {
        HandFixture table = headsUp(1000, 1000);

        assertThat(table.hand().currentBet()).isEqualTo(100);
        assertThat(table.hand().seat(1).streetBet()).isEqualTo(50);
        assertThat(table.hand().seat(2).stack()).isEqualTo(900);
        assertThat(table.hand().pots()).isEmpty();
        assertThat(table.hand().board()).isEmpty();
        assertThat(table.hand().seat(9)).isNull();

        table.act(1, new Call());
        table.act(2, new Check());

        assertThat(table.hand().currentBet()).isZero();
        assertThat(table.hand().seat(1).streetBet()).isZero();
        assertThat(table.hand().seat(1).totalCommitted()).isEqualTo(100);
        assertThat(table.hand().pots()).containsExactly(new Pot(200, Set.of(1, 2)));
        assertThat(table.hand().board()).hasSize(3);
    }

    // ---- using the hand in the wrong order

    @Test
    void theHandMustBeStartedOnceBeforeAnythingElse() {
        HoldemHand hand = new HoldemHand(
                new HandConfig(50, 100, 1, Map.of(1, 1000L, 2, 1000L)), new Deck(Deck.standardOrder()));

        assertThatIllegalStateException().isThrownBy(() -> hand.apply(1, new Call()));
        assertThatIllegalStateException().isThrownBy(() -> hand.forceFold(1));
        assertThatIllegalStateException().isThrownBy(hand::result);

        hand.start();

        assertThatIllegalStateException().isThrownBy(hand::start);
        assertThatIllegalStateException().isThrownBy(hand::result);
    }

    @Test
    void aHandNeedsSensibleSettings() {
        Map<Integer, Long> two = Map.of(1, 1000L, 2, 1000L);

        assertThatIllegalArgumentException().isThrownBy(() -> new HandConfig(0, 100, 1, two));
        assertThatIllegalArgumentException().isThrownBy(() -> new HandConfig(100, 50, 1, two));
        assertThatIllegalArgumentException().isThrownBy(() -> new HandConfig(50, 100, 3, two));
        assertThatIllegalArgumentException().isThrownBy(() -> new HandConfig(50, 100, 1, Map.of(1, 1000L)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HandConfig(50, 100, 1, Map.of(1, 1000L, 2, 0L)));
        assertThatIllegalArgumentException().isThrownBy(() -> new HandConfig(50, 100, 1, Map.of(
                0, 1L, 1, 1L, 2, 1L, 3, 1L, 4, 1L, 5, 1L, 6, 1L, 7, 1L, 8, 1L, 9, 1L)));
    }
}
