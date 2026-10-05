package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.client.net.ServerDiscovery;
import java.util.List;
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
    /** The servers "Find server" turned up, when there was more than one to choose from. */
    private final VBox choices = new VBox(6);
    private final Button find;
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
        find = Ui.button("Find server", this::find);
        HBox buttons = new HBox(10, find, SeatNode.spacer(), test, next);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        choices.managedProperty().bind(choices.visibleProperty());
        choices.setVisible(false);

        VBox panel = new VBox(16,
                Ui.label("Hold'em", "title"),
                Ui.label("Press Find server to look for the game on your network, or type the address of the "
                        + "computer running it. The host can read the address from the server's start-up "
                        + "message.", "muted"),
                Ui.field("Server address", address),
                choices,
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

    /** Searches the local network and offers whatever answers. */
    private void find() {
        busy(true);
        choices.setVisible(false);
        Ui.showGood(message, "Looking for servers on your network...");
        Ui.whenDone(ServerDiscovery.find(), found -> {
            busy(false);
            offer(found);
        }, failure -> {
            busy(false);
            Ui.showError(message, failure);
        });
    }

    private void offer(List<ServerAddress> found) {
        if (found.isEmpty()) {
            Ui.showError(message, "No server answered. Check that the host has started the server and that "
                    + "this computer is on the same Wi-Fi or network, or type the host's address.");
            return;
        }
        address.setText(found.get(0).display());
        if (found.size() == 1) {
            Ui.showGood(message, "Found a server at " + found.get(0).display() + ". Press Continue.");
            next.requestFocus();
            return;
        }
        // Two games on one network: let the player say which, rather than guess.
        choices.getChildren().setAll(Ui.label("More than one server answered. Choose yours:", "field-label"));
        for (ServerAddress server : found) {
            choices.getChildren().add(Ui.button(server.display(), () -> {
                address.setText(server.display());
                connect(false);
            }, "small"));
        }
        choices.setVisible(true);
        Ui.showGood(message, "Found " + found.size() + " servers.");
    }

    private void busy(boolean busy) {
        find.setDisable(busy);
        test.setDisable(busy);
        next.setDisable(busy);
        address.setDisable(busy);
    }
}
