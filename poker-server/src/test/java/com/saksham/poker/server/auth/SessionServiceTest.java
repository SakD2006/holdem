package com.saksham.poker.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.common.exception.InvalidCredentialsException;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.common.exception.UsernameTakenException;
import com.saksham.poker.server.db.AuthTokenDao;
import com.saksham.poker.server.db.User;
import com.saksham.poker.server.db.UserDao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Registering and logging in, with the database replaced by maps. */
class SessionServiceTest {

    private final FakeUsers users = new FakeUsers();
    private final FakeTokens tokens = new FakeTokens();
    private final MovableClock clock = new MovableClock();
    private final SessionService service =
            new SessionService(users, tokens, new PasswordHasher(1_000), clock, Duration.ofDays(7));

    // ---- registering

    @Test
    void registeringCreatesAnAccountAndLogsItIn() throws Exception {
        Session session = service.register("asha_01", "secret1");

        assertThat(session.user().username()).isEqualTo("asha_01");
        assertThat(session.token()).matches("[0-9a-f]{64}");
        assertThat(service.authenticate(session.token())).isEqualTo(session.user());
    }

    @Test
    void thePasswordAndTheTokenAreStoredOnlyAsHashes() throws Exception {
        Session session = service.register("asha", "secret1");

        assertThat(users.byId.get(session.user().id()).passwordHash())
                .startsWith("pbkdf2$").doesNotContain("secret1");
        assertThat(tokens.byHash).doesNotContainKey(session.token());
        assertThat(tokens.byHash).containsKey(SessionService.hashOf(session.token()));
    }

    @Test
    void aUsernameMustBe3To24LettersDigitsOrUnderscores() {
        for (String bad : List.of("", "ab", "a".repeat(25), "asha kumar", "asha!", "asha-k", "आशा")) {
            assertThatThrownBy(() -> service.register(bad, "secret1")).as(bad)
                    .isInstanceOf(InvalidRequestException.class).hasMessageContaining("username");
        }
        assertThatThrownBy(() -> service.register(null, "secret1")).isInstanceOf(InvalidRequestException.class);
        assertThat(users.byId).isEmpty();
    }

    @Test
    void aPasswordMustBe6To72Characters() {
        assertThatThrownBy(() -> service.register("asha", "12345"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("password");
        assertThatThrownBy(() -> service.register("asha", "x".repeat(73)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.register("asha", null)).isInstanceOf(InvalidRequestException.class);
        assertThat(users.byId).isEmpty();
    }

    @Test
    void aUsernameCannotBeRegisteredTwiceEvenInAnotherCase() throws Exception {
        service.register("Asha", "secret1");

        assertThatThrownBy(() -> service.register("asha", "another1"))
                .isInstanceOf(UsernameTakenException.class).hasMessageContaining("asha");
    }

    // ---- logging in

    @Test
    void loggingInWithTheRightPasswordStartsANewSession() throws Exception {
        Session registered = service.register("asha", "secret1");

        Session loggedIn = service.login("ASHA", "secret1");

        assertThat(loggedIn.user()).isEqualTo(registered.user());
        assertThat(loggedIn.token()).isNotEqualTo(registered.token());
        assertThat(service.authenticate(loggedIn.token())).isEqualTo(registered.user());
        assertThat(service.authenticate(registered.token())).isEqualTo(registered.user());
    }

    @Test
    void aWrongPasswordAndAnUnknownUsernameGiveTheSameError() throws Exception {
        service.register("asha", "secret1");

        Throwable wrongPassword = catchLogin("asha", "secret2");
        Throwable unknownUser = catchLogin("nobody", "secret1");

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownUser).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
        assertThat(catchLogin(null, "secret1")).isInstanceOf(InvalidCredentialsException.class);
        assertThat(catchLogin("asha", null)).isInstanceOf(InvalidCredentialsException.class);
    }

    private Throwable catchLogin(String username, String password) {
        try {
            service.login(username, password);
            return null;
        } catch (InvalidCredentialsException e) {
            return e;
        }
    }

    // ---- tokens

    @Test
    void aMissingMalformedOrUnknownTokenIsNotAuthorized() {
        for (String bad : new String[] {null, "", "not-a-token", "A".repeat(64), "a".repeat(63), "a".repeat(64)}) {
            assertThatThrownBy(() -> service.authenticate(bad)).as(String.valueOf(bad))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Test
    void aTokenStopsWorkingAfterSevenDays() throws Exception {
        Session session = service.register("asha", "secret1");

        clock.advance(Duration.ofDays(7).minusSeconds(1));
        assertThat(service.authenticate(session.token())).isEqualTo(session.user());

        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service.authenticate(session.token())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loggingOutEndsOnlyThatSession() throws Exception {
        Session first = service.register("asha", "secret1");
        Session second = service.login("asha", "secret1");

        service.logout(first.token());

        assertThatThrownBy(() -> service.authenticate(first.token())).isInstanceOf(UnauthorizedException.class);
        assertThat(service.authenticate(second.token())).isEqualTo(second.user());
        // Logging out again, or with rubbish, is harmless.
        service.logout(first.token());
        service.logout("rubbish");
        service.logout(null);
    }

    // ---- stand-ins for the database

    private static final class FakeUsers extends UserDao {

        final Map<Long, User> byId = new HashMap<>();

        FakeUsers() {
            super(null);
        }

        @Override
        public User create(String username, String passwordHash) throws UsernameTakenException {
            if (findByUsername(username).isPresent()) {
                throw new UsernameTakenException("The username \"" + username + "\" is already taken.");
            }
            User user = new User(byId.size() + 1, username, passwordHash, Instant.EPOCH, null);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return byId.values().stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst();
        }

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }
    }

    private static final class FakeTokens extends AuthTokenDao {

        private record Entry(long userId, Instant expiresAt) {
        }

        final Map<String, Entry> byHash = new HashMap<>();

        FakeTokens() {
            super(null);
        }

        @Override
        public void recordLogin(String tokenHash, long userId, Instant now, Instant expiresAt) {
            byHash.put(tokenHash, new Entry(userId, expiresAt));
        }

        @Override
        public Optional<Long> findUserId(String tokenHash, Instant now) {
            Entry entry = byHash.get(tokenHash);
            return entry != null && entry.expiresAt().isAfter(now) ? Optional.of(entry.userId()) : Optional.empty();
        }

        @Override
        public void delete(String tokenHash) {
            byHash.remove(tokenHash);
        }
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-05T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
