package com.saksham.poker.bot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.saksham.poker.common.api.ApiError;
import com.saksham.poker.common.api.AuthRequest;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.CreateRoomResponse;
import com.saksham.poker.common.error.ErrorCode;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** The few HTTP calls a bot makes before it opens its game connection. */
final class ApiClient {

    /** The server refused a request. */
    static final class ApiException extends Exception {

        private static final long serialVersionUID = 1L;
        private final ErrorCode code;

        ApiException(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        ErrorCode code() {
            return code;
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final String base;

    ApiClient(String base) {
        this.base = base;
    }

    HttpClient http() {
        return http;
    }

    /** Logs an account in, registering it first if it does not exist yet. */
    AuthResponse registerOrLogin(String username, String password)
            throws ApiException, IOException, InterruptedException {
        AuthRequest body = new AuthRequest(username, password);
        try {
            return post("/auth/register", null, body, AuthResponse.class);
        } catch (ApiException e) {
            if (e.code() != ErrorCode.USERNAME_TAKEN) {
                throw e;
            }
            return post("/auth/login", null, body, AuthResponse.class);
        }
    }

    /** Creates a room and returns its code. */
    String createRoom(String token, RoomSettingsInfo settings)
            throws ApiException, IOException, InterruptedException {
        return post("/rooms", token, settings, CreateRoomResponse.class).code();
    }

    private <T> T post(String path, String token, Object body, Class<T> answer)
            throws ApiException, IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            ApiError error;
            try {
                error = json.readValue(response.body(), ApiError.class);
            } catch (IOException e) {
                throw new IOException("The server answered " + response.statusCode() + " to " + path
                        + ". Is a Hold'em server running at " + base + "?");
            }
            throw new ApiException(error.code(), error.message());
        }
        return json.readValue(response.body(), answer);
    }
}
