package com.saksham.poker.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.ai.arena.SeatFeed;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.hand.HandConfig;
import com.saksham.poker.engine.hand.HoldemHand;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The arena feeds its bots through its own copy of the server's message router. A bot must be told
 * exactly the same thing in both places, or a strategy tuned in the arena would meet a different
 * game at a real table.
 */
class ArenaFeedMatchesServerTest {

    @Test
    void theArenaTellsEachSeatExactlyWhatTheServerWould() throws Exception {
        MessageCodec codec = new MessageCodec();
        Random random = new Random(31);
        List<Card> cards = new ArrayList<>(Deck.standardOrder());
        int compared = 0;
        int holeCardsSeen = 0;
        for (int handNo = 1; handNo <= 300; handNo++) {
            int players = 2 + random.nextInt(5);
            Map<Integer, Long> stacks = new TreeMap<>();
            for (int seat = 0; seat < players; seat++) {
                stacks.put(seat, 500L + random.nextInt(10_000));
            }
            Collections.shuffle(cards, random);
            HoldemHand hand = new HoldemHand(new HandConfig(50, 100, handNo % players, stacks), new Deck(cards));
            List<GameEvent> events = hand.start();
            while (true) {
                ActionRequested turn = null;
                for (GameEvent event : events) {
                    if (event instanceof ActionRequested requested) {
                        turn = requested;
                    }
                    for (int viewer = -1; viewer < players; viewer++) {
                        Optional<ServerMessage> server = EventRouter.messageFor(event, viewer, handNo);
                        Optional<ServerMessage> arena = SeatFeed.messageFor(event, viewer, handNo);
                        if (server.isPresent() && server.get() instanceof Showdown) {
                            // The one thing the arena leaves out: bots do not yet use shown hands.
                            assertThat(arena).isEmpty();
                            continue;
                        }
                        assertThat(arena.isPresent()).as("%s for seat %d", event, viewer).isEqualTo(server.isPresent());
                        if (server.isPresent()) {
                            assertThat(codec.encode(arena.get(), 1)).isEqualTo(codec.encode(server.get(), 1));
                            compared++;
                            if (arena.get() instanceof HoleCards dealt) {
                                assertThat(dealt.seat()).as("a seat sees only its own cards").isEqualTo(viewer);
                                holeCardsSeen++;
                            }
                        }
                    }
                }
                if (turn == null) {
                    break;
                }
                events = hand.apply(turn.seat(), randomAction(turn.legal(), random));
            }
        }
        assertThat(compared).isGreaterThan(10_000);
        assertThat(holeCardsSeen).isGreaterThan(600);
    }

    private static PlayerAction randomAction(LegalActions legal, Random random) {
        int roll = random.nextInt(10);
        if (roll < 2) {
            return legal.canCheck() ? new Check() : new Fold();
        }
        if (roll < 8 || !legal.canRaise()) {
            return legal.canCheck() ? new Check() : new Call();
        }
        return new Raise(legal.minRaiseTo());
    }
}
