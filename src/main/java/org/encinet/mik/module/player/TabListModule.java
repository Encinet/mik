package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.afk.AfkStateListener;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.util.NameMetaRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class TabListModule implements Listener, AfkStateListener {

    private static final long REFRESH_INTERVAL_TICKS = 5L * 20L;
    private static final long DAY_TICKS = 24_000L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static boolean showFooterOnlinePlayers = true;
    public static boolean showFooterTps = true;
    public static boolean showFooterMspt = true;
    public static boolean showFooterPing = true;
    public static boolean showFooterWorldDays = true;
    public static boolean showFooterPlayerList = true;
    public static int footerPlayerListMinOnline = 5;
    public static int footerPlayerListMaxNames = 5;

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
    private int playerListOffset = 0;

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

        TextComponent.Builder builder = Component.text();

        if (prefix != null && !prefix.isEmpty()) {
            builder.append(renderMiniMessage(player, prefix));
        }

        builder.append(renderPlayerName(player));

        if (suffix != null && !suffix.isEmpty()) {
            builder.append(renderMiniMessage(player, suffix));
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
        List<Component> footers = footerCandidates(viewer);
        if (footers.isEmpty()) {
            return Component.empty();
        }
        return footers.get(ThreadLocalRandom.current().nextInt(footers.size()));
    }

    private List<Component> footerCandidates(Player viewer) {
        List<Component> footers = new ArrayList<>();
        if (showFooterOnlinePlayers) {
            footers.add(onlineFooter(viewer));
        }
        if (showFooterTps) {
            footers.add(tpsFooter(viewer));
        }
        if (showFooterMspt) {
            footers.add(msptFooter(viewer));
        }
        if (showFooterPing) {
            footers.add(pingFooter(viewer));
        }
        if (showFooterWorldDays) {
            footers.add(worldDaysFooter(viewer));
        }
        if (showFooterPlayerList && Bukkit.getOnlinePlayers().size() >= footerPlayerListMinOnline) {
            footers.add(playersFooter(viewer));
        }
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

    private Component worldDaysFooter(Player viewer) {
        long days = Math.max(0L, viewer.getWorld().getFullTime() / DAY_TICKS);
        return languageService.rich(viewer, Message.TABLIST_FOOTER_WORLD_DAYS_RICH, NamedTextColor.GRAY,
                RichArg.component("world", Component.text(viewer.getWorld().getName(), NamedTextColor.AQUA), viewer.getWorld().getName()),
                RichArg.component("days", Component.text(days, NamedTextColor.GREEN), Long.toString(days)));
    }

    private Component playersFooter(Player viewer) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        List<String> names = onlinePlayers.stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (names.isEmpty()) {
            return onlineFooter(viewer);
        }

        int maxNames = Math.max(1, footerPlayerListMaxNames);
        int count = Math.min(maxNames, names.size());
        int start = Math.floorMod(playerListOffset++, names.size());
        List<String> visibleNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            visibleNames.add(names.get((start + i) % names.size()));
        }
        String visible = String.join(", ", visibleNames);
        return languageService.rich(viewer, Message.TABLIST_FOOTER_PLAYERS_RICH, NamedTextColor.GRAY,
                RichArg.component("players", Component.text(visible, NamedTextColor.YELLOW), visible));
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
        NamedTextColor color = player.hasPermission("group." + Mik.GROUP_MEMBER)
                ? NamedTextColor.WHITE
                : NamedTextColor.YELLOW;
        return Component.text(player.getName(), color);
    }

    private Component renderMiniMessage(Player player, String raw) {
        try {
            return NameMetaRenderer.deserialize(player, raw);
        } catch (RuntimeException e) {
            return NameMetaRenderer.fallback(player, raw);
        }
    }

    private Component renderAfkBadge() {
        return MINI_MESSAGE.deserialize(" <gray>[</gray><gold>AFK</gold><gray>]</gray>");
    }
}
