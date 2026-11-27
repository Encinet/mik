package org.encinet.mik.feature.server.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.server.players.NameAndId;
import org.bukkit.craftbukkit.CraftServer;
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
        Optional<BanAdmissionChecker.AdmissionBan> ban = admissionChecker.find(profile.getId(), profile.getName(), address);
        if (ban.isEmpty()) return;
        var match = ban.get();
        if (shouldAdmitJailedPlayer(match, profile.getId(), jailedLoginAdmission)) {
            var linked = admissionChecker.findAddressLink(profile.getId(), address);
            if (linked.isPresent()) {
                event.kickMessage(banMessage(address, linked.get()));
            } else if (nativeProfileBan(event.getKickMessage())) {
                recheckWithoutProfileBan(event, profile);
            }
        } else {
            event.kickMessage(banMessage(address, match));
        }
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
        if (allowJailedPlayer && ban.filter(match -> shouldAdmitJailedPlayer(match, playerUuid, jailedLoginAdmission))
                .isPresent()) {
            ban = admissionChecker.findAddressLink(playerUuid, address);
        }
        ban.ifPresent(match -> disallow.accept(banMessage(address, match)));
    }

    private Component banMessage(InetAddress address, BanAdmissionChecker.AdmissionBan match) {
        return renderer.banMessage(languageService.languageForAddress(address), match.record());
    }

    static boolean shouldAdmitJailedPlayer(
            BanAdmissionChecker.AdmissionBan match, UUID playerUuid, Predicate<UUID> jailedLoginAdmission) {
        return playerUuid != null && match.matchType() == BanAdmissionChecker.MatchType.DIRECT
                && playerUuid.equals(match.record().playerUuid()) && jailedLoginAdmission.test(playerUuid);
    }

    static boolean nativeProfileBan(Component message) {
        return message instanceof TranslatableComponent translation
                && translation.key().equals("multiplayer.disconnect.banned.reason");
    }

    /** Paper returns at the profile ban, before testing whitelist, IP bans and capacity. */
    private void recheckWithoutProfileBan(PlayerConnectionValidateLoginEvent event, PlayerProfile profile) {
        if (event.isAsynchronous() || profile.getName() == null) return;
        var players = ((CraftServer) plugin.getServer()).getHandle();
        var account = new NameAndId(profile.getId(), profile.getName());
        var whitelist = players.isWhiteListedLogin(account);
        if (!whitelist.isAllowed()) {
            event.kickMessage(PaperAdventure.asAdventure(whitelist.message()));
            return;
        }
        if (players.getIpBans().isBanned(event.getConnection().getAddress())) return;
        boolean full = players.getPlayerCount() >= players.getMaxPlayers() && !players.canBypassPlayerLimit(account);
        var capacity = new PlayerServerFullCheckEvent(profile,
                Component.translatable("multiplayer.disconnect.server_full"), full);
        capacity.callEvent();
        if (capacity.isAllowed()) event.allow();
        else event.kickMessage(capacity.kickMessage());
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
