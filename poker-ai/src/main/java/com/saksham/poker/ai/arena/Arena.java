package com.saksham.poker.ai.arena;

import com.saksham.poker.ai.BotStrategy;
import com.saksham.poker.ai.Decision;
import com.saksham.poker.ai.Observation;
import com.saksham.poker.ai.TableObserver;
import com.saksham.poker.common.action.Bet;
import com.saksham.poker.common.action.Call;
import com.saksham.poker.common.action.Check;
import com.saksham.poker.common.action.Fold;
import com.saksham.poker.common.action.PlayerAction;
import com.saksham.poker.common.action.Raise;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.GameRuleException;
import com.saksham.poker.engine.card.Deck;
import com.saksham.poker.engine.event.ActionRequested;
import com.saksham.poker.engine.event.GameEvent;
import com.saksham.poker.engine.hand.HandConfig;
import com.saksham.poker.engine.hand.HandResult;
import com.saksham.poker.engine.hand.HoldemHand;
import com.saksham.poker.engine.rules.LegalActions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plays a fixed line-up of bots against each other for many hands and adds up who won what.
 *
 * <p>To make the comparison fair, every hand starts with everyone on the same stack, the seating is
 * shuffled each hand so nobody is always to the left of the same opponent, and the dealer button
 * moves round. Results are in big blinds won per 100 hands, the usual measure in poker.
 */
public final class Arena {

    /** One bot in the line-up. Two entrants may share a strategy; they are still scored apart. */
    public record Entrant(String name, BotStrategy strategy) {
    }

    private static final long SMALL_BLIND = 50;
    private static final long BIG_BLIND = 100;
    /** 100 big blinds, the standard depth for comparing players. */
    private static final long STACK = 100 * BIG_BLIND;

    private final List<Entrant> entrants;

    /** @param entrants two to nine bots */
    public Arena(List<Entrant> entrants) {
        if (entrants.size() < HandConfig.MIN_PLAYERS || entrants.size() > HandConfig.MAX_PLAYERS) {
            throw new IllegalArgumentException("An arena needs " + HandConfig.MIN_PLAYERS + " to "
                    + HandConfig.MAX_PLAYERS + " bots, but was given " + entrants.size() + ".");
        }
        this.entrants = List.copyOf(entrants);
    }

    /**
     * Plays the hands, sharing them between several threads.
     *
     * @param hands how many hands to play in all
     * @param seed fixes the cards and every bot's luck, so a run can be repeated exactly
     * @param threads how many threads to use; each plays its own share at its own table
     */
    public ArenaResult play(int hands, long seed, int threads) {
        int workers = Math.max(1, Math.min(threads, hands));
        ExecutorService pool = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "arena");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<ArenaResult>> shares = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int share = hands / workers + (worker < hands % workers ? 1 : 0);
                long tableSeed = seed * 1_000_003L + worker;
                shares.add(pool.submit(() -> playTable(share, tableSeed)));
            }
            ArenaResult total = ArenaResult.empty(names());
            for (Future<ArenaResult> share : shares) {
                total = total.plus(share.get());
            }
            return total;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The arena was interrupted.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("A table in the arena failed: " + e.getCause(), e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    private List<String> names() {
        List<String> names = new ArrayList<>();
        for (Entrant entrant : entrants) {
            names.add(entrant.name());
        }
        return names;
    }

    /** One table, one thread: plays its share of the hands from start to finish. */
    private ArenaResult playTable(int hands, long seed) {
        int players = entrants.size();
        Random random = new Random(seed);
        List<Card> cards = new ArrayList<>(Deck.standardOrder());
        List<Integer> seating = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            seating.add(i);
        }
        double[] won = new double[players];
        double[] wonSquared = new double[players];
        int illegal = 0;
        for (int handNo = 1; handNo <= hands; handNo++) {
            Collections.shuffle(cards, random);
            Collections.shuffle(seating, random); // seating.get(seat) = which entrant sits there
            Map<Integer, Long> stacks = new TreeMap<>();
            TableObserver[] observers = new TableObserver[players];
            for (int seat = 0; seat < players; seat++) {
                stacks.put(seat, STACK);
                observers[seat] = new TableObserver();
            }
            HoldemHand hand = new HoldemHand(new HandConfig(SMALL_BLIND, BIG_BLIND, handNo % players, stacks),
                    new Deck(cards));
            List<GameEvent> events = hand.start();
            while (true) {
                ActionRequested turn = show(events, observers, handNo);
                if (turn == null) {
                    break; // the hand is over
                }
                int seat = turn.seat();
                LegalActions legal = turn.legal();
                Observation seen = observers[seat].observe(legal.canCheck(), legal.callAmount(), legal.canBet(),
                        legal.canRaise(), legal.minRaiseTo(), legal.maxRaiseTo());
                Decision decision = entrants.get(seating.get(seat)).strategy().decide(seen, random);
                try {
                    events = hand.apply(seat, toAction(decision));
                } catch (GameRuleException e) {
                    // Should never happen: decisions are made legal before they get here.
                    illegal++;
                    events = hand.forceFold(seat);
                }
            }
            HandResult result = hand.result();
            for (int seat = 0; seat < players; seat++) {
                double bigBlinds = (double) result.net(seat) / BIG_BLIND;
                int entrant = seating.get(seat);
                won[entrant] += bigBlinds;
                wonSquared[entrant] += bigBlinds * bigBlinds;
            }
        }
        return new ArenaResult(names(), hands, won, wonSquared, illegal);
    }

    /** Shows every seat the events it may see, and returns the turn that now needs an answer, if any. */
    private static ActionRequested show(List<GameEvent> events, TableObserver[] observers, long handNo) {
        ActionRequested turn = null;
        for (GameEvent event : events) {
            if (event instanceof ActionRequested requested) {
                turn = requested;
                continue;
            }
            for (int seat = 0; seat < observers.length; seat++) {
                int viewer = seat;
                SeatFeed.messageFor(event, seat, handNo).ifPresent(message -> observers[viewer].accept(message));
            }
        }
        return turn;
    }

    private static PlayerAction toAction(Decision decision) {
        return switch (decision.type()) {
            case FOLD -> new Fold();
            case CHECK -> new Check();
            case CALL -> new Call();
            case BET -> new Bet(decision.amount());
            default -> new Raise(decision.amount());
        };
    }
}
