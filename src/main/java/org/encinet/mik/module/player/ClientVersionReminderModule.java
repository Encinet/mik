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

public class ClientVersionReminderModule implements Listener {

    private static final String LATEST = "latest";
    private static final int LATEST_KNOWN_PROTOCOL = 776;
    private static final long REMINDER_DELAY_TICKS = 40L;
    private static final String BORDER = "<dark_gray>-----</dark_gray> <severity_title> <dark_gray>-----</dark_gray>";
    private static final String FOOTER_BORDER = "<dark_gray>------------------------------</dark_gray>";
    private static final List<String> MESSAGE_LINES = List.of(
            "<gray>当前 <white><client_version></white>，推荐 <white><min_version></white>。</gray>",
            "<severity_message>"
    );
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private ProtocolVersion minimumVersion = ProtocolVersion.unknown;
    private List<SeverityWarning> severityWarnings = List.of();

    public ClientVersionReminderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        if (!Via.isLoaded()) {
            plugin.getLogger().warning("ViaVersion API is not loaded! ClientVersionReminderModule disabled.");
            return;
        }

        reload();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ClientVersionReminderModule enabled, minimum client version: " + minimumVersion.getName());
    }

    public void reload() {
        minimumVersion = latestReleaseVersion();
        severityWarnings = List.of(
                warning("1.18", "<red><bold>严重缺失功能警报</bold></red>", "<red>跨度过大，大量新内容可能无法显示。</red>"),
                warning("1.21", "<gold><bold>大量缺失功能警报</bold></gold>", "<gold>缺少较多新内容，部分区域可能异常。</gold>"),
                warning("1.21.5", "<yellow><bold>明显缺失功能警报</bold></yellow>", "<yellow>可能缺少一部分新内容，建议更新。</yellow>"),
                warning(LATEST, "<green><bold>更新提醒</bold></green>", "<green>可能会有少数内容显示异常。</green>")
        );
    }

    public String clientVersionName(Player player) {
        ProtocolVersion clientVersion = clientVersion(player);
        return clientVersion.isKnown() ? clientVersion.getName() : "Unknown";
    }

    public String minimumVersionName() {
        return minimumVersion.isKnown() ? minimumVersion.getName() : "Unknown";
    }

    public boolean isOutdated(Player player) {
        ProtocolVersion clientVersion = clientVersion(player);
        return clientVersion.isKnown() && minimumVersion.isKnown() && clientVersion.olderThan(minimumVersion);
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
        }, REMINDER_DELAY_TICKS);
    }

    private void sendReminder(Player player, ProtocolVersion clientVersion) {
        String clientName = clientVersion.getName();
        String minimumName = minimumVersion.getName();
        SeverityWarning severityWarning = resolveSeverityWarning(clientVersion);
        String severityTitle = severityWarning.title();
        String severityMessage = severityWarning.message();

        player.sendMessage(MINI_MESSAGE.deserialize(formatLine(BORDER, clientName, minimumName,
                severityTitle, severityMessage)));
        for (String line : MESSAGE_LINES) {
            player.sendMessage(MINI_MESSAGE.deserialize(formatLine(line, clientName, minimumName,
                    severityTitle, severityMessage)));
        }
        player.sendMessage(MINI_MESSAGE.deserialize(formatLine(FOOTER_BORDER, clientName, minimumName,
                severityTitle, severityMessage)));
    }

    private ProtocolVersion clientVersion(Player player) {
        if (!Via.isLoaded()) {
            return ProtocolVersion.unknown;
        }
        return Via.getAPI().getPlayerProtocolVersion(player.getUniqueId());
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

    private SeverityWarning warning(String olderThanName, String title, String message) {
        ProtocolVersion olderThan = resolveThresholdVersion(olderThanName);
        if (olderThan == null) {
            throw new IllegalStateException("Unknown bundled client version severity: " + olderThanName);
        }

        return new SeverityWarning(olderThan, title, message);
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
