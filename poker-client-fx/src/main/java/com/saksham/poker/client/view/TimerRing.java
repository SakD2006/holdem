package com.saksham.poker.client.view;

import javafx.animation.AnimationTimer;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * A ring that empties as a player's turn runs out, with the seconds left in the middle. It turns red
 * for the last five seconds.
 */
public final class TimerRing extends StackPane {

    private static final Color PLENTY = Color.web("#d9b44a");
    private static final Color HURRY = Color.web("#e58a78");
    private static final long HURRY_MS = 5_000;

    private final Arc arc;
    private final Text seconds = new Text();
    private long endsAtMs;
    private long totalMs = 1;
    private final AnimationTimer ticker = new AnimationTimer() {
        @Override
        public void handle(long now) {
            redraw();
        }
    };

    public TimerRing(double radius) {
        setMinSize(radius * 2, radius * 2);
        setMaxSize(radius * 2, radius * 2);
        Circle track = new Circle(radius - 2, Color.web("#0b1814"));
        track.setStroke(Color.web("#24493d"));
        track.setStrokeWidth(3);
        arc = new Arc(0, 0, radius - 2, radius - 2, 90, 360);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStrokeWidth(3);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        // An Arc's bounds shrink with its length, which would make it wander. Keep it centred.
        arc.setManaged(false);
        arc.setLayoutX(radius);
        arc.setLayoutY(radius);
        seconds.setFont(Font.font("System", FontWeight.BOLD, radius * 0.8));
        seconds.setFill(Color.web("#f3ecd9"));
        getChildren().addAll(track, arc, seconds);
        setVisible(false);
        setManaged(false);
    }

    /**
     * Starts counting down.
     *
     * @param endsAtMs when the turn runs out, by this computer's clock
     * @param totalMs how long a whole turn is
     */
    public void start(long endsAtMs, long totalMs) {
        this.endsAtMs = endsAtMs;
        this.totalMs = Math.max(1, totalMs);
        setVisible(true);
        redraw();
        ticker.start();
    }

    public void stop() {
        ticker.stop();
        setVisible(false);
    }

    private void redraw() {
        long left = Math.max(0, endsAtMs - System.currentTimeMillis());
        double fraction = Math.min(1.0, (double) left / totalMs);
        arc.setLength(-360 * fraction);
        arc.setStroke(left <= HURRY_MS ? HURRY : PLENTY);
        seconds.setText(String.valueOf((left + 999) / 1_000));
    }
}
