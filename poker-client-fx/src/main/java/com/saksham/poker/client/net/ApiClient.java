package com.saksham.poker.client.net;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.saksham.poker.common.api.ApiError;
import com.saksham.poker.common.api.AuthRequest;
import com.saksham.poker.common.api.AuthResponse;
import com.saksham.poker.common.api.CreateRoomResponse;
import com.saksham.poker.common.api.HandDetail;
import com.saksham.poker.common.api.HandPage;
import com.saksham.poker.common.api.LeaderboardEntry;
import com.saksham.poker.common.api.PingResponse;
import com.saksham.poker.common.api.RoomPreview;
import com.saksham.poker.common.api.UserInfo;
import com.saksham.poker.common.protocol.dto.RoomSettingsInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The server's HTTP API (SPEC §6). Every call returns at once with a future, so nothing here can
 * freeze the window; the future completes on a background thread, and fails with an
 * {@link ApiException} whose message can be shown to the player.
 */
public final class ApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final ServerAddress server;

    public ApiClient(ServerAddress server) {
        this.server = server;
    }

    public ServerAddress server() {
        return server;
    }

    /** The HTTP client, shared with the game connection. */
    public HttpClient http() {
        return http;
    }

    public CompletableFuture<PingResponse> ping() {
        return send(get("/ping", null), new TypeReference<>() { });
    }

    public CompletableFuture<AuthResponse> register(String username, String password) {
        return send(post("/auth/register", null, new AuthRequest(username, password)), new TypeReference<>() { });
    }

    public CompletableFuture<AuthResponse> login(String username, String password) {
        return send(post("/auth/login", null, new AuthRequest(username, password)), new TypeReference<>() { });
    }

    public CompletableFuture<Void> logout(String token) {
        return send(post("/auth/logout", token, ""), new TypeReference<>() { });
    }

    /** Who the token belongs to; fails with {@code UNAUTHORIZED} if it is no longer good. */
    public CompletableFuture<UserInfo> me(String token) {
        return send(get("/me", token), new TypeReference<>() { });
    }

    public CompletableFuture<String> createRoom(String token, RoomSettingsInfo settings) {
        CompletableFuture<CreateRoomResponse> created = send(post("/rooms", token, settings),
                new TypeReference<>() { });
        return created.thenApply(CreateRoomResponse::code);
    }

    public CompletableFuture<RoomPreview> previewRoom(String token, String code) {
        return send(get("/rooms/" + code.trim(), token), new TypeReference<>() { });
    }

    public CompletableFuture<HandPage> hands(String token, int page) {
        return send(get("/hands?page=" + page, token), new TypeReference<>() { });
    }

    public CompletableFuture<HandDetail> hand(String token, long handId) {
        return send(get("/hands/" + handId, token), new TypeReference<>() { });
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String token) {
        return send(get("/leaderboard", token), new TypeReference<>() { });
    }

    // ---- plumbing

    private HttpRequest get(String path, String token) {
        return request(path, token).GET().build();
    }

    private HttpRequest post(String path, String token, Object body) {
        try {
            String text = body instanceof String already ? already : json.writeValueAsString(body);
            return request(path, token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(text)).build();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not write the request", e);
        }
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(server.apiBase() + path)).timeout(REQUEST_TIMEOUT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + server.display() + "\" is not a usable address.", e);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private <T> CompletableFuture<T> send(HttpRequest request, TypeReference<T> answer) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, failure) -> {
                    if (failure != null) {
                        throw new CompletionException(unreachable());
                    }
                    return read(response, answer);
                });
    }

    private <T> T read(HttpResponse<String> response, TypeReference<T> answer) {
        try {
            if (response.statusCode() >= 400) {
                ApiError error = json.readValue(response.body(), ApiError.class);
                throw new CompletionException(new ApiException(error.code(), error.message()));
            }
            if (response.body() == null || response.body().isBlank()) {
                return null;
            }
            return json.readValue(response.body(), answer);
        } catch (IOException e) {
            // Something answered, but not with what a Hold'em server sends.
            throw new CompletionException(new ApiException(null, "Something answered at " + server.display()
                    + ", but it is not a Hold'em server. Check the address."));
        }
    }

    private ApiException unreachable() {
        return new ApiException(null, "Could not reach a server at " + server.display()
                + ". Check the address, that the server is running, and that you are on the same network.");
    }

    /** The {@link ApiException} behind a failed future, whatever it was wrapped in. */
    public static ApiException reason(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ApiException api) {
                return api;
            }
            cause = cause.getCause();
        }
        return new ApiException(null, "Something went wrong: " + failure.getMessage());
    }
}
