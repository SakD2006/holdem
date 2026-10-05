package com.saksham.poker.client.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of the desktop app. Screens arrive in Phase 7; for now it only opens the window. */
public class HoldemApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(HoldemApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Hold'em");
        stage.setScene(new Scene(new StackPane(new Label("Hold'em")), 960, 640));
        stage.show();
        log.info("Window opened");
    }
}
