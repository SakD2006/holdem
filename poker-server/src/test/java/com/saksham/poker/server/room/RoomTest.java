package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.ActionRequired;
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
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import com.saksham.poker.engine.card.DeckFactory;
import com.saksham.poker.engine.card.StackedDeckFactory;
import com.saksham.poker.server.db.HandRecord;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A room driven directly, with fake players and timers that fire only when the test says so. */
class RoomTest {

    private static final RoomSettings SETTINGS = new RoomSettings("Test", 6, 50, 100, 10_000, 25, true, false);
    private static final RoomSettings NO_REBUY = new RoomSettings("Test", 6, 50, 100, 10_000, 25, false, false);
    /** Three seconds between hands, no run-out pause, a minute of reconnect grace. */
    private static final RoomTimings TIMINGS = new RoomTimings(3_000, 0, 60_000, 30 * 60_000L);

    /**
     * Heads-up with seat 0 on the button: seat 1 is dealt K-K and seat 0 A-A, and the board helps
     * neither, so seat 0 wins any showdown of the first hand.
     */
    private static final DeckFactory ACES_FOR_SEAT_0 =
            StackedDeckFactory.of("Kc Ah Kd Ad  2c 2s 7h 9c  3c Jd  4c 3s");

    private RoomHarness table = new RoomHarness(SETTINGS, TIMINGS, ACES_FOR_SEAT_0);

