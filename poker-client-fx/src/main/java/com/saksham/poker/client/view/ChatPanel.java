package com.saksham.poker.client.view;

import com.saksham.poker.client.state.ChatLine;
import com.saksham.poker.client.state.RoomState;
import com.saksham.poker.common.protocol.ClientMessage;
import com.saksham.poker.common.protocol.client.SendChat;
import java.util.function.Consumer;
import javafx.beans.InvalidationListener;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The room's chat: what has been said, and a box to say something. */
public final class ChatPanel extends VBox {

    private static final int MAX_LENGTH = 200;

    public ChatPanel(RoomState state, Consumer<ClientMessage> send) {
        setSpacing(8);

        ListView<ChatLine> lines = new ListView<>(state.chat());
        lines.setFocusTraversable(false);
        lines.setPlaceholder(Ui.label("Nobody has said anything yet.", "muted"));
        lines.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(ChatLine line, boolean empty) {
                super.updateItem(line, empty);
                setWrapText(true);
                setPrefWidth(0); // wrap to the list's width instead of growing sideways
                setText(empty || line == null ? null
                        : (line.userId() == state.yourUserIdProperty().get() ? "You" : line.username()) + ": "
                                + line.text());
            }
        });
        VBox.setVgrow(lines, Priority.ALWAYS);
        state.chat().addListener((InvalidationListener) observable ->
                lines.scrollTo(Math.max(0, state.chat().size() - 1)));

        TextField input = new TextField();
        input.setPromptText("Say something");
        input.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= MAX_LENGTH ? change : null));
        input.setOnAction(event -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                send.accept(new SendChat(text));
                input.clear();
            }
        });
        getChildren().addAll(Ui.label("Chat", "field-label"), lines, input);
    }
}
