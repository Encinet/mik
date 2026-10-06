package org.encinet.mik.module.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.module.ban.BanRecord;
import org.encinet.mik.module.ban.BanSeverity;
import org.encinet.mik.util.ShutdownSequence;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Owns the loopback HTTP listener and translates feature snapshots to HTTP responses. */
final class LocalApiServer {
    static final String BIND_ADDRESS = "127.0.0.1";
    private static final long PLAYER_RESOLVE_TIMEOUT_SECONDS = 3;
    private static final Pattern PLAYER_NAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final Pattern NOTICE_ID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final byte[] NOT_FOUND = bytes("{\"error\":\"not_found\"}");
    private static final byte[] METHOD_NOT_ALLOWED = bytes("{\"error\":\"method_not_allowed\"}");
    private static final byte[] INTERNAL_ERROR = bytes("{\"error\":\"internal_error\"}");
    private static final byte[] INVALID_NAME = bytes("{\"error\":\"invalid_name\"}");

    private final Plugin plugin;
    private final ApiPlayerSnapshot players;
    private final WebLoginChallengeStore challenges;
    private final Supplier<List<BanRecord>> activeBans;
    private final Supplier<byte[]> announcementsJson;
    private final CommunityBoardView communityBoard;

    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private int listenPort;

    private record ResolvedPlayer(UUID uuid, String name) { }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    LocalApiServer(Plugin plugin, ApiPlayerSnapshot players,
                   WebLoginChallengeStore challenges, Supplier<List<BanRecord>> activeBans,
                   Supplier<byte[]> announcementsJson, CommunityBoardView communityBoard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.activeBans = Objects.requireNonNull(activeBans, "activeBans");
        this.announcementsJson = Objects.requireNonNull(announcementsJson, "announcementsJson");
        this.communityBoard = Objects.requireNonNull(communityBoard, "communityBoard");
    }

