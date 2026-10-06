package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.LeaveReason;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.HostChanged;
import com.saksham.poker.common.protocol.server.PlayerJoined;
import com.saksham.poker.common.protocol.server.PlayerLeft;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.engine.card.SecureDeckFactory;
import com.saksham.poker.server.player.ActionRequest;
import com.saksham.poker.server.player.SeatController;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How a room treats computer players: seated by the host, never host themselves, never left alone. */
class RoomBotsTest {

    private static final RoomSettings THREE_SEATS = new RoomSettings("Test", 3, 50, 100, 10_000, 25, true, false);
    private static final RoomTimings TIMINGS = new RoomTimings(3_000, 0, 60_000, 30 * 60_000L);
    private static final long ADA = 900;
    private static final long BABBAGE = 901;

    /** A bot that does nothing but remember what it was sent. */
    private static final class SilentBot extends SeatController {
        final List<ServerMessage> received = new ArrayList<>();
        final List<ActionRequest> turns = new ArrayList<>();

        SilentBot(long userId) {
            super(userId);
        }

        @Override
        public void onActionRequested(ActionRequest request) {
            turns.add(request);
        }

        @Override
        public void onEvent(ServerMessage event) {
            received.add(event);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isBot() {
            return true;
        }
    }

    private final RoomHarness table = new RoomHarness(THREE_SEATS, TIMINGS, new SecureDeckFactory());

    private SilentBot addBot(long asker, long botId, String name) {
        SilentBot bot = new SilentBot(botId);
        table.run(new RoomCommands.AddBot(asker, botId, name, bot));
        return bot;
    }

    @Test
    void theHostSeatsABotInTheFirstFreeSeatAndEveryoneIsToldItIsABot() {
        FakePlayer host = table.joinAndSit(RoomHarness.HOST, "asha", 1);

        SilentBot ada = addBot(RoomHarness.HOST, ADA, "Ada_bot");

        PlayerInfo joined = host.last(PlayerJoined.class).player();
        assertThat(joined.username()).isEqualTo("Ada_bot");
        assertThat(joined.bot()).isTrue();
        PlayerInfo seated = host.last(SeatUpdate.class).player();
        assertThat(seated.userId()).isEqualTo(ADA);
        assertThat(seated.seat()).as("seat 0 was free; the host is in seat 1").isZero();
        assertThat(seated.stack()).isEqualTo(10_000);
        assertThat(seated.bot()).isTrue();
        assertThat(table.room.seatedCount()).isEqualTo(2);
        // The bot is sent a snapshot like anyone who joins, and people in it are not marked as bots.
        RoomSnapshot snapshot = (RoomSnapshot) ada.received.get(0);
        assertThat(snapshot.players()).extracting(PlayerInfo::bot).containsExactly(false, true);
    }

    @Test
    void onlyTheHostMayAddABotAndARefusedBotIsReleased() {
        table.joinAndSit(RoomHarness.HOST, "asha", 0);
        FakePlayer guest = table.joinAndSit(2, "ravi", 1);

        addBot(2, ADA, "Ada_bot");

        assertThat(guest.lastError()).isEqualTo(ErrorCode.NOT_HOST);
        assertThat(table.room.seatedCount()).isEqualTo(2);
        assertThat(table.released).as("the bot's account is free again").containsExactly(ADA);
    }

    @Test
    void aBotCannotBeAddedWhenEverySeatIsTaken() {
        FakePlayer host = table.joinAndSit(RoomHarness.HOST, "asha", 0);
        addBot(RoomHarness.HOST, ADA, "Ada_bot");
        addBot(RoomHarness.HOST, BABBAGE, "Babbage_bot");
        assertThat(table.room.seatedCount()).isEqualTo(3);

        addBot(RoomHarness.HOST, 902, "Curie_bot");

        assertThat(host.lastError()).isEqualTo(ErrorCode.ROOM_FULL);
        assertThat(table.released).containsExactly(902L);
    }

