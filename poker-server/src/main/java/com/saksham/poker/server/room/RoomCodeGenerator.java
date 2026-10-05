package com.saksham.poker.server.room;

import java.security.SecureRandom;

/**
 * Makes the 6-character codes players type to join a room. The alphabet leaves out 0, O, 1, I and L,
 * which are easy to confuse when read aloud or copied by hand.
 */
public final class RoomCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    /** A new random code. It may, very rarely, repeat an old one; the caller checks. */
    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Tidies a code a player typed: trims spaces and makes it upper case.
     *
     * @return the tidied code, or null if it could not be a room code
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.trim().toUpperCase();
        if (code.length() != LENGTH) {
            return null;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return null;
            }
        }
        return code;
    }
}
