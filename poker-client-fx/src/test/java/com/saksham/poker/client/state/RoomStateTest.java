package com.saksham.poker.client.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.dto.HandInfo;
import com.saksham.poker.common.protocol.dto.HandSeatInfo;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import com.saksham.poker.common.protocol.dto.TurnInfo;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.ChatPosted;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.GameState;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.HostChanged;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PlayerJoined;
import com.saksham.poker.common.protocol.server.PlayerLeft;
import com.saksham.poker.common.protocol.server.Pong;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The room state is fed the messages a server would send, and checked for what the screens would
 * then show. You are "asha", user 1, the host, in seat 0.
 */
class RoomStateTest {

    private static final RoomSettingsInfo SETTINGS = new RoomSettingsInfo("Friday game", 6, 50, 100, 10_000, 25, true);
    private static final com.saksham.poker.common.protocol.dto.RoomState WAITING =
            com.saksham.poker.common.protocol.dto.RoomState.WAITING;
    private static final com.saksham.poker.common.protocol.dto.RoomState PLAYING =
            com.saksham.poker.common.protocol.dto.RoomState.PLAYING;
    private static final com.saksham.poker.common.protocol.dto.RoomState PAUSED =
            com.saksham.poker.common.protocol.dto.RoomState.PAUSED;
    private static final com.saksham.poker.common.protocol.dto.RoomState CLOSED =
            com.saksham.poker.common.protocol.dto.RoomState.CLOSED;

    private static final PlayerInfo ASHA = new PlayerInfo(1, "asha", 0, 10_000, false, true);
    private static final PlayerInfo RAVI = new PlayerInfo(2, "ravi", 1, 10_000, false, true);
    private static final PlayerInfo MEERA = new PlayerInfo(3, "meera", PlayerInfo.NO_SEAT, 0, false, true);

    private final RoomState state = new RoomState();

    /** You and ravi seated, meera standing, waiting for the game to start. */
    @BeforeEach
    void joinTheRoom() {
        state.apply(new RoomSnapshot("ABC234", SETTINGS, WAITING, 1, List.of(ASHA, RAVI, MEERA), null, 1, 0,
                List.of()));
    }

    /** Deals hand 1 heads-up: you on the button posting 50, ravi posting 100, and your cards. */
    private void dealAHand() {
        state.apply(new GameState(PLAYING));
        state.apply(new HandStarted(1, 0, 0, 1, 50, 100, Map.of(0, 10_000L, 1, 10_000L)));
        state.apply(new BlindPosted(0, 50, false, false));
        state.apply(new BlindPosted(1, 100, true, false));
        state.apply(new HoleCards(0, Card.parseAll("Ah Ad")));
        state.apply(new ActionRequired(0, 1, false, 50, false, true, 200, 10_000, 1_790_000_025_000L));
    }

    private String lastLog() {
        return state.handLog().get(state.handLog().size() - 1);
    }

    // =====================================================================================
    // The room
    // =====================================================================================

    @Test
    void aSnapshotFillsInTheWholeRoom() {
        assertThat(state.codeProperty().get()).isEqualTo("ABC234");
        assertThat(state.settingsProperty().get()).isEqualTo(SETTINGS);
        assertThat(state.waiting()).isTrue();
        assertThat(state.youAreHost()).isTrue();
        assertThat(state.youAreSeated()).isTrue();
        assertThat(state.yourSeatProperty().get()).isZero();
        assertThat(state.players()).containsExactly(ASHA, RAVI, MEERA);
        assertThat(state.seatedCount()).isEqualTo(2);

        // One view model per seat, filled in for the two that are taken.
        assertThat(state.seats()).hasSize(6);
        assertThat(state.seatAt(0).usernameProperty().get()).isEqualTo("asha");
        assertThat(state.seatAt(1).occupiedProperty().get()).isTrue();
        assertThat(state.seatAt(1).stackProperty().get()).isEqualTo(10_000);
        assertThat(state.seatAt(2).occupiedProperty().get()).isFalse();
        assertThat(state.seatAt(6)).isNull();
        assertThat(state.seatAt(-1)).isNull();
        assertThat(state.handInProgressProperty().get()).isFalse();
    }

    @Test
    void aPlayerJoiningThenSittingAppearsInTheListAndTheSeat() {
        PlayerInfo dev = new PlayerInfo(4, "dev", PlayerInfo.NO_SEAT, 0, false, true);
        state.apply(new PlayerJoined(dev));

        assertThat(state.players()).hasSize(4);
        assertThat(state.player(4)).isEqualTo(dev);
        assertThat(lastLog()).isEqualTo("dev joined the room");

        state.apply(new SeatUpdate(new PlayerInfo(4, "dev", 3, 10_000, false, true)));

        assertThat(state.players()).hasSize(4);
        assertThat(state.seatedCount()).isEqualTo(3);
        assertThat(state.seatAt(3).usernameProperty().get()).isEqualTo("dev");
        assertThat(state.seatAt(3).stackProperty().get()).isEqualTo(10_000);
    }

