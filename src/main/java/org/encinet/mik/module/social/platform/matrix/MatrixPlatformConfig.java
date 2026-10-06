package org.encinet.mik.module.social.platform.matrix;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.chat.SocialChatOutboundPolicy;
import org.encinet.mik.module.social.chat.SocialChatRoute;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Validated Matrix Client-Server API configuration. */
public record MatrixPlatformConfig(
        boolean enabled,
        URI homeserverUri,
        String accessToken,
        long syncTimeoutMillis,
        int syncMaxResponseBytes,
        long reconnectBaseDelayMillis,
        long reconnectMaxDelayMillis,
        int maxReconnectAttempts,
        int workerMaxConcurrentTasks,
        Language defaultLanguage,
        Set<String> allowedRoomIds,
        List<SocialChatRoute> chatRoutes,
        boolean contentSafetyEnabled,
        int maxOutboundLength
) {
    public static final String PLATFORM_ID = "matrix";
    public static final String FILE_NAME = "social/matrix.yml";
    private static final String DEFAULT_HOMESERVER = "https://matrix-client.matrix.org";

    public static MatrixPlatformConfig load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.isFile()) {
            plugin.saveResource(FILE_NAME, false);
        }
        return from(YamlConfiguration.loadConfiguration(file));
    }

    public static MatrixPlatformConfig from(YamlConfiguration yaml) {
        boolean enabled = yaml.getBoolean("enabled", false);
        URI homeserver = parseHttpUri(configuredUrl(
                yaml.getString("homeserver-url"), DEFAULT_HOMESERVER),
                "homeserver-url");
        String accessToken = configuredValue(yaml.getString("access-token", ""));
        requireNoControls(accessToken, "access-token");

        long syncTimeoutMillis = requireRange(
                yaml.getLong("sync.timeout-millis", 30_000L),
                1_000L, 60_000L, "sync.timeout-millis");
        int syncMaxResponseBytes = (int) requireRange(
                yaml.getInt("sync.max-response-bytes", 2_097_152),
                65_536, 16_777_216, "sync.max-response-bytes");
        long reconnectBaseDelayMillis = requireRange(
                yaml.getLong("sync.reconnect-base-delay-millis", 1_000L),
                100L, 60_000L, "sync.reconnect-base-delay-millis");
        long reconnectMaxDelayMillis = requireRange(
                yaml.getLong("sync.reconnect-max-delay-millis", 30_000L),
                reconnectBaseDelayMillis, 300_000L,
                "sync.reconnect-max-delay-millis");
        int maxReconnectAttempts = (int) requireRange(
                yaml.getInt("sync.max-reconnect-attempts", 10),
                1, 100, "sync.max-reconnect-attempts");
        int workerMaxConcurrentTasks = (int) requireRange(
                yaml.getInt("worker.max-concurrent-tasks", 64),
                1, 512, "worker.max-concurrent-tasks");
        Language defaultLanguage = Language.fromId(
                        configuredValue(yaml.getString("default-language", "zh_cn")))
                .orElseThrow(() -> new IllegalArgumentException(
                        "default-language is not supported"));
        Set<String> allowedRooms = roomIds(yaml, "allowed-room-ids");
        List<SocialChatRoute> chatRoutes = chatRoutes(yaml, allowedRooms);
        boolean contentSafety = yaml.getBoolean("content-safety.enabled", true);
        int maxLength = (int) clamp(
                yaml.getInt("reply.max-message-length", 4_000), 100, 8_000);

        if (enabled && accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    FILE_NAME + " is enabled, but access-token is missing");
        }
        return new MatrixPlatformConfig(enabled, homeserver, accessToken,
                syncTimeoutMillis, syncMaxResponseBytes,
                reconnectBaseDelayMillis, reconnectMaxDelayMillis,
                maxReconnectAttempts, workerMaxConcurrentTasks,
                defaultLanguage, allowedRooms, chatRoutes,
                contentSafety, maxLength);
    }

    public boolean acceptsRoom(String roomId) {
        return allowedRoomIds.isEmpty() || allowedRoomIds.contains(roomId);
    }

    public Optional<SocialChatRoute> chatRoute(String roomId) {
        return chatRoutes.stream()
                .filter(route -> route.conversation().id().equals(roomId))
                .findFirst();
    }

    public URI apiUri(String path) {
        String base = homeserverUri.toString();
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }

    private static Set<String> roomIds(YamlConfiguration yaml, String path) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : yaml.getStringList(path)) {
            String roomId = configuredValue(value);
            if (roomId.isEmpty()) {
                continue;
            }
            if (!roomId.startsWith("!") || roomId.length() > 255) {
                throw new IllegalArgumentException(
                        path + " entries must be Matrix room IDs beginning with '!'");
            }
            requireNoControls(roomId, path);
            values.add(roomId);
            if (values.size() > 256) {
                throw new IllegalArgumentException(path + " supports at most 256 rooms");
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static List<SocialChatRoute> chatRoutes(
            YamlConfiguration yaml,
            Set<String> allowedRooms
    ) {
        java.util.ArrayList<SocialChatRoute> routes = new java.util.ArrayList<>();
        LinkedHashSet<String> roomIds = new LinkedHashSet<>();
        for (Map<?, ?> entry : yaml.getMapList("chat-bridge.routes")) {
            String roomId = configuredValue(string(entry.get("room-id")));
            if (!roomId.startsWith("!") || roomId.length() > 255) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes room-id must be a Matrix room ID beginning with '!'");
            }
            requireNoControls(roomId, "chat-bridge.routes room-id");
            if (!allowedRooms.isEmpty() && !allowedRooms.contains(roomId)) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes room-id must also appear in allowed-room-ids");
            }
            if (!roomIds.add(roomId)) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes contains duplicate room-id " + roomId);
            }
            Map<?, ?> outbound = map(entry.get("outbound"));
            String modeName = configuredValue(string(outbound.get("mode")));
            SocialChatOutboundPolicy.Mode mode;
            try {
                mode = SocialChatOutboundPolicy.Mode.valueOf(
                        (modeName.isEmpty() ? "always" : modeName)
                                .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes outbound.mode must be always or prefix", error);
            }
            SocialChatOutboundPolicy policy = mode == SocialChatOutboundPolicy.Mode.ALWAYS
                    ? SocialChatOutboundPolicy.always()
                    : SocialChatOutboundPolicy.prefixed(
                    string(outbound.get("prefix")),
                    bool(outbound.get("strip-prefix"), true));
            routes.add(new SocialChatRoute(
                    new SocialConversation(roomId, SocialConversation.Type.GROUP), policy));
            if (routes.size() > 256) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes supports at most 256 rooms");
            }
        }
        return List.copyOf(routes);
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean result ? result : fallback;
    }

    private static URI parseHttpUri(String value, String field) {
        try {
            URI uri = URI.create(trimTrailingSlash(value));
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(
                        field + " must be an absolute HTTP(S) URL");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(
                        field + " must not contain credentials, a query, or a fragment");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Invalid " + field + ": " + error.getMessage(), error);
        }
    }

    private static void requireNoControls(String value, String field) {
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
    }

    private static String configuredValue(String value) {
        return value == null ? "" : value.strip();
    }

    private static String configuredUrl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trimTrailingSlash(String value) {
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static long requireRange(long value, long minimum, long maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
