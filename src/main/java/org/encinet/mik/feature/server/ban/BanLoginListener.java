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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

final class BanLoginListener implements Listener {

    private final JavaPlugin plugin;
    private final PaperBanSynchronizer paperSynchronizer;
    private final LanguageService languageService;
    private final BanMessageRenderer renderer;
    private final BanAdmissionChecker admissionChecker;
    private volatile BannedLoginAdmission bannedLoginAdmission = BannedLoginAdmission.NONE;

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

    void bannedLoginAdmission(BannedLoginAdmission admission) {
        bannedLoginAdmission = admission;
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
        Optional<BanAdmissionChecker.AdmissionBan> ban = admissionChecker.find(profile.getId(), profile.getName(), address);
        if (ban.isEmpty()) return;
        validateBan(event, profile, address, ban.get());
        if (!event.isAllowed() && event.getConnection() instanceof PlayerConfigurationConnection configuration)
            PaperProfileBanAdmission.keepFinalRefusal(plugin.getServer(), configuration);
    }

    private void validateBan(PlayerConnectionValidateLoginEvent event, PlayerProfile profile, InetAddress address,
                             BanAdmissionChecker.AdmissionBan match) {
        UUID id = profile.getId();
        if (!matchesAccount(match, id)) {
            event.kickMessage(banMessage(address, match));
            return;
        }
        boolean nativeBan = PaperProfileBanAdmission.isProfileBan(event.getKickMessage());
        if (!event.isAllowed() && !nativeBan) return;
        BannedLoginAdmission admission = bannedLoginAdmission;
        boolean allowed = event.getConnection() instanceof PlayerConfigurationConnection configuration
                ? admission.canEnter(id, configuration)
                : admission.canConfigure(id);
        if (!allowed) {
            event.kickMessage(banMessage(address, match));
            return;
        }
        var linked = admissionChecker.findAddressLink(id, address);
        if (linked.isPresent()) event.kickMessage(banMessage(address, linked.get()));
        else if (nativeBan) PaperProfileBanAdmission.recheck(plugin.getServer(), event, profile);
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

    private void disallowIfBanned(UUID playerUuid, String playerName, InetAddress address,
                                  Consumer<Component> disallow, boolean allowJailedPlayer) {
        var ban = admissionChecker.find(playerUuid, playerName, address);
        if (allowJailedPlayer && ban.filter(match -> matchesAccount(match, playerUuid)
                && bannedLoginAdmission.canConfigure(playerUuid))
                .isPresent()) {
            ban = admissionChecker.findAddressLink(playerUuid, address);
        }
        ban.ifPresent(match -> disallow.accept(banMessage(address, match)));
    }

    private Component banMessage(InetAddress address, BanAdmissionChecker.AdmissionBan match) {
        return renderer.banMessage(languageService.languageForAddress(address), match.record());
    }

    static boolean matchesAccount(BanAdmissionChecker.AdmissionBan match, UUID playerUuid) {
        return playerUuid != null && match.matchType() == BanAdmissionChecker.MatchType.DIRECT
                && playerUuid.equals(match.record().playerUuid());
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
