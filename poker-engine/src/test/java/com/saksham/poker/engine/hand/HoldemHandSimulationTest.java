package com.saksham.poker.engine.hand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.NotYourTurnException;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.eval.HandEvaluator;
import com.saksham.poker.engine.eval.HandValue;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.event.HandCompleted;
import com.saksham.poker.engine.pot.Payout;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Plays a large number of random hands and checks that nothing breaks and no chip goes missing. */
class HoldemHandSimulationTest {

    private static final int HANDS = 100_000;

    @Test
    void randomHandsNeverFailAndAlwaysConserveChips() throws Exception {
        Random random = new Random(20261005L);
        RandomPlayer player = new RandomPlayer(random);
        int showdowns = 0;
        int sidePotHands = 0;
        int splitPots = 0;
        int forcedFolds = 0;

        for (int handNumber = 0; handNumber < HANDS; handNumber++) {
            HandConfig config = randomConfig(random);
            long totalChips = config.stacks().values().stream().mapToLong(Long::longValue).sum();
            List<Card> cards = new ArrayList<>(Deck.standardOrder());
            Collections.shuffle(cards, random);
            HoldemHand hand = new HoldemHand(config, new Deck(cards));
            String context = "hand " + handNumber + " " + config;

            List<GameEvent> events = hand.start();
            int steps = 0;
            while (!hand.isComplete()) {
                assertThat(steps++).as(context).isLessThan(1000);
                checkWaitingState(hand, events, totalChips, context);
                int seat = hand.seatToAct();

                if (random.nextInt(100) == 0) {
                    // A player leaves in the middle of the hand.
                    List<Integer> seats = new ArrayList<>(config.stacks().keySet());
                    events = hand.forceFold(seats.get(random.nextInt(seats.size())));
                    forcedFolds++;
                    boolean askedAgain = !events.isEmpty()
                            && events.get(events.size() - 1) instanceof ActionRequested;
                    if (!hand.isComplete() && !askedAgain) {
                        // An out-of-turn fold that changes nothing for the player being waited on
                        // does not ask them again: it is still their turn with the same options.
                        assertThat(hand.seatToAct()).as(context).isEqualTo(seat);
                        events = List.of(new ActionRequested(seat, hand.legalActionsFor(seat)));
                    }
                    continue;
                }
                if (random.nextInt(50) == 0) {
                    int wrongSeat = seat + 1;
                    assertThatThrownBy(() -> hand.apply(wrongSeat, new Fold()))
                            .as(context).isInstanceOf(NotYourTurnException.class);
                }
                PlayerAction action = player.choose(hand.legalActionsFor(seat), hand.seat(seat).stack());
                events = hand.apply(seat, action);
            }

            HandResult result = hand.result();
            assertThat(events.get(events.size() - 1)).as(context).isInstanceOf(HandCompleted.class);
            long endChips = 0;
            long net = 0;
            for (int seat : config.stacks().keySet()) {
                assertThat(result.endStacks().get(seat)).as(context).isNotNegative();
                endChips += result.endStacks().get(seat);
                net += result.net(seat);
            }
            assertThat(endChips).as(context).isEqualTo(totalChips);
            assertThat(net).as(context).isZero();
            long paid = 0;
            Set<Integer> potsPaid = new HashSet<>();
            for (Payout payout : result.payouts()) {
                assertThat(payout.amount()).as(context).isPositive();
                assertThat(hand.seat(payout.seat()).folded()).as(context).isFalse();
                paid += payout.amount();
                if (!potsPaid.add(payout.potIndex())) {
                    splitPots++;
                }
            }
            assertThat(paid).as(context).isEqualTo(result.totalPot());
            assertThat(result.board().size()).as(context).isIn(0, 3, 4, 5);
            if (result.showdown()) {
                assertThat(result.board()).as(context).hasSize(5);
                assertThat(mainPotWinners(result)).as(context).isEqualTo(bestHands(result));
                showdowns++;
            }
            if (potsPaid.size() > 1) {
                sidePotHands++;
            }
        }

        // The run must have exercised the interesting paths, or it proves little.
        assertThat(showdowns).isGreaterThan(HANDS / 20);
        assertThat(sidePotHands).isGreaterThan(HANDS / 100);
        assertThat(splitPots).isGreaterThan(HANDS / 1000);
        assertThat(forcedFolds).isGreaterThan(HANDS / 1000);
    }

