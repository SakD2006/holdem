package com.saksham.poker.server.room;

import com.saksham.poker.common.exception.InvalidRequestException;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;

/**
 * The settings of a room, chosen by its host when creating it.
 *
 * @param name the room's name, 1 to 40 characters
 * @param maxPlayers seats in the room, 2 to 9
 * @param smallBlind the small blind, at least 1
 * @param bigBlind the big blind, at least the small blind
 * @param startingStack chips every player starts with and rebuys to, at least the big blind
 * @param turnSeconds how long a player has to act, 10 to 60
 * @param rebuyAllowed whether a player who loses every chip may buy back in
 * @param allowBots reserved for AI players later; always false for now
 */
public record RoomSettings(String name, int maxPlayers, long smallBlind, long bigBlind, long startingStack,
        int turnSeconds, boolean rebuyAllowed, boolean allowBots) {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 9;
    public static final int MIN_TURN_SECONDS = 10;
    public static final int MAX_TURN_SECONDS = 60;
    public static final int MAX_NAME_LENGTH = 40;
    public static final long MAX_CHIPS = 1_000_000_000L;

    public RoomSettings {
        name = name == null ? "" : name.trim();
    }

    /** Settings as a client sent them. */
    public static RoomSettings from(RoomSettingsInfo info) {
        return new RoomSettings(info.name(), info.maxPlayers(), info.smallBlind(), info.bigBlind(),
                info.startingStack(), info.turnSeconds(), info.rebuyAllowed(), false);
    }

    /** Settings as clients are shown them. */
    public RoomSettingsInfo toInfo() {
        return new RoomSettingsInfo(name, maxPlayers, smallBlind, bigBlind, startingStack, turnSeconds,
                rebuyAllowed);
    }

    /** @throws InvalidRequestException naming the first setting that is out of range */
    public void validate() throws InvalidRequestException {
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException("A room name must be 1 to " + MAX_NAME_LENGTH + " characters long.");
        }
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new InvalidRequestException(
                    "A room must have " + MIN_PLAYERS + " to " + MAX_PLAYERS + " players, but " + maxPlayers
                            + " was asked for.");
        }
        if (smallBlind < 1) {
            throw new InvalidRequestException("The small blind must be at least 1.");
        }
        if (bigBlind < smallBlind) {
            throw new InvalidRequestException(
                    "The big blind (" + bigBlind + ") must be at least the small blind (" + smallBlind + ").");
        }
        if (startingStack < bigBlind) {
            throw new InvalidRequestException(
                    "The starting stack (" + startingStack + ") must be at least the big blind (" + bigBlind + ").");
        }
        if (startingStack > MAX_CHIPS) {
            throw new InvalidRequestException("The starting stack must be at most " + MAX_CHIPS + ".");
        }
        if (turnSeconds < MIN_TURN_SECONDS || turnSeconds > MAX_TURN_SECONDS) {
            throw new InvalidRequestException(
                    "The turn time must be " + MIN_TURN_SECONDS + " to " + MAX_TURN_SECONDS + " seconds, but was "
                            + turnSeconds + ".");
        }
    }
}
