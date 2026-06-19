package org.encinet.mik.module.api;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import org.h2.jdbcx.JdbcConnectionPool;
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
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final long OPEN_SESSION_SENTINEL = 9_999_999_999L;
    private static final int MAX_HISTORY_POINTS = 5_000;
    private static final long DEFAULT_HISTORY_INTERVAL_SECONDS = 60L * 60;

    private final JavaPlugin plugin;
    private HttpServer server;
    private ExecutorService httpExecutor;
    private ExecutorService sessionExecutor;
    private String totpSecret;
    private AnnouncementModule announcementModule;
    private JdbcConnectionPool sessionStore;

    private record RateLimitInfo(long windowStart, int requestCount) {
    }

    private record AuthFailureInfo(int failureCount, int banCount, long blockUntil) {
    }

    private record OnlinePlayerInfo(String name, long joinedAt) {
    }

    private record SessionSnapshot(String uuid, String name, long joinedAt, long effectiveLeft) {
    }

    private record HistoryPoint(long timestamp, long durationSeconds, double online, List<String> players) {
    }

    private record LiveHistorySnapshot(long timestamp, double online, List<String> players) {
    }

    private record HistorySummary(long peakTime, double peakOnline, double avgOnline, int totalUniquePlayers) {
    }

    private record PlayerSessionRecord(String name, long joinedAt, Long leftAt, long durationSec) {
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
        if (!initializeSessionStore()) {
            return;
        }

        sessionExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mik-api-session-store");
            t.setDaemon(true);
            return t;
        });

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bootstrapOnlinePlayers();

        totpSecret = plugin.getConfig().getString("api.totp-secret", "");
        if (totpSecret.isEmpty()) {
            plugin.getLogger().warning("api.totp-secret is not set in config.yml, player history will be tracked but API server will not start.");
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
                if (info.banCount() == 0) {
                    return now > info.blockUntil();
                }
                return now > info.blockUntil() + BAN_HISTORY_RETENTION_MS;
            });
        }, 1, 1, TimeUnit.MINUTES);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            createAuthorizedContext("/api/players/online", "GET", this::handlePlayersOnline);
            createAuthorizedContext("/api/players/history", "GET", this::handlePlayersHistory);
            createAuthorizedContext("/api/players/", "GET", this::handleSinglePlayerHistory);
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
        long now = Instant.now().getEpochSecond();
        onlineCache.put(player.getUniqueId(), new OnlinePlayerInfo(player.getName(), now));

        submitSessionWrite(() -> insertSession(player.getUniqueId(), player.getName(), now));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        long now = Instant.now().getEpochSecond();
        onlineCache.remove(player.getUniqueId());

        submitSessionWrite(() -> closeOpenSessionsForPlayer(player.getUniqueId(), now));
    }

    private boolean initializeSessionStore() {
        File dbFile = new File(plugin.getDataFolder(), "player-history");
        String jdbcUrl = "jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL;WRITE_DELAY=500;CACHE_SIZE=65536";

        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("H2 driver not found. Check paper-plugin.yml loader configuration.");
            return false;
        }

        sessionStore = JdbcConnectionPool.create(jdbcUrl, "sa", "");
        sessionStore.setMaxConnections(8);

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        uuid VARCHAR(36) NOT NULL,
                        name VARCHAR(32) NOT NULL,
                        joined_at BIGINT NOT NULL,
                        left_at BIGINT
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_time ON sessions(joined_at, left_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_uuid ON sessions(uuid)");
            repairOrphanedSessions();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize player history store: " + e.getMessage());
            return false;
        }
    }

    private void repairOrphanedSessions() throws SQLException {
        long now = Instant.now().getEpochSecond();
        Set<UUID> onlineUuids = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineUuids.add(player.getUniqueId());
        }

        String findOrphans = "SELECT id, uuid, joined_at FROM sessions WHERE left_at IS NULL";
        List<long[]> orphanIds = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(findOrphans)) {
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                long id = rs.getLong("id");
                long joinedAt = rs.getLong("joined_at");
                try {
                    UUID playerUuid = UUID.fromString(uuid);
                    if (!onlineUuids.contains(playerUuid)) {
                        long estimatedLeft = Math.max(joinedAt + 60, now - 300);
                        orphanIds.add(new long[]{id, estimatedLeft});
                    }
                } catch (IllegalArgumentException ignored) {
                    long estimatedLeft = Math.max(joinedAt + 60, now - 300);
                    orphanIds.add(new long[]{id, estimatedLeft});
                }
            }
        }

        if (!orphanIds.isEmpty()) {
            plugin.getLogger().warning("Found " + orphanIds.size() + " orphaned sessions from previous server run. Repairing...");
            try (Connection connection = openConnection();
                 PreparedStatement ps = connection.prepareStatement("UPDATE sessions SET left_at = ? WHERE id = ?")) {
                for (long[] pair : orphanIds) {
                    ps.setLong(1, pair[1]);
                    ps.setLong(2, pair[0]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            plugin.getLogger().info("Repaired " + orphanIds.size() + " orphaned sessions.");
        }
    }

    private void bootstrapOnlinePlayers() {
        long now = Instant.now().getEpochSecond();
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player player : onlinePlayers) {
            onlineCache.put(player.getUniqueId(), new OnlinePlayerInfo(player.getName(), now));
        }
        insertSessions(onlinePlayers, now);
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

    private void handlePlayersHistory(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String fromParam = params.get("from");
        String toParam = params.get("to");
        if (fromParam == null || toParam == null) {
            sendError(exchange, 400, "missing_from_or_to");
            return;
        }

        Instant from;
        Instant to;
        try {
            from = Instant.parse(fromParam);
            to = Instant.parse(toParam);
        } catch (DateTimeParseException e) {
            sendError(exchange, 400, "invalid_timestamp");
            return;
        }

        if (!from.isBefore(to)) {
            sendError(exchange, 400, "invalid_range");
            return;
        }

        Long intervalSeconds = parseIntervalSeconds(params.get("interval"));
        if (intervalSeconds == null) {
            sendError(exchange, 400, "invalid_interval");
            return;
        }

        long fromTs = from.getEpochSecond();
        long toTs = to.getEpochSecond();
        long totalPoints = (toTs - fromTs + intervalSeconds - 1) / intervalSeconds;
        if (totalPoints <= 0) {
            sendError(exchange, 400, "invalid_range");
            return;
        }
        if (totalPoints > MAX_HISTORY_POINTS) {
            sendError(exchange, 400, "too_many_points");
            return;
        }

        List<SessionSnapshot> sessions;
        try {
            sessions = queryOverlappingSessions(fromTs, toTs);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query player history: " + e.getMessage());
            sendError(exchange, 500, "history_query_failed");
            return;
        }

        long nowTs = Instant.now().getEpochSecond();
        List<HistoryPoint> points = sampleHistory(sessions, fromTs, toTs, intervalSeconds);
        HistorySummary summary = summarizeHistory(points, sessions, fromTs);
        LiveHistorySnapshot liveSnapshot = buildLiveHistorySnapshot(fromTs, toTs, nowTs);
        int liveSnapshotIndex = findLiveHistoryPointIndex(points, nowTs, toTs);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"meta\":{")
                .append("\"from\":\"").append(formatIso(fromTs)).append("\",")
                .append("\"to\":\"").append(formatIso(toTs)).append("\",")
                .append("\"interval\":").append(intervalSeconds).append(",")
                .append("\"total_points\":").append(points.size())
                .append("},\"summary\":{")
                .append("\"peak_online\":").append(formatJsonNumber(summary.peakOnline())).append(",")
                .append("\"peak_time\":\"").append(formatIso(summary.peakTime())).append("\",")
                .append("\"avg_online\":").append(formatJsonNumber(summary.avgOnline())).append(",")
                .append("\"total_unique_players\":").append(summary.totalUniquePlayers())
                .append("},\"data\":[");

        for (int i = 0; i < points.size(); i++) {
            HistoryPoint point = points.get(i);
            boolean useLiveSnapshot = liveSnapshot != null && i == liveSnapshotIndex;
            long pointTimestamp = useLiveSnapshot ? liveSnapshot.timestamp() : point.timestamp();
            double pointOnline = useLiveSnapshot ? liveSnapshot.online() : point.online();
            List<String> pointPlayers = useLiveSnapshot ? liveSnapshot.players() : point.players();
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"timestamp\":\"").append(formatIso(pointTimestamp)).append("\",")
                    .append("\"online\":").append(formatJsonNumber(pointOnline)).append(",\"players\":");
            appendJsonStringArray(sb, pointPlayers);
            sb.append("}");
        }
        sb.append("]}");

        String cacheControl = toTs < nowTs ? "public, max-age=300" : null;
        sendJson(exchange, 200, sb.toString(), cacheControl);
    }

    private void handleSinglePlayerHistory(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/players/";
        if (!path.startsWith(prefix) || !path.endsWith("/history")) {
            sendError(exchange, 404, "not_found");
            return;
        }

        String uuidPart = path.substring(prefix.length(), path.length() - "/history".length());
        if (uuidPart.isEmpty() || uuidPart.contains("/")) {
            sendError(exchange, 404, "not_found");
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "invalid_uuid");
            return;
        }

        long now = Instant.now().getEpochSecond();
        List<PlayerSessionRecord> sessions;
        try {
            sessions = queryPlayerSessions(playerUuid.toString(), now);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to query player session history for " + playerUuid + ": " + e.getMessage());
            sendError(exchange, 500, "history_query_failed");
            return;
        }

        if (sessions.isEmpty()) {
            sendError(exchange, 404, "player_not_found");
            return;
        }

        String playerName = sessions.stream()
                .map(PlayerSessionRecord::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElseGet(() -> {
                    OnlinePlayerInfo info = onlineCache.get(playerUuid);
                    return info != null ? info.name() : "";
                });

        long totalDurationSec = 0;
        long firstSeen = Long.MAX_VALUE;
        for (PlayerSessionRecord session : sessions) {
            totalDurationSec += session.durationSec();
            firstSeen = Math.min(firstSeen, session.joinedAt());
        }

        long totalHours = Math.round(totalDurationSec / 3600.0);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"uuid\":\"").append(playerUuid).append("\",")
                .append("\"name\":\"").append(escapeJson(playerName)).append("\",")
                .append("\"sessions\":[");

        for (int i = 0; i < sessions.size(); i++) {
            PlayerSessionRecord session = sessions.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"joined_at\":\"").append(formatIso(session.joinedAt())).append("\",")
                    .append("\"left_at\":");
            if (session.leftAt() == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(formatIso(session.leftAt())).append("\"");
            }
            sb.append(",\"duration_min\":").append(session.durationSec() / 60).append("}");
        }

        sb.append("],\"stats\":{")
                .append("\"total_sessions\":").append(sessions.size()).append(",")
                .append("\"total_hours\":").append(totalHours).append(",")
                .append("\"first_seen\":\"").append(formatIso(firstSeen)).append("\"")
                .append("}}");

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

    private List<SessionSnapshot> queryOverlappingSessions(long fromTs, long toTs) throws SQLException {
        String sql = """
                SELECT uuid, name, joined_at, COALESCE(left_at, ?) AS effective_left
                FROM sessions
                WHERE joined_at < ?
                  AND COALESCE(left_at, ?) > ?
                """;

        List<SessionSnapshot> sessions = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, OPEN_SESSION_SENTINEL);
            ps.setLong(2, toTs);
            ps.setLong(3, OPEN_SESSION_SENTINEL);
            ps.setLong(4, fromTs);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new SessionSnapshot(
                            rs.getString("uuid"),
                            rs.getString("name"),
                            rs.getLong("joined_at"),
                            rs.getLong("effective_left")
                    ));
                }
            }
        }
        return sessions;
    }

    private List<PlayerSessionRecord> queryPlayerSessions(String uuid, long now) throws SQLException {
        String sql = """
                SELECT name, joined_at, left_at, COALESCE(left_at, ?) - joined_at AS duration_sec
                FROM sessions
                WHERE uuid = ?
                ORDER BY joined_at DESC
                """;

        List<PlayerSessionRecord> sessions = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setString(2, uuid);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long leftAtValue = rs.getLong("left_at");
                    Long leftAt = rs.wasNull() ? null : leftAtValue;
                    sessions.add(new PlayerSessionRecord(
                            rs.getString("name"),
                            rs.getLong("joined_at"),
                            leftAt,
                            rs.getLong("duration_sec")
                    ));
                }
            }
        }
        return sessions;
    }

    private List<HistoryPoint> sampleHistory(List<SessionSnapshot> sessions, long fromTs, long toTs, long intervalSeconds) {
        List<HistoryPoint> points = new ArrayList<>();
        List<SessionSnapshot> byJoin = new ArrayList<>(sessions);
        byJoin.sort(Comparator.comparingLong(SessionSnapshot::joinedAt));

        List<SessionSnapshot> byLeft = new ArrayList<>(sessions);
        byLeft.sort(Comparator.comparingLong(SessionSnapshot::effectiveLeft));

        Map<String, Integer> activeCounts = new HashMap<>();
        Map<String, String> activePlayers = new HashMap<>();
        int joinIndex = 0;
        int leftIndex = 0;

        while (joinIndex < byJoin.size() && byJoin.get(joinIndex).joinedAt() <= fromTs) {
            activateSession(byJoin.get(joinIndex++), activeCounts, activePlayers);
        }

        while (leftIndex < byLeft.size() && byLeft.get(leftIndex).effectiveLeft() <= fromTs) {
            deactivateSession(byLeft.get(leftIndex++), activeCounts, activePlayers);
        }

        for (long bucketStart = fromTs; bucketStart < toTs; bucketStart += intervalSeconds) {
            long bucketEnd = Math.min(bucketStart + intervalSeconds, toTs);
            long cursor = bucketStart;
            double onlineSeconds = 0;
            Set<String> bucketPlayers = new HashSet<>(activePlayers.values());

            while (cursor < bucketEnd) {
                long nextJoinAt = joinIndex < byJoin.size() ? byJoin.get(joinIndex).joinedAt() : Long.MAX_VALUE;
                long nextLeftAt = leftIndex < byLeft.size() ? byLeft.get(leftIndex).effectiveLeft() : Long.MAX_VALUE;
                long nextChange = Math.min(bucketEnd, Math.min(nextJoinAt, nextLeftAt));

                if (nextChange > cursor) {
                    long duration = nextChange - cursor;
                    onlineSeconds += activePlayers.size() * duration;
                    cursor = nextChange;
                } else {
                    cursor = nextChange;
                }

                while (leftIndex < byLeft.size() && byLeft.get(leftIndex).effectiveLeft() <= cursor) {
                    deactivateSession(byLeft.get(leftIndex++), activeCounts, activePlayers);
                }

                while (joinIndex < byJoin.size() && byJoin.get(joinIndex).joinedAt() <= cursor) {
                    SessionSnapshot session = byJoin.get(joinIndex++);
                    activateSession(session, activeCounts, activePlayers);
                    bucketPlayers.add(session.name());
                }
            }

            List<String> names = new ArrayList<>(bucketPlayers);
            names.sort(String.CASE_INSENSITIVE_ORDER);
            long bucketDuration = bucketEnd - bucketStart;
            double avgOnline = bucketDuration == 0 ? 0 : onlineSeconds / bucketDuration;
            points.add(new HistoryPoint(bucketStart, bucketDuration, avgOnline, names));
        }

        return points;
    }

    private void activateSession(SessionSnapshot session, Map<String, Integer> activeCounts, Map<String, String> activePlayers) {
        int nextCount = activeCounts.getOrDefault(session.uuid(), 0) + 1;
        activeCounts.put(session.uuid(), nextCount);
        activePlayers.put(session.uuid(), session.name());
    }

    private void deactivateSession(SessionSnapshot session, Map<String, Integer> activeCounts, Map<String, String> activePlayers) {
        Integer currentCount = activeCounts.get(session.uuid());
        if (currentCount == null) {
            return;
        }
        if (currentCount <= 1) {
            activeCounts.remove(session.uuid());
            activePlayers.remove(session.uuid());
            return;
        }
        activeCounts.put(session.uuid(), currentCount - 1);
    }

    private HistorySummary summarizeHistory(List<HistoryPoint> points, List<SessionSnapshot> sessions, long defaultPeakTime) {
        Set<String> uniquePlayers = new HashSet<>();
        for (SessionSnapshot session : sessions) {
            uniquePlayers.add(session.uuid());
        }

        long peakTime = defaultPeakTime;
        double peakOnline = 0;
        double weightedOnlineTotal = 0;
        long totalDurationSeconds = 0;
        for (HistoryPoint point : points) {
            weightedOnlineTotal += point.online() * point.durationSeconds();
            totalDurationSeconds += point.durationSeconds();
            if (point.online() > peakOnline) {
                peakOnline = point.online();
                peakTime = point.timestamp();
            }
        }

        double avgOnline = totalDurationSeconds == 0 ? 0 : weightedOnlineTotal / totalDurationSeconds;
        return new HistorySummary(peakTime, peakOnline, avgOnline, uniquePlayers.size());
    }

    private LiveHistorySnapshot buildLiveHistorySnapshot(long fromTs, long toTs, long nowTs) {
        if (nowTs < fromTs || nowTs > toTs) {
            return null;
        }

        List<String> players = sortedOnlinePlayerNames();
        return new LiveHistorySnapshot(nowTs, players.size(), players);
    }

    private int findLiveHistoryPointIndex(List<HistoryPoint> points, long nowTs, long toTs) {
        for (int i = 0; i < points.size(); i++) {
            HistoryPoint point = points.get(i);
            long start = point.timestamp();
            long end = start + point.durationSeconds();
            if (nowTs >= start && nowTs < end) {
                return i;
            }
        }

        if (!points.isEmpty() && nowTs == toTs) {
            HistoryPoint lastPoint = points.getLast();
            long start = lastPoint.timestamp();
            long end = start + lastPoint.durationSeconds();
            if (nowTs >= start && nowTs == end) {
                return points.size() - 1;
            }
        }

        return -1;
    }

    private List<Map.Entry<UUID, OnlinePlayerInfo>> sortedOnlineEntries() {
        List<Map.Entry<UUID, OnlinePlayerInfo>> entries = new ArrayList<>(onlineCache.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private List<String> sortedOnlinePlayerNames() {
        List<String> players = new ArrayList<>(onlineCache.size());
        for (OnlinePlayerInfo info : onlineCache.values()) {
            players.add(info.name());
        }
        players.sort(String.CASE_INSENSITIVE_ORDER);
        return players;
    }

    private void appendJsonStringArray(StringBuilder sb, List<String> values) {
        sb.append("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(values.get(i))).append("\"");
        }
        sb.append("]");
    }

    private void insertSessions(List<Player> players, long joinedAt) {
        if (players.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO sessions (uuid, name, joined_at) VALUES (?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Player player : players) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setLong(3, joinedAt);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to bootstrap online sessions: " + e.getMessage());
        }
    }

    private void insertSession(UUID uuid, String name, long joinedAt) {
        String sql = "INSERT INTO sessions (uuid, name, joined_at) VALUES (?, ?, ?)";
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setLong(3, joinedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to persist join session for " + name + ": " + e.getMessage());
        }
    }

    private void closeOpenSessionsForPlayer(UUID uuid, long leftAt) {
        String sql = "UPDATE sessions SET left_at = ? WHERE uuid = ? AND left_at IS NULL";
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, leftAt);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close sessions for " + uuid + ": " + e.getMessage());
        }
    }

    private void closeAllOpenSessions(long leftAt) {
        String sql = "UPDATE sessions SET left_at = ? WHERE left_at IS NULL";
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, leftAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close open sessions on shutdown: " + e.getMessage());
        }
    }

    private void submitSessionWrite(Runnable task) {
        if (sessionExecutor == null || sessionExecutor.isShutdown()) {
            task.run();
            return;
        }
        sessionExecutor.execute(task);
    }

    private Connection openConnection() throws SQLException {
        if (sessionStore == null) {
            throw new SQLException("Session store is not initialized");
        }
        return sessionStore.getConnection();
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

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }

        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int index = pair.indexOf('=');
            if (index < 0) {
                params.put(decodeQueryComponent(pair), "");
                continue;
            }
            params.put(
                    decodeQueryComponent(pair.substring(0, index)),
                    decodeQueryComponent(pair.substring(index + 1))
            );
        }
        return params;
    }

    private String decodeQueryComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String formatJsonNumber(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private Long parseIntervalSeconds(String rawInterval) {
        String value = rawInterval == null ? String.valueOf(DEFAULT_HISTORY_INTERVAL_SECONDS) : rawInterval.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            long intervalSeconds = Long.parseLong(value);
            return intervalSeconds > 0 ? intervalSeconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendError(HttpExchange exchange, int code, String error) throws IOException {
        sendJson(exchange, code, "{\"error\":\"" + escapeJson(error) + "\"}", "no-store");
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
        if (sessionExecutor != null) {
            sessionExecutor.shutdown();
            try {
                if (!sessionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    sessionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sessionExecutor.shutdownNow();
            }
        }

        closeAllOpenSessions(Instant.now().getEpochSecond());
        onlineCache.clear();

        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
        }
        if (sessionStore != null) {
            sessionStore.dispose();
            sessionStore = null;
        }
    }
}
