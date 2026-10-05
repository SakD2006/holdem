package com.saksham.poker.client.view;

import com.saksham.poker.client.app.AppConfig;
import com.saksham.poker.client.app.ClientContext;
import com.saksham.poker.client.app.RoomSession;
import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.app.SessionStore;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import com.saksham.poker.common.protocol.dto.RoomState;
import com.saksham.poker.common.protocol.server.ErrorMessage;
import com.saksham.poker.common.protocol.server.RoomSnapshot;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        Platform.exit();
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
        System.out.println("Pictures written to " + out.toAbsolutePath());
        System.exit(0);
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
