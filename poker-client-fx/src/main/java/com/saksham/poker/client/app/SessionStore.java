package com.saksham.poker.client.app;

import com.saksham.poker.common.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Keeps the "remember me" login in {@code session.dat}, written with Java object serialization. */
public final class SessionStore {

    private static final String FILE_NAME = "session.dat";

    private final Path file;

    public SessionStore(Path folder) {
        this.file = folder.resolve(FILE_NAME);
    }

    /**
     * Remembers a login.
     *
     * @throws StorageException if the file cannot be written
     */
    public void save(SavedSession session) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file);
                    ObjectOutputStream objects = new ObjectOutputStream(out)) {
                objects.writeObject(session);
            }
        } catch (IOException e) {
            throw new StorageException("Could not remember your login in " + file + ".", e);
        }
    }

    /**
     * The remembered login, if there is one. A file that is damaged or from another version of the
     * app is treated as no login at all: the player simply logs in again.
     */
    public Optional<SavedSession> load() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file); ObjectInputStream objects = new ObjectInputStream(in)) {
            // Only the one class this file should contain may be read from it.
            objects.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    SavedSession.class.getName() + ";java.lang.*;!*"));
            Object read = objects.readObject();
            return read instanceof SavedSession session ? Optional.of(session) : Optional.empty();
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Forgets the login, on logging out.
     *
     * @throws StorageException if the file exists and cannot be removed
     */
    public void delete() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new StorageException("Could not remove your remembered login from " + file + ".", e);
        }
    }
}
