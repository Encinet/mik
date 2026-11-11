package org.encinet.mik.feature.community.social.platform.qq;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.chat.SocialChatPolicy;
import org.encinet.mik.feature.community.social.chat.SocialChatRoute;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinRule;
import org.encinet.mik.feature.community.social.platform.qq.join.QqTotp;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record QqPlatformConfig(
        boolean enabled,
        String appId,
        String appSecret,
        long gatewayReconnectBaseDelayMillis,
        long gatewayReconnectMaxDelayMillis,
        int gatewayMaxReconnectAttempts,
        int gatewayMaxFrameBytes,
        long gatewayHandshakeTimeoutMillis,
        boolean gatewayDebugEnabled,
        int workerMaxConcurrentTasks,
        Language defaultLanguage,
        Set<String> allowedGroupOpenIds,
        List<SocialChatRoute> chatRoutes,
        List<QqGroupJoinRule> groupJoinRules,
        long groupJoinReconcileIntervalSeconds,
        boolean contentSafetyEnabled,
        int maxOutboundLength
) {

    public static final String PLATFORM_ID = "qq";
    public static final String FILE_NAME = "social/qq.yml";
    private static final String LEGACY_FILE_NAME = "qq-bot.yml";
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
        List<SocialChatRoute> chatRoutes = chatRoutes(yaml, allowedGroups);
        List<QqGroupJoinRule> groupJoinRules = groupJoinRules(yaml, allowedGroups);
        long groupJoinReconcileInterval = requireRange(
                yaml.getLong("group-join.reconcile-interval-seconds", 30), 10, 3_600,
                "group-join.reconcile-interval-seconds");
        boolean contentSafety = yaml.getBoolean("content-safety.enabled", true);
        int maxLength = (int) clamp(yaml.getInt("reply.max-message-length", 1_500),
                100, 4_000);

        if (enabled && (appId.isBlank() || appSecret.isBlank())) {
            throw new IllegalArgumentException(
                    FILE_NAME + " is enabled, but app-id/app-secret are missing");
        }
        if (enabled && allowedGroups.isEmpty()) {
            throw new IllegalArgumentException("allowed-group-openids must name the communities used for identity membership");
        }

        return new QqPlatformConfig(enabled, appId, appSecret,
                gatewayReconnectBaseDelayMillis, gatewayReconnectMaxDelayMillis,
                gatewayMaxReconnectAttempts, gatewayMaxFrameBytes,
                gatewayHandshakeTimeoutMillis, gatewayDebugEnabled,
                workerMaxConcurrentTasks, defaultLanguage, allowedGroups,
                chatRoutes, groupJoinRules, groupJoinReconcileInterval, contentSafety, maxLength);
    }

    public boolean acceptsGroup(String groupOpenId) {
        return allowedGroupOpenIds.isEmpty() || allowedGroupOpenIds.contains(groupOpenId);
    }

    public Optional<SocialChatRoute> chatRoute(String groupOpenId) {
        return chatRoutes.stream()
                .filter(route -> route.conversation().id().equals(groupOpenId))
                .findFirst();
    }

    private static String configuredValue(String value) {
        return value == null ? "" : value.strip();
    }

    private static Set<String> stringSet(YamlConfiguration yaml, String path) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : yaml.getStringList(path)) {
            if (value != null && !value.isBlank()) {
                String groupOpenId = value.strip();
                requireGroupOpenId(groupOpenId, path);
                values.add(groupOpenId);
                if (values.size() > 256) {
                    throw new IllegalArgumentException(
                            path + " supports at most 256 groups");
                }
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static List<SocialChatRoute> chatRoutes(
            YamlConfiguration yaml,
            Set<String> allowedGroups
    ) {
        java.util.ArrayList<SocialChatRoute> routes = new java.util.ArrayList<>();
        LinkedHashSet<String> groupOpenIds = new LinkedHashSet<>();
        for (Map<?, ?> entry : yaml.getMapList("chat-bridge.routes")) {
            String groupOpenId = configuredValue(string(entry.get("group-openid")));
            requireGroupOpenId(groupOpenId, "chat-bridge.routes group-openid");
            if (!allowedGroups.isEmpty() && !allowedGroups.contains(groupOpenId)) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes group-openid must also appear in "
                                + "allowed-group-openids");
            }
            if (!groupOpenIds.add(groupOpenId)) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes contains duplicate group-openid "
                                + groupOpenId);
            }
            routes.add(new SocialChatRoute(
                    new SocialConversation(groupOpenId, SocialConversation.Type.GROUP),
                    SocialChatPolicy.fromConfig(entry.get("inbound"), "chat-bridge.routes inbound"),
                    SocialChatPolicy.fromConfig(entry.get("outbound"), "chat-bridge.routes outbound")));
            if (routes.size() > 256) {
                throw new IllegalArgumentException(
                        "chat-bridge.routes supports at most 256 groups");
            }
        }
        return List.copyOf(routes);
    }

    private static void requireGroupOpenId(String value, String field) {
        if (value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    field + " entries must be non-blank QQ group OpenIDs");
        }
    }

    private static List<QqGroupJoinRule> groupJoinRules(
            YamlConfiguration yaml,
            Set<String> allowedGroups
    ) {
        String path = "group-join.groups";
        if (yaml.contains("group-join") && !yaml.isConfigurationSection("group-join")) {
            throw new IllegalArgumentException("group-join must be a configuration section");
        }
        if (yaml.contains(path) && !yaml.isList(path)) {
            throw new IllegalArgumentException(path + " must be a list");
        }
        java.util.ArrayList<QqGroupJoinRule> rules = new java.util.ArrayList<>();
        for (Object value : yaml.getList(path, List.of())) {
            if (!(value instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException(path + " entries must be maps");
            }
            String groupOpenId = configuredValue(string(entry.get("group-openid")));
            requireGroupOpenId(groupOpenId, path + " group-openid");
            if (!allowedGroups.isEmpty() && !allowedGroups.contains(groupOpenId)) {
                throw new IllegalArgumentException(path
                        + " group-openid must also appear in allowed-group-openids");
            }
            QqTotp totp = new QqTotp(string(entry.get("totp-secret")));
            for (QqGroupJoinRule rule : rules) {
                if (rule.groupOpenId().equals(groupOpenId)) {
                    throw new IllegalArgumentException(path + " contains duplicate group-openid");
                }
                if (rule.totp().hasSameSecret(totp)) {
                    throw new IllegalArgumentException(path + " must use distinct TOTP secrets");
                }
            }
            rules.add(new QqGroupJoinRule(groupOpenId, totp));
            if (rules.size() > 2) {
                throw new IllegalArgumentException(path + " supports at most two groups");
            }
        }
        return List.copyOf(rules);
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
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
