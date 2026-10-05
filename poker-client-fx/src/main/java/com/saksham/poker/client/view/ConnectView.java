package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.net.ServerAddress;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** The first screen: where is the server? */
public final class ConnectView extends StackPane {

    private final SceneRouter router;
    private final TextField address = new TextField();
    private final Label message = Ui.message();
    private final Button test;
    private final Button next;

    public ConnectView(SceneRouter router, String notice) {
        this.router = router;
        getStyleClass().add("screen");

        String last = router.context().config().lastServer();
        address.setText(last.isEmpty() ? "127.0.0.1" : last);
        address.setPromptText("192.168.1.20");
        address.setOnAction(event -> connect(true));

        test = Ui.button("Test connection", () -> connect(false));
        next = Ui.button("Continue", () -> connect(true), "primary");
        HBox buttons = new HBox(10, test, next);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(16,
                Ui.label("Hold'em", "title"),
                Ui.label("Type the address of the computer running the game server. The host can read it "
                        + "from the server's start-up message.", "muted"),
                Ui.field("Server address", address),
                message,
                buttons);
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(460);
        panel.setMaxHeight(USE_PREF_SIZE);
        getChildren().add(panel);

        if (notice != null && !notice.isEmpty()) {
            Ui.showError(message, notice);
        }
    }

    /** Pings the server; if it answers, either says so or moves on to the login screen. */
    private void connect(boolean thenContinue) {
        ServerAddress server;
        try {
            server = ServerAddress.parse(address.getText());
        } catch (IllegalArgumentException e) {
            Ui.showError(message, e.getMessage());
            return;
        }
        busy(true);
        Ui.showGood(message, "Looking for a server at " + server.display() + "...");
        ApiClient api = new ApiClient(server);
        Ui.whenDone(api.ping(), ping -> {
            busy(false);
            if (thenContinue) {
                router.context().useServer(server);
                router.showLogin(null);
            } else {
                Ui.showGood(message, "Found " + ping.name() + " server, version " + ping.version() + ".");
            }
        }, failure -> {
            busy(false);
            Ui.showError(message, failure);
        });
    }

    private void busy(boolean busy) {
        test.setDisable(busy);
        next.setDisable(busy);
        address.setDisable(busy);
    }
}
