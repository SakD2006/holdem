package com.saksham.poker.bot;

import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.exception.ProtocolException;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.Envelope;
import com.saksham.poker.common.protocol.MessageCodec;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.Ping;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.GameState;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One headless player. It joins a room like a person would, takes a seat, answers every turn with a
 * random legal action and rebuys when it goes broke.
 *
 * <p>It also checks what the server sends it, and records a problem whenever something arrives that
 * never should: another player's hole cards, a skipped message, a hand whose chips do not add up, a
 * stack that changed between hands, or an error it did not provoke.
 *
 * <p>Messages for one connection arrive one at a time, so the fields used only while handling a
 * message need no locking; the few read by the main thread are volatile or atomic.
 */
final class Bot implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(Bot.class);

    private final String name;
    private final long userId;
    private final int preferredSeat;
    private final Queue<String> problems;
    private final MessageCodec codec = new MessageCodec();
    private final Random random;
    private final StringBuilder partial = new StringBuilder();
    private final Object sendLock = new Object();

    private WebSocket socket;
    private CompletableFuture<?> lastSend = CompletableFuture.completedFuture(null);
    private long sentSeq;
    private long receivedSeq;

    // ---- what the bot knows about the room
    private int maxPlayers = 9;
    private boolean rebuyAllowed;
    private final Set<Integer> triedSeats = new HashSet<>();
    private volatile int seat = PlayerInfo.NO_SEAT;
    /** The bot's chips as of the end of its last hand, or -1 before it has any. */
    private long stackBetweenHands = -1;
    /** True from being dealt into a hand until that hand ends. */
    private boolean inHand;

    private final AtomicInteger handsEnded = new AtomicInteger();
    private volatile boolean roomClosed;
    private volatile boolean connectionLost;
    /** The run is over: stop answering turns, and expect the room to vanish. */
    private volatile boolean stopping;

    Bot(String name, long userId, int preferredSeat, Queue<String> problems) {
        this.name = name;
        this.userId = userId;
        this.preferredSeat = preferredSeat;
        this.problems = problems;
        this.random = new Random(userId * 31 + System.nanoTime());
    }

    // ---- used by the coordinator

    /** Opens the game connection and asks to join the room. */
    void connect(HttpClient http, String url, String roomCode) {
        socket = http.newWebSocketBuilder().buildAsync(URI.create(url), this).join();
        send(new JoinRoom(roomCode));
    }

    String name() {
        return name;
    }

    boolean seated() {
        return seat != PlayerInfo.NO_SEAT;
    }

    int handsEnded() {
        return handsEnded.get();
    }

    boolean finished() {
        return roomClosed || connectionLost || stopping;
    }

    void ping() {
        send(new Ping());
    }

    /** Tells the bot the run is ending, so the room closing under it is not a surprise. */
    void stop() {
        stopping = true;
    }

    /** Queues a message. Sends are chained, because a WebSocket allows only one at a time. */
    void send(ClientMessage message) {
        synchronized (sendLock) {
            String text;
            try {
                text = codec.encode(message, ++sentSeq);
            } catch (ProtocolException e) {
                problem("could not encode " + message.type() + ": " + e.getMessage());
                return;
            }
            lastSend = lastSend.thenCompose(done -> socket.sendText(text, true)).exceptionally(error -> {
                if (!finished()) {
                    problem("could not send " + message.type() + ": " + error);
                }
                return null;
            });
        }
    }

    void close() {
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").exceptionally(error -> null);
        }
    }

    // ---- the connection

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            String text = partial.toString();
            partial.setLength(0);
            try {
                receive(text);
            } catch (RuntimeException e) {
                problem("failed while handling a message: " + e);
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connectionLost = true;
        if (!roomClosed && !stopping && statusCode != WebSocket.NORMAL_CLOSURE) {
            problem("the connection closed with code " + statusCode + " (" + reason + ")");
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connectionLost = true;
        if (!roomClosed && !stopping) {
            problem("the connection failed: " + error);
        }
    }

    private void receive(String text) {
        Envelope<ServerMessage> envelope;
        try {
            envelope = codec.decodeServer(text);
        } catch (ProtocolException e) {
            problem("received a message it could not read: " + e.getMessage());
            return;
        }
        if (envelope.seq() != receivedSeq + 1) {
            problem("missed messages: got number " + envelope.seq() + " after " + receivedSeq);
        }
        receivedSeq = envelope.seq();
        handle(envelope.message());
    }

    // ---- reacting to the room

    private void handle(ServerMessage message) {
        if (message instanceof RoomSnapshot snapshot) {
            onSnapshot(snapshot);
        } else if (message instanceof SeatUpdate update) {
            if (update.player().userId() == userId) {
                onOwnSeat(update.player());
            }
        } else if (message instanceof HoleCards cards) {
            if (cards.seat() != seat) {
                problem("was sent the hole cards of seat " + cards.seat());
            }
        } else if (message instanceof HandStarted started) {
            onHandStarted(started);
        } else if (message instanceof ActionRequired turn) {
            if (turn.seat() == seat && !stopping) {
                act(turn);
            }
        } else if (message instanceof HandEnded ended) {
            onHandEnded(ended);
        } else if (message instanceof GameState state) {
            roomClosed = state.state() == RoomState.CLOSED;
        } else if (message instanceof ErrorMessage error) {
            onError(error);
        }
    }

    private void onSnapshot(RoomSnapshot snapshot) {
        maxPlayers = snapshot.settings().maxPlayers();
        rebuyAllowed = snapshot.settings().rebuyAllowed();
        roomClosed = snapshot.state() == RoomState.CLOSED;
        if (snapshot.yourSeat() != PlayerInfo.NO_SEAT) {
            seat = snapshot.yourSeat();
            return;
        }
        for (PlayerInfo player : snapshot.players()) {
            if (player.seated()) {
                triedSeats.add(player.seat());
            }
        }
        takeNextSeat();
    }

    /** Asks for the preferred seat, or the next one round the table that has not been tried. */
    private void takeNextSeat() {
        for (int i = 0; i < maxPlayers; i++) {
            int candidate = (preferredSeat + i) % maxPlayers;
            if (triedSeats.add(candidate)) {
                send(new TakeSeat(candidate));
                return;
            }
        }
        problem("found no free seat in the room");
    }

    private void onOwnSeat(PlayerInfo me) {
        seat = me.seat();
        if (!inHand) {
            // Sitting down or rebuying changes the stack between hands; during a hand the figure
            // in a seat update is the live one, which HAND_ENDED will settle.
            stackBetweenHands = me.stack();
        }
    }

    private void onHandStarted(HandStarted started) {
        Long mine = started.stacks().get(seat);
        if (mine == null) {
            return; // not dealt in: sitting out, or waiting for the big blind
        }
        if (stackBetweenHands >= 0 && mine != stackBetweenHands) {
            problem("started hand " + started.handNo() + " with " + mine + " chips but should have had "
                    + stackBetweenHands);
        }
        inHand = true;
    }

    private void onHandEnded(HandEnded ended) {
        long net = 0;
        for (long each : ended.netBySeat().values()) {
            net += each;
        }
        if (net != 0) {
            problem("a hand ended with chips not adding up: the wins and losses total " + net);
        }
        Long mine = ended.stacks().get(seat);
        if (inHand && mine != null) {
            stackBetweenHands = mine;
            if (mine == 0 && rebuyAllowed && !stopping) {
                // Broke: buy back in. The room settles the hand before it reads this request.
                send(new Rebuy());
            }
        }
        inHand = false;
        handsEnded.incrementAndGet();
    }

    private void onError(ErrorMessage error) {
        if (error.code() == ErrorCode.SEAT_TAKEN && !seated()) {
            takeNextSeat(); // another bot got there first
        } else if (!stopping) {
            problem("was refused: " + error.code() + " - " + error.message());
        }
    }

    /** Mostly checks and calls, sometimes folds, bets or raises the minimum, occasionally shoves. */
    private void act(ActionRequired turn) {
        boolean canBetOrRaise = turn.canBet() || turn.canRaise();
        ActionType action;
        long amount = 0;
        int roll = random.nextInt(100);
        if (roll < 12) {
            action = turn.canCheck() ? ActionType.CHECK : ActionType.FOLD;
        } else if (roll < 75 || !canBetOrRaise) {
            action = turn.canCheck() ? ActionType.CHECK : ActionType.CALL;
        } else if (roll < 95) {
            action = turn.canBet() ? ActionType.BET : ActionType.RAISE;
            amount = turn.minRaiseTo();
        } else {
            action = ActionType.ALL_IN;
        }
        send(new SubmitAction(turn.turnId(), action, amount));
    }

    private void problem(String what) {
        log.warn("{} {}", name, what);
        problems.add(name + " " + what);
    }
}
