package com.saksham.poker.common.protocol.dto;

/**
 * The settings chosen when a room was created.
 *
 * @param name the room's name
 * @param maxPlayers seats in the room, 2 to 9
 * @param smallBlind the small blind
 * @param bigBlind the big blind
 * @param startingStack chips every player starts with, and rebuys to
 * @param turnSeconds how long a player has to act
 * @param rebuyAllowed whether a player who loses every chip may buy back in
 */
public record RoomSettingsInfo(
        String name,
        int maxPlayers,
        long smallBlind,
        long bigBlind,
        long startingStack,
        int turnSeconds,
        boolean rebuyAllowed) {
}
