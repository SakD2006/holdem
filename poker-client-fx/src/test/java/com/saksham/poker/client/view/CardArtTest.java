package com.saksham.poker.client.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saksham.poker.client.app.AppConfig;
import com.saksham.poker.common.exception.StorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Choosing how the cards look, and supplying your own pictures. */
class CardArtTest {

    @TempDir
    Path folder;

    @Test
    void theBackDesignIsFoundByItsSavedName() {
        assertThat(CardArt.Back.named("midnight")).isEqualTo(CardArt.Back.MIDNIGHT);
        assertThat(CardArt.Back.named("ONYX")).isEqualTo(CardArt.Back.ONYX);
        assertThat(CardArt.Back.named("custom")).isEqualTo(CardArt.Back.CUSTOM);
        // A name from a newer or older version, or a typo in the file, falls back to the default.
        assertThat(CardArt.Back.named("tartan")).isEqualTo(CardArt.Back.CRIMSON);
        assertThat(CardArt.Back.named(null)).isEqualTo(CardArt.Back.CRIMSON);
    }

    @Test
    void theChosenLookIsRememberedInTheSettings() {
        AppConfig config = AppConfig.load(folder);
        assertThat(config.cardBack()).isEqualTo("crimson");
        assertThat(config.fourColourDeck()).isFalse();

        config.setCardBack("midnight");
        config.setFourColourDeck(true);
        config.save();

        AppConfig again = AppConfig.load(folder);
        assertThat(again.cardBack()).isEqualTo("midnight");
        assertThat(again.fourColourDeck()).isTrue();
    }

    @Test
    void aPictureOfYourOwnBecomesTheCardBack() throws Exception {
        Path chosen = Files.write(folder.resolve("my holiday photo.PNG"), new byte[] {1, 2, 3});
        Path art = folder.resolve("holdem");
        assertThat(CardArt.hasBackPicture(art)).isFalse();

        CardArt.installBackPicture(chosen, art);

        assertThat(CardArt.hasBackPicture(art)).isTrue();
        assertThat(art.resolve("card-back.png")).hasBinaryContent(new byte[] {1, 2, 3});
        // The player's own file is left where it was.
        assertThat(chosen).exists();
    }

    @Test
    void choosingAnotherPictureReplacesTheFirstWhateverItsType() throws Exception {
        Path art = folder.resolve("holdem");
        CardArt.installBackPicture(Files.write(folder.resolve("first.png"), new byte[] {1}), art);
        CardArt.installBackPicture(Files.write(folder.resolve("second.jpg"), new byte[] {2}), art);

        assertThat(art.resolve("card-back.jpg")).hasBinaryContent(new byte[] {2});
        assertThat(art.resolve("card-back.png")).doesNotExist();
        assertThat(CardArt.find(art, "card-back")).contains(art.resolve("card-back.jpg"));
    }

    @Test
    void aFileThatIsNotAPictureIsRefusedByName() throws Exception {
        Path notes = Files.writeString(folder.resolve("notes.txt"), "not a picture");

        assertThatThrownBy(() -> CardArt.installBackPicture(notes, folder.resolve("holdem")))
                .isInstanceOf(StorageException.class).hasMessageContaining("notes.txt").hasMessageContaining(".png");
        assertThat(CardArt.hasBackPicture(folder.resolve("holdem"))).isFalse();
    }

    @Test
    void picturesForCardFacesAreFoundByCardName() throws Exception {
        Path cards = Files.createDirectories(folder.resolve("cards"));
        Files.write(cards.resolve("Ah.png"), new byte[] {1});
        Files.write(cards.resolve("Td.jpeg"), new byte[] {1});

        assertThat(CardArt.find(cards, "Ah")).contains(cards.resolve("Ah.png"));
        assertThat(CardArt.find(cards, "Td")).contains(cards.resolve("Td.jpeg"));
        assertThat(CardArt.find(cards, "Ks")).isEmpty();
        assertThat(CardArt.find(folder.resolve("no-such-folder"), "Ah")).isEmpty();
    }
}
