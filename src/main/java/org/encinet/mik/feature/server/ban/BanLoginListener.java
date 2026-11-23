package org.encinet.mik.feature.server.ban;

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
import org.encinet.mik.shared.i18n.LanguageService;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.function.Predicate;
final class BanLoginListener implements Listener {

    private final JavaPlugin plugin;
    private final PaperBanSynchronizer paperSynchronizer;
    private final LanguageService languageService;
    private final BanMessageRenderer renderer;
    private final BanAdmissionChecker admissionChecker;
    private volatile Predicate<UUID> jailedLoginAdmission = _ -> false;

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

    /** Admits directly banned players that are held in a prison cell; null restores the default. */
    void jailedLoginAdmission(Predicate<UUID> admission) {
        jailedLoginAdmission = admission == null ? _ -> false : admission;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        disallowIfBanned(event.getUniqueId(), event.getPlayerProfile().getName(), event.getAddress(),
                component -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, component), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerConnectionValidate(PlayerConnectionValidateLoginEvent event) {
        PlayerProfile profile = connectionProfile(event);
        if (profile == null) {
            return;
        }
        reconcile("login");
        InetAddress address = event.getConnection().getClientAddress().getAddress();
        disallowIfBanned(profile.getId(), profile.getName(), address, event::kickMessage, true);
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
        }, false);
    }

    private void disallowIfBanned(
            java.util.UUID playerUuid,
            String playerName,
            InetAddress address,
            java.util.function.Consumer<Component> disallow,
            boolean allowJailedPlayer
    ) {
        admissionChecker.find(playerUuid, playerName, address).ifPresent(match -> {
            if (allowJailedPlayer && shouldAdmitJailedPlayer(match, playerUuid, jailedLoginAdmission)) return;
            disallow.accept(renderer.banMessage(languageService.languageForAddress(address), match.record()));
        });
    }

    /** A prison cell only overrides a direct ban on the same account, never an address link. */
    static boolean shouldAdmitJailedPlayer(
            BanAdmissionChecker.AdmissionBan match, UUID playerUuid, Predicate<UUID> jailedLoginAdmission) {
        return match.matchType() == BanAdmissionChecker.MatchType.DIRECT && jailedLoginAdmission.test(playerUuid);
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
