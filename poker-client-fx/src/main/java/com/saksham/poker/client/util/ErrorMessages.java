package com.saksham.poker.client.util;

import com.saksham.poker.common.error.ErrorCode;

/**
 * What to tell the player for each of the server's error codes: one plain sentence saying what went
 * wrong and what to do about it.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /**
     * The sentence to show for a refusal from the server. The server usually sends a sentence of its
     * own that knows the details ("The smallest raise is to 400"), and that is shown when there is
     * one. The plain sentence for the code is used when there is none, and always for the two codes
     * whose details are of use only to a programmer.
     *
     * @param code what kind of error it was
     * @param serverMessage what the server said about it, or null
     */
    public static String text(ErrorCode code, String serverMessage) {
        if (code == null) {
            return serverMessage == null || serverMessage.isBlank()
                    ? "Something went wrong. Try again." : serverMessage;
        }
        boolean technical = code == ErrorCode.MALFORMED_MESSAGE || code == ErrorCode.INTERNAL;
        if (technical || serverMessage == null || serverMessage.isBlank()) {
            return plain(code);
        }
        return serverMessage;
    }

    /** The plain sentence for a code. Every code has one: the compiler checks none is forgotten. */
    public static String plain(ErrorCode code) {
        return switch (code) {
            case NOT_YOUR_TURN -> "It is not your turn. Wait until the timer is on your seat.";
            case INVALID_ACTION -> "That move is not allowed right now. Use one of the buttons shown.";
            case INVALID_AMOUNT -> "That amount is not allowed. Choose one between the smallest and largest shown.";
            case ROOM_NOT_FOUND -> "There is no room with that code. Check the code with the host.";
            case ROOM_FULL -> "Every seat in that room is taken. Ask the host to make a bigger room.";
            case ROOM_CLOSED -> "That room has closed. Ask the host to create a new one.";
            case SEAT_TAKEN -> "Someone else has just taken that seat. Pick another.";
            case NOT_HOST -> "Only the host of the room can do that.";
            case GAME_ALREADY_STARTED -> "The game has already started.";
            case NOT_ENOUGH_PLAYERS -> "At least two players must be seated before the game can start.";
            case REBUY_NOT_ALLOWED -> "You cannot buy back in: this room does not allow it, or you still have chips.";
            case NOT_IN_ROOM -> "You are not in a room. Create or join one from the home screen.";
            case ALREADY_IN_ROOM -> "You are already in a room. Leave it before joining another.";
            case INVALID_CREDENTIALS -> "The username or password is wrong. Check both and try again.";
            case USERNAME_TAKEN -> "That username is taken. Choose a different one.";
            case MALFORMED_MESSAGE -> "The app and the server did not understand each other. Check that everyone "
                    + "is using the same version of the game.";
            case INVALID_REQUEST -> "The server could not accept that. Check what you typed and try again.";
            case UNAUTHORIZED -> "Your login has run out. Log in again.";
            case INTERNAL -> "Something went wrong on the server. Try again; if it keeps happening, ask the host "
                    + "to look at the server log.";
        };
    }
}