    /** While the hand waits for a player: it is someone's turn, they have options, and all chips are accounted for. */
    private static void checkWaitingState(HoldemHand hand, List<GameEvent> lastEvents, long totalChips,
            String context) {
        int seat = hand.seatToAct();
        assertThat(seat).as(context).isNotNegative();
        GameEvent last = lastEvents.get(lastEvents.size() - 1);
        assertThat(last).as(context).isInstanceOf(ActionRequested.class);
        assertThat(((ActionRequested) last).seat()).as(context).isEqualTo(seat);

        LegalActions legal = hand.legalActionsFor(seat);
        assertThat(legal.canAct()).as(context).isTrue();
        assertThat(legal).as(context).isEqualTo(((ActionRequested) last).legal());
        SeatState state = hand.seat(seat);
        assertThat(state.canBet()).as(context).isTrue();
        assertThat(legal.callAmount()).as(context).isBetween(0L, state.stack());
        assertThat(legal.canCheck()).as(context).isEqualTo(legal.callAmount() == 0);
        assertThat(legal.canBet() && legal.canRaise()).as(context).isFalse();
        if (legal.canBet() || legal.canRaise()) {
            assertThat(legal.maxRaiseTo()).as(context).isEqualTo(state.streetBet() + state.stack());
            assertThat(legal.minRaiseTo()).as(context).isBetween(1L, legal.maxRaiseTo());
            assertThat(legal.maxRaiseTo()).as(context).isGreaterThan(hand.currentBet());
        }

        long chips = 0;
        for (SeatState each : hand.seats()) {
            assertThat(each.stack()).as(context).isNotNegative();
            assertThat(each.streetBet()).as(context).isNotNegative();
            chips += each.stack() + each.totalCommitted();
        }
        assertThat(chips).as(context).isEqualTo(totalChips);
    }

    /** Everyone still in at showdown can win the main pot, so it must go to the best hand or hands. */
    private static Set<Integer> bestHands(HandResult result) {
        Set<Integer> best = new HashSet<>();
        HandValue bestValue = null;
        for (int seat : result.shownSeats()) {
            List<Card> seven = new ArrayList<>(result.holeCards().get(seat));
            seven.addAll(result.board());
            HandValue value = HandEvaluator.evaluate(seven);
            int compared = bestValue == null ? 1 : value.compareTo(bestValue);
            if (compared > 0) {
                best.clear();
                bestValue = value;
            }
            if (compared >= 0) {
                best.add(seat);
            }
        }
        return best;
    }

    private static Set<Integer> mainPotWinners(HandResult result) {
        Set<Integer> winners = new HashSet<>();
        for (Payout payout : result.payouts()) {
            if (payout.potIndex() == 0) {
                winners.add(payout.seat());
            }
        }
        return winners;
    }

    /** Two to nine players in random seats, with stacks from a few chips to very deep. */
    private static HandConfig randomConfig(Random random) {
        long smallBlind = 1 + random.nextInt(50);
        long bigBlind = smallBlind + random.nextInt((int) smallBlind + 1);
        List<Integer> seatNumbers = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
        Collections.shuffle(seatNumbers, random);
        int players = 2 + random.nextInt(8);
        Map<Integer, Long> stacks = new TreeMap<>();
        for (int seat : seatNumbers.subList(0, players)) {
            long stack;
            switch (random.nextInt(4)) {
                case 0:
                    stack = 1 + random.nextInt((int) (bigBlind * 3));
                    break;
                case 1:
                    stack = bigBlind * (5 + random.nextInt(30));
                    break;
                case 2:
                    stack = bigBlind * (20 + random.nextInt(100)) + random.nextInt(50);
                    break;
                default:
                    stack = bigBlind * (100 + random.nextInt(400));
                    break;
            }
            stacks.put(seat, stack);
        }
        int button = seatNumbers.get(random.nextInt(players));
        return new HandConfig(smallBlind, bigBlind, button, stacks);
    }
}