    @Test
    void aPlayerWhoMovesSeatLeavesTheOldOneEmpty() {
        state.apply(new SeatUpdate(new PlayerInfo(2, "ravi", 4, 10_000, false, true)));

        assertThat(state.seatAt(1).occupiedProperty().get()).isFalse();
        assertThat(state.seatAt(1).usernameProperty().get()).isEmpty();
        assertThat(state.seatAt(4).usernameProperty().get()).isEqualTo("ravi");
        assertThat(state.seatedCount()).isEqualTo(2);
    }

    @Test
    void yourOwnSeatFollowsYourSeatUpdates() {
        state.apply(new SeatUpdate(new PlayerInfo(1, "asha", 5, 10_000, false, true)));

        assertThat(state.yourSeatProperty().get()).isEqualTo(5);
        assertThat(state.seatAt(0).occupiedProperty().get()).isFalse();
    }

    @Test
    void sittingOutAndLosingTheConnectionShowOnTheSeat() {
        state.apply(new SeatUpdate(new PlayerInfo(2, "ravi", 1, 10_000, true, false)));

        assertThat(state.seatAt(1).sittingOutProperty().get()).isTrue();
        assertThat(state.seatAt(1).connectedProperty().get()).isFalse();
        assertThat(state.player(2).sittingOut()).isTrue();
    }

    @Test
    void aPlayerLeavingIsRemovedAndTheirSeatFreed() {
        state.apply(new PlayerLeft(2, LeaveReason.LEFT));

        assertThat(state.players()).containsExactly(ASHA, MEERA);
        assertThat(state.seatAt(1).occupiedProperty().get()).isFalse();
        assertThat(lastLog()).isEqualTo("ravi left the room");
        assertThat(state.goneProperty().get()).isFalse();

        state.apply(new PlayerLeft(3, LeaveReason.KICKED));
        assertThat(lastLog()).isEqualTo("meera was removed by the host");
    }

    @Test
    void beingRemovedOrLeavingYourselfEndsYourTimeInTheRoom() {
        state.apply(new PlayerLeft(1, LeaveReason.KICKED));

        assertThat(state.goneProperty().get()).isTrue();
        assertThat(state.goneReasonProperty().get()).isEqualTo("The host removed you from the room.");
        assertThat(state.youAreSeated()).isFalse();
    }

    @Test
    void theRoomClosingEndsYourTimeInIt() {
        state.apply(new GameState(CLOSED));

        assertThat(state.goneProperty().get()).isTrue();
        assertThat(state.goneReasonProperty().get()).isEqualTo("The room has closed.");
    }

    @Test
    void theHostCanChange() {
        state.apply(new HostChanged(2));

        assertThat(state.youAreHost()).isFalse();
        assertThat(state.hostUserIdProperty().get()).isEqualTo(2);
        assertThat(lastLog()).isEqualTo("ravi is now the host");
    }

    @Test
    void theGameStartingPausingAndResumingChangesTheStage() {
        state.apply(new GameState(PLAYING));
        assertThat(state.waiting()).isFalse();
        assertThat(state.stageProperty().get()).isEqualTo(PLAYING);

        state.apply(new GameState(PAUSED));
        assertThat(state.paused()).isTrue();
        assertThat(lastLog()).isEqualTo("The host paused the game");
        assertThat(state.goneProperty().get()).isFalse();
    }

    @Test
    void chatAndErrorsAreKeptForTheScreenToShow() {
        state.apply(new ChatPosted(2, "ravi", "good luck"));
        assertThat(state.chat()).containsExactly(new ChatLine(2, "ravi", "good luck"));

        ErrorMessage error = new ErrorMessage(ErrorCode.SEAT_TAKEN, "Seat 1 is taken. Choose another seat.");
        state.apply(error);
        assertThat(state.lastErrorProperty().get()).isSameAs(error);
    }

    @Test
    void messagesThatChangeNothingAreIgnored() {
        state.apply(new Pong());

        assertThat(state.players()).hasSize(3);
    }

    // =====================================================================================
    // A hand
    // =====================================================================================

