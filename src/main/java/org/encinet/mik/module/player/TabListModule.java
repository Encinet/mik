package org.encinet.mik.module.player;

import org.encinet.mik.module.role.RolePermissions;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.afk.AfkStateListener;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageChangeListener;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.player.identity.PlayerIdentityRenderer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TabListModule implements Listener, AfkStateListener, LanguageChangeListener {

    private static final long REFRESH_INTERVAL_TICKS = 5L * 20L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static int footerOnlinePlayersMinOnline = 5;

    private static final Component TABLIST_HEADER = MINI_MESSAGE.deserialize(
            "<gold><bold>Mi</bold><white><bold>k</bold> <green><bold>Casual</bold></green></white></gold>"
    );
    private static final Component AFK_TABLIST_HEADER = MINI_MESSAGE.deserialize(
            "<gold><bold>AF</bold><white><bold>K</bold> <green><bold>Casual</bold></green></white></gold>"
    );
    private static final int AFK_EASTER_EGG_MIN_PLAYERS = 3;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final PlayerIdentityRenderer playerIdentities;
    private BukkitTask refreshTask;
    private BukkitTask pendingAfkRefreshTask;

    public TabListModule(JavaPlugin plugin, AfkService afkService,
                         LanguageService languageService,
                         PlayerIdentityRenderer playerIdentities) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.playerIdentities = playerIdentities;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);
        languageService.addLanguageChangeListener(this);

        refreshAll();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);

        plugin.getLogger().info("TabListModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendPlayerListHeaderAndFooter(resolveTabListHeader(), tabListFooter(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updateAllPlayerListNames();
                updateAllHeadersAndFooters();
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(plugin, this::updateAllHeadersAndFooters);
    }

    @Override
    public void onAfkStateChanged(Player player, AfkState state) {
        if (player.isOnline()) {
            updatePlayerListNameForAllViewers(player);
            scheduleAfkHeaderRefresh();
        }
    }

    @Override
    public void onLanguageChanged(Player player) {
        if (!player.isOnline()) {
            return;
        }
        updatePlayerListNamesForViewer(player);
        player.sendPlayerListHeaderAndFooter(resolveTabListHeader(), tabListFooter(player));
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        afkService.removeListener(this);
        languageService.removeLanguageChangeListener(this);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (pendingAfkRefreshTask != null) {
            pendingAfkRefreshTask.cancel();
            pendingAfkRefreshTask = null;
        }
        List<Player> onlinePlayers = List.copyOf(Bukkit.getOnlinePlayers());
        for (Player viewer : onlinePlayers) {
            restorePlayerListNames(viewer, onlinePlayers);
            viewer.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void refreshAll() {
        updateAllPlayerListNames();
        updateAllHeadersAndFooters();
    }

    private void updateAllPlayerListNames() {
        List<Player> onlinePlayers = List.copyOf(Bukkit.getOnlinePlayers());
        Map<Language, Map<UUID, Component>> namesByLanguage = new EnumMap<>(Language.class);
        for (Player viewer : onlinePlayers) {
            Language language = languageService.language(viewer);
            Map<UUID, Component> localizedNames = namesByLanguage.computeIfAbsent(
                    language,
                    value -> renderPlayerListNames(onlinePlayers, value));
            sendPlayerListNames(viewer, onlinePlayers, localizedNames);
        }
    }

    private void updatePlayerListNameForAllViewers(Player subject) {
        Map<Language, Component> localizedNames = new EnumMap<>(Language.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!canSee(viewer, subject)) {
                continue;
            }
            Language language = languageService.language(viewer);
            Component displayName = localizedNames.computeIfAbsent(
                    language,
                    value -> renderPlayerListName(subject, value));
            sendPlayerListNames(viewer, List.of(subject),
                    Map.of(subject.getUniqueId(), displayName));
        }
    }

    private void updatePlayerListNamesForViewer(Player viewer) {
        List<Player> onlinePlayers = List.copyOf(Bukkit.getOnlinePlayers());
        sendPlayerListNames(
                viewer,
                onlinePlayers,
                renderPlayerListNames(onlinePlayers, languageService.language(viewer)));
    }

    private Map<UUID, Component> renderPlayerListNames(
            List<Player> subjects,
            Language language
    ) {
        Map<UUID, Component> names = new HashMap<>();
        for (Player subject : subjects) {
            names.put(subject.getUniqueId(), renderPlayerListName(subject, language));
        }
        return names;
    }

    private Component renderPlayerListName(Player subject, Language language) {
        Component identity = playerIdentities
                .render(subject, language, renderPlayerName(subject))
                .component();
        return afkService.isAfk(subject.getUniqueId())
                ? identity.append(renderAfkBadge())
                : identity;
    }

    private void sendPlayerListNames(
            Player viewer,
            List<Player> subjects,
            Map<UUID, Component> displayNames
    ) {
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (Player subject : subjects) {
            if (!canSee(viewer, subject)) {
                continue;
            }
            Component displayName = displayNames.get(subject.getUniqueId());
            if (displayName == null) {
                continue;
            }
            entries.add(playerInfo(subject, displayName));
        }
        sendPlayerListNames(viewer, entries);
    }

    private void restorePlayerListNames(Player viewer, List<Player> subjects) {
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
        for (Player subject : subjects) {
            if (canSee(viewer, subject)) {
                entries.add(playerInfo(subject, subject.playerListName()));
            }
        }
        sendPlayerListNames(viewer, entries);
    }

    private WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo(
            Player subject,
            Component displayName
    ) {
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(subject.getUniqueId());
        entry.setDisplayName(displayName);
        return entry;
    }

    private void sendPlayerListNames(
            Player viewer,
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries
    ) {
        if (entries.isEmpty()) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                new WrapperPlayServerPlayerInfoUpdate(
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                        entries));
    }

    private boolean canSee(Player viewer, Player subject) {
        return subject.isOnline()
                && (viewer.getUniqueId().equals(subject.getUniqueId()) || viewer.canSee(subject));
    }

    private void updateAllHeadersAndFooters() {
        Component header = resolveTabListHeader();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, tabListFooter(player));
        }
    }

    private void scheduleAfkHeaderRefresh() {
        if (pendingAfkRefreshTask != null) {
            return;
        }
        pendingAfkRefreshTask = Bukkit.getScheduler().runTask(plugin, () -> {
            pendingAfkRefreshTask = null;
            updateAllHeadersAndFooters();
        });
    }

    private Component tabListFooter(Player viewer) {
        List<Component> footers = footerCandidates(viewer);
        if (footers.isEmpty()) {
            return Component.empty();
        }
        return footers.get(ThreadLocalRandom.current().nextInt(footers.size()));
    }

    private List<Component> footerCandidates(Player viewer) {
        List<Component> footers = new ArrayList<>();
        if (Bukkit.getOnlinePlayers().size() >= footerOnlinePlayersMinOnline) {
            footers.add(onlineFooter(viewer));
        }
        footers.add(tpsFooter(viewer));
        footers.add(msptFooter(viewer));
        footers.add(pingFooter(viewer));
        return footers;
    }

    private Component onlineFooter(Player viewer) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        return languageService.rich(viewer, Message.TABLIST_FOOTER_ONLINE_RICH, NamedTextColor.GRAY,
                richNumber("online", onlinePlayers, NamedTextColor.GREEN),
                richNumber("max", maxPlayers, NamedTextColor.GRAY));
    }

    private Component tpsFooter(Player viewer) {
        double tps = Math.clamp(Bukkit.getTPS()[0], 0.0, 20.0);
        String formatted = String.format(Locale.ROOT, "%.2f", tps);
        return languageService.rich(viewer, Message.TABLIST_FOOTER_TPS_RICH, NamedTextColor.GRAY,
                RichArg.component("tps", Component.text(formatted, tpsColor(tps)), formatted));
    }

    private Component msptFooter(Player viewer) {
        double mspt = Math.max(0.0, Bukkit.getAverageTickTime());
        String formatted = String.format(Locale.ROOT, "%.2f", mspt);
        return languageService.rich(viewer, Message.TABLIST_FOOTER_MSPT_RICH, NamedTextColor.GRAY,
                RichArg.component("mspt", Component.text(formatted, msptColor(mspt)), formatted));
    }

    private Component pingFooter(Player viewer) {
        int ping = Math.max(0, viewer.getPing());
        return languageService.rich(viewer, Message.TABLIST_FOOTER_PING_RICH, NamedTextColor.GRAY,
                RichArg.component("ping", Component.text(ping, pingColor(ping)), Integer.toString(ping)));
    }

    private RichArg richNumber(String name, int value, NamedTextColor color) {
        return RichArg.component(name, Component.text(value, color), Integer.toString(value));
    }

    private NamedTextColor tpsColor(double tps) {
        if (tps >= 19.0) {
            return NamedTextColor.GREEN;
        }
        if (tps >= 15.0) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private NamedTextColor msptColor(double mspt) {
        if (mspt < 40.0) {
            return NamedTextColor.GREEN;
        }
        if (mspt < 50.0) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private NamedTextColor pingColor(int ping) {
        if (ping < 100) {
            return NamedTextColor.GREEN;
        }
        if (ping < 200) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private Component resolveTabListHeader() {
        return shouldShowAfkEasterEgg() ? AFK_TABLIST_HEADER : TABLIST_HEADER;
    }

    private boolean shouldShowAfkEasterEgg() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (onlinePlayers < AFK_EASTER_EGG_MIN_PLAYERS) {
            return false;
        }

        int nonAfkPlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!afkService.isAfk(player.getUniqueId())) {
                nonAfkPlayers++;
            }
        }

        int nonAfkThreshold = Math.max(1, (int) Math.floor(onlinePlayers * 0.1));
        return nonAfkPlayers <= nonAfkThreshold;
    }

    private Component renderPlayerName(Player player) {
        NamedTextColor color = RolePermissions.isMember(player)
                ? NamedTextColor.WHITE
                : NamedTextColor.YELLOW;
        return Component.text(player.getName(), color);
    }

    private Component renderAfkBadge() {
        return MINI_MESSAGE.deserialize(" <gray>[</gray><gold>AFK</gold><gray>]</gray>");
    }
}
