package com.saksham.poker.client.view;

import com.saksham.poker.client.app.AppConfig;
import com.saksham.poker.client.app.AppFiles;
import com.saksham.poker.common.card.Card;
import com.saksham.poker.common.exception.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.scene.image.Image;

/**
 * How the cards look: which back design is in use, whether the deck has four colours, and any
 * pictures the player has supplied to replace the drawn ones.
 *
 * <p>A player's own artwork lives in the app's folder ({@code ~/.holdem}):
 * <ul>
 *   <li>{@code card-back.png} (or {@code .jpg}) replaces the back of every card;</li>
 *   <li>{@code cards/Ah.png}, {@code cards/Td.png} and so on replace individual card faces. Any
 *       card without a picture keeps its drawn face.</li>
 * </ul>
 * Pictures are best at the proportions of a playing card, 5 wide by 7 tall.
 */
public final class CardArt {

    /** The built-in back designs, by the name saved in the settings. */
    public enum Back {
        CRIMSON("Crimson", "#8a2331", "#5d1520", "#f3ecd9"),
        MIDNIGHT("Midnight", "#1f3460", "#121f3c", "#d9b44a"),
        ONYX("Onyx", "#2b2b2f", "#141416", "#d9b44a"),
        EMERALD("Emerald", "#1d6a4d", "#0f3f2d", "#f3ecd9"),
        /** The player's own picture. Falls back to crimson if the picture is missing. */
        CUSTOM("Your own picture", "#8a2331", "#5d1520", "#f3ecd9");

        final String label;
        final String top;
        final String bottom;
        final String ink;

        Back(String label, String top, String bottom, String ink) {
            this.label = label;
            this.top = top;
            this.bottom = bottom;
            this.ink = ink;
        }

        public String label() {
            return label;
        }

        static Back named(String name) {
            for (Back back : values()) {
                if (back.name().equalsIgnoreCase(name)) {
                    return back;
                }
            }
            return CRIMSON;
        }
    }

    private static final List<String> PICTURE_TYPES = List.of("png", "jpg", "jpeg");
    private static final String BACK_FILE = "card-back";
    private static final String FACES_FOLDER = "cards";
    /** Pictures are scaled down to this width when loaded; plenty for a card on screen. */
    private static final double LOAD_WIDTH = 360;

    private static Back back = Back.CRIMSON;
    private static boolean fourColour;
    private static Path folder = AppFiles.folder();
    private static final Map<String, Optional<Image>> pictures = new HashMap<>();

    private CardArt() {
    }

    /** Takes the look from the player's settings. Call at startup and whenever they change. */
    public static void use(AppConfig config) {
        use(Back.named(config.cardBack()), config.fourColourDeck(), AppFiles.folder());
    }

    /** As {@link #use(AppConfig)}, with everything given directly. */
    public static void use(Back chosen, boolean fourColourDeck, Path artFolder) {
        back = chosen;
        fourColour = fourColourDeck;
        folder = artFolder;
        pictures.clear();
    }

    static Back back() {
        return back;
    }

    static boolean fourColour() {
        return fourColour;
    }

    /** The player's own picture for the back of the cards, if that is the chosen design and it exists. */
    static Optional<Image> backPicture() {
        return back == Back.CUSTOM ? picture(folder, BACK_FILE) : Optional.empty();
    }

    /** The player's own picture for one card's face, if they supplied one. */
    static Optional<Image> facePicture(Card card) {
        return picture(folder.resolve(FACES_FOLDER), card.toString());
    }

    /** True if the player has supplied a picture for the back of the cards. */
    public static boolean hasBackPicture(Path artFolder) {
        return find(artFolder, BACK_FILE).isPresent();
    }

    /**
     * Makes a picture the player's own card back, by copying it into the app's folder.
     *
     * @throws StorageException if the file is not a PNG or JPEG, or cannot be copied
     */
    public static void installBackPicture(Path chosen, Path artFolder) {
        String name = chosen.getFileName().toString();
        String type = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!PICTURE_TYPES.contains(type)) {
            throw new StorageException("\"" + name + "\" is not a PNG or JPEG picture. Choose a .png or .jpg file.");
        }
        try {
            Files.createDirectories(artFolder);
            // Only one back picture is kept, whatever its type was last time.
            for (String each : PICTURE_TYPES) {
                Files.deleteIfExists(artFolder.resolve(BACK_FILE + "." + each));
            }
            Files.copy(chosen, artFolder.resolve(BACK_FILE + "." + type), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Could not copy \"" + name + "\" into " + artFolder + ".", e);
        }
        pictures.clear();
    }

    private static Optional<Image> picture(Path in, String baseName) {
        return pictures.computeIfAbsent(in + "/" + baseName, key -> find(in, baseName).flatMap(CardArt::load));
    }

    /** The picture file with this name and any supported type, if there is one. */
    static Optional<Path> find(Path in, String baseName) {
        for (String type : PICTURE_TYPES) {
            Path file = in.resolve(baseName + "." + type);
            if (Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private static Optional<Image> load(Path file) {
        try {
            Image image = new Image(file.toUri().toString(), LOAD_WIDTH, 0, true, true);
            return image.isError() ? Optional.empty() : Optional.of(image);
        } catch (RuntimeException e) {
            // A file that is not really a picture: keep the drawn card.
            return Optional.empty();
        }
    }
}
