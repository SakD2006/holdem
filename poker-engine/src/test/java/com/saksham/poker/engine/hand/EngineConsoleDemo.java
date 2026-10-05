package com.saksham.poker.engine.hand;

import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.engine.card.SecureDeckFactory;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.GameEvent;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Plays one hand between six random players and prints what happens. Run it after
 * {@code ./mvnw -q -pl poker-engine -am test-compile} with:
 *
 * <pre>
 * java -cp poker-common/target/classes:poker-engine/target/classes:poker-engine/target/test-classes \
 *     com.saksham.poker.engine.hand.EngineConsoleDemo
 * </pre>
 */
public final class EngineConsoleDemo {

    private EngineConsoleDemo() {
    }

    public static void main(String[] args) throws GameRuleException {
        Map<Integer, Long> stacks = new TreeMap<>();
        for (int seat = 1; seat <= 6; seat++) {
            stacks.put(seat, 1000L * seat);
        }
        HoldemHand hand = new HoldemHand(new HandConfig(50, 100, 1, stacks), new SecureDeckFactory().create());
        RandomPlayer player = new RandomPlayer(new Random());

        print(hand.start());
        while (!hand.isComplete()) {
            int seat = hand.seatToAct();
            print(hand.apply(seat, player.choose(hand.legalActionsFor(seat), hand.seat(seat).stack())));
        }
    }

    private static void print(List<GameEvent> events) {
        for (GameEvent event : events) {
            if (!(event instanceof ActionRequested)) {
                System.out.println(event.describe());
            }
        }
    }
}
