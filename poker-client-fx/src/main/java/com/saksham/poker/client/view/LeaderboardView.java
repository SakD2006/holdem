package com.saksham.poker.client.view;

import com.saksham.poker.client.app.SceneRouter;
import com.saksham.poker.client.util.Formatters;
import com.saksham.poker.common.api.LeaderboardEntry;
import java.util.List;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Who is winning: every player's total over all the hands played on this server. */
public final class LeaderboardView extends StackPane {

    private final SceneRouter router;
    private final GridPane grid = new GridPane();
    private final Label message = Ui.message();

    public LeaderboardView(SceneRouter router) {
        this.router = router;
        getStyleClass().add("screen");

        grid.setHgap(24);
        grid.setVgap(8);
        ColumnConstraints rank = new ColumnConstraints(40);
        ColumnConstraints name = new ColumnConstraints();
        name.setHgrow(Priority.ALWAYS);
        ColumnConstraints number = new ColumnConstraints(110);
        number.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().addAll(rank, name, number, number, number);

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox panel = new VBox(14, Ui.label("Leaderboard", "title"),
                Ui.label("Chips won less chips lost, over every hand played on this server.", "muted"),
                message, scroll, Ui.button("Back", () -> router.showHome(null)));
        panel.getStyleClass().add("panel");
        panel.setMaxWidth(720);
        panel.setMaxHeight(620);
        getChildren().add(panel);

        Ui.whenDone(router.context().api().leaderboard(router.context().token()), this::show,
                failure -> Ui.showError(message, failure));
    }

    private void show(List<LeaderboardEntry> entries) {
        grid.getChildren().clear();
        if (entries.isEmpty()) {
            grid.add(Ui.label("Nobody has played a hand yet.", "muted"), 0, 0, 5, 1);
            return;
        }
        String[] headings = {"#", "Player", "Net", "Hands", "Won"};
        for (int column = 0; column < headings.length; column++) {
            grid.add(Ui.label(headings[column], "field-label"), column, 0);
        }
        String you = router.context().user().username();
        int row = 1;
        for (LeaderboardEntry entry : entries) {
            boolean mine = entry.username().equals(you);
            grid.add(Ui.label(String.valueOf(entry.rank()), "muted"), 0, row);
            grid.add(Ui.label(entry.username() + (mine ? "  (you)" : ""), mine ? "seat-name" : "label"), 1, row);
            grid.add(Ui.label(Formatters.signed(entry.totalNet()), entry.totalNet() < 0 ? "error" : "good"), 2, row);
            grid.add(Ui.label(Formatters.chips(entry.handsPlayed())), 3, row);
            grid.add(Ui.label(Formatters.chips(entry.handsWon())), 4, row);
            row++;
        }
    }
}
