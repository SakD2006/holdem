package com.saksham.poker.client.view;

import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.card.Suit;
import java.util.Optional;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * One playing card. It lies face down showing the chosen back design, or face up showing its rank
 * and suit, and can turn over from one to the other.
 *
 * <p>Everything is drawn with shapes and text unless the player has supplied their own pictures
 * (see {@link CardArt}).
 */
public final class CardNode extends StackPane {

    /** Cards narrower than this use the simple face: a big rank over a big suit. */
    private static final double FULL_FACE_FROM = 48;
    private static final String SERIF = "Georgia";

    private final double width;
    private final double height;
    private Node showing;

    /**
     * @param card the card to show face up, or null for a card lying face down
     * @param width the card's width in pixels; its height follows
     */
    public CardNode(Card card, double width) {
        this.width = width;
        this.height = Math.rint(width * 1.4);
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
        DropShadow shadow = new DropShadow(width * 0.16, 0, width * 0.04, Color.color(0, 0, 0, 0.45));
        setEffect(shadow);
        showing = card == null ? back() : face(card);
        getChildren().add(showing);
    }

    /**
     * Turns the card over to show a face, after a wait. The card narrows to an edge, changes sides,
     * and widens again.
     *
     * @param card the face to show
     * @param delay how long to wait before starting
     */
    public void flipTo(Card card, Duration delay) {
        Node face = face(card);
        if (!Motion.enabled) {
            getChildren().setAll(face);
            showing = face;
            return;
        }
        ScaleTransition close = new ScaleTransition(Motion.HALF_FLIP, this);
        close.setFromX(1);
        close.setToX(0);
        close.setInterpolator(Interpolator.EASE_IN);
        close.setOnFinished(event -> {
            getChildren().setAll(face);
            showing = face;
        });
        ScaleTransition open = new ScaleTransition(Motion.HALF_FLIP, this);
        open.setFromX(0);
        open.setToX(1);
        open.setInterpolator(Interpolator.EASE_OUT);
        new SequentialTransition(new PauseTransition(delay), close, open).play();
    }

    // =====================================================================================
    // The back
    // =====================================================================================

