package com.saksham.poker.client.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.saksham.poker.common.exception.StorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The app's two files: its settings and the remembered login. */
class AppFilesTest {

    @TempDir
    Path folder;

    // ---- client.properties

    @Test
    void settingsStartWithDefaults() {
        AppConfig config = AppConfig.load(folder);

        assertThat(config.lastServer()).isEmpty();
        assertThat(config.lastUsername()).isEmpty();
        assertThat(config.soundOn()).isTrue();
    }

    @Test
    void settingsSurviveBeingSavedAndLoaded() {
        AppConfig config = AppConfig.load(folder);
        config.setLastServer("192.168.1.20");
        config.setLastUsername("asha");
        config.setSoundOn(false);
        config.save();

        AppConfig again = AppConfig.load(folder);

        assertThat(again.lastServer()).isEqualTo("192.168.1.20");
        assertThat(again.lastUsername()).isEqualTo("asha");
        assertThat(again.soundOn()).isFalse();
        assertThat(folder.resolve("client.properties")).isRegularFile();
    }

    @Test
    void savingCreatesTheFolderIfItIsMissing() {
        Path missing = folder.resolve("not").resolve("yet");
        AppConfig config = AppConfig.defaults(missing);
        config.setLastUsername("asha");

        config.save();

        assertThat(AppConfig.load(missing).lastUsername()).isEqualTo("asha");
    }

    @Test
    void settingsThatCannotBeSavedRaiseAStorageException() throws Exception {
        Path blocked = folder.resolve("blocked");
        Files.writeString(blocked, "a file where the folder should be");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AppConfig.defaults(blocked).save())
                .isInstanceOf(StorageException.class).hasMessageContaining("settings");
    }

    // ---- session.dat

    @Test
    void aRememberedLoginSurvivesBeingSavedAndLoaded() {
        SessionStore store = new SessionStore(folder);
        SavedSession session = new SavedSession("192.168.1.20", 7, "asha", "a".repeat(64));

        store.save(session);

        assertThat(new SessionStore(folder).load()).contains(session);
        assertThat(folder.resolve("session.dat")).isRegularFile();
    }

    @Test
    void thereIsNoLoginUntilOneIsSaved() {
        assertThat(new SessionStore(folder).load()).isEmpty();
    }

    @Test
    void loggingOutRemovesTheFile() {
        SessionStore store = new SessionStore(folder);
        store.save(new SavedSession("192.168.1.20", 7, "asha", "token"));

        store.delete();
        store.delete(); // already gone: harmless

        assertThat(store.load()).isEmpty();
        assertThat(folder.resolve("session.dat")).doesNotExist();
    }

    @Test
    void aDamagedFileIsTreatedAsNoLogin() throws Exception {
        Files.writeString(folder.resolve("session.dat"), "this is not a serialized object");

        assertThat(new SessionStore(folder).load()).isEmpty();
    }

    @Test
    void aFileHoldingSomethingElseIsNotRead() throws Exception {
        // A serialized list, not a SavedSession: the filter refuses to read it at all.
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
                Files.newOutputStream(folder.resolve("session.dat")))) {
            out.writeObject(new java.util.ArrayList<>(java.util.List.of("not", "a", "session")));
        }

        assertThat(new SessionStore(folder).load()).isEmpty();
    }

    @Test
    void thePasswordIsNeverPartOfWhatIsRemembered() {
        assertThat(SavedSession.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("server", "userId", "username", "token");
    }

    @Test
    void theAppFolderCanBeMovedForTesting() {
        String before = System.getProperty(AppFiles.HOME_PROPERTY);
        try {
            System.setProperty(AppFiles.HOME_PROPERTY, folder.toString());
            assertThat(AppFiles.folder()).isEqualTo(folder);
            System.clearProperty(AppFiles.HOME_PROPERTY);
            assertThat(AppFiles.folder().getFileName().toString()).isEqualTo(".holdem");
        } finally {
            if (before != null) {
                System.setProperty(AppFiles.HOME_PROPERTY, before);
            }
        }
    }
}