    @Test
    void aNewHandSetsOutStacksBlindsButtonAndYourCards() {
        dealAHand();

        assertThat(state.handInProgressProperty().get()).isTrue();
        assertThat(state.handNoProperty().get()).isEqualTo(1);
        assertThat(state.streetProperty().get()).isEqualTo("PREFLOP");
        SeatViewModel you = state.seatAt(0);
        SeatViewModel ravi = state.seatAt(1);
        assertThat(you.inHandProperty().get()).isTrue();
        assertThat(you.buttonProperty().get()).isTrue();
        assertThat(ravi.buttonProperty().get()).isFalse();
        assertThat(you.stackProperty().get()).isEqualTo(9_950);
        assertThat(you.streetBetProperty().get()).isEqualTo(50);
        assertThat(ravi.stackProperty().get()).isEqualTo(9_900);
        assertThat(ravi.streetBetProperty().get()).isEqualTo(100);
        // Your cards are face up; nobody else's are known.
        assertThat(state.yourCards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(you.cards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(ravi.cards()).isEmpty();
        assertThat(state.seatAt(2).inHandProperty().get()).isFalse();
        assertThat(state.handLog()).contains("Hand #1 - blinds 50/100", "You post the small blind 50",
                "ravi posts the big blind 100", "You are dealt Ah Ad");
    }

    @Test
    void whoseTurnItIsShowsOnTheSeatAndInTheTurn() {
        dealAHand();

        TurnInfo turn = state.turnProperty().get();
        assertThat(turn.seat()).isZero();
        assertThat(turn.turnId()).isEqualTo(1);
        assertThat(turn.callAmount()).isEqualTo(50);
        assertThat(turn.canRaise()).isTrue();
        assertThat(turn.minRaiseTo()).isEqualTo(200);
        assertThat(turn.deadlineEpochMs()).isEqualTo(1_790_000_025_000L);
        assertThat(state.yourTurn()).isTrue();
        assertThat(state.seatAt(0).turnProperty().get()).isTrue();
        assertThat(state.seatAt(1).turnProperty().get()).isFalse();
    }

    @Test
    void anActionUpdatesTheSeatEndsThatTurnAndIsLogged() {
        dealAHand();

        state.apply(new PlayerActed(0, ActionType.RAISE, 250, 300, 9_700, false));

        SeatViewModel you = state.seatAt(0);
        assertThat(you.stackProperty().get()).isEqualTo(9_700);
        assertThat(you.streetBetProperty().get()).isEqualTo(300);
        assertThat(you.lastActionProperty().get()).isEqualTo("Raise 300");
        assertThat(state.turnProperty().get()).isNull();
        assertThat(state.yourTurn()).isFalse();
        assertThat(you.turnProperty().get()).isFalse();
        assertThat(lastLog()).isEqualTo("You raise to 300");

        state.apply(new ActionRequired(1, 2, false, 200, false, true, 500, 10_000, 0));
        assertThat(state.yourTurn()).isFalse();
        assertThat(state.seatAt(1).turnProperty().get()).isTrue();

        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 9_900, false));
        assertThat(state.seatAt(1).foldedProperty().get()).isTrue();
        assertThat(state.seatAt(1).lastActionProperty().get()).isEqualTo("Fold");
        assertThat(lastLog()).isEqualTo("ravi folds");
    }

    @Test
    void everyKindOfActionHasAShortLabel() {
        dealAHand();

        state.apply(new PlayerActed(0, ActionType.CALL, 50, 100, 9_900, false));
        assertThat(state.seatAt(0).lastActionProperty().get()).isEqualTo("Call 50");
        state.apply(new PlayerActed(1, ActionType.CHECK, 0, 100, 9_900, false));
        assertThat(state.seatAt(1).lastActionProperty().get()).isEqualTo("Check");
        state.apply(new PlayerActed(1, ActionType.BET, 9_900, 9_900, 0, true));
        assertThat(state.seatAt(1).lastActionProperty().get()).isEqualTo("Bet 9900");
        assertThat(state.seatAt(1).allInProperty().get()).isTrue();
        assertThat(lastLog()).isEqualTo("ravi bets 9900 and is all-in");
        state.apply(new PlayerActed(0, ActionType.CALL, 9_900, 9_900, 0, true));
        assertThat(lastLog()).isEqualTo("You call 9900 and are all-in");
    }