    void start(int port) throws IOException {
        if (server != null) throw new IllegalStateException("Local API is already running");
        HttpServer created = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName(BIND_ADDRESS), port), 0);
        ExecutorService createdExecutor = null;
        try {
            installContexts(created);
            createdExecutor = Executors.newVirtualThreadPerTaskExecutor();
            created.setExecutor(createdExecutor);
            created.start();
            int boundPort = created.getAddress().getPort();
            plugin.getLogger().info("API server started on " + BIND_ADDRESS
                    + ":" + boundPort + " (local only)");
            executor = createdExecutor;
            server = created;
            listenPort = boundPort;
        } catch (RuntimeException | LinkageError error) {
            try {
                created.stop(0);
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            if (createdExecutor != null) {
                try {
                    createdExecutor.shutdown();
                } catch (RuntimeException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw error;
        }
    }

    void stop() {
        HttpServer activeServer = server;
        ExecutorService activeExecutor = executor;
        server = null;
        executor = null;
        ShutdownSequence shutdown = new ShutdownSequence();
        if (activeServer != null) shutdown.attempt("HTTP listener", () -> activeServer.stop(0));
        if (activeExecutor != null) shutdown.attempt("HTTP executor", activeExecutor::shutdown);
        shutdown.finish("local API server");
    }

    boolean running() {
        ExecutorService activeExecutor = executor;
        return server != null && activeExecutor != null && !activeExecutor.isShutdown();
    }

    int port() {
        return listenPort;
    }

    private void installContexts(HttpServer target) {
        createLocalContext(target, "/api/players", "GET", exchange ->
                sendJson(exchange, 200, players.jsonBytes(), "no-store"));
        createLocalContext(target, "/api/announcements", "GET", exchange ->
                sendJson(exchange, 200, announcementsJson.get(), null));
        createLocalContext(target, "/api/bans", "GET", exchange ->
                sendJson(exchange, 200, bansJson(activeBans.get()), null));
        createLocalContext(target, "/api/players/resolve", "GET", this::handlePlayerResolve);
        createAuthChallengeContext(target);
        createCommunityBoardContext(target);
        target.createContext("/", exchange -> {
            if (!isLocalRequest(exchange)) {
                drop(exchange);
                return;
            }
            sendJson(exchange, 404, NOT_FOUND, "no-store");
        });
    }

    private void handlePlayerResolve(HttpExchange exchange) throws IOException {
        String name = queryParam(exchange, "name").trim();
        if (!PLAYER_NAME.matcher(name).matches()) {
            sendJson(exchange, 400, INVALID_NAME, "no-store");
            return;
        }

        Optional<ResolvedPlayer> resolved = resolvePlayer(name);
        sendJson(exchange, resolved.isPresent() ? 200 : 404,
                resolved.map(player -> playerJson(player.uuid(), player.name()))
                        .orElse(NOT_FOUND), "no-store");
    }

    private Optional<ResolvedPlayer> resolvePlayer(String name) throws IOException {
        if (Bukkit.isPrimaryThread()) return resolveOnServerThread(name);
        Future<Optional<ResolvedPlayer>> result;
        try {
            result = Bukkit.getScheduler().callSyncMethod(plugin,
                    () -> resolveOnServerThread(name));
        } catch (RuntimeException error) {
            throw new IOException("Could not schedule player lookup on the server thread", error);
        }
        try {
            return result.get(PLAYER_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            result.cancel(false);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resolving player", error);
        } catch (ExecutionException error) {
            throw new IOException("Player lookup failed on the server thread", error.getCause());
        } catch (TimeoutException error) {
            result.cancel(false);
            throw new IOException("Timed out waiting for player lookup", error);
        }
    }

    private static Optional<ResolvedPlayer> resolveOnServerThread(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return Optional.of(new ResolvedPlayer(
                online.getUniqueId(), online.getName()));

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) return Optional.empty();
        String resolvedName = offline.getName() != null ? offline.getName() : name;
        return Optional.of(new ResolvedPlayer(offline.getUniqueId(), resolvedName));
    }

    static byte[] playerJson(UUID uuid, String name) {
        JsonObject player = new JsonObject();
        player.addProperty("uuid", uuid.toString());
        player.addProperty("name", name);
        JsonObject response = new JsonObject();
        response.add("player", player);
        return bytes(response.toString());
    }

    private void createAuthChallengeContext(HttpServer target) {
        target.createContext("/api/auth/challenges", exchange -> {
            try {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }
                handleAuthChallenge(exchange);
            } catch (Exception error) {
                serveError(exchange, error);
            }
        });
    }

    private void handleAuthChallenge(HttpExchange exchange) throws IOException {
        String prefix = "/api/auth/challenges/";
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(prefix)) {
            sendJson(exchange, 404, NOT_FOUND, "no-store");
            return;
        }

        String suffix = path.substring(prefix.length());
        boolean consume = suffix.endsWith("/consume");
        String code = consume ? suffix.substring(0, suffix.length() - "/consume".length()) : suffix;
        if (!WebLoginChallengeStore.validCode(code)) {
            sendJson(exchange, 404, NOT_FOUND, "no-store");
            return;
        }

        String requiredMethod = consume ? "POST" : "GET";
        if (!exchange.getRequestMethod().equalsIgnoreCase(requiredMethod)) {
            sendJson(exchange, 405, METHOD_NOT_ALLOWED, "no-store");
            return;
        }
        WebLoginChallengeStore.Confirmation confirmation = consume
                ? challenges.consume(code) : challenges.find(code);
        sendJson(exchange, 200, challengeJson(confirmation), "no-store");
    }

    static byte[] challengeJson(WebLoginChallengeStore.Confirmation confirmation) {
        JsonObject response = new JsonObject();
        if (confirmation == null) {
            response.addProperty("status", "not_found");
        } else if (confirmation.consumed()) {
            response.addProperty("status", "consumed");
        } else {
            response.addProperty("status", "confirmed");
            JsonObject player = new JsonObject();
            player.addProperty("uuid", confirmation.playerUuid().toString());
            player.addProperty("name", confirmation.playerName());
            player.addProperty("role", confirmation.role());
            response.add("player", player);
            response.addProperty("confirmedAt", confirmation.confirmedAt());
        }
        return bytes(response.toString());
    }

    private void createCommunityBoardContext(HttpServer target) {
        target.createContext("/api/community/notices", exchange -> {
            try {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }
                if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    sendJson(exchange, 405, METHOD_NOT_ALLOWED, "no-store");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/api/community/notices")) {
                    sendJson(exchange, 200, bytes(communityBoard.listJson()), "no-store");
                    return;
                }
                String prefix = "/api/community/notices/";
                if (!path.startsWith(prefix)) {
                    sendJson(exchange, 404, NOT_FOUND, "no-store");
                    return;
                }
                String id = path.substring(prefix.length());
                if (!NOTICE_ID.matcher(id).matches()) {
                    sendJson(exchange, 404, NOT_FOUND, "no-store");
                    return;
                }
                String detail = communityBoard.detailJson(id);
                sendJson(exchange, detail == null ? 404 : 200,
                        detail == null ? NOT_FOUND : bytes(detail), "no-store");
            } catch (Exception error) {
                serveError(exchange, error);
            }
        });
    }

    static byte[] bansJson(List<BanRecord> records) {
        JsonArray response = new JsonArray();
        for (BanRecord record : records) {
            JsonObject item = new JsonObject();
            item.addProperty("playerName", record.playerName());
            item.addProperty("playerUuid", record.playerUuid() == null
                    ? "" : record.playerUuid().toString());
            item.addProperty("reason", BanSeverity.userReason(record.reason()));
            item.addProperty("bannedBy", record.source());
            item.addProperty("bannedAt", record.createdAt().toString());
            if (record.expiresAt() == null) item.add("expiresAt", JsonNull.INSTANCE);
            else item.addProperty("expiresAt", record.expiresAt().toString());
            item.addProperty("isPermanent", record.expiresAt() == null);
            response.add(item);
        }
        return bytes(response.toString());
    }

    private void createLocalContext(HttpServer target, String path, String method,
                                    ExchangeHandler handler) {
        target.createContext(path, exchange -> {
            try {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }
                if (!exchange.getRequestURI().getPath().equals(path)) {
                    sendJson(exchange, 404, NOT_FOUND, "no-store");
                    return;
                }
                if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, METHOD_NOT_ALLOWED, "no-store");
                    return;
                }
                handler.handle(exchange);
            } catch (Exception error) {
                serveError(exchange, error);
            }
        });
    }

    private void serveError(HttpExchange exchange, Exception error) throws IOException {
        plugin.getLogger().log(Level.WARNING,
                "Could not serve local API request " + exchange.getRequestURI().getPath(), error);
        sendJson(exchange, 500, INTERNAL_ERROR, "no-store");
    }

    private static boolean isLocalRequest(HttpExchange exchange) {
        var address = exchange.getRemoteAddress().getAddress();
        return address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) return "";
        String prefix = key + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static void sendJson(HttpExchange exchange, int code, byte[] body,
                                 String cacheControl) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (cacheControl != null) exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    /** Rejects non-loopback callers without sending response data. */
    private static void drop(HttpExchange exchange) {
        exchange.close();
    }
}
