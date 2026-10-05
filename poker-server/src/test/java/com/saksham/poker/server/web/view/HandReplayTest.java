package com.saksham.poker.server.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.server.web.view.HandReplay.PlayerLine;
import com.saksham.poker.server.web.view.HandReplay.Step;
import com.saksham.poker.server.web.view.HandReplay.Street;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandReplayTest {

    private static HandPlayerInfo player(int seat, String name, String cards, long net, boolean won) {
        return new HandPlayerInfo(seat, seat + 100, name, Card.parseAll(cards), 10_000, 10_000 + net, net,
                !cards.isEmpty(), won);
    }

    private static HandDetail hand(String board, List<HandPlayerInfo> players, List<HandActionInfo> actions) {
        return new HandDetail(7, "ABC234", "Friday game", 3, 1, Card.parseAll(board), 600, 0, 1_000, players,
                actions);
    }

    @Test
    void actionsAreGroupedByStreetWithTheCardsDealtOnEach() {
        HandReplay replay = HandReplay.from(hand("2c 5d 9h Js 3s",
                List.of(player(0, "asha", "", 0, false), player(1, "ravi", "Kh Kd", -300, false),
                        player(2, "meera", "Ah Ad", 300, true)),
                List.of(new HandActionInfo(1, 1, "ravi", "PREFLOP", "POST_SB", 50),
                        new HandActionInfo(2, 2, "meera", "PREFLOP", "POST_BB", 100),
                        new HandActionInfo(3, 0, "asha", "PREFLOP", "FOLD", 0),
                        new HandActionInfo(4, 1, "ravi", "PREFLOP", "CALL", 50),
                        new HandActionInfo(5, 2, "meera", "PREFLOP", "CHECK", 0),
                        new HandActionInfo(6, 1, "ravi", "FLOP", "BET", 200),
                        new HandActionInfo(7, 2, "meera", "FLOP", "CALL", 200),
                        new HandActionInfo(8, 1, "ravi", "TURN", "CHECK", 0),
                        new HandActionInfo(9, 2, "meera", "TURN", "CHECK", 0))));

        assertThat(replay.streets()).extracting(Street::name)
                .containsExactly("Before the flop", "Flop", "Turn", "River");
        assertThat(replay.streets().get(0).cards()).isEmpty();
        assertThat(replay.streets().get(1).cards()).extracting(CardView::rank).containsExactly("2", "5", "9");
        assertThat(replay.streets().get(2).cards()).extracting(CardView::rank).containsExactly("J");
        assertThat(replay.streets().get(3).cards()).extracting(CardView::rank).containsExactly("3");
        assertThat(replay.streets().get(0).steps()).containsExactly(
                new Step("ravi", "posts the small blind, 50"), new Step("meera", "posts the big blind, 100"),
                new Step("asha", "folds"), new Step("ravi", "calls 50"), new Step("meera", "checks"));
        assertThat(replay.streets().get(1).steps())
                .containsExactly(new Step("ravi", "bets 200"), new Step("meera", "calls 200"));
        // A street that was dealt but had no betting is still there, with nothing in it.
        assertThat(replay.streets().get(3).steps()).isEmpty();
    }

    @Test
    void aRaiseSaysWhatItWasRaisedToNotHowMuchWasAdded() {
        HandReplay replay = HandReplay.from(hand("",
                List.of(player(0, "asha", "", 100, true), player(1, "ravi", "", -100, false)),
                List.of(new HandActionInfo(1, 0, "asha", "PREFLOP", "POST_SB", 50),
                        new HandActionInfo(2, 1, "ravi", "PREFLOP", "POST_BB", 100),
                        new HandActionInfo(3, 0, "asha", "PREFLOP", "RAISE", 1_250),
                        new HandActionInfo(4, 1, "ravi", "PREFLOP", "FOLD", 0))));

        assertThat(replay.streets()).extracting(Street::name).containsExactly("Before the flop");
        assertThat(replay.streets().get(0).steps().get(2)).isEqualTo(new Step("asha", "raises to 1,300"));
    }

    @Test
    void playersAreNumberedFromOneAndTheDealerIsMarked() {
        HandReplay replay = HandReplay.from(hand("",
                List.of(player(0, "asha", "", 100, true), player(1, "ravi", "Kh Td", -100, false)), List.of()));

        assertThat(replay.players()).extracting(PlayerLine::seat).containsExactly(1, 2);
        assertThat(replay.players()).extracting(PlayerLine::button).containsExactly(false, true);
        assertThat(replay.players().get(0).cards()).isEmpty();
        assertThat(replay.players().get(1).cards())
                .containsExactly(new CardView("K", "♥", "red"), new CardView("10", "♦", "red"));
    }

    @Test
    void cardsAreDrawnWithTheirSuitSymbolAndColour() {
        assertThat(CardView.of(Card.parseAll("As 2c")))
                .containsExactly(new CardView("A", "♠", "black"), new CardView("2", "♣", "black"));
    }
}
