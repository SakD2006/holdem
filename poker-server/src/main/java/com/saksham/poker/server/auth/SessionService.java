package com.saksham.poker.server.auth;

import com.saksham.poker.common.exception.InvalidCredentialsException;
import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.common.exception.UsernameTakenException;
import com.saksham.poker.server.db.AuthTokenDao;
import com.saksham.poker.server.db.User;
import com.saksham.poker.server.db.UserDao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/** Registers accounts, logs players in and out, and checks the token sent with each request. */
public final class SessionService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{64}");
    private static final int MIN_PASSWORD = 6;
    private static final int MAX_PASSWORD = 72;
    private static final int TOKEN_BYTES = 32;

    private final UserDao users;
    private final AuthTokenDao tokens;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final Duration tokenLifetime;
    private final SecureRandom random = new SecureRandom();
    /** Checked when the username is unknown, so that takes as long as a wrong password does. */
    private final String decoyHash;

    public SessionService(UserDao users, AuthTokenDao tokens, PasswordHasher hasher, Clock clock,
            Duration tokenLifetime) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.clock = clock;
        this.tokenLifetime = tokenLifetime;
        this.decoyHash = hasher.hash("no account has this password");
    }

    /**
     * Creates an account and logs it in.
     *
     * @throws InvalidRequestException if the username or password is not acceptable
     * @throws UsernameTakenException if the username is in use
     */
    public Session register(String username, String password)
            throws InvalidRequestException, UsernameTakenException {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new InvalidRequestException(
                    "A username must be 3 to 24 characters, using only letters, digits and underscores.");
        }
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new InvalidRequestException(
                    "A password must be " + MIN_PASSWORD + " to " + MAX_PASSWORD + " characters long.");
        }
        return open(users.create(username, hasher.hash(password)));
    }

    /**
     * Logs a player in.
     *
     * @throws InvalidCredentialsException if the username does not exist or the password is wrong;
     *     the two are deliberately not told apart
     */
    public Session login(String username, String password) throws InvalidCredentialsException {
        Optional<User> user = username == null ? Optional.empty() : users.findByUsername(username);
        boolean passwordMatches = hasher.verify(password, user.map(User::passwordHash).orElse(decoyHash));
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException("The username or password is wrong. Check both and try again.");
        }
        return open(user.get());
    }

    /**
     * Finds who a token belongs to.
     *
     * @throws UnauthorizedException if the token is missing, unknown or expired
     */
    public User authenticate(String token) throws UnauthorizedException {
        if (token != null && TOKEN.matcher(token).matches()) {
            Optional<Long> userId = tokens.findUserId(hashOf(token), clock.instant());
            if (userId.isPresent()) {
                Optional<User> user = users.findById(userId.get());
                if (user.isPresent()) {
                    return user.get();
                }
            }
        }
        throw new UnauthorizedException("You are not logged in, or your login has expired. Log in again.");
    }

    /** Ends the session a token belongs to. Does nothing if the token is unknown. */
    public void logout(String token) {
        if (token != null && TOKEN.matcher(token).matches()) {
            tokens.delete(hashOf(token));
        }
    }

    private Session open(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Instant now = clock.instant();
        tokens.recordLogin(hashOf(token), user.id(), now, now.plus(tokenLifetime));
        return new Session(token, user);
    }

    /** Tokens are stored as their SHA-256, so a copy of the database cannot be used to log in. */
    static String hashOf(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("This Java installation has no SHA-256.", e);
        }
    }
}
