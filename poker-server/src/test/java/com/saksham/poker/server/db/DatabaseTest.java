package com.saksham.poker.server.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.api.HandActionInfo;
import com.saksham.poker.common.api.HandPage;
import com.saksham.poker.common.api.HandPlayerInfo;
import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.api.LeaderboardEntry;
import com.saksham.poker.common.api.RoomPreview;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.common.exception.UsernameTakenException;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.engine.card.SecureDeckFactory;
import com.saksham.poker.server.auth.PasswordHasher;
import com.saksham.poker.server.auth.Session;
import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.room.RoomCodeGenerator;
import com.saksham.poker.server.room.RoomManager;
import com.saksham.poker.server.room.RoomService;
import com.saksham.poker.server.room.RoomStore;
import com.saksham.poker.server.room.RoomTimings;
import com.saksham.poker.server.room.RoomSettings;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The data-access classes against a real PostgreSQL. Needs {@code docker compose up -d}. */
@Tag("db")
class DatabaseTest {

    private static final RoomSettings SETTINGS = new RoomSettings("Friday game", 6, 50, 100, 10_000, 25, true, false);
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private static TestDatabase database;
    private static UserDao users;
    private static AuthTokenDao tokens;
    private static RoomDao rooms;
    private static HandDao hands;

    @BeforeAll
    static void createSchema() throws Exception {
        database = new TestDatabase();
        assertThat(new MigrationRunner(database.dataSource()).migrate()).isEqualTo(2);
        users = new UserDao(database.dataSource());
        tokens = new AuthTokenDao(database.dataSource());
        rooms = new RoomDao(database.dataSource());
        hands = new HandDao(database.dataSource());
    }

    @AfterAll
    static void dropSchema() throws Exception {
        database.close();
    }

    // ---- migrations

