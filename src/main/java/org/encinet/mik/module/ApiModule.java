package org.encinet.mik.module;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.util.CloudflareSpoof;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApiModule {
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
    private AnnouncementModule announcementModule;

    private record RateLimitInfo(long windowStart, int requestCount) {
    }

    private record AuthFailureInfo(int failureCount, int banCount, long blockUntil) {
    }

    private final Map<String, RateLimitInfo> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, AuthFailureInfo> authFailureMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setAnnouncementModule(AnnouncementModule module) {
        this.announcementModule = module;
    }

    public void start(int port) {
        plugin.saveDefaultConfig();
        apiKey = plugin.getConfig().getString("api.key", "");
        if (apiKey.isEmpty()) {
            plugin.getLogger().warning("api.key is not set in config.yml, API server will not start.");
            return;
        }

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

                sendJson(exchange, 200, announcementModule.getAnnouncementsJson());
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

            server.createContext("/", exchange -> {
                try {
                    CloudflareSpoof.drop(exchange);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to drop connection: " + e.getMessage());
                }
            });

            httpExecutor = Executors.newFixedThreadPool(4);
            server.setExecutor(httpExecutor);
            server.start();
            plugin.getLogger().info("API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
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
//            sendJson(exchange, 429, "{\"error\":\"rate_limit_exceeded\"}");
            CloudflareSpoof.drop(exchange);
            plugin.getLogger().warning("Rate limit exceeded for IP: " + clientIp);
            return false;
        }

        rateLimitMap.put(clientIp, new RateLimitInfo(info.windowStart(), info.requestCount() + 1));
        return true;
    }

    private boolean checkMethod(com.sun.net.httpserver.HttpExchange exchange, String expectedMethod) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
//            sendJson(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            CloudflareSpoof.drop(exchange);
            return false;
        }
        return true;
    }

    private boolean checkAuth(com.sun.net.httpserver.HttpExchange exchange, String clientIp) throws IOException {
        AuthFailureInfo failureInfo = authFailureMap.get(clientIp);
        long now = System.currentTimeMillis();

        if (failureInfo != null && now < failureInfo.blockUntil()) {
//            sendJson(exchange, 403, "{\"error\":\"temporarily_blocked\"}");
            CloudflareSpoof.drop(exchange);
            plugin.getLogger().warning("Blocked authentication attempt from IP: " + clientIp);
            return false;
        }

        String key = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (!apiKey.equals(key)) {
            // If a previous ban has expired, reset failure count but preserve banCount
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
                authFailureMap.put(clientIp, new AuthFailureInfo(failureCount, banCount, failureInfo != null ? failureInfo.blockUntil() : 0));
            }
//            sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            CloudflareSpoof.drop(exchange);
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