    private Node back() {
        Optional<Image> own = CardArt.backPicture();
        if (own.isPresent()) {
            return picture(own.get());
        }
        CardArt.Back design = CardArt.back();
        Color ink = Color.web(design.ink);
        double arc = width * 0.2;

        Rectangle body = new Rectangle(width, height);
        body.setArcWidth(arc);
        body.setArcHeight(arc);
        body.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(design.top)), new Stop(1, Color.web(design.bottom))));
        body.setStroke(Color.color(0, 0, 0, 0.5));

        // A lattice of fine diagonal lines, made from two repeating gradients laid across each other.
        double inset = Math.max(3, width * 0.09);
        double step = Math.max(5, width * 0.13);
        Color line = ink.deriveColor(0, 1, 1, 0.34);
        Rectangle rising = lattice(inset, arc, step, step, line);
        Rectangle falling = lattice(inset, arc, step, -step, line);

        Rectangle frame = new Rectangle(width - inset * 2, height - inset * 2);
        frame.setArcWidth(arc * 0.6);
        frame.setArcHeight(arc * 0.6);
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(ink.deriveColor(0, 1, 1, 0.75));
        frame.setStrokeWidth(Math.max(1, width * 0.025));

        // A small emblem in the middle: a spade on a disc.
        Circle disc = new Circle(width * 0.2, Color.web(design.bottom));
        disc.setStroke(ink.deriveColor(0, 1, 1, 0.85));
        disc.setStrokeWidth(Math.max(1, width * 0.025));
        Text emblem = new Text("♠");
        emblem.setFont(Font.font("System", width * 0.24));
        emblem.setFill(ink.deriveColor(0, 1, 1, 0.9));

        return new StackPane(body, rising, falling, frame, disc, emblem);
    }

    private Rectangle lattice(double inset, double arc, double dx, double dy, Color line) {
        Rectangle lines = new Rectangle(width - inset * 2, height - inset * 2);
        lines.setArcWidth(arc * 0.6);
        lines.setArcHeight(arc * 0.6);
        lines.setFill(new LinearGradient(0, 0, dx, dy, false, CycleMethod.REPEAT,
                new Stop(0, Color.TRANSPARENT), new Stop(0.42, Color.TRANSPARENT), new Stop(0.5, line),
                new Stop(0.58, Color.TRANSPARENT), new Stop(1, Color.TRANSPARENT)));
        return lines;
    }

    // =====================================================================================
    // The face
    // =====================================================================================

    private Node face(Card card) {
        Optional<Image> own = CardArt.facePicture(card);
        if (own.isPresent()) {
            return picture(own.get());
        }
        Color ink = ink(card.suit());
        double arc = width * 0.2;
        Rectangle body = new Rectangle(width, height);
        body.setArcWidth(arc);
        body.setArcHeight(arc);
        body.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fdf9ee")), new Stop(1, Color.web("#ece3cb"))));
        body.setStroke(Color.color(0, 0, 0, 0.35));

        StackPane face = new StackPane(body);
        String rank = rankText(card);
        String suit = suitSymbol(card.suit());
        if (width < FULL_FACE_FROM) {
            // Small cards: just a big rank over a big suit, readable across the table.
            Text bigRank = text(rank, SERIF, FontWeight.BOLD, width * (rank.length() > 1 ? 0.44 : 0.52), ink);
            Text bigSuit = text(suit, "System", FontWeight.NORMAL, width * 0.46, ink);
            StackPane.setAlignment(bigRank, Pos.TOP_CENTER);
            StackPane.setAlignment(bigSuit, Pos.BOTTOM_CENTER);
            bigRank.setTranslateY(width * 0.05);
            bigSuit.setTranslateY(-width * 0.07);
            face.getChildren().addAll(bigRank, bigSuit);
            return face;
        }

        // Larger cards: an index in two corners and the suit, or a framed court letter, in the middle.
        Node topIndex = index(rank, suit, ink);
        Node bottomIndex = index(rank, suit, ink);
        bottomIndex.setRotate(180);
        StackPane.setAlignment(topIndex, Pos.TOP_LEFT);
        StackPane.setAlignment(bottomIndex, Pos.BOTTOM_RIGHT);
        double margin = width * 0.07;
        topIndex.setTranslateX(margin);
        topIndex.setTranslateY(margin * 0.6);
        bottomIndex.setTranslateX(-margin);
        bottomIndex.setTranslateY(-margin * 0.6);

        Node centre;
        char symbol = card.rank().symbol();
        if (symbol == 'J' || symbol == 'Q' || symbol == 'K') {
            Rectangle panel = new Rectangle(width * 0.5, height * 0.52);
            panel.setArcWidth(arc * 0.5);
            panel.setArcHeight(arc * 0.5);
            panel.setFill(ink.deriveColor(0, 1, 1, 0.08));
            panel.setStroke(ink.deriveColor(0, 1, 1, 0.55));
            panel.setStrokeWidth(Math.max(1, width * 0.018));
            Text letter = text(rank, SERIF, FontWeight.BOLD, width * 0.42, ink);
            Text small = text(suit, "System", FontWeight.NORMAL, width * 0.2, ink);
            VBox court = new VBox(-width * 0.04, letter, small);
            court.setAlignment(Pos.CENTER);
            centre = new StackPane(panel, court);
        } else {
            centre = text(suit, "System", FontWeight.NORMAL, width * (symbol == 'A' ? 0.7 : 0.54), ink);
        }
        face.getChildren().addAll(centre, topIndex, bottomIndex);
        return face;
    }

    /** A corner index: the rank with a small suit under it. */
    private Node index(String rank, String suit, Color ink) {
        Text rankText = text(rank, SERIF, FontWeight.BOLD, width * (rank.length() > 1 ? 0.22 : 0.27), ink);
        Text suitText = text(suit, "System", FontWeight.NORMAL, width * 0.19, ink);
        VBox index = new VBox(-width * 0.05, rankText, suitText);
        index.setAlignment(Pos.CENTER);
        index.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        return index;
    }

    private static Text text(String content, String family, FontWeight weight, double size, Color fill) {
        Text text = new Text(content);
        text.setFont(Font.font(family, weight, size));
        text.setFill(fill);
        return text;
    }

    /** The player's own picture, cut to the shape of a card. */
    private Node picture(Image image) {
        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(false);
        view.setSmooth(true);
        Rectangle shape = new Rectangle(width, height);
        shape.setArcWidth(width * 0.2);
        shape.setArcHeight(width * 0.2);
        view.setClip(shape);
        Rectangle edge = new Rectangle(width, height);
        edge.setArcWidth(width * 0.2);
        edge.setArcHeight(width * 0.2);
        edge.setFill(Color.TRANSPARENT);
        edge.setStroke(Color.color(0, 0, 0, 0.45));
        return new StackPane(view, edge);
    }

    private static Color ink(Suit suit) {
        boolean four = CardArt.fourColour();
        return switch (suit) {
            case HEARTS -> Color.web("#c2382c");
            case DIAMONDS -> four ? Color.web("#1f5fae") : Color.web("#c2382c");
            case CLUBS -> four ? Color.web("#1f7a3d") : Color.web("#1d2321");
            case SPADES -> Color.web("#1d2321");
        };
    }

    private static String rankText(Card card) {
        char symbol = card.rank().symbol();
        return symbol == 'T' ? "10" : String.valueOf(symbol);
    }

    private static String suitSymbol(Suit suit) {
        return switch (suit) {
            case SPADES -> "♠";
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
        };
    }
}
