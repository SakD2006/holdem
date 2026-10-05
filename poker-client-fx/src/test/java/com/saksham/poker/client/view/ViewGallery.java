package com.saksham.poker.client.view;

import com.saksham.poker.client.app.AppConfig;
import com.saksham.poker.client.app.ClientContext;
import com.saksham.poker.client.app.RoomSession;
import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.app.SessionStore;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.common.action.ActionType;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.dto.PayoutInfo;
import com.saksham.poker.common.protocol.dto.PotInfo;
import com.saksham.poker.common.protocol.dto.ShownHandInfo;
import com.saksham.poker.common.protocol.server.ActionRequired;
import com.saksham.poker.common.protocol.server.BlindPosted;
import com.saksham.poker.common.protocol.server.HandEnded;
import com.saksham.poker.common.protocol.server.HandStarted;
import com.saksham.poker.common.protocol.server.HoleCards;
import com.saksham.poker.common.protocol.server.PlayerActed;
import com.saksham.poker.common.protocol.server.PotsUpdated;
import com.saksham.poker.common.protocol.server.SeatUpdate;
import com.saksham.poker.common.protocol.server.Showdown;
import com.saksham.poker.common.protocol.server.StreetDealt;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/**
 * Draws each screen with made-up data and saves it as a picture, so the look of the app can be
 * checked without a server or anyone clicking through it. Run it with the test classpath; the
 * pictures go to the folder given as the first argument (default {@code target/gallery}).
 */
public final class ViewGallery {

    private static final RoomSettingsInfo SETTINGS = new RoomSettingsInfo("Friday game", 6, 50, 100, 10_000, 25, true);

