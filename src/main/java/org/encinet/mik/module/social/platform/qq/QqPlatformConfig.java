package org.encinet.mik.module.social.platform.qq;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record QqPlatformConfig(
        boolean enabled,
        String appId,
        String appSecret,
        URI apiBaseUri,
        URI accessTokenUri,
        long gatewayReconnectBaseDelayMillis,
        long gatewayReconnectMaxDelayMillis,
        int gatewayMaxReconnectAttempts,
        int gatewayMaxFrameBytes,
        long gatewayHandshakeTimeoutMillis,
        boolean gatewayDebugEnabled,
        int workerMaxConcurrentTasks,
        Language defaultLanguage,
        Set<String> allowedGroupOpenIds,
        boolean contentSafetyEnabled,
        int maxOutboundLength
) {

    public static final String FILE_NAME = "social/qq.yml";
    public static final String SAFETY_FILE_NAME = "social/blocked-keywords.txt";
    private static final String LEGACY_FILE_NAME = "qq-bot.yml";
    private static final String DEFAULT_API_BASE = "https://api.sgroup.qq.com";
    private static final String DEFAULT_ACCESS_TOKEN_URL =
            "https://bots.qq.com/app/getAppAccessToken";
    private static final String UNIFIED_API_HOST = "api.bot.qq.com";

    public static QqPlatformConfig load(JavaPlugin plugin) {
        File legacy = new File(plugin.getDataFolder(), LEGACY_FILE_NAME);
        if (legacy.isFile()) {
            plugin.getLogger().warning("Legacy " + LEGACY_FILE_NAME
                    + " is ignored; migrate QQ configuration to " + FILE_NAME);
        }
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.isFile()) {
            plugin.saveResource(FILE_NAME, false);
        }
        return from(YamlConfiguration.loadConfiguration(file));
    }

    public static QqPlatformConfig from(YamlConfiguration yaml) {
        boolean enabled = yaml.getBoolean("enabled", false);
        String appId = configuredValue(yaml.getString("app-id", ""));
        String appSecret = configuredValue(yaml.getString("app-secret", ""));

        if (yaml.contains("client-secret")) {
            throw new IllegalArgumentException(
                    "client-secret has been removed; configure app-secret instead");
        }

        if (yaml.contains("webhook")) {
            throw new IllegalArgumentException(
                    "webhook configuration has been removed; QQ now uses WebSocket Gateway");
        }
        if (yaml.contains("event-transport") || yaml.contains("gateway-url")) {
            throw new IllegalArgumentException(
                    "event-transport/gateway-url are obsolete; QQ always discovers its Gateway");
        }

        URI apiBase = compatibleApiBase(parseHttpUri(configuredUrl(
                yaml.getString("api-base-url"), DEFAULT_API_BASE), "api-base-url"));
        URI accessTokenUri = compatibleAccessTokenUri(parseHttpUri(
                configuredUrl(yaml.getString("access-token-url"), DEFAULT_ACCESS_TOKEN_URL),
                "access-token-url"));
        long gatewayReconnectBaseDelayMillis = requireRange(
                yaml.getLong("gateway.reconnect-base-delay-millis", 1_000L),
                100L, 60_000L, "gateway.reconnect-base-delay-millis");
        long gatewayReconnectMaxDelayMillis = requireRange(
                yaml.getLong("gateway.reconnect-max-delay-millis", 30_000L),
                gatewayReconnectBaseDelayMillis, 300_000L,
                "gateway.reconnect-max-delay-millis");
        int gatewayMaxReconnectAttempts = (int) requireRange(
                yaml.getInt("gateway.max-reconnect-attempts", 10),
                1, 100, "gateway.max-reconnect-attempts");
        int gatewayMaxFrameBytes = (int) requireRange(
                yaml.getInt("gateway.max-frame-bytes", 1_048_576),
                4_096, 8_388_608, "gateway.max-frame-bytes");
        long gatewayHandshakeTimeoutMillis = requireRange(
                yaml.getLong("gateway.handshake-timeout-millis", 30_000L),
                1_000L, 120_000L, "gateway.handshake-timeout-millis");
        boolean gatewayDebugEnabled = yaml.getBoolean("gateway.debug", false);
        int workerMaxConcurrentTasks = (int) requireRange(
                yaml.getInt("worker.max-concurrent-tasks", 64),
                1, 512, "worker.max-concurrent-tasks");
        Language defaultLanguage = Language.fromId(
                        configuredValue(yaml.getString("default-language", "zh_cn")))
                .orElseThrow(() -> new IllegalArgumentException(
                        "default-language is not supported"));

        Set<String> allowedGroups = stringSet(yaml, "allowed-group-openids");
        boolean contentSafety = yaml.getBoolean("content-safety.enabled", true);
        int maxLength = (int) clamp(yaml.getInt("reply.max-message-length", 1_500),
                100, 4_000);

        if (enabled && (appId.isBlank() || appSecret.isBlank())) {
            throw new IllegalArgumentException(
                    FILE_NAME + " is enabled, but app-id/app-secret are missing");
        }

        return new QqPlatformConfig(enabled, appId, appSecret, apiBase, accessTokenUri,
                gatewayReconnectBaseDelayMillis, gatewayReconnectMaxDelayMillis,
                gatewayMaxReconnectAttempts, gatewayMaxFrameBytes,
                gatewayHandshakeTimeoutMillis, gatewayDebugEnabled,
                workerMaxConcurrentTasks, defaultLanguage, allowedGroups,
                contentSafety, maxLength);
    }

    public boolean acceptsGroup(String groupOpenId) {
        return allowedGroupOpenIds.isEmpty() || allowedGroupOpenIds.contains(groupOpenId);
    }

    public URI apiUri(String path) {
        String base = apiBaseUri.toString();
        return URI.create(base + (path.startsWith("/") ? path : "/" + path));
    }

    private static String configuredValue(String value) {
        return value == null ? "" : value.strip();
    }

    private static Set<String> stringSet(YamlConfiguration yaml, String path) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : yaml.getStringList(path)) {
            if (value != null && !value.isBlank()) {
                values.add(value.strip());
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static URI parseHttpUri(String value, String field) {
        URI uri = parseAbsoluteUri(trimTrailingSlash(value), field);
        if (!uri.getScheme().equalsIgnoreCase("https")
                && !uri.getScheme().equalsIgnoreCase("http")) {
            throw new IllegalArgumentException(field + " must use http or https");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    field + " must not contain credentials, a query, or a fragment");
        }
        return uri;
    }

    private static String configuredUrl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static URI compatibleApiBase(URI configured) {
        if (UNIFIED_API_HOST.equalsIgnoreCase(configured.getHost())
                && (configured.getPath().isEmpty() || "/".equals(configured.getPath()))) {
            return URI.create(DEFAULT_API_BASE);
        }
        return configured;
    }

    private static URI compatibleAccessTokenUri(URI configured) {
        if (UNIFIED_API_HOST.equalsIgnoreCase(configured.getHost())
                && "/app/getAppAccessToken".equals(configured.getPath())) {
            return URI.create(DEFAULT_ACCESS_TOKEN_URL);
        }
        return configured;
    }

    private static URI parseAbsoluteUri(String value, String field) {
        try {
            URI uri = URI.create(value.strip());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new IllegalArgumentException(field + " must be an absolute URL");
            }
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid " + field + ": " + error.getMessage(), error);
        }
    }

    private static long requireRange(long value, long minimum, long maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    field + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String trimTrailingSlash(String value) {
        String result = value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
