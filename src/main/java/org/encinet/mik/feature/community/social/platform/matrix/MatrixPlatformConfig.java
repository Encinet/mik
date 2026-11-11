package org.encinet.mik.feature.community.social.platform.matrix;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;

import java.io.File;
import java.net.URI;

/** Validated Matrix Client-Server API configuration. */
public record MatrixPlatformConfig(
        boolean enabled,
        URI homeserverUri,
        String accessToken,
        String recoveryKey,
        boolean onlyVerifiedUsers,
        long syncTimeoutMillis,
        int syncMaxResponseBytes,
        long reconnectBaseDelayMillis,
        long reconnectMaxDelayMillis,
        int maxReconnectAttempts,
        int workerMaxConcurrentTasks,
        Language defaultLanguage,
        MatrixRoomPolicy rooms,
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
        String recoveryKey = configuredValue(yaml.getString("encryption.recovery-key", ""));
        requireNoControls(recoveryKey, "encryption.recovery-key");
        if (!(yaml.get("encryption.only-verified-users", false) instanceof Boolean onlyVerifiedUsers)) {
            throw new IllegalArgumentException("encryption.only-verified-users must be a boolean");
        }

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
        MatrixRoomPolicy rooms = MatrixRoomPolicy.from(yaml);
        boolean contentSafety = yaml.getBoolean("content-safety.enabled", true);
        int maxLength = (int) clamp(
                yaml.getInt("reply.max-message-length", 4_000), 100, 8_000);

        if (enabled && accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    FILE_NAME + " is enabled, but access-token is missing");
        }
        if (enabled && rooms.membershipRoomIds().isEmpty()) {
            throw new IllegalArgumentException("membership.room-ids must name the rooms or spaces granting server admission");
        }
        if (enabled && rooms.commandRoomIds().isEmpty()) {
            throw new IllegalArgumentException("commands.room-ids must name the chat rooms accepting binding commands");
        }
        return new MatrixPlatformConfig(enabled, homeserver, accessToken, recoveryKey, onlyVerifiedUsers,
                syncTimeoutMillis, syncMaxResponseBytes,
                reconnectBaseDelayMillis, reconnectMaxDelayMillis,
                maxReconnectAttempts, workerMaxConcurrentTasks,
                defaultLanguage, rooms,
                contentSafety, maxLength);
    }

    public URI apiUri(String path) {
        String base = homeserverUri.toString();
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
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
