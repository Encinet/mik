package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.afk.AfkStateListener;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

public class TabListModule implements Listener, AfkStateListener {

    private static final long REFRESH_INTERVAL_TICKS = 5L * 20L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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
    private LuckPerms luckPerms;
    private BukkitTask refreshTask;

    public TabListModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found! TabListModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);

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
                updatePlayerListName(player);
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
            updatePlayerListName(player);
            updateAllHeadersAndFooters();
        }
    }

    public void disable() {
        afkService.removeListener(this);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(null);
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
    }

    private void refreshAll() {
        updateAllPlayerListNames();
        updateAllHeadersAndFooters();
    }

    private void updateAllPlayerListNames() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerListName(player);
        }
    }

    private void updatePlayerListName(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return;
        }

        CachedMetaData metaData = user.getCachedData().getMetaData();
        String prefix = metaData.getPrefix();
        String suffix = metaData.getSuffix();
        String usernameColor = metaData.getMetaValue("username-color");

        TextComponent.Builder builder = Component.text();

        if (prefix != null && !prefix.isEmpty()) {
            builder.append(renderMiniMessage(prefix));
        }

        builder.append(renderPlayerName(player, usernameColor));

        if (suffix != null && !suffix.isEmpty()) {
            builder.append(renderMiniMessage(suffix));
        }

        if (afkService.isAfk(player.getUniqueId())) {
            builder.append(renderAfkBadge());
        }
        player.playerListName(builder.build());
    }

    private void updateAllHeadersAndFooters() {
        Component header = resolveTabListHeader();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, tabListFooter(player));
        }
    }

    private Component tabListFooter(Player viewer) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        return languageService.rich(viewer, Message.TABLIST_FOOTER_ONLINE_RICH,
                net.kyori.adventure.text.format.NamedTextColor.GRAY,
                RichArg.component("online",
                        Component.text(onlinePlayers, net.kyori.adventure.text.format.NamedTextColor.GREEN),
                        Integer.toString(onlinePlayers)),
                RichArg.component("max",
                        Component.text(maxPlayers, net.kyori.adventure.text.format.NamedTextColor.GRAY),
                        Integer.toString(maxPlayers)));
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

    private Component renderPlayerName(Player player, String usernameColor) {
        String escapedName = MINI_MESSAGE.escapeTags(player.getName());
        String colorTag = (usernameColor != null && !usernameColor.isEmpty()) ? usernameColor : "<white>";
        try {
            return MINI_MESSAGE.deserialize(colorTag + escapedName);
        } catch (RuntimeException e) {
            return MINI_MESSAGE.deserialize("<white>" + escapedName);
        }
    }

    private Component renderMiniMessage(String raw) {
        try {
            return MINI_MESSAGE.deserialize(raw);
        } catch (RuntimeException e) {
            return Component.text(raw);
        }
    }

    private Component renderAfkBadge() {
        return MINI_MESSAGE.deserialize(" <gray>[</gray><gold>AFK</gold><gray>]</gray>");
    }
}