    @Test
    void migrationsCreateEveryTableAndRunOnlyOnce() throws Exception {
        assertThat(new MigrationRunner(database.dataSource()).migrate()).isZero();

        List<String> tables = new ArrayList<>();
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = current_schema() ORDER BY table_name")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        assertThat(tables).containsExactly("auth_tokens", "hand_actions", "hand_players", "hands", "rooms",
                "schema_version", "users");
    }

    // ---- users

    @Test
    void aUserIsSavedAndFoundByIdOrByNameInAnyCase() throws Exception {
        User created = users.create("Asha_dao", "hash-1");

        assertThat(created.id()).isPositive();
        assertThat(created.username()).isEqualTo("Asha_dao");
        assertThat(created.passwordHash()).isEqualTo("hash-1");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.lastLoginAt()).isNull();
        assertThat(users.findById(created.id())).contains(created);
        assertThat(users.findByUsername("asha_DAO")).contains(created);
        assertThat(users.findByUsername("nobody_dao")).isEmpty();
        assertThat(users.findById(-1)).isEmpty();
    }

    @Test
    void aUsernameIsUniqueIgnoringCase() throws Exception {
        users.create("Ravi_dao", "hash");

        assertThatThrownBy(() -> users.create("ravi_DAO", "hash"))
                .isInstanceOf(UsernameTakenException.class).hasMessageContaining("ravi_DAO");
    }

    // ---- tokens

    @Test
    void aLoginStoresTheTokenAndStampsTheUserInOneGo() throws Exception {
        User user = users.create("token_user", "hash");

        tokens.recordLogin("a".repeat(64), user.id(), NOW, NOW.plus(7, ChronoUnit.DAYS));

        assertThat(tokens.findUserId("a".repeat(64), NOW)).contains(user.id());
        assertThat(users.findById(user.id()).orElseThrow().lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void aFailedLoginRecordSavesNothing() throws Exception {
        User user = users.create("rollback_user", "hash");
        tokens.recordLogin("b".repeat(64), user.id(), NOW, NOW.plus(7, ChronoUnit.DAYS));
        Instant later = NOW.plusSeconds(60);

        // The same token again breaks the primary key, so the whole transaction is undone.
        assertThatThrownBy(() -> tokens.recordLogin("b".repeat(64), user.id(), later, later.plusSeconds(60)))
                .isInstanceOf(com.saksham.poker.common.exception.PersistenceException.class);

        assertThat(users.findById(user.id()).orElseThrow().lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void anExpiredOrDeletedTokenIsNotFound() throws Exception {
        User user = users.create("expiry_user", "hash");
        tokens.recordLogin("c".repeat(64), user.id(), NOW, NOW.plusSeconds(60));
        tokens.recordLogin("d".repeat(64), user.id(), NOW, NOW.plusSeconds(600));

        assertThat(tokens.findUserId("c".repeat(64), NOW.plusSeconds(59))).contains(user.id());
        assertThat(tokens.findUserId("c".repeat(64), NOW.plusSeconds(60))).isEmpty();
        assertThat(tokens.findUserId("e".repeat(64), NOW)).isEmpty();

        assertThat(tokens.deleteExpired(NOW.plusSeconds(120))).isEqualTo(1);
        assertThat(tokens.findUserId("d".repeat(64), NOW)).contains(user.id());

        tokens.delete("d".repeat(64));
        assertThat(tokens.findUserId("d".repeat(64), NOW)).isEmpty();
    }

    // ---- rooms

    @Test
    void aRoomIsSavedWaitingAndFoundByCode() throws Exception {
        User host = users.create("room_host", "hash");

        RoomRecord created = rooms.create("ABC234", host.id(), SETTINGS).orElseThrow();

        assertThat(created.code()).isEqualTo("ABC234");
        assertThat(created.hostUserId()).isEqualTo(host.id());
        assertThat(created.settings()).isEqualTo(SETTINGS);
        assertThat(created.state()).isEqualTo(RoomState.WAITING);
        assertThat(created.closedAt()).isNull();
        assertThat(rooms.findByCode("ABC234")).contains(created);
        assertThat(rooms.findByCode("ZZZ999")).isEmpty();
    }

    @Test
    void aCodeCanNeverBeUsedTwiceEvenAfterTheRoomCloses() throws Exception {
        User host = users.create("code_host", "hash");
        rooms.create("DEF567", host.id(), SETTINGS).orElseThrow();
        rooms.updateState("DEF567", RoomState.CLOSED, NOW);

        assertThat(rooms.create("DEF567", host.id(), SETTINGS)).isEmpty();
    }

    @Test
    void closingARoomRecordsWhen() throws Exception {
        User host = users.create("close_host", "hash");
        rooms.create("GHJ234", host.id(), SETTINGS).orElseThrow();

        rooms.updateState("GHJ234", RoomState.PLAYING, NOW);
        assertThat(rooms.findByCode("GHJ234").orElseThrow().state()).isEqualTo(RoomState.PLAYING);
        assertThat(rooms.findByCode("GHJ234").orElseThrow().closedAt()).isNull();

        rooms.updateState("GHJ234", RoomState.CLOSED, NOW);
        assertThat(rooms.findByCode("GHJ234").orElseThrow().state()).isEqualTo(RoomState.CLOSED);
        assertThat(rooms.findByCode("GHJ234").orElseThrow().closedAt()).isEqualTo(NOW);
    }

    @Test
    void roomsLeftOpenAreAllClosedAtStartup() throws Exception {
        User host = users.create("restart_host", "hash");
        rooms.create("KMN234", host.id(), SETTINGS).orElseThrow();
        rooms.create("PQR234", host.id(), SETTINGS).orElseThrow();
        rooms.updateState("PQR234", RoomState.PLAYING, NOW);

        assertThat(rooms.closeAllOpen(NOW)).isGreaterThanOrEqualTo(2);

        assertThat(rooms.findByCode("KMN234").orElseThrow().state()).isEqualTo(RoomState.CLOSED);
        assertThat(rooms.findByCode("PQR234").orElseThrow().state()).isEqualTo(RoomState.CLOSED);
        assertThat(rooms.closeAllOpen(NOW)).isZero();
    }

    // ---- hands

    /** Three users and a room for them to have played in. */
    private static long[] threePlayersIn(String roomCode, String prefix) throws Exception {
        long asha = users.create(prefix + "_asha", "hash").id();
        long ravi = users.create(prefix + "_ravi", "hash").id();
        long meera = users.create(prefix + "_meera", "hash").id();
        rooms.create(roomCode, asha, SETTINGS).orElseThrow();
        return new long[] {asha, ravi, meera};
    }

    @Test
    void aSavedHandIsReadBackWithItsPlayersAndActions() throws Exception {
        long[] u = threePlayersIn("HND234", "read");
        hands.save(Hands.showdown("HND234", 12, u[0], u[1], u[2]));

        HandPage page = hands.pageFor(u[2], 1, 20);
        assertThat(page.total()).isEqualTo(1);
        HandSummary summary = page.hands().get(0);
        assertThat(summary.roomCode()).isEqualTo("HND234");
        assertThat(summary.roomName()).isEqualTo("Friday game");
        assertThat(summary.handNo()).isEqualTo(12);
        assertThat(summary.endedAtMs()).isEqualTo(Hands.ENDED.toEpochMilli());
        assertThat(summary.yourCards()).isEqualTo(Card.parseAll("Ah Ad"));
        assertThat(summary.board()).isEqualTo(Card.parseAll("2c 5d 9h Js 3s"));
        assertThat(summary.net()).isEqualTo(300);
        assertThat(summary.won()).isTrue();

        StoredHand stored = hands.find(summary.id()).orElseThrow();
        assertThat(stored.buttonSeat()).isZero();
        assertThat(stored.totalPot()).isEqualTo(600);
        assertThat(stored.startedAtMs()).isEqualTo(Hands.STARTED.toEpochMilli());
        assertThat(stored.players()).extracting(HandPlayerInfo::username)
                .containsExactly("read_asha", "read_ravi", "read_meera");
        assertThat(stored.players()).extracting(HandPlayerInfo::net).containsExactly(0L, -300L, 300L);
        assertThat(stored.players().get(0).holeCards()).isEqualTo(Card.parseAll("7c 2d"));
        assertThat(stored.players().get(1).showedDown()).isTrue();
        assertThat(stored.actions()).hasSize(11);
        assertThat(stored.actions().get(0).action()).isEqualTo("POST_SB");
        assertThat(stored.actions().get(0).username()).isEqualTo("read_ravi");
        assertThat(stored.actions().get(5).street()).isEqualTo("FLOP");
        assertThat(stored.actions().get(5).amount()).isEqualTo(200);
        assertThat(stored.actions()).extracting(HandActionInfo::seq).isSorted();

        // Through the API's eyes: the folder's cards are hidden from the others.
        assertThat(stored.viewFor(u[2]).players().get(0).holeCards()).isEmpty();
        assertThat(stored.viewFor(u[0]).players().get(0).holeCards()).isEqualTo(Card.parseAll("7c 2d"));
        assertThat(hands.find(-1)).isEmpty();
    }

    @Test
    void aHandFoldedPreflopIsStoredWithAnEmptyBoard() throws Exception {
        long[] u = threePlayersIn("HND567", "fold");
        hands.save(Hands.foldedPreflop("HND567", 1, u[0], u[1]));

        HandSummary summary = hands.pageFor(u[0], 1, 20).hands().get(0);
        assertThat(summary.board()).isEmpty();
        assertThat(summary.net()).isEqualTo(100);
        assertThat(hands.pageFor(u[2], 1, 20).total()).isZero();
    }

    @Test
    void savingTheSameHandTwiceStoresItOnce() throws Exception {
        long[] u = threePlayersIn("HND789", "twice");
        HandRecord hand = Hands.showdown("HND789", 1, u[0], u[1], u[2]);

        hands.save(hand);
        hands.save(hand);

        assertThat(hands.pageFor(u[0], 1, 20).total()).isEqualTo(1);
    }

    @Test
    void aHandIsSavedWhollyOrNotAtAll() throws Exception {
        long[] u = threePlayersIn("HNDKMN", "atomic");
        // The third player does not exist, so the hand's own row goes in and then a player row fails.
        HandRecord broken = Hands.showdown("HNDKMN", 1, u[0], u[1], -99);

        assertThatThrownBy(() -> hands.save(broken))
                .isInstanceOf(com.saksham.poker.common.exception.PersistenceException.class);
        assertThatThrownBy(() -> hands.save(Hands.showdown("NOROOM", 1, u[0], u[1], u[2])))
                .isInstanceOf(com.saksham.poker.common.exception.PersistenceException.class);

        // Nothing of the broken hand was kept, so the same hand number can be saved properly.
        assertThat(hands.pageFor(u[0], 1, 20).total()).isZero();
        hands.save(Hands.showdown("HNDKMN", 1, u[0], u[1], u[2]));
        assertThat(hands.pageFor(u[0], 1, 20).total()).isEqualTo(1);
    }

    @Test
    void historyIsPagedNewestFirst() throws Exception {
        long[] u = threePlayersIn("HNDPQR", "page");
        for (int handNo = 1; handNo <= 5; handNo++) {
            HandRecord base = Hands.foldedPreflop("HNDPQR", handNo, u[0], u[1]);
            hands.save(new HandRecord(base.roomCode(), base.roomName(), base.handNo(), base.smallBlind(),
                    base.bigBlind(), base.buttonSeat(), base.board(), base.totalPot(), base.startedAt(),
                    base.endedAt().plusSeconds(handNo), base.players(), base.actions()));
        }

        HandPage first = hands.pageFor(u[0], 1, 2);
        HandPage third = hands.pageFor(u[0], 3, 2);

        assertThat(first.total()).isEqualTo(5);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.pageSize()).isEqualTo(2);
        assertThat(first.hands()).extracting(HandSummary::handNo).containsExactly(5L, 4L);
        assertThat(third.hands()).extracting(HandSummary::handNo).containsExactly(1L);
        assertThat(hands.pageFor(u[0], 4, 2).hands()).isEmpty();
    }

    @Test
    void theLeaderboardTotalsEveryPlayersHandsAndRanksThem() throws Exception {
        long[] u = threePlayersIn("HNDSTU", "lead");
        hands.save(Hands.showdown("HNDSTU", 1, u[0], u[1], u[2]));       // asha 0, ravi -300, meera +300
        hands.save(Hands.showdown("HNDSTU", 2, u[0], u[1], u[2]));
        hands.save(Hands.foldedPreflop("HNDSTU", 3, u[0], u[1]));         // asha +100, ravi -100

        List<LeaderboardEntry> board = hands.leaderboard(1_000);

        LeaderboardEntry meera = entry(board, "lead_meera");
        LeaderboardEntry asha = entry(board, "lead_asha");
        LeaderboardEntry ravi = entry(board, "lead_ravi");
        assertThat(meera.totalNet()).isEqualTo(600);
        assertThat(meera.handsPlayed()).isEqualTo(2);
        assertThat(meera.handsWon()).isEqualTo(2);
        assertThat(asha.totalNet()).isEqualTo(100);
        assertThat(asha.handsPlayed()).isEqualTo(3);
        assertThat(asha.handsWon()).isEqualTo(1);
        assertThat(ravi.totalNet()).isEqualTo(-700);
        assertThat(ravi.handsWon()).isZero();
        assertThat(meera.rank()).isLessThan(asha.rank());
        assertThat(asha.rank()).isLessThan(ravi.rank());
        // Biggest winners first, and everyone's wins and losses cancel out.
        assertThat(board).extracting(LeaderboardEntry::totalNet).isSortedAccordingTo((a, b) -> Long.compare(b, a));
        assertThat(board.stream().mapToLong(LeaderboardEntry::totalNet).sum()).isZero();
        assertThat(hands.leaderboard(1)).hasSize(1);
    }

    private static LeaderboardEntry entry(List<LeaderboardEntry> board, String username) {
        return board.stream().filter(e -> e.username().equals(username)).findFirst().orElseThrow();
    }

    // ---- the services on a real database

    @Test
    void aRoomsResultsTotalItsOwnHandsOnly() throws Exception {
        long[] u = threePlayersIn("RES234", "res");
        rooms.create("RES235", u[0], SETTINGS).orElseThrow();
        hands.save(Hands.showdown("RES234", 1, u[0], u[1], u[2]));
        hands.save(Hands.showdown("RES234", 2, u[0], u[1], u[2]));
        hands.save(Hands.foldedPreflop("RES234", 3, u[0], u[1]));
        hands.save(Hands.showdown("RES235", 1, u[0], u[1], u[2])); // another room: must not be counted
        long roomId = rooms.findByCode("RES234").orElseThrow().id();

        List<RoomStanding> standings = hands.standings(roomId);

        assertThat(standings).containsExactly(new RoomStanding("res_meera", 2, 2, 600),
                new RoomStanding("res_asha", 3, 1, 100), new RoomStanding("res_ravi", 3, 0, -700));
        assertThat(standings.stream().mapToLong(RoomStanding::net).sum()).isZero();
        assertThat(hands.standings(rooms.findByCode("RES235").orElseThrow().id())).hasSize(3);
    }

    @Test
    void aRoomsHandsAreListedNewestFirstWithTheirWinners() throws Exception {
        long[] u = threePlayersIn("LST234", "lst");
        hands.save(Hands.showdown("LST234", 1, u[0], u[1], u[2]));
        hands.save(Hands.foldedPreflop("LST234", 2, u[0], u[1]));
        long roomId = rooms.findByCode("LST234").orElseThrow().id();
        long before = hands.count();

        List<RoomHand> listed = hands.handsInRoom(roomId, 10);

        assertThat(listed).extracting(RoomHand::handNo).containsExactly(2L, 1L);
        assertThat(listed).extracting(RoomHand::winners).containsExactly("lst_asha", "lst_meera");
        assertThat(listed.get(0).board()).isEmpty();
        assertThat(listed.get(1).board()).isEqualTo(Card.parseAll("2c 5d 9h Js 3s"));
        assertThat(listed.get(1).totalPot()).isEqualTo(600);
        assertThat(listed.get(1).endedAt()).isEqualTo(Hands.ENDED);
        assertThat(hands.handsInRoom(roomId, 1)).extracting(RoomHand::handNo).containsExactly(2L);
        assertThat(hands.find(listed.get(1).id())).isPresent();
        assertThat(before).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theNewestRoomsAreListedFirst() throws Exception {
        long host = users.create("recent_host", "hash").id();
        rooms.create("REC234", host, SETTINGS).orElseThrow();
        rooms.create("REC235", host, SETTINGS).orElseThrow();

        assertThat(rooms.recent(2)).extracting(RoomRecord::code).containsExactly("REC235", "REC234");
        assertThat(rooms.recent(1)).hasSize(1);
    }

    @Test
    void aPlayerCanRegisterLogInCreateARoomAndPreviewIt() throws Exception {
        SessionService sessions = new SessionService(users, tokens, new PasswordHasher(1_000),
                Clock.systemUTC(), Duration.ofDays(7));
        ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
        RoomManager manager = new RoomManager(timers, RoomStore.NONE, hand -> { }, RoomTimings.DEFAULT,
                new SecureDeckFactory(), Clock.systemUTC());
        RoomService roomService = new RoomService(rooms, users, new RoomCodeGenerator(), manager);

        sessions.register("Flow_player", "secret1");
        Session session = sessions.login("flow_player", "secret1");
        User user = sessions.authenticate(session.token());
        assertThat(user.username()).isEqualTo("Flow_player");
        assertThat(user.lastLoginAt()).isNotNull();

        RoomRecord room = roomService.create(user, SETTINGS);
        RoomPreview preview = roomService.preview(" " + room.code().toLowerCase() + " ");

        assertThat(preview.code()).isEqualTo(room.code());
        assertThat(preview.settings()).isEqualTo(SETTINGS.toInfo());
        assertThat(preview.state()).isEqualTo(RoomState.WAITING);
        assertThat(preview.hostUsername()).isEqualTo("Flow_player");
        // The room is live as soon as it is created, so the preview reports its real state.
        assertThat(preview.seatedPlayers()).isZero();

        assertThatThrownBy(() -> roomService.preview("ZZZ999")).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> roomService.preview("not a code")).isInstanceOf(RoomNotFoundException.class);

        sessions.logout(session.token());
        assertThatThrownBy(() -> sessions.authenticate(session.token())).isInstanceOf(UnauthorizedException.class);

        manager.shutdown();
        timers.shutdownNow();
    }
}