    /** Two players seated and the first hand dealt: seat 0 (the host) to act. */
    private FakePlayer[] headsUp() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.joinAndSit(2, "ravi", 1);
        table.start();
        return new FakePlayer[] {asha, ravi};
    }

    private FakePlayer[] threeHanded() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.joinAndSit(2, "ravi", 1);
        FakePlayer meera = table.joinAndSit(3, "meera", 2);
        table.start();
        return new FakePlayer[] {asha, ravi, meera};
    }

    private static PlayerInfo playerIn(SeatUpdate update) {
        return update.player();
    }

    // =====================================================================================
    // Joining and seats
    // =====================================================================================

    @Test
    void joiningGivesASnapshotAndTellsEveryoneElse() {
        FakePlayer asha = table.join(1, "asha");
        FakePlayer ravi = table.join(2, "ravi");

        RoomSnapshot snapshot = ravi.last(RoomSnapshot.class);
        assertThat(snapshot.code()).isEqualTo("ABC234");
        assertThat(snapshot.settings()).isEqualTo(SETTINGS.toInfo());
        assertThat(snapshot.state()).isEqualTo(RoomState.WAITING);
        assertThat(snapshot.hostUserId()).isEqualTo(1);
        assertThat(snapshot.players()).extracting(PlayerInfo::username).containsExactly("asha", "ravi");
        assertThat(snapshot.yourUserId()).isEqualTo(2);
        assertThat(snapshot.yourSeat()).isEqualTo(PlayerInfo.NO_SEAT);
        assertThat(snapshot.hand()).isNull();

        assertThat(asha.last(PlayerJoined.class).player().username()).isEqualTo("ravi");
        assertThat(ravi.all(PlayerJoined.class)).isEmpty();
    }

    @Test
    void aFullRoomTurnsNewcomersAway() {
        table = new RoomHarness(new RoomSettings("Small", 2, 50, 100, 10_000, 25, true, false), TIMINGS,
                ACES_FOR_SEAT_0);
        table.join(1, "asha");
        table.join(2, "ravi");

        FakePlayer meera = table.join(3, "meera");

        assertThat(meera.lastError()).isEqualTo(ErrorCode.ROOM_FULL);
        assertThat(meera.all(RoomSnapshot.class)).isEmpty();
        // Released, so she is free to join a different room.
        assertThat(table.released).containsExactly(3L);
    }

    @Test
    void takingASeatGivesTheStartingStackAndTellsEveryone() {
        FakePlayer asha = table.join(1, "asha");
        FakePlayer ravi = table.join(2, "ravi");

        table.sit(ravi, 4);

        PlayerInfo seated = playerIn(asha.last(SeatUpdate.class));
        assertThat(seated.userId()).isEqualTo(2);
        assertThat(seated.seat()).isEqualTo(4);
        assertThat(seated.stack()).isEqualTo(10_000);
        assertThat(seated.sittingOut()).isFalse();
        assertThat(table.room.seatedCount()).isEqualTo(1);
    }

    @Test
    void aTakenOrMissingSeatIsRefused() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.join(2, "ravi");

        table.sit(ravi, 0);
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.SEAT_TAKEN);

        table.sit(ravi, 6);
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
        table.sit(ravi, -1);
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(table.room.seatedCount()).isEqualTo(1);
        assertThat(asha.lastError()).isNull();
    }

    @Test
    void aPlayerCanChangeSeatsOnlyBeforeTheGameStarts() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.joinAndSit(2, "ravi", 1);

        table.sit(ravi, 3);
        assertThat(playerIn(asha.last(SeatUpdate.class)).seat()).isEqualTo(3);
        assertThat(playerIn(asha.last(SeatUpdate.class)).stack()).isEqualTo(10_000);
        assertThat(table.room.seatedCount()).isEqualTo(2);

        table.start();
        table.sit(ravi, 4);
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void someoneWhoIsNotInTheRoomCannotActInIt() {
        table.join(1, "asha");
        FakePlayer stranger = new FakePlayer(9, "stranger");

        table.run(new RoomCommands.TakeSeat(9, 0));
        table.run(new RoomCommands.Chat(9, "hello"));

        // No member to tell, and nothing happened.
        assertThat(stranger.received).isEmpty();
        assertThat(table.room.seatedCount()).isZero();
    }

    // =====================================================================================
    // Starting the game
    // =====================================================================================

    @Test
    void onlyTheHostCanStartAndOnlyWithTwoSeatedPlayers() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.join(2, "ravi");

        table.run(new RoomCommands.StartGame(2));
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.NOT_HOST);

        table.start();
        assertThat(asha.lastError()).isEqualTo(ErrorCode.NOT_ENOUGH_PLAYERS);
        assertThat(table.room.state()).isEqualTo(RoomState.WAITING);

        table.sit(ravi, 1);
        table.start();
        assertThat(table.room.state()).isEqualTo(RoomState.PLAYING);
        assertThat(ravi.last(GameState.class).state()).isEqualTo(RoomState.PLAYING);
        assertThat(table.states).containsExactly(RoomState.PLAYING);

        table.start();
        assertThat(asha.lastError()).isEqualTo(ErrorCode.GAME_ALREADY_STARTED);
    }

    @Test
    void startingDealsTheFirstHandAndAsksTheFirstPlayerToAct() {
        FakePlayer[] p = headsUp();

        HandStarted started = p[1].last(HandStarted.class);
        assertThat(started.handNo()).isEqualTo(1);
        assertThat(started.buttonSeat()).isZero();
        assertThat(started.smallBlindSeat()).isZero();
        assertThat(started.bigBlindSeat()).isEqualTo(1);
        assertThat(started.stacks()).containsOnlyKeys(0, 1);

        // Everyone is told whose turn it is; only that player is asked.
        ActionRequired turn = p[1].last(ActionRequired.class);
        assertThat(turn.seat()).isZero();
        assertThat(turn.callAmount()).isEqualTo(50);
        assertThat(turn.canRaise()).isTrue();
        assertThat(turn.deadlineEpochMs()).isEqualTo(table.nowMs + 25_000);
        assertThat(p[0].last(ActionRequired.class).turnId()).isEqualTo(turn.turnId());
        assertThat(table.toAct()).isSameAs(p[0]);
        assertThat(table.waiting(RoomCommands.TurnTimeout.class).delayMs).isEqualTo(25_000);
    }

    @Test
    void eachPlayerReceivesOnlyTheirOwnHoleCards() {
        FakePlayer[] p = threeHanded();
        FakePlayer watcher = table.join(4, "watcher");
        table.checkDown();

        for (FakePlayer player : p) {
            List<HoleCards> dealt = player.all(HoleCards.class);
            assertThat(dealt).as(player.username).hasSize(1);
            assertThat(dealt.get(0).seat()).as(player.username).isEqualTo(player.seat);
            assertThat(dealt.get(0).cards()).hasSize(2);
        }
        // Someone watching without a seat is dealt nothing, but sees the showdown like everyone.
        assertThat(watcher.all(HoleCards.class)).isEmpty();
        assertThat(watcher.all(Showdown.class)).hasSize(1);
        assertThat(watcher.last(Showdown.class).hands()).hasSize(3);
    }

    // =====================================================================================
    // Playing
    // =====================================================================================

    @Test
    void aHandIsPlayedToShowdownAndTheNextOneFollowsAfterAPause() {
        FakePlayer[] p = headsUp();

        table.act(p[0], ActionType.RAISE, 300);
        PlayerActed raise = p[1].last(PlayerActed.class);
        assertThat(raise.seat()).isZero();
        assertThat(raise.action()).isEqualTo(ActionType.RAISE);
        assertThat(raise.streetBet()).isEqualTo(300);
        assertThat(raise.stack()).isEqualTo(9_700);

        table.act(p[1], ActionType.CALL);
        assertThat(p[0].last(StreetDealt.class).street()).isEqualTo("FLOP");
        table.checkDown();

        HandEnded ended = p[1].last(HandEnded.class);
        assertThat(ended.netBySeat()).containsEntry(0, 300L).containsEntry(1, -300L);
        assertThat(ended.stacks()).containsEntry(0, 10_300L).containsEntry(1, 9_700L);
        assertThat(ended.payouts()).hasSize(1);
        assertThat(ended.payouts().get(0).seat()).isZero();
        assertThat(ended.payouts().get(0).amount()).isEqualTo(600);

        // Nothing happens until the pause between hands is over.
        assertThat(table.toAct()).isNull();
        assertThat(table.waiting(RoomCommands.StartNextHand.class).delayMs).isEqualTo(3_000);
        table.fire(RoomCommands.StartNextHand.class);

        HandStarted second = p[0].last(HandStarted.class);
        assertThat(second.handNo()).isEqualTo(2);
        assertThat(second.buttonSeat()).isEqualTo(1);
        assertThat(second.stacks()).containsEntry(0, 10_300L).containsEntry(1, 9_700L);
        assertThat(table.toAct()).isSameAs(p[1]);
    }

    @Test
    void actingOutOfTurnOrIllegallyIsRefusedAndChangesNothing() {
        FakePlayer[] p = headsUp();

        table.run(new RoomCommands.PlayerActionCmd(2, 1, ActionType.FOLD, 0));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.NOT_YOUR_TURN);

        table.act(p[0], ActionType.CHECK);
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.INVALID_ACTION);
        table.act(p[0], ActionType.RAISE, 150);
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.INVALID_AMOUNT);
        table.act(p[0], ActionType.RAISE, 0);
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.INVALID_AMOUNT);
        table.act(p[0], ActionType.RAISE, 999_999);
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.INVALID_AMOUNT);

        // Still seat 0's turn, with its timer running, and nobody saw an action.
        assertThat(table.toAct()).isSameAs(p[0]);
        assertThat(table.waiting(RoomCommands.TurnTimeout.class)).isNotNull();
        assertThat(p[1].all(PlayerActed.class)).isEmpty();
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.NOT_YOUR_TURN);
    }

    @Test
    void anAnswerToAnOldTurnIsIgnored() {
        FakePlayer[] p = headsUp();
        long firstTurn = p[0].pendingRequest.turnId();
        table.act(p[0], ActionType.CALL);
        table.act(p[1], ActionType.CHECK);
        // Flop: seat 1 acts first, then seat 0, whose first turn is long over.
        table.act(p[1], ActionType.CHECK);
        assertThat(table.toAct()).isSameAs(p[0]);
        int actionsSeen = p[1].all(PlayerActed.class).size();

        table.run(new RoomCommands.PlayerActionCmd(1, firstTurn, ActionType.FOLD, 0));

        assertThat(p[0].lastError()).isNull();
        assertThat(p[1].all(PlayerActed.class)).hasSize(actionsSeen);
        assertThat(table.toAct()).isSameAs(p[0]);
    }

    // =====================================================================================
    // Timeouts
    // =====================================================================================

    @Test
    void aTimeoutFoldsWhenThereIsABetToCallAndSitsThePlayerOut() {
        FakePlayer[] p = headsUp();

        table.fire(RoomCommands.TurnTimeout.class);

        PlayerActed fold = p[1].last(PlayerActed.class);
        assertThat(fold.seat()).isZero();
        assertThat(fold.action()).isEqualTo(ActionType.FOLD);
        assertThat(p[1].last(HandEnded.class).netBySeat()).containsEntry(0, -50L).containsEntry(1, 50L);
        PlayerInfo satOut = playerIn(p[1].last(SeatUpdate.class));
        assertThat(satOut.userId()).isEqualTo(1);
        assertThat(satOut.sittingOut()).isTrue();

        // With one player sitting out there is nobody to play the next hand against.
        table.fire(RoomCommands.StartNextHand.class);
        assertThat(p[1].all(HandStarted.class)).hasSize(1);
    }

    @Test
    void aTimeoutChecksWhenCheckingIsFree() {
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.CALL);

        // The big blind may check its option; it lets the clock run out instead.
        table.fire(RoomCommands.TurnTimeout.class);

        PlayerActed check = p[0].last(PlayerActed.class);
        assertThat(check.seat()).isEqualTo(1);
        assertThat(check.action()).isEqualTo(ActionType.CHECK);
        assertThat(p[0].last(StreetDealt.class).street()).isEqualTo("FLOP");
    }

    @Test
    void afterTimingOutAPlayerIsCheckedOrFoldedAtOnceForTheRestOfTheHand() {
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.CALL);
        table.fire(RoomCommands.TurnTimeout.class);   // seat 1 checks its option by timing out

        // On the flop seat 1 is first to act, and is checked for immediately: seat 0 is asked next.
        assertThat(table.toAct()).isSameAs(p[0]);
        table.act(p[0], ActionType.BET, 200);

        // Facing a bet, seat 1 is folded for at once.
        assertThat(p[0].last(PlayerActed.class).seat()).isEqualTo(1);
        assertThat(p[0].last(PlayerActed.class).action()).isEqualTo(ActionType.FOLD);
        assertThat(p[0].all(HandEnded.class)).hasSize(1);
    }

    @Test
    void aTimerForATurnThatWasAnsweredDoesNothing() {
        FakePlayer[] p = headsUp();
        RoomHarness.Scheduled firstTimer = table.waiting(RoomCommands.TurnTimeout.class);

        table.act(p[0], ActionType.CALL);
        assertThat(firstTimer.cancelled).isTrue();
        int actionsSeen = p[0].all(PlayerActed.class).size();

        // The cancelled timer goes off anyway, as one already in flight would.
        table.fire(firstTimer);

        assertThat(p[0].all(PlayerActed.class)).hasSize(actionsSeen);
        assertThat(table.toAct()).isSameAs(p[1]);
        assertThat(p[0].all(SeatUpdate.class).stream().filter(u -> u.player().sittingOut())).isEmpty();
    }

    @Test
    void sittingBackInClearsTheAutomaticPlay() {
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.CALL);
        table.fire(RoomCommands.TurnTimeout.class);

        table.run(new RoomCommands.SitIn(2));
        table.act(p[0], ActionType.BET, 200);

        // Seat 1 is back in control and is asked to act instead of being folded.
        assertThat(table.toAct()).isSameAs(p[1]);
    }

    // =====================================================================================
    // Disconnecting and reconnecting
    // =====================================================================================

    @Test
    void aDisconnectedPlayerIsShownAsSuchAndGetsASnapshotOnReturning() {
        FakePlayer[] p = headsUp();
        List<Card> ashasCards = p[0].last(HoleCards.class).cards();

        table.run(new RoomCommands.Disconnected(1));

        assertThat(playerIn(p[1].last(SeatUpdate.class)).connected()).isFalse();
        assertThat(table.waiting(RoomCommands.ReconnectGraceExpired.class).delayMs).isEqualTo(60_000);

        // She reconnects with a new connection, in the middle of her turn.
        FakePlayer back = new FakePlayer(1, "asha");
        back.seat = 0;
        table.players.put(1L, back);
        table.run(new RoomCommands.Reconnected(1, back));

        RoomSnapshot snapshot = back.last(RoomSnapshot.class);
        assertThat(snapshot.state()).isEqualTo(RoomState.PLAYING);
        assertThat(snapshot.yourSeat()).isZero();
        assertThat(snapshot.yourCards()).isEqualTo(ashasCards);
        assertThat(snapshot.hand().handNo()).isEqualTo(1);
        assertThat(snapshot.hand().street()).isEqualTo("PREFLOP");
        assertThat(snapshot.hand().turn().seat()).isZero();
        assertThat(snapshot.hand().seats()).hasSize(2);
        assertThat(snapshot.hand().seats().get(1).streetBet()).isEqualTo(100);
        assertThat(snapshot.players().get(0).stack()).isEqualTo(9_950);
        // And she is asked again, for the same turn.
        assertThat(back.pendingRequest.turnId()).isEqualTo(snapshot.hand().turn().turnId());
        assertThat(playerIn(p[1].last(SeatUpdate.class)).connected()).isTrue();

        // The grace timer from the disconnect is now out of date.
        table.fire(RoomCommands.ReconnectGraceExpired.class);
        assertThat(playerIn(p[1].last(SeatUpdate.class)).sittingOut()).isFalse();
        table.act(back, ActionType.CALL);
        assertThat(table.toAct()).isSameAs(p[1]);
    }

    @Test
    void aSnapshotNeverContainsAnotherPlayersCards() {
        FakePlayer[] p = headsUp();
        List<Card> ashasCards = p[0].last(HoleCards.class).cards();

        table.run(new RoomCommands.SendSnapshot(2));

        assertThat(p[1].last(RoomSnapshot.class).yourCards())
                .isEqualTo(p[1].last(HoleCards.class).cards())
                .doesNotContainAnyElementsOf(ashasCards);
    }

    @Test
    void aPlayerWhoDoesNotComeBackInTimeIsSatOutAndPlayedFor() {
        FakePlayer[] p = headsUp();

        table.run(new RoomCommands.Disconnected(1));
        // Within the grace period her turn simply waits on its usual timer.
        assertThat(table.toAct()).isSameAs(p[0]);
        assertThat(p[1].all(PlayerActed.class)).isEmpty();

        table.fire(RoomCommands.ReconnectGraceExpired.class);

        PlayerInfo info = playerIn(p[1].last(SeatUpdate.class));
        assertThat(info.sittingOut()).isTrue();
        assertThat(info.connected()).isFalse();
        assertThat(p[1].last(PlayerActed.class).action()).isEqualTo(ActionType.FOLD);
        assertThat(p[1].all(HandEnded.class)).hasSize(1);
        // She keeps her seat for when she returns.
        assertThat(table.room.seatedCount()).isEqualTo(2);
    }

    @Test
    void aRoomWithNobodyConnectedClosesAfterTheIdleTime() {
        FakePlayer[] p = headsUp();
        RoomHarness.Scheduled fromOpening = table.waiting(RoomCommands.IdleCheck.class);
        table.run(new RoomCommands.Disconnected(1));
        // One player is still connected, so no new idle timer is set.
        assertThat(table.waiting(RoomCommands.IdleCheck.class)).isSameAs(fromOpening);

        table.run(new RoomCommands.Disconnected(2));
        RoomHarness.Scheduled idle = table.waiting(RoomCommands.IdleCheck.class);
        assertThat(idle).isNotSameAs(fromOpening);
        assertThat(idle.delayMs).isEqualTo(30 * 60_000L);

        // The timer from when the room opened is out of date and closes nothing.
        table.fire(fromOpening);
        assertThat(table.closed).isFalse();

        table.fire(idle);

        assertThat(table.closed).isTrue();
        assertThat(table.room.state()).isEqualTo(RoomState.CLOSED);
        assertThat(table.released).containsExactlyInAnyOrder(1L, 2L);
        assertThat(p[0].received).isNotEmpty();
    }

    @Test
    void comingBackCancelsTheIdleClose() {
        FakePlayer[] p = headsUp();
        table.run(new RoomCommands.Disconnected(1));
        table.run(new RoomCommands.Disconnected(2));
        RoomHarness.Scheduled idle = table.waiting(RoomCommands.IdleCheck.class);

        table.run(new RoomCommands.Reconnected(2, p[1]));
        table.fire(idle);

        assertThat(table.closed).isFalse();
    }

    @Test
    void aRoomNobodyEverJoinsClosesAfterTheIdleTime() {
        table.fire(RoomCommands.IdleCheck.class);

        assertThat(table.closed).isTrue();
        assertThat(table.states).containsExactly(RoomState.CLOSED);
    }

    // =====================================================================================
    // Leaving, the host and kicking
    // =====================================================================================

    @Test
    void whenTheHostLeavesTheNextSeatedPlayerBecomesHost() {
        FakePlayer asha = table.join(1, "asha");
        FakePlayer watcher = table.join(2, "watcher");
        FakePlayer meera = table.joinAndSit(3, "meera", 4);

        table.run(new RoomCommands.LeaveRoom(1));

        assertThat(asha.last(PlayerLeft.class).userId()).isEqualTo(1);
        assertThat(asha.last(PlayerLeft.class).reason()).isEqualTo(LeaveReason.LEFT);
        assertThat(watcher.last(HostChanged.class).hostUserId()).isEqualTo(3);
        assertThat(meera.last(HostChanged.class).hostUserId()).isEqualTo(3);
        assertThat(table.released).containsExactly(1L);

        // The new host has the host's powers; the old one no longer does.
        table.joinAndSit(4, "dev", 0);
        table.run(new RoomCommands.StartGame(3));
        assertThat(table.room.state()).isEqualTo(RoomState.PLAYING);
    }

    @Test
    void theRoomClosesWhenTheLastPersonLeaves() {
        table.join(1, "asha");
        table.join(2, "ravi");

        table.run(new RoomCommands.LeaveRoom(1));
        assertThat(table.closed).isFalse();
        table.run(new RoomCommands.LeaveRoom(2));

        assertThat(table.closed).isTrue();
        assertThat(table.room.state()).isEqualTo(RoomState.CLOSED);
        assertThat(table.states).containsExactly(RoomState.CLOSED);
    }

    @Test
    void leavingInTheMiddleOfAHandFoldsItAndTheHandGoesOn() {
        FakePlayer[] p = threeHanded();
        // Seat 0 is on the button, seat 1 the small blind, seat 2 the big blind; seat 0 acts first.
        assertThat(table.toAct()).isSameAs(p[0]);

        table.run(new RoomCommands.LeaveRoom(2));   // the small blind leaves out of turn

        PlayerActed fold = p[0].last(PlayerActed.class);
        assertThat(fold.seat()).isEqualTo(1);
        assertThat(fold.action()).isEqualTo(ActionType.FOLD);
        assertThat(p[0].last(PlayerLeft.class).userId()).isEqualTo(2);
        assertThat(table.toAct()).isSameAs(p[0]);
        assertThat(table.room.seatedCount()).isEqualTo(2);

        table.checkDown();

        // The small blind's 50 stayed in the pot, and the leaver's stack is not reported as playing on.
        HandEnded ended = p[0].last(HandEnded.class);
        assertThat(ended.netBySeat().values().stream().mapToLong(Long::longValue).sum()).isZero();
        assertThat(ended.netBySeat()).containsEntry(1, -50L);
    }

    @Test
    void leavingOnYourTurnPassesTheTurnOn() {
        FakePlayer[] p = threeHanded();

        table.run(new RoomCommands.LeaveRoom(1));

        assertThat(p[1].last(PlayerActed.class).seat()).isZero();
        assertThat(p[1].last(PlayerActed.class).action()).isEqualTo(ActionType.FOLD);
        assertThat(table.toAct()).isSameAs(p[1]);
        assertThat(p[1].last(HostChanged.class).hostUserId()).isEqualTo(2);
    }

    @Test
    void leavingHeadsUpEndsTheHandForTheOtherPlayer() {
        FakePlayer[] p = headsUp();

        table.run(new RoomCommands.LeaveRoom(2));

        HandEnded ended = p[0].last(HandEnded.class);
        assertThat(ended.netBySeat()).containsEntry(0, 100L);
        // Alone at the table, no further hand starts.
        table.fire(RoomCommands.StartNextHand.class);
        assertThat(p[0].all(HandStarted.class)).hasSize(1);
    }

    @Test
    void theHostCanKickOnlyBeforeTheGameStarts() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.joinAndSit(2, "ravi", 1);
        FakePlayer meera = table.joinAndSit(3, "meera", 2);

        table.run(new RoomCommands.KickPlayer(2, 3));
        assertThat(ravi.lastError()).isEqualTo(ErrorCode.NOT_HOST);
        table.run(new RoomCommands.KickPlayer(1, 1));
        assertThat(asha.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
        table.run(new RoomCommands.KickPlayer(1, 99));
        assertThat(asha.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);

        table.run(new RoomCommands.KickPlayer(1, 3));
        assertThat(meera.last(PlayerLeft.class).reason()).isEqualTo(LeaveReason.KICKED);
        assertThat(ravi.last(PlayerLeft.class).userId()).isEqualTo(3);
        assertThat(table.released).containsExactly(3L);
        assertThat(table.room.seatedCount()).isEqualTo(2);

        table.start();
        table.run(new RoomCommands.KickPlayer(1, 2));
        assertThat(asha.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(table.room.seatedCount()).isEqualTo(2);
    }

    @Test
    void theHostCanEndTheRoomForEveryone() {
        FakePlayer[] p = headsUp();
        RoomHarness.Scheduled turnTimer = table.waiting(RoomCommands.TurnTimeout.class);

        table.run(new RoomCommands.EndRoom(2));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.NOT_HOST);

        table.run(new RoomCommands.EndRoom(1));

        assertThat(p[1].last(GameState.class).state()).isEqualTo(RoomState.CLOSED);
        assertThat(table.closed).isTrue();
        assertThat(table.released).containsExactlyInAnyOrder(1L, 2L);

        // A closed room does nothing more, even if a timer or a late message arrives.
        int received = p[0].received.size();
        assertThat(turnTimer.cancelled).isTrue();
        table.fire(turnTimer);
        table.run(new RoomCommands.Chat(1, "anyone there?"));
        assertThat(p[0].received).hasSize(received);
    }

    // =====================================================================================
    // Pausing
    // =====================================================================================

    @Test
    void pausingLetsTheHandFinishAndResumingDealsTheNextOne() {
        FakePlayer[] p = headsUp();

        table.run(new RoomCommands.PauseGame(2));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.NOT_HOST);
        table.run(new RoomCommands.PauseGame(1));
        assertThat(p[1].last(GameState.class).state()).isEqualTo(RoomState.PAUSED);

        table.checkDown();
        assertThat(p[1].all(HandEnded.class)).hasSize(1);
        assertThat(table.waiting(RoomCommands.StartNextHand.class)).isNull();

        table.run(new RoomCommands.PauseGame(1));
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);

        table.run(new RoomCommands.ResumeGame(1));
        assertThat(p[1].last(GameState.class).state()).isEqualTo(RoomState.PLAYING);
        assertThat(p[1].last(HandStarted.class).handNo()).isEqualTo(2);
        assertThat(table.states).containsExactly(RoomState.PLAYING, RoomState.PAUSED, RoomState.PLAYING);
    }

    // =====================================================================================
    // Sitting out, busting and rebuying
    // =====================================================================================

    @Test
    void aPlayerSittingOutIsNotDealtInAndWaitsForTheBigBlindOnReturning() {
        FakePlayer[] p = threeHanded();
        table.run(new RoomCommands.SitOut(3));
        assertThat(playerIn(p[0].last(SeatUpdate.class)).sittingOut()).isTrue();

        // Sitting out takes effect from the next hand: this one is finished normally.
        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);
        assertThat(p[0].last(HandStarted.class).stacks()).containsOnlyKeys(0, 1);

        table.run(new RoomCommands.SitIn(3));
        assertThat(playerIn(p[0].last(SeatUpdate.class)).sittingOut()).isFalse();
        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);

        // Hand 3: the button is on seat 0 and seat 1 is the small blind, so the big blind has
        // reached seat 2 and the returning player is dealt in to post it.
        HandStarted third = p[0].last(HandStarted.class);
        assertThat(third.handNo()).isEqualTo(3);
        assertThat(third.stacks()).containsOnlyKeys(0, 1, 2);
        assertThat(third.bigBlindSeat()).isEqualTo(2);
    }

    @Test
    void someoneWhoSitsDownDuringTheGameWaitsForTheBigBlind() {
        FakePlayer[] p = headsUp();
        FakePlayer meera = table.joinAndSit(3, "meera", 2);
        assertThat(meera.last(RoomSnapshot.class).hand()).isNotNull();

        // Hand 1 goes on without her, and so does hand 2, where she would have been on the button's left.
        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);
        HandStarted second = p[0].last(HandStarted.class);
        assertThat(second.buttonSeat()).isEqualTo(1);
        assertThat(second.stacks()).containsOnlyKeys(0, 1);
        assertThat(meera.all(HoleCards.class)).isEmpty();

        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);

        HandStarted third = p[0].last(HandStarted.class);
        assertThat(third.buttonSeat()).isZero();
        assertThat(third.smallBlindSeat()).isEqualTo(1);
        assertThat(third.bigBlindSeat()).isEqualTo(2);
        assertThat(third.stacks()).containsOnlyKeys(0, 1, 2);
        assertThat(meera.all(HoleCards.class)).hasSize(1);
    }

    @Test
    void twoPlayersNeverWaitOnEachOther() {
        FakePlayer asha = table.joinAndSit(1, "asha", 0);
        FakePlayer ravi = table.joinAndSit(2, "ravi", 1);
        table.start();
        table.run(new RoomCommands.SitOut(2));
        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);
        assertThat(asha.all(HandStarted.class)).hasSize(1);

        // Ravi comes back. With only two players there is nobody to wait behind, so play resumes.
        table.run(new RoomCommands.SitIn(2));
        table.fire(RoomCommands.StartNextHand.class);

        assertThat(ravi.last(HandStarted.class).handNo()).isEqualTo(2);
    }

    /** Seat 1 calls an all-in with kings against aces and loses everything. */
    private FakePlayer[] bustSeatOne() {
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.ALL_IN);
        table.act(p[1], ActionType.CALL);
        return p;
    }

    @Test
    void aBustedPlayerStaysAndWatchesAndNoHandStartsWithOnePlayer() {
        table = new RoomHarness(NO_REBUY, TIMINGS, ACES_FOR_SEAT_0);
        FakePlayer[] p = bustSeatOne();

        assertThat(p[0].last(HandEnded.class).stacks()).containsEntry(0, 20_000L).containsEntry(1, 0L);
        PlayerInfo busted = playerIn(p[0].last(SeatUpdate.class));
        assertThat(busted.userId()).isEqualTo(2);
        assertThat(busted.stack()).isZero();
        assertThat(busted.sittingOut()).isTrue();
        assertThat(table.room.seatedCount()).isEqualTo(2);

        table.fire(RoomCommands.StartNextHand.class);
        assertThat(p[0].all(HandStarted.class)).hasSize(1);

        // No rebuys here, and no sitting in without chips.
        table.run(new RoomCommands.Rebuy(2));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.REBUY_NOT_ALLOWED);
        table.run(new RoomCommands.SitIn(2));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void aPlayerWhoBustsWhileAlreadySittingOutIsStillAnnounced() {
        FakePlayer[] p = headsUp();
        table.run(new RoomCommands.SitOut(2));
        int updatesBefore = p[0].all(SeatUpdate.class).size();

        table.act(p[0], ActionType.ALL_IN);
        table.act(p[1], ActionType.CALL);

        assertThat(p[0].all(SeatUpdate.class)).hasSize(updatesBefore + 1);
        PlayerInfo busted = playerIn(p[0].last(SeatUpdate.class));
        assertThat(busted.userId()).isEqualTo(2);
        assertThat(busted.stack()).isZero();
        assertThat(busted.sittingOut()).isTrue();
    }

    @Test
    void aBustedPlayerCanRebuyToTheStartingStackWhenTheRoomAllowsIt() {
        FakePlayer[] p = bustSeatOne();

        table.run(new RoomCommands.Rebuy(2));

        PlayerInfo rebought = playerIn(p[0].last(SeatUpdate.class));
        assertThat(rebought.stack()).isEqualTo(10_000);
        assertThat(rebought.sittingOut()).isFalse();
        table.fire(RoomCommands.StartNextHand.class);
        assertThat(p[0].last(HandStarted.class).stacks()).containsEntry(0, 20_000L).containsEntry(1, 10_000L);
    }

    @Test
    void aPlayerWithChipsCannotRebuy() {
        FakePlayer[] p = headsUp();

        table.run(new RoomCommands.Rebuy(2));
        assertThat(p[1].lastError()).isEqualTo(ErrorCode.REBUY_NOT_ALLOWED);

        // Not even while all-in, before the hand has decided whether the chips are lost.
        table.act(p[0], ActionType.ALL_IN);
        table.run(new RoomCommands.Rebuy(1));
        assertThat(p[0].lastError()).isEqualTo(ErrorCode.REBUY_NOT_ALLOWED);
    }

    // =====================================================================================
    // Running out the board
    // =====================================================================================

    @Test
    void whenEveryoneIsAllInTheBoardIsRevealedOneStreetAtATime() {
        table = new RoomHarness(SETTINGS, new RoomTimings(3_000, 1_000, 60_000, 30 * 60_000L), ACES_FOR_SEAT_0);
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.ALL_IN);
        table.act(p[1], ActionType.CALL);

        // Only the flop so far, with a one-second pause before the turn.
        assertThat(p[0].all(StreetDealt.class)).extracting(StreetDealt::street).containsExactly("FLOP");
        assertThat(p[0].all(HandEnded.class)).isEmpty();
        assertThat(table.waiting(RoomCommands.ContinueDelivery.class).delayMs).isEqualTo(1_000);

        // A snapshot taken now shows what has been revealed and nothing more.
        table.run(new RoomCommands.SendSnapshot(2));
        RoomSnapshot snapshot = p[1].last(RoomSnapshot.class);
        assertThat(snapshot.hand().board()).hasSize(3);
        assertThat(snapshot.hand().street()).isEqualTo("FLOP");
        assertThat(snapshot.hand().turn()).isNull();
        assertThat(snapshot.players().get(1).stack()).isZero();

        table.fire(RoomCommands.ContinueDelivery.class);
        assertThat(p[0].all(StreetDealt.class)).extracting(StreetDealt::street).containsExactly("FLOP", "TURN");
        table.fire(RoomCommands.ContinueDelivery.class);
        assertThat(p[0].all(StreetDealt.class)).hasSize(3);
        assertThat(p[0].all(Showdown.class)).isEmpty();

        table.fire(RoomCommands.ContinueDelivery.class);
        assertThat(p[0].all(Showdown.class)).hasSize(1);
        assertThat(p[0].last(HandEnded.class).stacks()).containsEntry(0, 20_000L);
    }

    // =====================================================================================
    // The record of each hand
    // =====================================================================================

    @Test
    void aFinishedHandIsHandedOverAsACompleteRecord() {
        FakePlayer[] p = headsUp();
        List<Card> ashasCards = p[0].last(HoleCards.class).cards();
        table.act(p[0], ActionType.RAISE, 300);
        table.act(p[1], ActionType.CALL);
        assertThat(table.hands).isEmpty();
        table.checkDown();

        assertThat(table.hands).hasSize(1);
        HandRecord hand = table.hands.get(0);
        assertThat(hand.roomCode()).isEqualTo("ABC234");
        assertThat(hand.roomName()).isEqualTo("Test");
        assertThat(hand.handNo()).isEqualTo(1);
        assertThat(hand.smallBlind()).isEqualTo(50);
        assertThat(hand.bigBlind()).isEqualTo(100);
        assertThat(hand.buttonSeat()).isZero();
        assertThat(hand.board()).isEqualTo(Card.parseAll("2s 7h 9c Jd 3s"));
        assertThat(hand.totalPot()).isEqualTo(600);
        assertThat(hand.startedAt()).isEqualTo(hand.endedAt());

        assertThat(hand.players()).extracting(HandRecord.PlayerRecord::username).containsExactly("asha", "ravi");
        HandRecord.PlayerRecord asha = hand.players().get(0);
        assertThat(asha.userId()).isEqualTo(1);
        assertThat(asha.seat()).isZero();
        assertThat(asha.holeCards()).isEqualTo(ashasCards);
        assertThat(asha.startStack()).isEqualTo(10_000);
        assertThat(asha.endStack()).isEqualTo(10_300);
        assertThat(asha.net()).isEqualTo(300);
        assertThat(asha.showedDown()).isTrue();
        assertThat(asha.won()).isTrue();
        assertThat(hand.players().get(1).net()).isEqualTo(-300);
        assertThat(hand.players().get(1).won()).isFalse();

        // Blinds are part of the record, then every action in order.
        assertThat(hand.actions()).extracting(HandRecord.ActionRecord::action).containsExactly(
                "POST_SB", "POST_BB", "RAISE", "CALL", "CHECK", "CHECK", "CHECK", "CHECK", "CHECK", "CHECK");
        assertThat(hand.actions()).extracting(HandRecord.ActionRecord::seq)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(hand.actions()).extracting(HandRecord.ActionRecord::street).containsExactly(
                "PREFLOP", "PREFLOP", "PREFLOP", "PREFLOP", "FLOP", "FLOP", "TURN", "TURN", "RIVER", "RIVER");
        HandRecord.ActionRecord raise = hand.actions().get(2);
        assertThat(raise.userId()).isEqualTo(1);
        assertThat(raise.amount()).isEqualTo(250);
        assertThat(raise.streetTotal()).isEqualTo(300);
        assertThat(hand.actions().get(3).amount()).isEqualTo(200);
    }

    @Test
    void aHandWonWithoutShowdownRecordsNoShownCardsAndNoBoard() {
        FakePlayer[] p = headsUp();
        table.act(p[0], ActionType.FOLD);

        HandRecord hand = table.hands.get(0);
        assertThat(hand.board()).isEmpty();
        assertThat(hand.totalPot()).isEqualTo(100);
        assertThat(hand.players()).extracting(HandRecord.PlayerRecord::showedDown).containsExactly(false, false);
        assertThat(hand.players()).extracting(HandRecord.PlayerRecord::won).containsExactly(false, true);
        // The cards are still kept, privately, for each player's own history.
        assertThat(hand.players().get(0).holeCards()).hasSize(2);
        assertThat(hand.actions()).extracting(HandRecord.ActionRecord::action)
                .containsExactly("POST_SB", "POST_BB", "FOLD");
    }

    @Test
    void aPlayerWhoLeftDuringTheHandIsStillInItsRecord() {
        FakePlayer[] p = threeHanded();
        table.run(new RoomCommands.LeaveRoom(2));
        table.checkDown();

        HandRecord hand = table.hands.get(0);
        assertThat(hand.players()).extracting(HandRecord.PlayerRecord::userId).containsExactly(1L, 2L, 3L);
        assertThat(hand.players().get(1).net()).isEqualTo(-50);
        assertThat(hand.players().stream().mapToLong(HandRecord.PlayerRecord::net).sum()).isZero();
        assertThat(hand.actions().stream().filter(a -> a.userId() == 2).map(HandRecord.ActionRecord::action))
                .containsExactly("POST_SB", "FOLD");
        assertThat(p[0].all(HandEnded.class)).hasSize(1);
    }

    @Test
    void everyHandGetsItsOwnRecordNumberedInOrder() {
        headsUp();
        table.checkDown();
        table.fire(RoomCommands.StartNextHand.class);
        table.checkDown();

        assertThat(table.hands).extracting(HandRecord::handNo).containsExactly(1L, 2L);
        assertThat(table.hands.get(1).buttonSeat()).isEqualTo(1);
    }

    // =====================================================================================
    // Chat
    // =====================================================================================

    @Test
    void chatReachesEveryoneWithTheSendersName() {
        FakePlayer asha = table.join(1, "asha");
        FakePlayer ravi = table.join(2, "ravi");

        table.run(new RoomCommands.Chat(1, "  good luck\tall  "));

        ChatPosted posted = ravi.last(ChatPosted.class);
        assertThat(posted.userId()).isEqualTo(1);
        assertThat(posted.username()).isEqualTo("asha");
        assertThat(posted.text()).isEqualTo("good luck all");
        assertThat(asha.all(ChatPosted.class)).hasSize(1);
    }

    @Test
    void emptyTooLongOrTooFrequentChatIsRefused() {
        FakePlayer asha = table.join(1, "asha");
        FakePlayer ravi = table.join(2, "ravi");

        table.run(new RoomCommands.Chat(1, "   "));
        assertThat(asha.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
        table.run(new RoomCommands.Chat(1, null));
        table.run(new RoomCommands.Chat(1, "x".repeat(201)));
        assertThat(asha.all(ErrorMessage.class)).hasSize(3);
        assertThat(ravi.all(ChatPosted.class)).isEmpty();

        table.run(new RoomCommands.Chat(1, "x".repeat(200)));
        table.run(new RoomCommands.Chat(1, "again, too soon"));
        assertThat(ravi.all(ChatPosted.class)).hasSize(1);
        assertThat(asha.all(ErrorMessage.class)).hasSize(4);
        assertThat(asha.last(ErrorMessage.class).message()).contains("too quickly");

        // The limit is per player, and passes with time.
        table.run(new RoomCommands.Chat(2, "hello"));
        table.nowMs += 1_000;
        table.run(new RoomCommands.Chat(1, "now it is fine"));
        assertThat(ravi.all(ChatPosted.class)).hasSize(3);
    }

    // =====================================================================================
    // Everything a player is sent is a message they are allowed to see
    // =====================================================================================

    @Test
    void noMessageBeforeShowdownRevealsAnotherPlayersCards() {
        FakePlayer[] p = threeHanded();
        table.act(p[0], ActionType.FOLD);
        table.act(p[1], ActionType.FOLD);

        // The hand ended without a showdown: nobody ever learns anybody else's cards.
        for (FakePlayer player : p) {
            for (ServerMessage message : player.received) {
                assertThat(message).as(player.username).isNotInstanceOf(Showdown.class);
                if (message instanceof HoleCards cards) {
                    assertThat(cards.seat()).as(player.username).isEqualTo(player.seat);
                }
            }
        }
    }
}
