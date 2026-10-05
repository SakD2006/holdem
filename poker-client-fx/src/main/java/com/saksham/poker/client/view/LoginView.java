package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.common.api.AuthResponse;
import java.util.concurrent.CompletableFuture;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Log in, or create an account, on the chosen server. */
public final class LoginView extends StackPane {

    private final SceneRouter router;
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final CheckBox remember = new CheckBox("Remember me on this computer");
    private final Label message = Ui.message();
    private final Button login;
    private final Button register;

    public LoginView(SceneRouter router, String notice) {
        this.router = router;
        getStyleClass().add("screen");

        username.setText(router.context().config().lastUsername());
        username.setPromptText("3 to 24 letters, digits or underscores");
        password.setPromptText("At least 6 characters");
        password.setOnAction(event -> submit(false));
        remember.setSelected(true);

        login = Ui.button("Log in", () -> submit(false), "primary");
        register = Ui.button("Create account", () -> submit(true));
        Region gap = new Region();
        HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
        HBox buttons = new HBox(10, Ui.button("Use a different server", () -> router.showConnect(null), "link"),
                gap, register, login);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(16,
                Ui.label("Log in", "title"),
                Ui.label("Server: " + router.context().api().server().display(), "muted"),
                Ui.field("Username", username),
                Ui.field("Password", password),
                remember,
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

    private void submit(boolean newAccount) {
        String name = username.getText().trim();
        String secret = password.getText();
        if (name.isEmpty() || secret.isEmpty()) {
            Ui.showError(message, "Type your username and password.");
            return;
        }
        busy(true);
        message.setText("");
        CompletableFuture<AuthResponse> call = newAccount
                ? router.context().api().register(name, secret)
                : router.context().api().login(name, secret);
        Ui.whenDone(call, answer -> {
            router.context().loggedIn(answer, remember.isSelected());
            router.showHome(null);
        }, failure -> {
            busy(false);
            Ui.showError(message, failure);
        });
    }

    private void busy(boolean busy) {
        login.setDisable(busy);
        register.setDisable(busy);
    }
}
