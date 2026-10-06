package com.saksham.poker.common.protocol.dto;

/** How well a computer player plays. A host picks one when adding a bot to a room. */
public enum BotLevel {
    /** Plays by simple rules of thumb, a little too loosely. Beatable with patience. */
    EASY("Easy");

    private final String label;

    BotLevel(String label) {
        this.label = label;
    }

    /** The name to show a person, such as "Easy". */
    public String label() {
        return label;
    }
}
