package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.util.HandHistoryExporter;
import com.saksham.poker.client.util.HandText;
import com.saksham.poker.common.api.HandPage;
import com.saksham.poker.common.api.HandSummary;
import com.saksham.poker.common.exception.StorageException;
import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * The hands you have played, newest first, a page at a time. Choosing one shows it step by step;
 * "Export" saves the whole history as a text file.
 */
public final class HistoryView extends StackPane {

    private static final String MONO = "-fx-font-family: 'Menlo', 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;";

    private final SceneRouter router;
    private final ZoneId zone = ZoneId.systemDefault();
    private final ObservableList<HandSummary> hands = FXCollections.observableArrayList();
    private final ObservableList<String> detail = FXCollections.observableArrayList();
    private final Label pageLabel = Ui.label("", "muted");
    private final Label message = Ui.message();
    private final Button previous;
    private final Button next;
    private final Button export;
    private int page = 1;
    private long total;
    private int pageSize = 20;

    public HistoryView(SceneRouter router) {
        this.router = router;
        getStyleClass().add("screen");

        ListView<HandSummary> list = new ListView<>(hands);
        list.setPlaceholder(Ui.label("You have not played any hands yet.", "muted"));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(HandSummary hand, boolean empty) {
                super.updateItem(hand, empty);
                setStyle(MONO);
                setText(empty || hand == null ? null : HandText.line(hand, zone));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((property, was, chosen) -> show(chosen));
        list.setPrefWidth(470);

        ListView<String> steps = new ListView<>(detail);
        steps.setFocusTraversable(false);
        steps.setPlaceholder(Ui.label("Choose a hand to see how it went.", "muted"));
        HBox.setHgrow(steps, Priority.ALWAYS);
        HBox lists = new HBox(14, list, steps);
        VBox.setVgrow(lists, Priority.ALWAYS);

        previous = Ui.button("Newer", () -> load(page - 1), "small");
        next = Ui.button("Older", () -> load(page + 1), "small");
        export = Ui.button("Export to a file...", this::export);
        HBox footer = new HBox(10, Ui.button("Back", () -> router.showHome(null)), SeatNode.spacer(),
                previous, pageLabel, next, SeatNode.spacer(), export);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(14, Ui.label("My hand history", "title"), message, lists, footer);
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(1020);
        panel.setMaxHeight(640);
        getChildren().add(panel);
        load(1);
    }

    private void load(int wanted) {
        previous.setDisable(true);
        next.setDisable(true);
        Ui.whenDone(router.context().api().hands(router.context().token(), wanted), this::showPage,
                failure -> Ui.showError(message, failure));
    }

    private void showPage(HandPage loaded) {
        page = loaded.page();
        total = loaded.total();
        pageSize = loaded.pageSize();
        hands.setAll(loaded.hands());
        detail.clear();
        long pages = Math.max(1, (total + pageSize - 1) / pageSize);
        pageLabel.setText("Page " + page + " of " + pages + "  (" + total + (total == 1 ? " hand)" : " hands)"));
        previous.setDisable(page <= 1);
        next.setDisable(page >= pages);
        export.setDisable(total == 0);
    }

    private void show(HandSummary chosen) {
        if (chosen == null) {
            return;
        }
        Ui.whenDone(router.context().api().hand(router.context().token(), chosen.id()),
                hand -> detail.setAll(HandText.lines(hand, zone)),
                failure -> Ui.showError(message, failure));
    }

    /** Asks where to save, fetches every page, and writes the file. */
    private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export hand history");
        chooser.setInitialFileName("holdem-hands-" + router.context().user().username() + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text files", "*.txt"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        export.setDisable(true);
        Ui.showGood(message, "Collecting your hands...");
        Ui.whenDone(allHands(), all -> {
            export.setDisable(false);
            try {
                HandHistoryExporter.write(file.toPath(), router.context().user().username(), all, zone);
                Ui.showGood(message, "Saved " + all.size() + " hands to " + file.getPath());
            } catch (StorageException e) {
                Ui.showError(message, e.getMessage());
            }
        }, failure -> {
            export.setDisable(false);
            Ui.showError(message, failure);
        });
    }

    /** Every page of the history, one request after another. */
    private CompletableFuture<List<HandSummary>> allHands() {
        ApiClient api = router.context().api();
        String token = router.context().token();
        return collect(api, token, 1, new ArrayList<>());
    }

    private static CompletableFuture<List<HandSummary>> collect(ApiClient api, String token, int page,
            List<HandSummary> soFar) {
        return api.hands(token, page).thenCompose(loaded -> {
            soFar.addAll(loaded.hands());
            boolean more = !loaded.hands().isEmpty() && soFar.size() < loaded.total();
            return more ? collect(api, token, page + 1, soFar) : CompletableFuture.completedFuture(soFar);
        });
    }
}