    @Test
    void aNewStreetShowsTheBoardAndClearsTheBetsInFrontOfPlayers() {
        dealAHand();
        state.apply(new PlayerActed(0, ActionType.CALL, 50, 100, 9_900, false));
        state.apply(new PlayerActed(1, ActionType.CHECK, 0, 100, 9_900, false));

        state.apply(new PotsUpdated(List.of(new PotInfo(200, List.of(0, 1)))));
        state.apply(new StreetDealt("FLOP", Card.parseAll("2c 5d 9h"), Card.parseAll("2c 5d 9h")));

        assertThat(state.streetProperty().get()).isEqualTo("FLOP");
        assertThat(state.board()).isEqualTo(Card.parseAll("2c 5d 9h"));
        assertThat(state.pots()).containsExactly(new PotInfo(200, List.of(0, 1)));
        assertThat(state.seatAt(0).streetBetProperty().get()).isZero();
        assertThat(state.seatAt(1).streetBetProperty().get()).isZero();
        assertThat(state.seatAt(0).lastActionProperty().get()).isEmpty();
        assertThat(lastLog()).isEqualTo("Flop: 2c 5d 9h");

        state.apply(new StreetDealt("TURN", Card.parseAll("Js"), Card.parseAll("2c 5d 9h Js")));
        assertThat(state.board()).hasSize(4);
        assertThat(lastLog()).isEqualTo("Turn: Js");
    }

