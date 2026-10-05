package com.saksham.poker.client.app;

import com.saksham.poker.common.exception.StorageException;
import java.nio.file.Path;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of the desktop app. */
public class HoldemApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(HoldemApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Path folder = AppFiles.folder();
        AppConfig config;
        try {
            config = AppConfig.load(folder);
        } catch (StorageException e) {
            // Unreadable settings are not worth stopping for: start with the defaults.
            log.warn("{} Starting with default settings.", e.getMessage());
            config = AppConfig.defaults(folder);
        }
        ClientContext context = new ClientContext(config, new SessionStore(folder));
        stage.setTitle("Hold'em");
        new SceneRouter(stage, context).start();
        stage.show();
        log.info("Window opened");
    }
}