    private ViewGallery() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/gallery");
        Files.createDirectories(out);
        Path home = Files.createTempDirectory("holdem-gallery");
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.startup(() -> {
            try {
                draw(out, home);
            } catch (Throwable e) {
                failure[0] = e;
            } finally {
                done.countDown();
            }
        });
        done.await();
        if (args.length > 1 && failure[0] == null) {
            liveScreens(out, home, args[1], failure);
        }
        Platform.exit();
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
        System.out.println("Pictures written to " + out.toAbsolutePath());
        System.exit(0);
    }

    /**
     * The screens that fetch their contents: drawn against a real server, logged in as one of the
     * bots, and given a moment to load before the picture is taken.
     */
    private static void liveScreens(Path out, Path home, String server, Throwable[] failure) throws Exception {
        ClientContext context = new ClientContext(AppConfig.load(home), new SessionStore(home));
        CountDownLatch built = new CountDownLatch(1);
        Parent[] screens = new Parent[2];
        var api = new com.saksham.poker.client.net.ApiClient(ServerAddress.parse(server));
        AuthResponse login = api.login("bot_2", "bot-test-password").join();
        Platform.runLater(() -> {
            context.useServer(ServerAddress.parse(server));
            context.loggedIn(login, false);
            SceneRouter router = new SceneRouter(new Stage(), context);
            screens[0] = new HistoryView(router);
            screens[1] = new LeaderboardView(router);
            built.countDown();
        });
        built.await();
        Thread.sleep(2_000);
        CountDownLatch saved = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                save(out, "9-history", screens[0]);
                save(out, "9-leaderboard", screens[1]);
            } catch (Throwable e) {
                failure[0] = e;
            } finally {
                saved.countDown();
            }
        });
        saved.await();
    }

    private static void draw(Path out, Path home) throws Exception {
        ClientContext context = new ClientContext(AppConfig.load(home), new SessionStore(home));
        SceneRouter router = new SceneRouter(new Stage(), context);

        save(out, "1-connect", new ConnectView(router, null));
        save(out, "1-connect-error", new ConnectView(router, "Could not reach a server at 192.168.1.20. Check "
                + "the address, that the server is running, and that you are on the same network."));

        context.useServer(ServerAddress.parse("192.168.1.20"));
        save(out, "2-login", new LoginView(router, null));

        context.loggedIn(new AuthResponse("t".repeat(64), new UserInfo(1, "asha")), false);
        save(out, "3-home", new HomeView(router, null));
        save(out, "3-home-notice", new HomeView(router, "The room has closed."));

        save(out, "4-create-room", new CreateRoomDialog("asha").getDialogPane());
        save(out, "5-join-room", new JoinRoomDialog(context).getDialogPane());

        // The waiting room as the host, with three seated, one standing and one offline.
        RoomSession hosting = new RoomSession(context, "ABC234", () -> { }, reason -> { });
        hosting.state().apply(new RoomSnapshot("ABC234", SETTINGS, RoomState.WAITING, 1, List.of(
                new PlayerInfo(1, "asha", 0, 10_000, false, true),
                new PlayerInfo(2, "ravi", 1, 10_000, false, true),
                new PlayerInfo(3, "meera", 4, 10_000, false, false),
                new PlayerInfo(4, "dev", PlayerInfo.NO_SEAT, 0, false, true)), null, 1, 0, List.of()));
        save(out, "6-waiting-host", new WaitingRoomView(hosting));

        // The same room as a guest who has not sat down, just after picking a taken seat.
        RoomSession guest = new RoomSession(context, "ABC234", () -> { }, reason -> { });
        guest.state().apply(new RoomSnapshot("ABC234", SETTINGS, RoomState.WAITING, 2, List.of(
                new PlayerInfo(1, "asha", PlayerInfo.NO_SEAT, 0, false, true),
                new PlayerInfo(2, "ravi", 1, 10_000, false, true)), null, 1, PlayerInfo.NO_SEAT, List.of()));
        WaitingRoomView guestView = new WaitingRoomView(guest);
        guest.state().apply(new ErrorMessage(ErrorCode.SEAT_TAKEN, "Seat 1 is taken. Choose another seat."));
        save(out, "6-waiting-guest", guestView);

        TableSurface.animate = false;
        table(out, context);
    }

    private static final RoomSettingsInfo NINE = new RoomSettingsInfo("Friday game", 9, 50, 100, 10_000, 25, true);

    /** Five players at a six-seat table, with you (asha) in seat 0. Hand 12 has just been dealt. */
    private static RoomSession dealt(ClientContext context) {
        return deal(new RoomSession(context, "ABC234", () -> { }, reason -> { }));
    }

    private static RoomSession deal(RoomSession session) {
        var state = session.state();
        state.apply(new RoomSnapshot("ABC234", SETTINGS, RoomState.PLAYING, 1, List.of(
                new PlayerInfo(1, "asha", 0, 10_000, false, true),
                new PlayerInfo(2, "ravi", 1, 8_450, false, true),
                new PlayerInfo(3, "meera", 2, 12_300, false, true),
                new PlayerInfo(4, "dev", 4, 3_200, false, true),
                new PlayerInfo(5, "a_long_username_of_24_ch", 5, 15_050, false, false)), null, 1, 0, List.of()));
        state.apply(new HandStarted(12, 5, 0, 1, 50, 100,
                Map.of(0, 10_000L, 1, 8_450L, 2, 12_300L, 4, 3_200L, 5, 15_050L)));
        state.apply(new BlindPosted(0, 50, false, false));
        state.apply(new BlindPosted(1, 100, true, false));
        state.apply(new HoleCards(0, Card.parseAll("Ah Kd")));
        state.apply(new PlayerActed(2, ActionType.RAISE, 300, 300, 12_000, false));
        state.apply(new PlayerActed(4, ActionType.CALL, 300, 300, 2_900, false));
        state.apply(new PlayerActed(5, ActionType.FOLD, 0, 0, 15_050, false));
        return session;
    }

    private static void table(Path out, ClientContext context) throws Exception {
        // Your turn before the flop, facing a raise.
        RoomSession turn = dealt(context);
        turn.state().apply(new ActionRequired(0, 31, false, 250, false, true, 500, 10_000, 0));
        save(out, "7-table-your-turn", new TableView(turn));

        // The flop: you bet, ravi folded, meera is thinking, dev is all-in.
        RoomSession flop = dealt(context);
        var state = flop.state();
        state.apply(new PlayerActed(0, ActionType.CALL, 250, 300, 9_700, false));
        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 8_350, false));
        state.apply(new PotsUpdated(List.of(new PotInfo(1_000, List.of(0, 2, 4)))));
        state.apply(new StreetDealt("FLOP", Card.parseAll("As 7h 2c"), Card.parseAll("As 7h 2c")));
        state.apply(new PlayerActed(0, ActionType.BET, 600, 600, 9_100, false));
        state.apply(new PlayerActed(4, ActionType.RAISE, 2_900, 2_900, 0, true));
        state.apply(new ActionRequired(2, 35, false, 2_900, false, true, 5_200, 12_000, 0));
        save(out, "7-table-flop", new TableView(flop));

        // Showdown: you win the main pot with two pair, dev shows a pair.
        RoomSession showdown = dealt(context);
        state = showdown.state();
        state.apply(new PlayerActed(0, ActionType.CALL, 250, 300, 9_700, false));
        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 8_350, false));
        state.apply(new PotsUpdated(List.of(new PotInfo(1_000, List.of(0, 2, 4)))));
        state.apply(new StreetDealt("RIVER", Card.parseAll("Kc"), Card.parseAll("As 7h 2c 9d Kc")));
        state.apply(new PotsUpdated(List.of(new PotInfo(9_700, List.of(0, 2, 4)), new PotInfo(1_200, List.of(0, 2)))));
        state.apply(new Showdown(List.of(new ShownHandInfo(0, Card.parseAll("Ah Kd"), "TWO_PAIR"),
                new ShownHandInfo(2, Card.parseAll("Qs Qh"), "PAIR"),
                new ShownHandInfo(4, Card.parseAll("7s 8s"), "PAIR"))));
        state.apply(new HandEnded(List.of(new PayoutInfo(0, 0, 9_700), new PayoutInfo(1, 0, 1_200)),
                Map.of(0, 7_100L, 1, -100L, 2, -3_800L, 4, -3_200L, 5, 0L),
                Map.of(0, 17_100L, 1, 8_350L, 2, 8_500L, 4, 0L, 5, 15_050L)));
        state.apply(new SeatUpdate(new PlayerInfo(4, "dev", 4, 0, true, true)));
        save(out, "7-table-showdown", new TableView(showdown));

        // A full nine-seat table, to check nothing overlaps, with you out of chips.
        RoomSession nine = new RoomSession(context, "ABC234", () -> { }, reason -> { });
        List<PlayerInfo> players = new java.util.ArrayList<>();
        java.util.Map<Integer, Long> stacks = new java.util.TreeMap<>();
        for (int seat = 0; seat < 9; seat++) {
            boolean you = seat == 3;
            players.add(new PlayerInfo(seat + 1, you ? "asha" : "player_" + (seat + 1), seat, you ? 0 : 10_000, you, true));
            if (!you) {
                stacks.put(seat, 10_000L);
            }
        }
        nine.state().apply(new RoomSnapshot("ABC234", NINE, RoomState.PLAYING, 1, players, null, 4, 3, List.of()));
        nine.state().apply(new HandStarted(40, 0, 1, 2, 50, 100, stacks));
        nine.state().apply(new BlindPosted(1, 50, false, false));
        nine.state().apply(new BlindPosted(2, 100, true, false));
        for (int seat = 4; seat < 9; seat++) {
            nine.state().apply(new PlayerActed(seat, ActionType.CALL, 100, 100, 9_900, false));
        }
        nine.state().apply(new ActionRequired(0, 90, false, 100, false, true, 200, 10_000, 0));
        save(out, "7-table-nine-broke", new TableView(nine));

        // The same screen kept open while a game happens to it: built before any message arrives,
        // then fed a hand, its ending, the next hand, a reconnect and a pause. Any listener that
        // cannot cope with a change made after the screen exists fails here.
        RoomSession live = new RoomSession(context, "ABC234", () -> { }, reason -> { });
        TableView liveView = new TableView(live);
        deal(live);
        state = live.state();
        state.apply(new ActionRequired(0, 31, false, 250, false, true, 500, 10_000, 0));
        state.apply(new PlayerActed(0, ActionType.RAISE, 850, 900, 9_100, false));
        state.apply(new PlayerActed(1, ActionType.FOLD, 0, 100, 8_350, false));
        state.apply(new PlayerActed(2, ActionType.CALL, 600, 900, 11_400, false));
        state.apply(new PlayerActed(4, ActionType.FOLD, 0, 300, 2_900, false));
        state.apply(new PotsUpdated(List.of(new PotInfo(2_200, List.of(0, 2)))));
        state.apply(new StreetDealt("FLOP", Card.parseAll("As 7h 2c"), Card.parseAll("As 7h 2c")));
        state.apply(new ActionRequired(0, 32, true, 0, true, false, 100, 9_100, 0));
        state.apply(new PlayerActed(0, ActionType.BET, 9_100, 9_100, 0, true));
        state.apply(new PlayerActed(2, ActionType.FOLD, 0, 0, 11_400, false));
        state.apply(new com.saksham.poker.common.protocol.server.BetReturned(0, 9_100));
        state.apply(new HandEnded(List.of(new PayoutInfo(0, 0, 2_200)),
                Map.of(0, 1_300L, 1, -100L, 2, -900L, 4, -300L, 5, 0L),
                Map.of(0, 11_300L, 1, 8_350L, 2, 11_400L, 4, 2_900L, 5, 15_050L)));
        state.apply(new com.saksham.poker.common.protocol.server.PlayerLeft(4,
                com.saksham.poker.common.protocol.dto.LeaveReason.LEFT));
        state.apply(new HandStarted(13, 0, 1, 2, 50, 100, Map.of(0, 11_300L, 1, 8_350L, 2, 11_400L)));
        state.apply(new BlindPosted(1, 50, false, false));
        state.apply(new BlindPosted(2, 100, true, false));
        state.apply(new HoleCards(0, Card.parseAll("9s 9d")));
        deal(live); // a reconnect: a fresh snapshot replaces everything, mid-hand
        state.apply(new ActionRequired(0, 40, false, 250, false, true, 500, 10_000, 0));
        state.apply(new com.saksham.poker.common.protocol.server.GameState(RoomState.PAUSED));
        state.apply(new ErrorMessage(ErrorCode.INVALID_AMOUNT, "A raise must be from 500 to 10000, but was 20."));
        save(out, "7-table-live", liveView);
        state.apply(new com.saksham.poker.common.protocol.server.ChatPosted(2, "ravi", "nice hand"));
        state.apply(new com.saksham.poker.common.protocol.server.ChatPosted(1, "asha",
                "thanks - that river card was exactly what I needed, I was sure you had the flush"));
        live.reconnectingProperty().set(true);
        save(out, "8-reconnecting", new StackPane(liveView, new ReconnectingOverlay(live)));
    }

    /** Lays a screen out at the app's window size and writes it as a PNG. */
    private static void save(Path folder, String name, Parent screen) throws Exception {
        Scene scene = new Scene(new StackPane(screen), 1100, 720);
        scene.getStylesheets().add(ViewGallery.class.getResource("/holdem.css").toExternalForm());
        WritableImage image = scene.snapshot(null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", folder.resolve(name + ".png").toFile());
    }
}
