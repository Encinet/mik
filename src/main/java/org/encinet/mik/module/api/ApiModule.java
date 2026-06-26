package org.encinet.mik.module.api;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.communication.AnnouncementModule;
import org.encinet.mik.util.HmacTimestamp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class ApiModule implements Listener {
    private static final String AUTH_HEADER = "X-HMAC-Token";
    private static final String COMMAND_PERMISSION = "mik.command.api";
    private static final String ROUTES = "/api/players, /api/announcements, /api/bans";

    private final JavaPlugin plugin;
    private final Map<UUID, OnlinePlayerInfo> onlineCache = new ConcurrentHashMap<>();

    private HttpServer server;
    private ExecutorService httpExecutor;
    private String hmacSecret;
    private AnnouncementModule announcementModule;
    private volatile boolean debugMode;
    private int listenPort;

    private record OnlinePlayerInfo(String name, long joinedAt) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setAnnouncementModule(AnnouncementModule module) {
        this.announcementModule = module;
    }

    public void start(int port) {
        plugin.saveDefaultConfig();
        listenPort = port;

        hmacSecret = plugin.getConfig().getString("api.totp-secret", "");
        if (hmacSecret.isEmpty()) {
            plugin.getLogger().warning("api.totp-secret is not set in config.yml, API server will not start.");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bootstrapOnlinePlayers();

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            createAuthorizedContext("/api/players", "GET", this::handlePlayersOnline);
            createAuthorizedContext("/api/announcements", "GET",
                    exchange -> sendJson(exchange, 200, announcementModule.getAnnouncementsJson(), null));
            createAuthorizedContext("/api/bans", "GET", this::handleBans);

            server.createContext("/", exchange -> fail(exchange, 404, "unknown_path",
                    "Unknown API path: " + exchange.getRequestURI().getPath(), null));

            httpExecutor = Executors.newFixedThreadPool(4);
            server.setExecutor(httpExecutor);
            server.start();
            plugin.getLogger().info("API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("mikapi")
                        .requires(source -> source.getSender().hasPermission(COMMAND_PERMISSION))
                        .executes(ctx -> {
                            sendStatus(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    sendStatus(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("on")
                                .executes(ctx -> setDebugModeCommand(ctx.getSource().getSender(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setDebugModeCommand(ctx.getSource().getSender(), false)))
                        .then(Commands.literal("debug")
                                .executes(ctx -> {
                                    sendStatus(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("on")
                                        .executes(ctx -> setDebugModeCommand(ctx.getSource().getSender(), true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setDebugModeCommand(ctx.getSource().getSender(), false))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    clearRuntimeState();
                                    ctx.getSource().getSender().sendMessage(Component.text(
                                            "Mik API runtime state cleared.", NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Manage Mik API debug mode"));
    }

    private int setDebugModeCommand(CommandSender sender, boolean enabled) {
        setDebugMode(enabled);
        sender.sendMessage(Component.text("Mik API debug mode "
                        + (enabled ? "enabled." : "disabled."), enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(statusComponent()));
        return Command.SINGLE_SUCCESS;
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(statusComponent());
    }

    private Component statusComponent() {
        boolean running = server != null && httpExecutor != null && !httpExecutor.isShutdown();
        boolean secretConfigured = hmacSecret != null && !hmacSecret.isBlank();

        return Component.text("Mik API status", NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text("Server: " + (running ? "running" : "stopped")
                        + " port=" + listenPort, running ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Debug mode: " + (debugMode ? "enabled" : "disabled"),
                        debugMode ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("External errors: "
                        + (debugMode ? "JSON details" : "silent drop"), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Auth header: " + AUTH_HEADER, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Secret configured: " + (secretConfigured ? "yes" : "no"),
                        secretConfigured ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Online cache: " + onlineCache.size() + " player(s)", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Routes: " + ROUTES, NamedTextColor.GRAY));
    }

    private void setDebugMode(boolean enabled) {
        debugMode = enabled;
        clearRuntimeState();
        plugin.getLogger().info("API debug mode " + (enabled ? "enabled" : "disabled") + "; runtime state cleared.");
    }

    private void clearRuntimeState() {
        bootstrapOnlinePlayers();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        onlineCache.put(player.getUniqueId(), new OnlinePlayerInfo(player.getName(), Instant.now().getEpochSecond()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlineCache.remove(event.getPlayer().getUniqueId());
    }

    private void bootstrapOnlinePlayers() {
        onlineCache.clear();
        long now = Instant.now().getEpochSecond();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineCache.put(player.getUniqueId(), new OnlinePlayerInfo(player.getName(), now));
        }
    }

    private void handlePlayersOnline(HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"online\":").append(onlineCache.size()).append(",\"players\":[");

        List<Map.Entry<UUID, OnlinePlayerInfo>> entries = sortedOnlineEntries();

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, OnlinePlayerInfo> entry = entries.get(i);
            OnlinePlayerInfo info = entry.getValue();
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"uuid\":\"").append(entry.getKey()).append("\"")
                    .append(",\"name\":\"").append(escapeJson(info.name())).append("\"")
                    .append(",\"joined_at\":\"").append(formatIso(info.joinedAt())).append("\"}");
        }
        sb.append("]}");

        sendJson(exchange, 200, sb.toString(), "no-store");
    }

    private void handleBans(HttpExchange exchange) throws IOException {
        var banList = Bukkit.getBanList(BanListType.PROFILE);
        var banEntries = banList.getEntries();

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var entry : banEntries) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            PlayerProfile profile = (PlayerProfile) entry.getBanTarget();
            String playerName = profile.getName() != null ? profile.getName() : "";
            String playerUuid = profile.getId() != null ? profile.getId().toString() : "";
            String reason = entry.getReason() != null ? entry.getReason() : "";
            String bannedBy = entry.getSource();
            String bannedAt = entry.getCreated().toInstant().toString();
            String expiresAt = entry.getExpiration() != null ? entry.getExpiration().toInstant().toString() : null;
            boolean isPermanent = expiresAt == null;

            sb.append("{\"playerName\":\"").append(escapeJson(playerName)).append("\"")
                    .append(",\"playerUuid\":\"").append(escapeJson(playerUuid)).append("\"")
                    .append(",\"reason\":\"").append(escapeJson(reason)).append("\"")
                    .append(",\"bannedBy\":\"").append(escapeJson(bannedBy)).append("\"")
                    .append(",\"bannedAt\":\"").append(escapeJson(bannedAt)).append("\"")
                    .append(",\"expiresAt\":").append(expiresAt == null ? "null" : "\"" + escapeJson(expiresAt) + "\"")
                    .append(",\"isPermanent\":").append(isPermanent)
                    .append("}");
        }
        sb.append("]");
        sendJson(exchange, 200, sb.toString(), null);
    }

    private List<Map.Entry<UUID, OnlinePlayerInfo>> sortedOnlineEntries() {
        List<Map.Entry<UUID, OnlinePlayerInfo>> entries = new ArrayList<>(onlineCache.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private void createAuthorizedContext(String path, String method, ExchangeHandler handler) {
        server.createContext(path, exchange -> {
            try {
                if (!exchange.getRequestURI().getPath().equals(path)) {
                    fail(exchange, 404, "invalid_path",
                            "Expected path " + path + " but got " + exchange.getRequestURI().getPath(), null);
                    return;
                }

                if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                    fail(exchange, 405, "invalid_method",
                            "Expected method " + method + " but got " + exchange.getRequestMethod(), null);
                    return;
                }

                String token = exchange.getRequestHeaders().getFirst(AUTH_HEADER);
                if (!HmacTimestamp.verify(hmacSecret, token)) {
                    fail(exchange, 403, authFailureReason(token), authFailureMessage(token), null);
                    return;
                }

                handler.handle(exchange);
            } catch (Exception error) {
                fail(exchange, 500, "internal_error", error.getMessage(), error);
            }
        });
    }

    private String authFailureReason(String token) {
        if (token == null || token.isBlank()) {
            return "missing_auth_token";
        }
        if (token.length() != 64) {
            return "invalid_auth_token_length";
        }
        if (!token.matches("[a-f0-9]{64}")) {
            return "invalid_auth_token_format";
        }
        return "invalid_auth_token";
    }

    private String authFailureMessage(String token) {
        return switch (authFailureReason(token)) {
            case "missing_auth_token" -> "Missing " + AUTH_HEADER + " header.";
            case "invalid_auth_token_length" -> AUTH_HEADER + " must be a 64-character HMAC-SHA256 hex digest.";
            case "invalid_auth_token_format" -> AUTH_HEADER + " must contain lowercase hex characters only.";
            default -> "Invalid HMAC timestamp token.";
        };
    }

    private void fail(HttpExchange exchange, int status, String code, String message, Exception error) throws IOException {
        logFailure(exchange, status, code, message, error);

        if (!debugMode) {
            drop(exchange);
            return;
        }

        sendJson(exchange, status, debugErrorJson(status, code, message, error), "no-store");
    }

    private void logFailure(HttpExchange exchange, int status, String code, String message, Exception error) {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        String errorDetails = error == null ? "" : " error=" + error.getClass().getSimpleName();
        plugin.getLogger().warning("API request failed: status=" + status
                + " code=" + code
                + " debug=" + debugMode
                + " ip=" + clientIp
                + " method=" + exchange.getRequestMethod()
                + " path=" + exchange.getRequestURI().getPath()
                + " message=" + message
                + errorDetails);
    }

    private String debugErrorJson(int status, String code, String message, Exception error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"error\":\"").append(escapeJson(code)).append("\"")
                .append(",\"status\":").append(status)
                .append(",\"message\":\"").append(escapeJson(message == null ? "" : message)).append("\"");

        if (error != null) {
            sb.append(",\"exception\":\"").append(escapeJson(error.getClass().getName())).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    private void sendJson(HttpExchange exchange, int code, String body, String cacheControl) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean useGzip = acceptEncoding != null && acceptEncoding.contains("gzip");

        if (useGzip) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(bytes);
            }
            bytes = baos.toByteArray();
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        }

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Connection", "close");
        if (cacheControl != null) {
            exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        }
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
            os.flush();
        }
    }

    private String formatIso(long epochSecond) {
        return Instant.ofEpochSecond(epochSecond).toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Immediately closes the exchange without writing a single byte.
     */
    public static void drop(HttpExchange exchange) {
        try {
            exchange.close();
        } catch (Exception ignored) {
            // Already closed or reset.
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdown();
        }
        onlineCache.clear();
    }
}
