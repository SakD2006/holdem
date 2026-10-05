package com.saksham.poker.server.ws;

import com.saksham.poker.common.exception.UnauthorizedException;
import com.saksham.poker.server.bootstrap.AppContext;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks the login token in the WebSocket address ({@code ...?token=<token>}) while the connection
 * is being set up. If the token is good, the user is attached to the connection; if not, nothing is
 * attached and the endpoint closes the connection with code 4401 as soon as it opens.
 */
public class AuthHandshakeConfigurator extends ServerEndpointConfig.Configurator {

    private static final Logger log = LoggerFactory.getLogger(AuthHandshakeConfigurator.class);

    /** Where the authenticated user is kept for the endpoint to pick up. */
    static final String USER_PROPERTY = "holdem.user";

    @Override
    public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
        config.getUserProperties().remove(USER_PROPERTY);
        List<String> tokens = request.getParameterMap().get("token");
        String token = tokens == null || tokens.isEmpty() ? null : tokens.get(0);
        try {
            config.getUserProperties().put(USER_PROPERTY, AppContext.current().sessions().authenticate(token));
        } catch (UnauthorizedException e) {
            // Left unauthenticated; the endpoint closes it.
        } catch (RuntimeException e) {
            log.error("Could not check the login for a game connection", e);
        }
    }
}