    @Test
    void anUncalledBetGoesBackToTheStack() {
        dealAHand();
        state.apply(new PlayerActed(0, ActionType.RAISE, 9_950, 10_000, 0, true));
        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 9_900, false));

        state.apply(new BetReturned(0, 9_900));

        assertThat(state.seatAt(0).stackProperty().get()).isEqualTo(9_900);
        assertThat(state.seatAt(0).streetBetProperty().get()).isEqualTo(100);
        assertThat(state.seatAt(0).allInProperty().get()).isFalse();
        assertThat(lastLog()).isEqualTo("9900 returned to you");
    }

    @Test
    void atShowdownTheShownCardsAppearOnTheirSeats() {
        dealAHand();

        state.apply(new Showdown(List.of(new ShownHandInfo(1, Card.parseAll("Kh Kd"), "PAIR"),
                new ShownHandInfo(0, Card.parseAll("Ah Ad"), "TWO_PAIR"))));

        assertThat(state.streetProperty().get()).isEqualTo("SHOWDOWN");
        assertThat(state.seatAt(1).cards()).isEqualTo(Card.parseAll("Kh Kd"));
        assertThat(state.seatAt(1).shownHandProperty().get()).isEqualTo("Pair");
        assertThat(state.seatAt(0).shownHandProperty().get()).isEqualTo("Two pair");
        assertThat(state.turnProperty().get()).isNull();
        assertThat(state.handLog()).contains("ravi shows Kh Kd (pair)", "You show Ah Ad (two pair)");
    }

    @Test
    void theEndOfAHandSettlesStacksAndMarksTheWinners() {
        dealAHand();
        state.apply(new PotsUpdated(List.of(new PotInfo(600, List.of(0, 1)))));

        state.apply(new HandEnded(List.of(new PayoutInfo(0, 0, 400), new PayoutInfo(1, 0, 200)),
                Map.of(0, 300L, 1, -300L), Map.of(0, 10_300L, 1, 9_700L)));

        assertThat(state.handInProgressProperty().get()).isFalse();
        assertThat(state.seatAt(0).stackProperty().get()).isEqualTo(10_300);
        assertThat(state.seatAt(1).stackProperty().get()).isEqualTo(9_700);
        // Winnings from more than one pot are added together.
        assertThat(state.seatAt(0).wonProperty().get()).isEqualTo(600);
        assertThat(state.seatAt(1).wonProperty().get()).isZero();
        assertThat(state.pots()).isEmpty();
        assertThat(state.turnProperty().get()).isNull();
        assertThat(lastLog()).isEqualTo("You win 600");
        // The cards stay on show until the next hand begins.
        assertThat(state.yourCards()).hasSize(2);
    }

    @Test
    void theNextHandWipesTheLastOne() {
        dealAHand();
        state.apply(new Showdown(List.of(new ShownHandInfo(1, Card.parseAll("Kh Kd"), "PAIR"))));
        state.apply(new StreetDealt("FLOP", Card.parseAll("2c 5d 9h"), Card.parseAll("2c 5d 9h")));
        state.apply(new HandEnded(List.of(new PayoutInfo(0, 0, 200)), Map.of(0, 100L, 1, -100L),
                Map.of(0, 10_100L, 1, 9_900L)));

        state.apply(new HandStarted(2, 1, 1, 0, 50, 100, Map.of(0, 10_100L, 1, 9_900L)));

        assertThat(state.handNoProperty().get()).isEqualTo(2);
        assertThat(state.board()).isEmpty();
        assertThat(state.yourCards()).isEmpty();
        assertThat(state.seatAt(0).wonProperty().get()).isZero();
        assertThat(state.seatAt(0).cards()).isEmpty();
        assertThat(state.seatAt(1).cards()).isEmpty();
        assertThat(state.seatAt(1).shownHandProperty().get()).isEmpty();
        assertThat(state.seatAt(1).buttonProperty().get()).isTrue();
        assertThat(state.seatAt(0).buttonProperty().get()).isFalse();
        assertThat(state.seatAt(0).stackProperty().get()).isEqualTo(10_100);
    }

    @Test
    void aPlayerNotDealtInIsNotInTheHand() {
        state.apply(new SeatUpdate(new PlayerInfo(3, "meera", 2, 10_000, false, true)));
        dealAHand();

        assertThat(state.seatAt(2).occupiedProperty().get()).isTrue();
        assertThat(state.seatAt(2).inHandProperty().get()).isFalse();
    }

    @Test
    void aPlayerWhoLeavesMidHandKeepsTheirBetOnShow() {
        dealAHand();

        state.apply(new PlayerLeft(2, LeaveReason.LEFT));

        assertThat(state.seatAt(1).occupiedProperty().get()).isFalse();
        assertThat(state.seatAt(1).inHandProperty().get()).isTrue();
        assertThat(state.seatAt(1).streetBetProperty().get()).isEqualTo(100);
        // With nobody's name on the seat, the log falls back to its number, counted from 1.
        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 9_900, false));
        assertThat(lastLog()).isEqualTo("Seat 2 folds");
    }

    // =====================================================================================
    // Reconnecting
    // =====================================================================================

    @Test
    void aSnapshotInTheMiddleOfAHandRestoresTheTable() {
        HandInfo hand = new HandInfo(7, 1, 0, 1, "TURN", Card.parseAll("2c 5d 9h Js"),
                List.of(new PotInfo(1_200, List.of(0, 1))),
                List.of(new HandSeatInfo(0, 9_400, 0, false, false), new HandSeatInfo(1, 0, 600, false, true)),
                new TurnInfo(0, 31, false, 600, false, false, 0, 0, 1_790_000_000_000L));

        state.apply(new RoomSnapshot("ABC234", SETTINGS, PLAYING, 2, List.of(ASHA, RAVI), hand, 1, 0,
                Card.parseAll("Ah Ad")));

        assertThat(state.waiting()).isFalse();
        assertThat(state.youAreHost()).isFalse();
        assertThat(state.handInProgressProperty().get()).isTrue();
        assertThat(state.handNoProperty().get()).isEqualTo(7);
        assertThat(state.streetProperty().get()).isEqualTo("TURN");
        assertThat(state.board()).hasSize(4);
        assertThat(state.pots()).containsExactly(new PotInfo(1_200, List.of(0, 1)));
        assertThat(state.yourCards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(state.seatAt(0).cards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(state.seatAt(0).stackProperty().get()).isEqualTo(9_400);
        assertThat(state.seatAt(1).streetBetProperty().get()).isEqualTo(600);
        assertThat(state.seatAt(1).allInProperty().get()).isTrue();
        assertThat(state.seatAt(1).buttonProperty().get()).isTrue();
        assertThat(state.yourTurn()).isTrue();
        assertThat(state.turnProperty().get().turnId()).isEqualTo(31);
        assertThat(state.players()).containsExactly(ASHA, RAVI);
    }

    @Test
    void aSnapshotReplacesWhateverWasKnownBefore() {
        dealAHand();
        state.apply(new PlayerLeft(1, LeaveReason.DISCONNECTED));
        assertThat(state.goneProperty().get()).isTrue();

        // Back in, between hands: nothing of the old hand may linger.
        state.apply(new RoomSnapshot("ABC234", SETTINGS, PLAYING, 2, List.of(ASHA, RAVI), null, 1, 0, List.of()));

        assertThat(state.goneProperty().get()).isFalse();
        assertThat(state.handInProgressProperty().get()).isFalse();
        assertThat(state.yourCards()).isEmpty();
        assertThat(state.board()).isEmpty();
        assertThat(state.turnProperty().get()).isNull();
        assertThat(state.seatAt(0).streetBetProperty().get()).isZero();
        assertThat(state.seatAt(0).cards()).isEmpty();
        assertThat(state.seatAt(1).occupiedProperty().get()).isTrue();
    }

    @Test
    void handCategoriesAreWrittenAsWords() {
        assertThat(RoomState.handName("HIGH_CARD")).isEqualTo("High card");
        assertThat(RoomState.handName("THREE_OF_A_KIND")).isEqualTo("Three of a kind");
        assertThat(RoomState.handName("FLUSH")).isEqualTo("Flush");
        assertThat(RoomState.handName("")).isEmpty();
    }
}
