package org.encinet.mik.module;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ApiModule — Lightweight HTTP API server for external integrations.
 *
 * <p>Built on the JDK built-in {@link com.sun.net.httpserver.HttpServer}.
 * Intended for use by websites, bots, or dashboards that need to query
 * server state without direct Minecraft protocol access.
 *
 * <hr>
 * <h2>Authentication</h2>
 * Every request must include the following header:
 * <pre>
 *   X-API-Key: &lt;value of api.key in config.yml&gt;
 * </pre>
 * If {@code api.key} is empty the server will not start.
 * An incorrect or missing key returns {@code 401 Unauthorized}.
 *
 * <hr>
 * <h2>Endpoints</h2>
 *
 * <h3>GET /api/players</h3>
 * Returns the current number of online players and player list.
 * <pre>
 * Response 200 OK
 * Content-Type: application/json
 *
 * {
 *   "count": 3,
 *   "players": [
 *     {"name": "Steve", "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5"},
 *     {"name": "Alex", "uuid": "8667ba71-b85a-4004-af54-457a9734eed7"}
 *   ]
 * }
 * </pre>
 * Fields:
 * <ul>
 *   <li>{@code count} (integer) — number of players currently online</li>
 *   <li>{@code players} (array) — list of online players, each with name and uuid</li>
 * </ul>
 *
 * <h3>GET /api/announcements</h3>
 * Returns all announcements loaded from {@code announcements.txt}.
 * <pre>
 * Response 200 OK
 * Content-Type: application/json
 *
 * [
 *   {"timestamp": 1704067200, "content": "Server is now open!"},
 *   {"timestamp": 1707868800, "content": "Valentine event started!"}
 * ]
 * </pre>
 * Fields per object:
 * <ul>
 *   <li>{@code timestamp} (integer) — Unix epoch seconds (server local timezone)</li>
 *   <li>{@code content}   (string)  — announcement body, may contain newlines</li>
 * </ul>
 *
 * <h3>GET /api/bans</h3>
 * Returns all active ban records from the server's ban list in real-time.
 * <pre>
 * Response 200 OK
 * Content-Type: application/json
 *
 * [
 *   {
 *     "id": 1,
 *     "playerName": "Griefer123",
 *     "playerUuid": "",
 *     "reason": "恶意破坏他人建筑",
 *     "bannedBy": "Admin",
 *     "bannedAt": "2024-01-15T10:30:00Z",
 *     "expiresAt": null,
 *     "isPermanent": true
 *   }
 * ]
 * </pre>
 * Fields per object:
 * <ul>
 *   <li>{@code id} (integer) — sequential index starting from 1</li>
 *   <li>{@code playerName} (string) — banned player name</li>
 *   <li>{@code playerUuid} (string) — currently empty, reserved for future use</li>
 *   <li>{@code reason} (string) — ban reason</li>
 *   <li>{@code bannedBy} (string) — administrator who issued the ban</li>
 *   <li>{@code bannedAt} (string) — ban timestamp in ISO 8601 format</li>
 *   <li>{@code expiresAt} (string|null) — unban timestamp, null for permanent bans</li>
 *   <li>{@code isPermanent} (boolean) — whether this is a permanent ban</li>
 * </ul>
 * <p>Note: This endpoint retrieves data in real-time from Bukkit's ban system.
 *
 * <hr>
 * <h2>Error Responses</h2>
 * <pre>
 * 401 Unauthorized  {"error": "unauthorized"}  — missing or invalid X-API-Key
 * </pre>
 *
 * <hr>
 * <h2>Announcement File Format (announcements.txt)</h2>
 * Located in the plugin data folder. Entries are separated by {@code ---}.
 * The first line of each entry is the timestamp ({@code yyyy-MM-dd HH:mm:ss});
 * the remaining lines are the announcement body.
 * <pre>
 *   2024-01-01 12:00:00
 *   Server is now open. Welcome!
 *   ---
 *   2024-02-14 08:00:00
 *   Valentine's Day event has started!
 * </pre>
 * Use {@code /reloadannouncements} (alias: {@code /reloadannounce}) to hot-reload
 * the file without restarting the server.
 * Required permission: {@code mik.command.reloadannouncements}
 *
 * <hr>
 * <h2>Configuration (config.yml)</h2>
 * <pre>
 *   api:
 *     key: "your-secret-key"   # leave empty to disable the API server
 *     port: 8080               # passed to start(port) by the plugin main class
 * </pre>
 */
