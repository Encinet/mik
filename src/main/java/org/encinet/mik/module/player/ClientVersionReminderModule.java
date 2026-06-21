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
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.List;
import java.util.Locale;

public class ClientVersionReminderModule implements Listener {

    private static final String LATEST = "latest";
    private static final int LATEST_KNOWN_PROTOCOL = 776;
    private static final long REMINDER_DELAY_TICKS = 40L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private ProtocolVersion minimumVersion = ProtocolVersion.unknown;
    private List<SeverityWarning> severityWarnings = List.of();

    public ClientVersionReminderModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
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
                warning("1.18", Message.CLIENT_REMINDER_SEVERE_TITLE_MM, Message.CLIENT_REMINDER_SEVERE_MESSAGE_MM),
                warning("1.21", Message.CLIENT_REMINDER_MANY_TITLE_MM, Message.CLIENT_REMINDER_MANY_MESSAGE_MM),
                warning("1.21.5", Message.CLIENT_REMINDER_OBVIOUS_TITLE_MM, Message.CLIENT_REMINDER_OBVIOUS_MESSAGE_MM),
                warning(LATEST, Message.CLIENT_REMINDER_UPDATE_TITLE_MM, Message.CLIENT_REMINDER_UPDATE_MESSAGE_MM)
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
        String severityTitle = languageService.t(player, severityWarning.title());
        String severityMessage = languageService.t(player, severityWarning.message());

        player.sendMessage(message(player, Message.CLIENT_REMINDER_BORDER_MM, severityTitle));
        player.sendMessage(message(player, Message.CLIENT_REMINDER_VERSION_LINE_MM, clientName, minimumName));
        player.sendMessage(MINI_MESSAGE.deserialize(severityMessage));
        player.sendMessage(message(player, Message.CLIENT_REMINDER_FOOTER_MM));
    }

    private ProtocolVersion clientVersion(Player player) {
        if (!Via.isLoaded()) {
            return ProtocolVersion.unknown;
        }
        return Via.getAPI().getPlayerProtocolVersion(player.getUniqueId());
    }

    private SeverityWarning resolveSeverityWarning(ProtocolVersion clientVersion) {
        return severityWarnings.stream()
                .filter(warning -> clientVersion.olderThan(warning.olderThan()))
                .findFirst()
                .orElse(new SeverityWarning(
                        minimumVersion,
                        Message.CLIENT_REMINDER_DEFAULT_TITLE_MM,
                        Message.CLIENT_REMINDER_DEFAULT_MESSAGE_MM
                ));
    }

    private SeverityWarning warning(String olderThanName, Message title, Message message) {
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

    private net.kyori.adventure.text.Component message(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private record SeverityWarning(ProtocolVersion olderThan, Message title, Message message) {
    }
}
