package com.saksham.poker.common.protocol.dto;

/** How well a computer player plays. A host picks one when adding a bot to a room. */
public enum BotLevel {
    /** Plays by simple rules of thumb, a little too loosely. Beatable with patience. */
    EASY("Easy"),
    /** Works out its real chance of winning by simulation and bets accordingly. A fair match for most. */
    MEDIUM("Medium"),
    /**
     * Plays as Medium does and also studies each opponent: it remembers how they bet and what they
     * showed, bluffs the ones who fold and calls the ones who bluff. Gets harder the longer you play it.
     */
    HARD("Hard");

    private final String label;

    BotLevel(String label) {
        this.label = label;
    }

    /** The name to show a person, such as "Easy". */
    public String label() {
        return label;
    }
}
