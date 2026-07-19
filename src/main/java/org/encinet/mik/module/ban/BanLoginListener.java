package org.encinet.mik.module.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;

import java.net.InetAddress;
import java.net.InetSocketAddress;
final class BanLoginListener implements Listener {

    private final JavaPlugin plugin;
    private final PaperBanSynchronizer paperSynchronizer;
    private final LanguageService languageService;
    private final BanMessageRenderer renderer;
    private final BanAdmissionChecker admissionChecker;

    BanLoginListener(
            JavaPlugin plugin,
            PaperBanSynchronizer paperSynchronizer,
            LanguageService languageService,
            BanMessageRenderer renderer,
            BanAdmissionChecker admissionChecker
    ) {
        this.plugin = plugin;
        this.paperSynchronizer = paperSynchronizer;
        this.languageService = languageService;
        this.renderer = renderer;
        this.admissionChecker = admissionChecker;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        disallowIfBanned(event.getUniqueId(), event.getPlayerProfile().getName(), event.getAddress(),
                component -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, component));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnectionValidate(PlayerConnectionValidateLoginEvent event) {
        PlayerProfile profile = connectionProfile(event);
        if (profile == null) {
            return;
        }
        reconcile("login");
        InetAddress address = event.getConnection().getClientAddress().getAddress();
        disallowIfBanned(profile.getId(), profile.getName(), address, event::kickMessage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        reconcile("kick");
        InetSocketAddress socketAddress = player.getAddress();
        InetAddress address = socketAddress == null ? null : socketAddress.getAddress();
        disallowIfBanned(player.getUniqueId(), player.getName(), address, component -> {
            event.leaveMessage(Component.empty());
            event.reason(component);
        });
    }

    private void disallowIfBanned(
            java.util.UUID playerUuid,
            String playerName,
            InetAddress address,
            java.util.function.Consumer<Component> disallow
    ) {
        admissionChecker.find(playerUuid, playerName, address).ifPresent(match ->
                disallow.accept(renderer.banMessage(languageService.languageForAddress(address), match.record())));
    }

    private void reconcile(String phase) {
        try {
            paperSynchronizer.reconcileNow();
        } catch (BanServiceException e) {
            plugin.getLogger().severe("Failed to synchronize bans during " + phase + ": " + e.getMessage());
        }
    }

    private PlayerProfile connectionProfile(PlayerConnectionValidateLoginEvent event) {
        if (event.getConnection() instanceof PlayerConfigurationConnection configuration) {
            return configuration.getProfile();
        }
        if (event.getConnection() instanceof PlayerLoginConnection login) {
            PlayerProfile authenticated = login.getAuthenticatedProfile();
            return authenticated != null ? authenticated : login.getUnsafeProfile();
        }
        return null;
    }
}
