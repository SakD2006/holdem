package com.saksham.poker.client.view;

import com.saksham.poker.client.net.ApiClient;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Small building blocks the screens share, so they look and behave alike. */
final class Ui {

    private Ui() {
    }

    static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        label.setWrapText(true);
        return label;
    }

    static Button button(String text, Runnable action, String... styleClasses) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClasses);
        button.setOnAction(event -> action.run());
        return button;
    }

    /** A field with its name above it. */
    static VBox field(String name, Node input) {
        VBox box = new VBox(5, label(name, "field-label"), input);
        box.setFillWidth(true);
        return box;
    }

    /** A message area that takes no room while empty. */
    static Label message() {
        Label label = label("", "error");
        label.managedProperty().bind(label.textProperty().isNotEmpty());
        label.visibleProperty().bind(label.textProperty().isNotEmpty());
        return label;
    }

    static void showError(Label message, String text) {
        message.getStyleClass().setAll("label", "error");
        message.setText(text);
    }

    static void showGood(Label message, String text) {
        message.getStyleClass().setAll("label", "good");
        message.setText(text);
    }

    /**
     * Waits for a network call without freezing the window: the outcome is handed back on the JavaFX
     * thread, as either the value or a message fit to show the player.
     */
    static <T> void whenDone(CompletableFuture<T> call, Consumer<T> onSuccess, Consumer<String> onFailure) {
        call.whenComplete((value, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                onSuccess.accept(value);
            } else {
                onFailure.accept(ApiClient.reason(failure).getMessage());
            }
        }));
    }

    /** Gives a dialog the app's look. */
    static void style(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(Ui.class.getResource("/holdem.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("root");
    }
}
