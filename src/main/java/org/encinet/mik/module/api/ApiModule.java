package org.encinet.mik.module.api;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public class ApiModule implements Listener {
    private static final int RATE_LIMIT_REQUESTS = 600;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final int MAX_AUTH_FAILURES = 3;
    private static final int AUTH_FAILURE_BLOCK_MINUTES = 10;
    private static final int AUTH_BAN_MAX_SHIFTS = 6;
    private static final long BAN_HISTORY_RETENTION_MS = 24 * 60 * 60 * 1000L;

    private final JavaPlugin plugin;
    private HttpServer server;
    private ExecutorService httpExecutor;
    private String totpSecret;
    private AnnouncementModule announcementModule;

    private record RateLimitInfo(long windowStart, int requestCount) {
    }

    private record AuthFailureInfo(int failureCount, int banCount, long blockUntil) {
    }

    private record OnlinePlayerInfo(String name, long joinedAt) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, AuthFailureInfo> authFailureMap = new ConcurrentHashMap<>();
    private final Map<UUID, OnlinePlayerInfo> onlineCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setAnnouncementModule(AnnouncementModule module) {
        this.announcementModule = module;
    }

    public void start(int port) {
        plugin.saveDefaultConfig();

        totpSecret = plugin.getConfig().getString("api.totp-secret", "");
        if (totpSecret.isEmpty()) {
            plugin.getLogger().warning("api.totp-secret is not set in config.yml, API server will not start.");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bootstrapOnlinePlayers();

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
                if (info.banCount() == 0) {
                    return now > info.blockUntil();
                }
                return now > info.blockUntil() + BAN_HISTORY_RETENTION_MS;
            });
        }, 1, 1, TimeUnit.MINUTES);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            createAuthorizedContext("/api/players", "GET", this::handlePlayersOnline);
            createAuthorizedContext("/api/announcements", "GET",
                    exchange -> sendJson(exchange, 200, announcementModule.getAnnouncementsJson(), null));
            createAuthorizedContext("/api/bans", "GET", this::handleBans);

            server.createContext("/", ApiModule::drop);

            httpExecutor = Executors.newFixedThreadPool(4);
            server.setExecutor(httpExecutor);
            server.start();
            plugin.getLogger().info("API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
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
            if (!authorize(exchange, method)) {
                return;
            }
            handler.handle(exchange);
        });
    }

    private boolean authorize(HttpExchange exchange, String expectedMethod) throws IOException {
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        return checkRateLimit(exchange, clientIp)
                && checkMethod(exchange, expectedMethod)
                && checkAuth(exchange, clientIp);
    }

    private boolean checkRateLimit(HttpExchange exchange, String clientIp) throws IOException {
        long now = System.currentTimeMillis();
        RateLimitInfo info = rateLimitMap.get(clientIp);

        if (info == null || now - info.windowStart() > RATE_LIMIT_WINDOW_SECONDS * 1000L) {
            rateLimitMap.put(clientIp, new RateLimitInfo(now, 1));
            return true;
        }

        if (info.requestCount() >= RATE_LIMIT_REQUESTS) {
            drop(exchange);
            plugin.getLogger().warning("Rate limit exceeded for IP: " + clientIp);
            return false;
        }

        rateLimitMap.put(clientIp, new RateLimitInfo(info.windowStart(), info.requestCount() + 1));
        return true;
    }

    private boolean checkMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            drop(exchange);
            return false;
        }
        return true;
    }

    private boolean checkAuth(HttpExchange exchange, String clientIp) throws IOException {
        AuthFailureInfo failureInfo = authFailureMap.get(clientIp);
        long now = System.currentTimeMillis();

        if (failureInfo != null && now < failureInfo.blockUntil()) {
            drop(exchange);
            plugin.getLogger().warning("Blocked authentication attempt from IP: " + clientIp);
            return false;
        }

        String token = exchange.getRequestHeaders().getFirst("X-TOTP-Token");
        if (!HmacTimestamp.verify(totpSecret, token)) {
            int prevFailures = failureInfo != null && failureInfo.banCount() > 0
                    ? 0 : failureInfo != null ? failureInfo.failureCount() : 0;
            int banCount = failureInfo != null ? failureInfo.banCount() : 0;
            int failureCount = prevFailures + 1;

            if (failureCount >= MAX_AUTH_FAILURES) {
                banCount++;
                long blockMs = AUTH_FAILURE_BLOCK_MINUTES * 60 * 1000L * (1L << Math.min(banCount - 1, AUTH_BAN_MAX_SHIFTS));
                authFailureMap.put(clientIp, new AuthFailureInfo(0, banCount, now + blockMs));
                plugin.getLogger().warning("IP blocked (ban #" + banCount + ", " + (blockMs / 60000) + " min): " + clientIp);
            } else {
                authFailureMap.put(clientIp, new AuthFailureInfo(
                        failureCount,
                        banCount,
                        failureInfo != null ? failureInfo.blockUntil() : 0
                ));
            }
            drop(exchange);
            return false;
        }

        if (failureInfo != null && failureInfo.banCount() > 0) {
            authFailureMap.put(clientIp, new AuthFailureInfo(0, failureInfo.banCount(), 0));
        } else {
            authFailureMap.remove(clientIp);
        }
        return true;
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
        if (cacheControl != null) {
            exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        }
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
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
     * The OS sends a TCP FIN/RST; the client receives an empty response error.
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

        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
    }
}
