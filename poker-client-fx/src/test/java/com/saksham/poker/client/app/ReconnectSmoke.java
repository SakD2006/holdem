package com.saksham.poker.client.app;

import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.SendChat;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Rehearses losing the connection, with the real {@link RoomSession} on the real JavaFX thread and
 * a real server. It joins a room, cuts its own connection twice, and checks that each time the
 * session notices, gets back in by itself and still has its seat. Usage: {@code ReconnectSmoke
 * <server>}; exits 0 only if everything went as it should.
 */
public final class ReconnectSmoke {

    private static RoomSession session;
    private static volatile String endedBecause;
    private static int failures;

    private ReconnectSmoke() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable e) {
            // The JavaFX thread would keep the program alive after a failure, so leave explicitly.
            System.out.println("FAIL " + e);
            if (endedBecause != null) {
                System.out.println("     the session ended: " + endedBecause);
            }
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        ServerAddress server = ServerAddress.parse(args.length > 0 ? args[0] : "127.0.0.1");
        Platform.startup(() -> { });
        ClientContext context = new ClientContext(
                AppConfig.defaults(Files.createTempDirectory("holdem-reconnect")),
                new SessionStore(Files.createTempDirectory("holdem-reconnect")));
        onFx(() -> {
            context.useServer(server);
            return null;
        });
        ApiClient api = context.api();
        AuthResponse login;
        try {
            login = api.register("smoke_host", "smoke-test-password").join();
        } catch (CompletionException e) {
            if (ApiClient.reason(e).code() != ErrorCode.USERNAME_TAKEN) {
                throw e;
            }
            login = api.login("smoke_host", "smoke-test-password").join();
        }
        AuthResponse loggedIn = login;
        String code = api.createRoom(login.token(),
                new RoomSettingsInfo("Reconnect table", 4, 50, 100, 10_000, 10, true)).join();

        CompletableFuture<Void> entered = new CompletableFuture<>();
        onFx(() -> {
            context.loggedIn(loggedIn, false);
            session = new RoomSession(context, code, () -> entered.complete(null), reason -> endedBecause = reason);
            session.start();
            return null;
        });
        entered.get(10, TimeUnit.SECONDS);
        check("entered the room", true);
        onFx(() -> {
            session.send(new TakeSeat(2));
            return null;
        });
        await("our seat", () -> onFx(() -> session.state().yourSeatProperty().get() == 2));

        for (int round = 1; round <= 2; round++) {
            onFx(() -> {
                session.dropConnection();
                return null;
            });
            await("the session to notice drop " + round, () -> onFx(() -> session.reconnectingProperty().get()));
            long started = System.currentTimeMillis();
            await("the session to get back in", () -> onFx(() -> !session.reconnectingProperty().get()));
            long took = System.currentTimeMillis() - started;
            check("it retried after about two seconds (took " + took + " ms)", took >= 1_500 && took <= 6_000);
            check("the seat was kept", onFx(() -> session.state().yourSeatProperty().get() == 2));
            check("we are still the host", onFx(() -> session.state().youAreHost()));
            check("the session did not end", endedBecause == null);
        }

        // The new connection works both ways.
        onFx(() -> {
            session.send(new SendChat("back again"));
            return null;
        });
        await("our chat to come back", () -> onFx(() -> session.state().chat().size() == 1));
        onFx(() -> {
            session.send(new EndRoom());
            return null;
        });
        await("the room to close", () -> endedBecause != null);
        check("ending was explained: " + endedBecause, "The room has closed.".equals(endedBecause));

        System.out.println(failures == 0 ? "OK: reconnected twice with no problems" : failures + " PROBLEM(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Runs something on the JavaFX thread and waits for its answer. */
    private static <T> T onFx(Supplier<T> work) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result.join();
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        failures += ok ? 0 : 1;
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                System.out.println("FAIL timed out waiting for " + what
                        + (endedBecause == null ? "" : " (the session ended: " + endedBecause + ")"));
                System.exit(1);
            }
            Thread.sleep(25);
        }
        System.out.println("ok   " + what);
    }
}
