package com.saksham.poker.server.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.api.RoomPreview;
import com.saksham.poker.common.exception.RoomNotFoundException;
import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.common.exception.UsernameTakenException;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.server.auth.PasswordHasher;
import com.saksham.poker.server.auth.Session;
import com.saksham.poker.server.auth.SessionService;
import com.saksham.poker.server.room.RoomCodeGenerator;
import com.saksham.poker.server.room.RoomService;
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

    @BeforeAll
    static void createSchema() throws Exception {
        database = new TestDatabase();
        assertThat(new MigrationRunner(database.dataSource()).migrate()).isEqualTo(1);
        users = new UserDao(database.dataSource());
        tokens = new AuthTokenDao(database.dataSource());
        rooms = new RoomDao(database.dataSource());
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

    // ---- the services on a real database

    @Test
    void aPlayerCanRegisterLogInCreateARoomAndPreviewIt() throws Exception {
        SessionService sessions = new SessionService(users, tokens, new PasswordHasher(1_000),
                Clock.systemUTC(), Duration.ofDays(7));
        RoomService roomService = new RoomService(rooms, users, new RoomCodeGenerator());

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
        assertThat(preview.seatedPlayers()).isZero();

        assertThatThrownBy(() -> roomService.preview("ZZZ999")).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> roomService.preview("not a code")).isInstanceOf(RoomNotFoundException.class);

        sessions.logout(session.token());
        assertThatThrownBy(() -> sessions.authenticate(session.token())).isInstanceOf(UnauthorizedException.class);
    }
}