    @Test
    void aBotIsDealtInAndAskedToActLikeAnyPlayer() {
        table.joinAndSit(RoomHarness.HOST, "asha", 0);
        SilentBot ada = addBot(RoomHarness.HOST, ADA, "Ada_bot");

        table.start();

        assertThat(table.states).contains(RoomState.PLAYING);
        // Heads-up the button posts the small blind and acts first; one of the two is on turn.
        FakePlayer human = table.toAct();
        assertThat(human != null || !ada.turns.isEmpty()).isTrue();
        assertThat(ada.received).anyMatch(message -> message instanceof com.saksham.poker.common.protocol.server.HoleCards);
    }

    @Test
    void theHostCanRemoveABotEvenAfterTheGameHasStarted() {
        FakePlayer host = table.joinAndSit(RoomHarness.HOST, "asha", 0);
        table.joinAndSit(2, "ravi", 1);
        addBot(RoomHarness.HOST, ADA, "Ada_bot");
        table.start();

        table.run(new RoomCommands.KickPlayer(RoomHarness.HOST, ADA));

        PlayerLeft left = host.last(PlayerLeft.class);
        assertThat(left.userId()).isEqualTo(ADA);
        assertThat(left.reason()).isEqualTo(LeaveReason.KICKED);
        assertThat(table.released).contains(ADA);
        assertThat(table.room.seatedCount()).isEqualTo(2);
        // A person still cannot be removed mid-game.
        table.run(new RoomCommands.KickPlayer(RoomHarness.HOST, 2));
        assertThat(host.lastError()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void whenTheHostLeavesTheNextPersonBecomesHostNeverABot() {
        table.join(RoomHarness.HOST, "asha");
        addBot(RoomHarness.HOST, ADA, "Ada_bot"); // seat 0, ahead of every person
        FakePlayer guest = table.joinAndSit(2, "ravi", 2);

        table.run(new RoomCommands.LeaveRoom(RoomHarness.HOST));

        assertThat(guest.last(HostChanged.class).hostUserId()).isEqualTo(2);
        assertThat(table.closed).isFalse();
    }

    @Test
    void aRoomWithOnlyBotsLeftInItCloses() {
        table.joinAndSit(RoomHarness.HOST, "asha", 0);
        addBot(RoomHarness.HOST, ADA, "Ada_bot");
        addBot(RoomHarness.HOST, BABBAGE, "Babbage_bot");
        table.start();

        table.run(new RoomCommands.LeaveRoom(RoomHarness.HOST));

        assertThat(table.closed).isTrue();
        assertThat(table.released).contains(RoomHarness.HOST, ADA, BABBAGE);
        assertThat(table.states).endsWith(RoomState.CLOSED);
    }

    @Test
    void botsDoNotKeepARoomOpenOnceEveryPersonHasDisconnected() {
        table.joinAndSit(RoomHarness.HOST, "asha", 0);
        addBot(RoomHarness.HOST, ADA, "Ada_bot");

        table.run(new RoomCommands.Disconnected(RoomHarness.HOST));

        // The room starts counting down to closing, exactly as it would with nobody else in it.
        assertThat(table.waiting(RoomCommands.IdleCheck.class)).isNotNull();
        table.fire(RoomCommands.IdleCheck.class);
        assertThat(table.closed).isTrue();
    }

    @Test
    void aBotsActionIsAnOrdinaryCommand() {
        FakePlayer host = table.joinAndSit(RoomHarness.HOST, "asha", 0);
        SilentBot ada = addBot(RoomHarness.HOST, ADA, "Ada_bot");
        table.start();
        // Play until the bot is asked, then answer for it the way AiController would.
        for (int i = 0; i < 4 && ada.turns.isEmpty(); i++) {
            table.act(host, table.toAct() == host && host.last(
                    com.saksham.poker.common.protocol.server.ActionRequired.class).canCheck()
                    ? ActionType.CHECK : ActionType.CALL);
        }
        assertThat(ada.turns).isNotEmpty();
        ActionRequest turn = ada.turns.get(ada.turns.size() - 1);

        table.run(new RoomCommands.PlayerActionCmd(ADA, turn.turnId(), ActionType.FOLD, 0));

        assertThat(table.hands).as("the bot folded, so the hand is over").hasSize(1);
        assertThat(table.hands.get(0).players()).extracting(p -> p.username()).contains("Ada_bot");
    }
}
