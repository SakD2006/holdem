package com.saksham.poker.ai;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.server.BetReturned;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.StreetDealt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A bot's picture of the hand, built only from the messages the server sends its seat. They are the
 * same messages a person's app receives, already stripped of other players' hole cards, so a bot
 * cannot know more than a person would.
 *
 * <p>Not thread-safe: feed it and read it from one thread.
 */
public final class TableObserver {

    private int mySeat = -1;
    private boolean inHand;
    private long bigBlind;
    private int buttonSeat;
    private List<Integer> dealtSeats = List.of();
    private final Map<Integer, Long> stacks = new TreeMap<>();
    private final Map<Integer, Long> streetBets = new TreeMap<>();
    private final Set<Integer> folded = new HashSet<>();
    private List<Card> holeCards = List.of();
    private List<Card> board = List.of();
    /** Chips swept into the middle on earlier streets. */
    private long collected;
    private int raisesThisStreet;
    /** The bot's chips after the last hand it was in; -1 before it has played one. */
    private long stackAfterLastHand = -1;
    /** Whether the hand that has just ended was one this seat was dealt into. */
    private boolean playedLastHand;

    /** Takes in one message. Messages that say nothing about the hand are ignored. */
    public void accept(ServerMessage message) {
        if (message instanceof HandStarted m) {
            started(m);
        } else if (message instanceof HoleCards m) {
            mySeat = m.seat();
            holeCards = List.copyOf(m.cards());
            inHand = true;
        } else if (message instanceof BlindPosted m) {
            streetBets.merge(m.seat(), m.amount(), Long::sum);
            stacks.merge(m.seat(), -m.amount(), Long::sum);
        } else if (message instanceof PlayerActed m) {
            acted(m);
        } else if (message instanceof PotsUpdated m) {
            collected = 0;
            for (PotInfo pot : m.pots()) {
                collected += pot.amount();
            }
            streetBets.clear();
        } else if (message instanceof StreetDealt m) {
            board = List.copyOf(m.board());
            raisesThisStreet = 0;
        } else if (message instanceof BetReturned m) {
            returned(m);
        } else if (message instanceof HandEnded m) {
            playedLastHand = inHand && m.stacks().containsKey(mySeat);
            if (playedLastHand) {
                stackAfterLastHand = m.stacks().get(mySeat);
            }
            inHand = false;
        }
    }

    private void started(HandStarted m) {
        inHand = false; // true once this seat is dealt cards
        bigBlind = m.bigBlind();
        buttonSeat = m.buttonSeat();
        dealtSeats = new ArrayList<>(new TreeMap<>(m.stacks()).keySet());
        stacks.clear();
        stacks.putAll(m.stacks());
        streetBets.clear();
        folded.clear();
        holeCards = List.of();
        board = List.of();
        collected = 0;
        raisesThisStreet = 0;
    }

    private void acted(PlayerActed m) {
        streetBets.put(m.seat(), m.streetBet());
        stacks.put(m.seat(), m.stack());
        if (m.action() == ActionType.FOLD) {
            folded.add(m.seat());
        } else if (m.action() == ActionType.BET || m.action() == ActionType.RAISE) {
            raisesThisStreet++;
        }
    }

    /** An uncalled bet goes back to whoever made it. */
    private void returned(BetReturned m) {
        long onStreet = streetBets.getOrDefault(m.seat(), 0L);
        if (onStreet >= m.amount()) {
            streetBets.put(m.seat(), onStreet - m.amount());
        } else {
            collected = Math.max(0, collected - m.amount());
        }
        stacks.merge(m.seat(), m.amount(), Long::sum);
    }

    /** True while this seat holds cards in a hand that has not ended. */
    public boolean inHand() {
        return inHand;
    }

    /** The bot's seat, or -1 before it has been dealt a hand. */
    public int seat() {
        return mySeat;
    }

    /**
     * True if the hand that most recently ended was one this seat played. A seat waiting to be dealt
     * in sees other people's hands end too, and those say nothing about its own chips.
     */
    public boolean playedLastHand() {
        return playedLastHand;
    }

    /** The bot's chips after the last hand it played, or -1 if it has not played one. */
    public long stackAfterLastHand() {
        return stackAfterLastHand;
    }

    /**
     * What the bot knows now that it has been asked to act. The limits come from the server's
     * request, which is the authority on what is allowed.
     */
    public Observation observe(boolean canCheck, long toCall, boolean canBet, boolean canRaise, long minRaiseTo,
            long maxRaiseTo) {
        long pot = collected;
        for (long bet : streetBets.values()) {
            pot += bet;
        }
        int position = 0;
        int buttonIndex = dealtSeats.indexOf(buttonSeat);
        int myIndex = dealtSeats.indexOf(mySeat);
        if (buttonIndex >= 0 && myIndex >= 0) {
            position = (myIndex - buttonIndex + dealtSeats.size()) % dealtSeats.size();
        }
        return new Observation(holeCards, board, pot, toCall, canCheck, canBet, canRaise, minRaiseTo, maxRaiseTo,
                stacks.getOrDefault(mySeat, 0L), streetBets.getOrDefault(mySeat, 0L), Math.max(1, bigBlind),
                Math.max(1, dealtSeats.size() - folded.size()), Math.max(1, dealtSeats.size()), position,
                raisesThisStreet);
    }
}
