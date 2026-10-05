package com.saksham.poker.engine.hand;

import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.card.StackedDeckFactory;
import com.saksham.poker.engine.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sets up a hand with chosen hole cards and board, and records every event it produces.
 *
 * <pre>
 * HandFixture table = new HandFixture().blinds(50, 100).button(1)
 *         .seat(1, 1000, "Ah Ad").seat(2, 1000, "Kc Kd").board("2s 7h 9c Jd 3s").start();
 * </pre>
 */
final class HandFixture {

    private long smallBlind = 50;
    private long bigBlind = 100;
    private int button = 1;
    private final Map<Integer, Long> stacks = new TreeMap<>();
    private final Map<Integer, List<Card>> holes = new TreeMap<>();
    private List<Card> boardCards = List.of();

    private HoldemHand hand;
    private final List<GameEvent> events = new ArrayList<>();

    HandFixture blinds(long small, long big) {
        this.smallBlind = small;
        this.bigBlind = big;
        return this;
    }

    HandFixture button(int seat) {
        this.button = seat;
        return this;
    }

    /** Adds a player with the hole cards they will be dealt. */
    HandFixture seat(int seat, long stack, String holeCards) {
        stacks.put(seat, stack);
        holes.put(seat, Card.parseAll(holeCards));
        return this;
    }

    /** Adds a player whose hole cards do not matter. */
    HandFixture seat(int seat, long stack) {
        stacks.put(seat, stack);
        return this;
    }

    /** The community cards, in the order they will come: flop, turn, river. */
    HandFixture board(String cards) {
        this.boardCards = Card.parseAll(cards);
        return this;
    }

    HandFixture start() {
        hand = new HoldemHand(new HandConfig(smallBlind, bigBlind, button, stacks), stackedDeck());
        events.addAll(hand.start());
        return this;
    }

    /** Arranges the deck so the dealing order hands out the chosen cards. */
    private Deck stackedDeck() {
        // Hole cards are dealt one at a time starting left of the button.
        List<Integer> dealOrder = new ArrayList<>();
        for (int seat : stacks.keySet()) {
            if (seat > button) {
                dealOrder.add(seat);
            }
        }
        for (int seat : stacks.keySet()) {
            if (seat <= button) {
                dealOrder.add(seat);
            }
        }

        List<Card> spare = new ArrayList<>(Deck.standardOrder());
        holes.values().forEach(spare::removeAll);
        spare.removeAll(boardCards);

        List<Card> top = new ArrayList<>();
        Map<Integer, List<Card>> dealt = new TreeMap<>();
        for (int seat : dealOrder) {
            dealt.put(seat, holes.containsKey(seat)
                    ? holes.get(seat)
                    : List.of(spare.remove(0), spare.remove(0)));
        }
        for (int card = 0; card < 2; card++) {
            for (int seat : dealOrder) {
                top.add(dealt.get(seat).get(card));
            }
        }
        for (int i = 0; i < boardCards.size(); i++) {
            if (i == 0 || i >= 3) {
                top.add(spare.remove(0)); // burn before the flop, the turn and the river
            }
            top.add(boardCards.get(i));
        }
        return new StackedDeckFactory(top).create();
    }

    /** Plays an action and returns only the events it caused. */
    List<GameEvent> act(int seat, PlayerAction action) throws GameRuleException {
        List<GameEvent> caused = hand.apply(seat, action);
        events.addAll(caused);
        return caused;
    }

    List<GameEvent> forceFold(int seat) {
        List<GameEvent> caused = hand.forceFold(seat);
        events.addAll(caused);
        return caused;
    }

    HoldemHand hand() {
        return hand;
    }

    /** Every event so far. */
    List<GameEvent> events() {
        return events;
    }

    /** Every event so far of one kind, in order. */
    <T extends GameEvent> List<T> events(Class<T> type) {
        return of(events, type);
    }

    /** The most recent event of one kind. */
    <T extends GameEvent> T last(Class<T> type) {
        List<T> matching = events(type);
        if (matching.isEmpty()) {
            throw new AssertionError("No " + type.getSimpleName() + " event yet: " + events);
        }
        return matching.get(matching.size() - 1);
    }

    static <T extends GameEvent> List<T> of(List<GameEvent> events, Class<T> type) {
        List<T> matching = new ArrayList<>();
        for (GameEvent event : events) {
            if (type.isInstance(event)) {
                matching.add(type.cast(event));
            }
        }
        return matching;
    }

    long stack(int seat) {
        return hand.seat(seat).stack();
    }
}
