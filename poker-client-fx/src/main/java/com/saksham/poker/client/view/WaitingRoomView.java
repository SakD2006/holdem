package com.saksham.poker.client.view;

import com.saksham.poker.client.util.ErrorMessages;
import com.saksham.poker.client.app.RoomSession;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.protocol.client.AddBot;
import com.saksham.poker.common.protocol.client.EndRoom;
import com.saksham.poker.common.protocol.client.Kick;
import com.saksham.poker.common.protocol.client.StartGame;
import com.saksham.poker.common.protocol.client.TakeSeat;
import com.saksham.poker.common.protocol.dto.PlayerInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.util.StringJoiner;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The room before the game starts: the code to share, who is here, and the seats. Everything on it
 * is drawn from {@link RoomState}, so it redraws itself whenever a player joins, sits or leaves.
 */
public final class WaitingRoomView extends StackPane {

    private final RoomSession session;
    private final RoomState state;
    private final Label roomName = Ui.label("", "heading");
    private final Label settingsLine = Ui.label("", "muted");
    private final Label code = Ui.label("", "room-code");
    private final Button copy;
    private final FlowPane seatTiles = new FlowPane(12, 12);
    private final Label standing = Ui.label("", "muted");
    private final Label status = Ui.label("", "muted");
    private final Label message = Ui.message();
    private final MenuButton addBot;
    private final Button start;
    private final Button end;

    public WaitingRoomView(RoomSession session) {
        this.session = session;
        this.state = session.state();
        getStyleClass().add("screen");

        copy = Ui.button("Copy", this::copyCode, "small");
        HBox codeRow = new HBox(16, code, copy);
        codeRow.setAlignment(Pos.CENTER_LEFT);

        start = Ui.button("Start game", () -> session.send(new StartGame()), "primary");
        end = Ui.button("End room", () -> session.send(new EndRoom()), "danger");
        // The server seats the bot in the first free seat.
        addBot = Ui.addBotMenu(level -> session.send(new AddBot(level)));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox buttons = new HBox(10, Ui.button("Leave room", session::leave), gap, addBot, end, start);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(14,
                roomName,
                settingsLine,
                Ui.label("Room code - share it with the other players", "field-label"),
                codeRow,
                Ui.label("Seats", "field-label"),
                seatTiles,
                standing,
                message,
                status,
                buttons);
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(820);
        panel.setMaxHeight(USE_PREF_SIZE);
        getChildren().add(panel);

        // Any change to who is here, who is host or where you sit redraws the lot. It is a small
        // screen and these changes are rare, so there is no need to be cleverer.
        InvalidationListener redraw = observable -> draw();
        state.players().addListener(redraw);
        state.hostUserIdProperty().addListener(redraw);
        state.yourSeatProperty().addListener(redraw);
        state.settingsProperty().addListener(redraw);
        state.lastErrorProperty().addListener((property, was, error) -> {
            if (error != null) {
                Ui.showError(message, ErrorMessages.text(error.code(), error.message()));
            }
        });
        draw();
    }

    private void draw() {
        RoomSettingsInfo settings = state.settingsProperty().get();
        if (settings == null) {
            return;
        }
        roomName.setText(settings.name());
        settingsLine.setText(settings.maxPlayers() + " seats, blinds " + Formatters.chips(settings.smallBlind())
                + "/" + Formatters.chips(settings.bigBlind()) + ", starting stack "
                + Formatters.chips(settings.startingStack()) + ", " + settings.turnSeconds() + " seconds per turn, "
                + (settings.rebuyAllowed() ? "rebuys allowed" : "no rebuys"));
        code.setText(state.codeProperty().get());

        seatTiles.getChildren().clear();
        for (int seat = 0; seat < settings.maxPlayers(); seat++) {
            seatTiles.getChildren().add(seatTile(seat));
        }

        StringJoiner notSeated = new StringJoiner(", ");
        for (PlayerInfo player : state.players()) {
            if (!player.seated()) {
                notSeated.add(player.userId() == state.yourUserIdProperty().get() ? "you" : player.username());
            }
        }
        standing.setText(notSeated.length() == 0 ? "" : "Not seated yet: " + notSeated);

        boolean host = state.youAreHost();
        int seated = state.seatedCount();
        start.setVisible(host);
        start.setManaged(host);
        end.setVisible(host);
        end.setManaged(host);
        start.setDisable(seated < 2);
        addBot.setVisible(host);
        addBot.setManaged(host);
        addBot.setDisable(state.players().size() >= settings.maxPlayers());
        PlayerInfo hostPlayer = state.player(state.hostUserIdProperty().get());
        if (host) {
            status.setText(seated < 2
                    ? "You are the host. The game can start once 2 players are seated. To play alone, add a bot."
                    : "You are the host. Start the game when everyone is ready.");
        } else {
            status.setText("Waiting for " + (hostPlayer == null ? "the host" : hostPlayer.username())
                    + " to start the game." + (state.youAreSeated() ? "" : " Pick a seat to play."));
        }
    }

    private VBox seatTile(int seat) {
        PlayerInfo occupant = null;
        for (PlayerInfo player : state.players()) {
            if (player.seat() == seat) {
                occupant = player;
            }
        }
        VBox tile = new VBox(6, Ui.label(Formatters.seat(seat), "seat-number"));
        tile.getStyleClass().add("seat-tile");
        if (occupant == null) {
            tile.getChildren().add(Ui.label("Empty", "muted"));
            Region gap = new Region();
            VBox.setVgrow(gap, Priority.ALWAYS);
            tile.getChildren().addAll(gap,
                    Ui.button(state.youAreSeated() ? "Move here" : "Sit here",
                            () -> session.send(new TakeSeat(seat)), "small"));
            return tile;
        }
        boolean mine = occupant.userId() == state.yourUserIdProperty().get();
        tile.getStyleClass().add("taken");
        if (mine) {
            tile.getStyleClass().add("mine");
        }
        tile.getChildren().add(Ui.label(occupant.username(), "seat-name"));
        HBox badges = new HBox(6);
        if (mine) {
            badges.getChildren().add(Ui.label("You", "badge"));
        }
        if (occupant.userId() == state.hostUserIdProperty().get()) {
            badges.getChildren().add(Ui.label("Host", "badge", "quiet"));
        }
        if (occupant.bot()) {
            badges.getChildren().add(Ui.label("Bot", "badge", "quiet"));
        }
        if (!occupant.connected()) {
            badges.getChildren().add(Ui.label("Offline", "badge", "quiet"));
        }
        tile.getChildren().add(badges);
        if (state.youAreHost() && !mine) {
            long target = occupant.userId();
            Region gap = new Region();
            VBox.setVgrow(gap, Priority.ALWAYS);
            tile.getChildren().addAll(gap, Ui.button("Remove", () -> session.send(new Kick(target)), "small"));
        }
        return tile;
    }

    private void copyCode() {
        ClipboardContent content = new ClipboardContent();
        content.putString(state.codeProperty().get());
        Clipboard.getSystemClipboard().setContent(content);
        copy.setText("Copied");
    }
}
