package com.saksham.poker.client.net;

import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.RoomPreview;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.ServerMessage;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.JoinRoom;
import com.saksham.poker.common.protocol.client.Rebuy;
import com.saksham.poker.common.protocol.client.SitIn;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.client.SubmitAction;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.TurnInfo;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Exercises the app's network code and room state against a real server, with no window. It logs in,
 * creates a room, waits for other players to join by code (run the bots with {@code --room}), starts
 * the game as host, plays its own turns, drops its connection and comes back, then ends the room.
 *
 * <p>Usage: {@code ClientSmoke <server> <players to wait for> <hands before and after reconnecting>}.
 * It prints {@code ROOM <code>} once the room is ready to be joined, and exits 0 only if everything
 * went as it should.
 */
public final class ClientSmoke {

    /** Stands in for the JavaFX thread: everything that touches the state runs here, one at a time. */
    private static final ExecutorService UI = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "smoke-ui");
        thread.setDaemon(true);
        return thread;
    });

    private static final RoomState STATE = new RoomState();
    private static final AtomicInteger HANDS = new AtomicInteger();
    private static final AtomicInteger SNAPSHOTS = new AtomicInteger();
    private static final List<String> PROBLEMS = new CopyOnWriteArrayList<>();
    private static volatile GameSocket socket;
    private static volatile long answeredTurn = -1;

    private ClientSmoke() {
    }

    public static void main(String[] args) throws Exception {
        ServerAddress server = ServerAddress.parse(args.length > 0 ? args[0] : "127.0.0.1");
        int others = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int hands = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        ApiClient api = new ApiClient(server);

        System.out.println("ping: " + api.ping().join());
        AuthResponse login = registerOrLogin(api, "smoke_host", "smoke-test-password");
        String token = login.token();
        check("me() returns the logged-in user", api.me(token).join().equals(login.user()));

        String code = api.createRoom(token, new RoomSettingsInfo("Smoke table", 6, 50, 100, 10_000, 10, true)).join();
        RoomPreview preview = api.previewRoom(token, code.toLowerCase()).join();
        check("the preview shows a waiting room hosted by us", preview.hostUsername().equals("smoke_host")
                && preview.seatedPlayers() == 0 && preview.settings().name().equals("Smoke table"));

        connect(api, token, code);
        await("the first snapshot", () -> SNAPSHOTS.get() == 1);
        check("we are the host and not yet seated", ask(() -> STATE.youAreHost() && !STATE.youAreSeated()));
        socket.send(new TakeSeat(0));
        await("our seat", () -> ask(STATE::youAreSeated));
        check("the preview now counts our seat", api.previewRoom(token, code).join().seatedPlayers() == 1);

        System.out.println("ROOM " + code);
        await(others + " other players to sit down", () -> ask(() -> STATE.seatedCount() >= others + 1));
        socket.send(new StartGame());
        await(hands + " hands", () -> HANDS.get() >= hands);
        check("the room is playing", ask(() -> !STATE.waiting()));

        // Drop the connection in the middle of the game and come back, as a real app would.
        int before = HANDS.get();
        socket.close();
        Thread.sleep(300);
        connect(api, token, code);
        await("a snapshot after reconnecting", () -> SNAPSHOTS.get() == 2);
        check("we got our seat back", ask(() -> STATE.yourSeatProperty().get() == 0));
        check("the others are still there", ask(() -> STATE.seatedCount() == others + 1));
        // Time away may have got us sat out or broke; get back in the game either way.
        socket.send(new SitIn());
        await(hands + " more hands after reconnecting", () -> HANDS.get() >= before + hands);

        socket.send(new EndRoom());
        await("the room to close", () -> ask(() -> STATE.goneProperty().get()));
        check("leaving was explained", ask(() -> STATE.goneReasonProperty().get().equals("The room has closed.")));
        check("the hand log was kept", ask(() -> STATE.handLog().size() > hands));

        if (PROBLEMS.isEmpty()) {
            System.out.println("OK: played " + HANDS.get() + " hands, reconnected once, no problems");
            System.exit(0);
        }
        PROBLEMS.forEach(problem -> System.out.println("PROBLEM: " + problem));
        System.exit(1);
    }

    private static AuthResponse registerOrLogin(ApiClient api, String username, String password) {
        try {
            return api.register(username, password).join();
        } catch (CompletionException e) {
            if (ApiClient.reason(e).code() != ErrorCode.USERNAME_TAKEN) {
                throw e;
            }
            return api.login(username, password).join();
        }
    }

    private static void connect(ApiClient api, String token, String code) {
        GameSocket fresh = new GameSocket(UI, ClientSmoke::received,
                closeCode -> PROBLEMS.add("the connection closed unexpectedly with code " + closeCode));
        fresh.connect(api.http(), api.server().gameSocketUrl(token)).join();
        socket = fresh;
        fresh.send(new JoinRoom(code));
    }

    /** Runs on the stand-in UI thread, as the app's listeners run on the JavaFX thread. */
    private static void received(ServerMessage message) {
        STATE.apply(message);
        if (message instanceof RoomSnapshot) {
            SNAPSHOTS.incrementAndGet();
        } else if (message instanceof HandEnded ended) {
            HANDS.incrementAndGet();
            long net = ended.netBySeat().values().stream().mapToLong(Long::longValue).sum();
            if (net != 0) {
                PROBLEMS.add("a hand's wins and losses totalled " + net);
            }
            Long mine = ended.stacks().get(STATE.yourSeatProperty().get());
            if (mine != null && mine == 0) {
                socket.send(new Rebuy());
            }
        } else if (message instanceof ErrorMessage error && error.code() != ErrorCode.INVALID_REQUEST) {
            // INVALID_REQUEST is the harmless "nothing to sit in from" after reconnecting.
            PROBLEMS.add("refused: " + error.code() + " - " + error.message());
        }
        TurnInfo turn = STATE.turnProperty().get();
        if (STATE.yourTurn() && turn.turnId() != answeredTurn) {
            answeredTurn = turn.turnId();
            socket.send(new SubmitAction(turn.turnId(), turn.canCheck() ? ActionType.CHECK : ActionType.CALL, 0));
        }
    }

    /** Reads the state on the thread that owns it. */
    private static boolean ask(BooleanSupplier question) {
        try {
            return UI.submit(question::getAsBoolean).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) {
            PROBLEMS.add(what);
        }
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                System.out.println("PROBLEM: timed out waiting for " + what);
                PROBLEMS.forEach(problem -> System.out.println("PROBLEM: " + problem));
                System.exit(1);
            }
            Thread.sleep(20);
        }
        System.out.println("ok   " + what);
    }
}
