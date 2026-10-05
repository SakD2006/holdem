package com.saksham.poker.server.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Turns a password into a salted hash that is slow to guess, and checks a password against one.
 * A stored hash looks like {@code pbkdf2$210000$<salt>$<hash>}, with the salt and hash in Base64, so
 * each hash records how it was made.
 */
public final class PasswordHasher {

    /** How many rounds of PBKDF2 a new hash uses. */
    public static final int DEFAULT_ITERATIONS = 210_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** @param iterations rounds for new hashes; lower values are only for tests */
    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be at least 1, but was " + iterations);
        }
        this.iterations = iterations;
    }

    /** Hashes a password with a new random salt. */
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations);
        Base64.Encoder base64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(hash);
    }

    /** True if the password is the one the stored hash was made from. False for a damaged hash. */
    public boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (storedIterations < 1) {
                return false;
            }
            // Compared in constant time, so the answer does not leak how much of the hash matched.
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, storedIterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("This Java installation cannot hash passwords with " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }
}
