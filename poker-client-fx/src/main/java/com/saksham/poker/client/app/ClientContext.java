package com.saksham.poker.client.app;

import com.saksham.poker.client.net.ApiClient;
import com.saksham.poker.client.net.ServerAddress;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.common.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the app knows while it runs: the player's preferences, which server it is talking to, and
 * who is logged in. Used only from the JavaFX thread.
 */
public final class ClientContext {

    private static final Logger log = LoggerFactory.getLogger(ClientContext.class);

    private final AppConfig config;
    private final SessionStore sessions;
    private ApiClient api;
    private String token;
    private UserInfo user;

    public ClientContext(AppConfig config, SessionStore sessions) {
        this.config = config;
        this.sessions = sessions;
    }

    public AppConfig config() {
        return config;
    }

    public SessionStore sessions() {
        return sessions;
    }

    /** Points the app at a server and remembers the address for next time. */
    public void useServer(ServerAddress server) {
        api = new ApiClient(server);
        config.setLastServer(server.display());
        saveConfig();
    }

    /** The API of the chosen server; null until {@link #useServer} has been called. */
    public ApiClient api() {
        return api;
    }

    /** Records a successful login, and remembers it on disk if the player asked for that. */
    public void loggedIn(AuthResponse login, boolean remember) {
        token = login.token();
        user = login.user();
        config.setLastUsername(user.username());
        saveConfig();
        try {
            if (remember) {
                sessions.save(new SavedSession(api.server().display(), user.id(), user.username(), token));
            } else {
                sessions.delete();
            }
        } catch (StorageException e) {
            // Not being remembered is an inconvenience, not a reason to stop the player getting in.
            log.warn("{}", e.getMessage());
        }
    }

    /** Picks up a login remembered from an earlier run, once the server has confirmed it. */
    public void resumed(SavedSession saved, UserInfo confirmed) {
        token = saved.token();
        user = confirmed;
    }

    /** Forgets the login, here and on disk. */
    public void loggedOut() {
        token = null;
        user = null;
        try {
            sessions.delete();
        } catch (StorageException e) {
            log.warn("{}", e.getMessage());
        }
    }

    public String token() {
        return token;
    }

    public UserInfo user() {
        return user;
    }

    private void saveConfig() {
        try {
            config.save();
        } catch (StorageException e) {
            log.warn("{}", e.getMessage());
        }
    }
}
