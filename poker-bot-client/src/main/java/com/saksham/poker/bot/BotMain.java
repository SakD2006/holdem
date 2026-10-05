package com.saksham.poker.bot;

import com.saksham.poker.bot.ApiClient.ApiException;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a table of bots against a real server, over the same HTTP API and WebSocket a person's
 * desktop app uses. With no {@code --room} the bots create a room, play the hands asked for and
 * close it; with {@code --room} they join a room someone else is hosting and play there.
 *
 * <p>The exit code is 0 if the bots played without noticing a problem, 1 if they noticed any, and 2
 * if the run could not start.
 */
public final class BotMain {

    private static final Logger log = LoggerFactory.getLogger(BotMain.class);

    /** Test accounts for the bots, created on first use. They only ever hold play chips. */
    private static final String PASSWORD = "bot-test-password";
    private static final int SEATING_TIMEOUT_SECONDS = 15;
    private static final int PING_SECONDS = 15;

    private BotMain() {
    }

    public static void main(String[] args) {
        BotOptions options;
        try {
            options = BotOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(BotOptions.USAGE);
            System.exit(2);
            return;
        }
        int exit;
        try {
            exit = run(options);
        } catch (ApiException | IOException e) {
            log.error("Could not start the bots: {}", e.getMessage());
            exit = 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exit = 2;
        } catch (RuntimeException e) {
            log.error("The bot run failed", e);
            exit = 2;
        }
        System.exit(exit);
    }

    static int run(BotOptions options) throws ApiException, IOException, InterruptedException {
        ApiClient api = new ApiClient(options.httpBase());
        Queue<String> problems = new ConcurrentLinkedQueue<>();
        List<Bot> bots = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < options.bots(); i++) {
            String name = "bot_" + (i + 1);
            AuthResponse login = api.registerOrLogin(name, PASSWORD);
            bots.add(new Bot(name, login.user().id(), i, problems));
            tokens.add(login.token());
        }

        boolean ownRoom = options.room() == null;
        String code = options.room();
        if (ownRoom) {
            code = api.createRoom(tokens.get(0), new RoomSettingsInfo(
                    "Bot table", Math.max(2, options.bots()), 50, 100, 10_000, 10, true));
            log.info("Created room {}", code);
        }

        ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bot-ping");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (int i = 0; i < bots.size(); i++) {
                bots.get(i).connect(api.http(), options.webSocketUrl(tokens.get(i)), code);
            }
            pinger.scheduleAtFixedRate(() -> bots.forEach(Bot::ping), PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);

            if (!awaitSeated(bots)) {
                problems.add("not every bot found a seat within " + SEATING_TIMEOUT_SECONDS + " seconds");
            } else {
                log.info("{} bots seated in room {}", bots.size(), code);
                if (ownRoom) {
                    bots.get(0).send(new StartGame());
                }
                play(bots, options, problems);
            }
            bots.forEach(Bot::stop);
            if (ownRoom) {
                bots.get(0).send(new EndRoom());
                Thread.sleep(300);
            }
        } finally {
            bots.forEach(Bot::close);
            pinger.shutdownNow();
        }

        int hands = bots.get(0).handsEnded();
        if (problems.isEmpty()) {
            log.info("Finished: {} bots played {} hands in room {} with no problems", bots.size(), hands, code);
            return 0;
        }
        log.error("Finished: {} bots played {} hands in room {} and noticed {} problem(s):", bots.size(), hands,
                code, problems.size());
        problems.stream().limit(20).forEach(problem -> log.error("  {}", problem));
        return 1;
    }

    private static boolean awaitSeated(List<Bot> bots) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SEATING_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (bots.stream().allMatch(Bot::seated)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    /** Waits while the bots play, until enough hands are done, the room closes or play stalls. */
    private static void play(List<Bot> bots, BotOptions options, Queue<String> problems)
            throws InterruptedException {
        Bot witness = bots.get(0);
        int lastSeen = 0;
        long lastProgress = System.nanoTime();
        int nextReport = 100;
        while (true) {
            int hands = witness.handsEnded();
            if (options.hands() > 0 && hands >= options.hands()) {
                return;
            }
            if (bots.stream().anyMatch(Bot::finished)) {
                if (options.hands() > 0) {
                    problems.add("the room closed or a connection dropped after " + hands + " of "
                            + options.hands() + " hands");
                }
                return;
            }
            if (hands != lastSeen) {
                lastSeen = hands;
                lastProgress = System.nanoTime();
                if (hands >= nextReport) {
                    log.info("{} hands played", hands);
                    nextReport += 100;
                }
            } else if (System.nanoTime() - lastProgress > TimeUnit.SECONDS.toNanos(options.idleSeconds())) {
                problems.add("no hand finished for " + options.idleSeconds() + " seconds after " + hands + " hands");
                return;
            }
            Thread.sleep(50);
        }
    }
}
