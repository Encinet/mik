package org.encinet.mik.feature.player.client;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.TextArg;

import java.util.UUID;
import java.util.function.Predicate;

public class ClientVersionReminderModule implements Listener {

    private static final long REMINDER_DELAY_TICKS = 40L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Predicate<UUID> bedrockPlayer;
    private ProtocolVersion serverProtocol = ProtocolVersion.unknown;
    private String serverVersionName = "Unknown";

    public ClientVersionReminderModule(JavaPlugin plugin, LanguageService languageService,
                                      Predicate<UUID> bedrockPlayer) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.bedrockPlayer = bedrockPlayer;
    }

    public void enable() {
        if (!Via.isLoaded()) {
            plugin.getLogger().warning("ViaVersion API is not loaded! ClientVersionReminderModule disabled.");
            return;
        }

        serverVersionName = Bukkit.getMinecraftVersion();
        serverProtocol = resolveServerProtocol();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ClientVersionReminderModule enabled, server version: " + serverVersionName);
    }

    public String clientVersionName(Player player) {
        ProtocolVersion clientVersion = clientProtocol(player);
        return clientVersion.isKnown() ? clientVersion.getName() : "Unknown";
    }

    public boolean isClientOlderThanServer(Player player) {
        ProtocolVersion clientVersion = clientProtocol(player);
        return clientVersion.isKnown() && serverProtocol.isKnown() && clientVersion.olderThan(serverProtocol);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || bedrockPlayer.test(player.getUniqueId())) {
                return;
            }

            ProtocolVersion clientVersion = clientProtocol(player);
            if (!clientVersion.isKnown() || !serverProtocol.isKnown() || clientVersion.equals(serverProtocol)) {
                return;
            }

            sendVersionMismatch(player, clientVersion);
        }, REMINDER_DELAY_TICKS);
    }

    private void sendVersionMismatch(Player player, ProtocolVersion clientVersion) {
        String text = languageService.format(player, Message.CLIENT_VERSION_MISMATCH_MM,
                TextArg.of("clientVersion", clientVersion.getName()),
                TextArg.of("serverVersion", serverVersionName));
        player.sendMessage(MINI_MESSAGE.deserialize(text));
    }

    private ProtocolVersion clientProtocol(Player player) {
        if (!Via.isLoaded()) {
            return ProtocolVersion.unknown;
        }
        return Via.getAPI().getPlayerProtocolVersion(player.getUniqueId());
    }

    private ProtocolVersion resolveServerProtocol() {
        var serverVersion = Via.getAPI().getServerVersion();
        if (serverVersion.isKnown()) {
            return serverVersion.highestSupportedProtocolVersion();
        }

        ProtocolVersion closestVersion = ProtocolVersion.getClosest(serverVersionName);
        return closestVersion != null ? closestVersion : ProtocolVersion.unknown;
    }
}