public class ApiModule {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RATE_LIMIT_REQUESTS = 30;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final int MAX_AUTH_FAILURES = 3;
    private static final int AUTH_FAILURE_BLOCK_MINUTES = 10;
    private static final int AUTH_BAN_MAX_SHIFTS = 6;       // max block = 10 * 2^6 = 640 min ≈ 10.7 h
    private static final long BAN_HISTORY_RETENTION_MS = 24 * 60 * 60 * 1000L; // 24 h

    private final JavaPlugin plugin;
    private HttpServer server;
    private ExecutorService httpExecutor;
    private String apiKey;

    private record RateLimitInfo(long windowStart, int requestCount) {
    }

    private record AuthFailureInfo(int failureCount, int banCount, long blockUntil) {
    }

    private volatile String announcementsJson = "[]";
    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, AuthFailureInfo> authFailureMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(int port) {
        plugin.saveDefaultConfig();
        apiKey = plugin.getConfig().getString("api.key", "");
        if (apiKey.isEmpty()) {
            plugin.getLogger().warning("api.key is not set in config.yml, API server will not start.");
            return;
        }

        saveDefaultAnnouncements();
        reloadAnnouncements();

        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mik-api-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            rateLimitMap.entrySet().removeIf(entry ->
                    now - entry.getValue().windowStart() > RATE_LIMIT_WINDOW_SECONDS * 1000L);
            authFailureMap.entrySet().removeIf(entry -> {
                AuthFailureInfo info = entry.getValue();
                if (info.banCount() == 0) return now > info.blockUntil();
                return now > info.blockUntil() + BAN_HISTORY_RETENTION_MS;
            });
        }, 1, 1, TimeUnit.MINUTES);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/api/players", exchange -> {
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!checkRateLimit(exchange, clientIp)) return;
                if (!checkMethod(exchange, "GET")) return;
                if (!checkAuth(exchange, clientIp)) return;

                var onlinePlayers = Bukkit.getOnlinePlayers();
                int count = onlinePlayers.size();

                StringBuilder sb = new StringBuilder("{\"count\":").append(count).append(",\"players\":[");
                int index = 0;
                for (var player : onlinePlayers) {
                    if (index > 0) sb.append(",");
                    sb.append("{\"name\":\"").append(escapeJson(player.getName())).append("\"")
                            .append(",\"uuid\":\"").append(player.getUniqueId()).append("\"}");
                    index++;
                }
                sb.append("]}");

                sendJson(exchange, 200, sb.toString());
            });

            server.createContext("/api/announcements", exchange -> {
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!checkRateLimit(exchange, clientIp)) return;
                if (!checkMethod(exchange, "GET")) return;
                if (!checkAuth(exchange, clientIp)) return;

                sendJson(exchange, 200, announcementsJson);
            });

            server.createContext("/api/bans", exchange -> {
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                if (!checkRateLimit(exchange, clientIp)) return;
                if (!checkMethod(exchange, "GET")) return;
                if (!checkAuth(exchange, clientIp)) return;

                var banList = Bukkit.getBanList(BanListType.PROFILE);
                var banEntries = banList.getEntries();

                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (var entry : banEntries) {
                    if (!first) sb.append(",");
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
                sendJson(exchange, 200, sb.toString());
            });

            httpExecutor = Executors.newFixedThreadPool(4);
            server.setExecutor(httpExecutor);
            server.start();
            plugin.getLogger().info("API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void reloadAnnouncements() {
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) {
            plugin.getLogger().warning("announcements.txt not found.");
            return;
        }
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] blocks = raw.split("---");
            StringBuilder sb = new StringBuilder("[");
            int count = 0;
            for (String block : blocks) {
                String trimmed = block.strip();
                if (trimmed.isEmpty()) continue;
                int newline = trimmed.indexOf('\n');
                if (newline == -1) continue;
                String dateLine = trimmed.substring(0, newline).strip();
                String content = trimmed.substring(newline + 1).strip();
                try {
                    LocalDateTime ldt = LocalDateTime.parse(dateLine, DATE_FMT);
                    long ts = ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
                    if (count > 0) sb.append(",");
                    sb.append("{\"timestamp\":").append(ts)
                            .append(",\"content\":\"").append(escapeJson(content)).append("\"}");
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid date in announcements.txt: " + dateLine);
                }
            }
            sb.append("]");
            announcementsJson = sb.toString();
            plugin.getLogger().info("Loaded " + count + " announcement(s).");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read announcements.txt: " + e.getMessage());
        }
    }

    private void saveDefaultAnnouncements() {
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) {
            plugin.saveResource("announcements.txt", false);
        }
    }

    private boolean checkRateLimit(com.sun.net.httpserver.HttpExchange exchange, String clientIp) throws IOException {
        long now = System.currentTimeMillis();
        RateLimitInfo info = rateLimitMap.get(clientIp);

        if (info == null || now - info.windowStart() > RATE_LIMIT_WINDOW_SECONDS * 1000L) {
            rateLimitMap.put(clientIp, new RateLimitInfo(now, 1));
            return true;
        }

        if (info.requestCount() >= RATE_LIMIT_REQUESTS) {
            sendJson(exchange, 429, "{\"error\":\"rate_limit_exceeded\"}");
            plugin.getLogger().warning("Rate limit exceeded for IP: " + clientIp);
            return false;
        }

        rateLimitMap.put(clientIp, new RateLimitInfo(info.windowStart(), info.requestCount() + 1));
        return true;
    }

    private boolean checkMethod(com.sun.net.httpserver.HttpExchange exchange, String expectedMethod) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return false;
        }
        return true;
    }

    private boolean checkAuth(com.sun.net.httpserver.HttpExchange exchange, String clientIp) throws IOException {
        AuthFailureInfo failureInfo = authFailureMap.get(clientIp);
        long now = System.currentTimeMillis();

        if (failureInfo != null && now < failureInfo.blockUntil()) {
            sendJson(exchange, 403, "{\"error\":\"temporarily_blocked\"}");
            plugin.getLogger().warning("Blocked authentication attempt from IP: " + clientIp);
            return false;
        }

        String key = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (!apiKey.equals(key)) {
            // If a previous ban has expired, reset failure count but preserve banCount
            int prevFailures = (failureInfo != null && failureInfo.blockUntil() <= now && failureInfo.banCount() > 0)
                    ? 0 : (failureInfo != null ? failureInfo.failureCount() : 0);
            int banCount = failureInfo != null ? failureInfo.banCount() : 0;
            int failureCount = prevFailures + 1;

            if (failureCount >= MAX_AUTH_FAILURES) {
                banCount++;
                long blockMs = AUTH_FAILURE_BLOCK_MINUTES * 60 * 1000L * (1L << Math.min(banCount - 1, AUTH_BAN_MAX_SHIFTS));
                authFailureMap.put(clientIp, new AuthFailureInfo(0, banCount, now + blockMs));
                plugin.getLogger().warning("IP blocked (ban #" + banCount + ", " + (blockMs / 60000) + " min): " + clientIp);
            } else {
                authFailureMap.put(clientIp, new AuthFailureInfo(failureCount, banCount, failureInfo != null ? failureInfo.blockUntil() : 0));
            }
            sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return false;
        }

        // Auth success: clear failure count but keep ban history so repeat offenders keep escalating
        if (failureInfo != null && failureInfo.banCount() > 0) {
            authFailureMap.put(clientIp, new AuthFailureInfo(0, failureInfo.banCount(), 0));
        } else {
            authFailureMap.remove(clientIp);
        }
        return true;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("reloadannouncements")
                .requires(source -> source.getSender().hasPermission("mik.command.reloadannouncements"))
                .executes(ctx -> {
                    reloadAnnouncements();
                    ctx.getSource().getSender().sendMessage(
                            Component.text("公告已重新加载。", NamedTextColor.GREEN)
                    );
                    return Command.SINGLE_SUCCESS;
                }).build(), "重新加载公告文件", List.of("reloadannounce")));
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdown();
        }
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
    }
}