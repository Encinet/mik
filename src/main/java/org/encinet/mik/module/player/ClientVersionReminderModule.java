package org.encinet.mik.module.player;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ClientVersionReminderModule implements Listener {

    private static final String CONFIG_PATH = "client-version-reminder";
    private static final String LATEST = "latest";
    private static final int LATEST_KNOWN_PROTOCOL = 776;
    private static final long DEFAULT_DELAY_TICKS = 40L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;

    private boolean enabled;
    private ProtocolVersion minimumVersion;
    private long delayTicks;
    private String border;
    private String footerBorder;
    private List<String> messageLines;
    private List<SeverityWarning> severityWarnings;

    public ClientVersionReminderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!Via.isLoaded()) {
            plugin.getLogger().warning("ViaVersion API is not loaded! ClientVersionReminderModule disabled.");
            return;
        }

        reload();
        if (!enabled) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ClientVersionReminderModule enabled, minimum client version: " + minimumVersion.getName());
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean(CONFIG_PATH + ".enabled", true);
        minimumVersion = resolveMinimumVersion(plugin.getConfig().getString(CONFIG_PATH + ".min-version", LATEST));
        delayTicks = Math.max(0L, plugin.getConfig().getLong(CONFIG_PATH + ".delay-ticks", DEFAULT_DELAY_TICKS));
        border = plugin.getConfig().getString(CONFIG_PATH + ".border",
                "<dark_gray>--------------------------------</dark_gray>");
        footerBorder = plugin.getConfig().getString(CONFIG_PATH + ".footer-border",
                "<dark_gray>------------------------------</dark_gray>");
        messageLines = plugin.getConfig().getStringList(CONFIG_PATH + ".message");
        severityWarnings = loadSeverityWarnings();

        if (messageLines.isEmpty()) {
            messageLines = List.of(
                    "<gold><bold>客户端版本提醒</bold></gold>",
                    "<gray>当前客户端：</gray><white><client_version></white> <dark_gray>|</dark_gray> <gray>推荐版本：</gray><white><min_version></white>",
                    "<severity_message>",
                    "<gray>建议更新游戏客户端，避免功能异常或显示问题。</gray>"
            );
        }

        if (severityWarnings.isEmpty()) {
            severityWarnings = defaultSeverityWarnings();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            ProtocolVersion clientVersion = Via.getAPI().getPlayerProtocolVersion(player.getUniqueId());
            if (!clientVersion.isKnown() || !clientVersion.olderThan(minimumVersion)) {
                return;
            }

            sendReminder(player, clientVersion);
        }, delayTicks);
    }

    private void sendReminder(Player player, ProtocolVersion clientVersion) {
        String clientName = clientVersion.getName();
        String minimumName = minimumVersion.getName();
        SeverityWarning severityWarning = resolveSeverityWarning(clientVersion);
        String severityTitle = severityWarning.title();
        String severityMessage = severityWarning.message();

        player.sendMessage(MINI_MESSAGE.deserialize(formatLine(border, clientName, minimumName,
                severityTitle, severityMessage)));
        for (String line : messageLines) {
            player.sendMessage(MINI_MESSAGE.deserialize(formatLine(line, clientName, minimumName,
                    severityTitle, severityMessage)));
        }
        player.sendMessage(MINI_MESSAGE.deserialize(formatLine(footerBorder, clientName, minimumName,
                severityTitle, severityMessage)));
    }

    private String formatLine(String line, String clientName, String minimumName,
                              String severityTitle, String severityMessage) {
        return line
                .replace("<client_version>", clientName)
                .replace("<min_version>", minimumName)
                .replace("<severity_title>", severityTitle)
                .replace("<severity_message>", severityMessage);
    }

    private SeverityWarning resolveSeverityWarning(ProtocolVersion clientVersion) {
        return severityWarnings.stream()
                .filter(warning -> clientVersion.olderThan(warning.olderThan()))
                .findFirst()
                .orElse(new SeverityWarning(
                        minimumVersion,
                        "<gray><bold>版本功能警报</bold></gray>",
                        "<gray>当前客户端版本较旧，可能有少量内容无法完整显示。</gray>"
                ));
    }

    private List<SeverityWarning> loadSeverityWarnings() {
        List<Map<?, ?>> warningMaps = plugin.getConfig().getMapList(CONFIG_PATH + ".severity-warnings");
        if (warningMaps.isEmpty()) {
            return List.of();
        }

        return warningMaps.stream()
                .map(this::loadSeverityWarning)
                .filter(Objects::nonNull)
                .toList();
    }

    private SeverityWarning loadSeverityWarning(Map<?, ?> warningMap) {
        Object olderThanValue = warningMap.get("older-than");
        if (olderThanValue == null) {
            return null;
        }

        ProtocolVersion olderThan = resolveThresholdVersion(String.valueOf(olderThanValue));
        if (olderThan == null) {
            plugin.getLogger().warning("Unknown client-version-reminder severity older-than '"
                    + olderThanValue + "', skipping.");
            return null;
        }

        Object messageValue = warningMap.get("message");
        if (messageValue == null || String.valueOf(messageValue).isBlank()) {
            return null;
        }

        Object titleValue = warningMap.get("title");
        String title = titleValue == null || String.valueOf(titleValue).isBlank()
                ? "<yellow><bold>缺失功能警报</bold></yellow>"
                : String.valueOf(titleValue);

        return new SeverityWarning(olderThan, title, String.valueOf(messageValue));
    }

    private List<SeverityWarning> defaultSeverityWarnings() {
        return List.of(
                warning("1.18", "<red><bold>严重缺失功能警报</bold></red>", "<red>跨度过大，大量新内容可能无法显示。</red>"),
                warning("1.21", "<gold><bold>大量缺失功能警报</bold></gold>", "<gold>缺少较多新内容，部分区域可能异常。</gold>"),
                warning("1.21.5", "<yellow><bold>明显缺失功能警报</bold></yellow>", "<yellow>可能缺少一部分新内容，建议更新。</yellow>"),
                warning(LATEST, "<green><bold>更新提醒</bold></green>", "<green>可能会有少数内容显示异常。</green>")
        );
    }

    private SeverityWarning warning(String olderThanName, String title, String message) {
        ProtocolVersion olderThan = resolveThresholdVersion(olderThanName);
        if (olderThan == null) {
            throw new IllegalStateException("Unknown bundled client version severity: " + olderThanName);
        }

        return new SeverityWarning(olderThan, title, message);
    }

    private ProtocolVersion resolveMinimumVersion(String configuredVersion) {
        String version = configuredVersion == null ? LATEST : configuredVersion.trim();
        if (version.isEmpty() || version.toLowerCase(Locale.ROOT).equals(LATEST)) {
            return latestReleaseVersion();
        }

        ProtocolVersion protocolVersion = findProtocolVersion(version);
        if (protocolVersion != null) {
            return protocolVersion;
        }

        plugin.getLogger().warning("Unknown client-version-reminder.min-version '" + configuredVersion
                + "', falling back to latest.");
        return latestReleaseVersion();
    }

    private ProtocolVersion latestReleaseVersion() {
        return ProtocolVersion.getReversedProtocols().stream()
                .filter(version -> version.getVersionType() == VersionType.RELEASE)
                .findFirst()
                .orElseGet(() -> ProtocolVersion.getProtocol(LATEST_KNOWN_PROTOCOL));
    }

    private ProtocolVersion findProtocolVersion(String version) {
        String trimmedVersion = version == null ? "" : version.trim();
        if (trimmedVersion.isEmpty()) {
            return null;
        }

        return ProtocolVersion.getClosest(trimmedVersion);
    }

    private ProtocolVersion resolveThresholdVersion(String version) {
        String trimmedVersion = version == null ? "" : version.trim();
        if (trimmedVersion.toLowerCase(Locale.ROOT).equals(LATEST)) {
            return latestReleaseVersion();
        }

        return findProtocolVersion(trimmedVersion);
    }

    private record SeverityWarning(ProtocolVersion olderThan, String title, String message) {
    }
}
