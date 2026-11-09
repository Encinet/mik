package org.encinet.mik.shell.menu;

import org.encinet.mik.shared.role.PlayerRole;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.player.client.ClientVersions;
import org.encinet.mik.feature.player.pvp.PvpModule;
import org.encinet.mik.feature.world.plot.PlotModule;
import org.encinet.mik.feature.community.governance.GovernanceModule;
import org.encinet.mik.feature.community.communication.AnnouncementHighlights;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.encinet.mik.shared.util.ShutdownSequence;

public class MainMenuModule {

    private static final DateTimeFormatter FIRST_JOINED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Shanghai"));
    private static final String URL_WEBSITE = "https://mcmik.top";
    private static final String URL_MAP = "https://mcmik.top/map";
    private static final String URL_WIKI = "https://mcmik.top/wiki";
    private static final int LIVE_STATE_REFRESH_TICKS = 5;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final PvpModule pvpModule;
    private final LanguageService languageService;
    private final SettingsMenu settingsMenu;
    private final ClientVersions clientVersions;
    private final PlotModule plotModule;
    private final GovernanceModule governanceModule;
    private final AnnouncementHighlights announcements;
    private final java.util.function.Consumer<Player> vehicles;
    private final Set<Session> sessions = new HashSet<>();

    public MainMenuModule(JavaPlugin plugin, AfkService afkService, PvpModule pvpModule,
                          LanguageService languageService, SettingsMenu settingsMenu,
                          ClientVersions clientVersions,
                          PlotModule plotModule, GovernanceModule governanceModule,
                          AnnouncementHighlights announcements, java.util.function.Consumer<Player> vehicles) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.pvpModule = pvpModule;
        this.languageService = languageService;
        this.settingsMenu = settingsMenu;
        this.clientVersions = clientVersions;
        this.plotModule = plotModule;
        this.governanceModule = governanceModule;
        this.announcements = announcements;
        this.vehicles = vehicles;
    }

    public void enable() {
        settingsMenu.enable();
        FloatingMenus.setMainMenuOpener(this::openMenu);
        plugin.getLogger().info("MainMenuModule enabled");
    }

    public void disable() {
        var shutdown = new ShutdownSequence();
        List.copyOf(sessions).forEach(session -> shutdown.attempt("main menu session", session::close));
        shutdown.attempt("settings", settingsMenu::close);
        shutdown.attempt("main menu opener", FloatingMenus::clearMainMenuOpener);
        shutdown.finish("Main menu shutdown failed");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("menu")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    openMenu(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    languageService.t(Language.DEFAULT, Message.MAIN_MENU_TITLE)
            );
        });
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    public void openMenu(Player player) {
        var session = new Session(player);
        sessions.add(session);
        try { FloatingMenus.openRoot(player, session.scene()); }
        catch (RuntimeException error) { session.close(); throw error; }
    }

    private MenuScene.Builder buildMenu(Player player, UiNode left, UiNode right, Runnable redraw,
                                      int page, java.util.function.IntConsumer selectPage) {
        MenuScene.Builder menu = MenuScene.screen("main")
                .appearance(FloatingMenuAppearance.DEFAULT)
                .stableAnchor()
                .framing(FloatingMenuFraming.COMFORTABLE)
                .content(MainMenuComposition.content(left, right, page, selectPage));
        menu.information("heading",
                        Component.text(languageService.t(player, Message.MAIN_MENU_TITLE),
                                NamedTextColor.LIGHT_PURPLE))
                .region("heading");
        menu.item("home", Material.RED_BED, simpleLabel(player, Message.MAIN_HOME))
                .region("destinations")
                .keepAccessible()
                .primary((p, _) -> runMenuCommand(p, "home"));
        menu.item("plots", Material.GRASS_BLOCK, simpleLabel(player, Message.PLOT_TITLE))
                .region("destinations")
                .keepAccessible()
                .primary((p, _) -> plotModule.openMenu(p));
        menu.item("teleport-points", Material.ENDER_PEARL,
                        simpleLabel(player, Message.PLOT_DESTINATIONS))
                .region("destinations").keepAccessible()
                .primary((viewer, handle) -> handle.openPopover(UiKey.item("teleport-points"),
                        redrawPublic -> plotModule.publicDestinationsPanel(viewer, redrawPublic)));
        menu.item("governance", Material.LECTERN,
                        simpleLabel(player, Message.GOVERNANCE_MENU_TITLE))
                .region("destinations")
                .keepAccessible()
                .primary((p, _) -> governanceModule.openMenu(p));
        menu.item("music", Material.MUSIC_DISC_13, simpleLabel(player, Message.MAIN_MUSIC))
                .region("destinations")
                .keepAccessible()
                .primary((p, _) -> runMenuCommand(p, "music"));
        if (player.hasPermission("mik.vehicle.use") || player.hasPermission("mik.vehicle.admin"))
            menu.item("vehicles", Material.MINECART, languageService.text(player, Message.VEHICLE_MENU_LABEL, NamedTextColor.AQUA, "home"))
                    .region("destinations").keepAccessible().primary((actor, _) -> vehicles.accept(actor));
        menu.item("announcements", Material.PAPER,
                        simpleLabel(player, Message.MAIN_ANNOUNCEMENTS))
                .region("destinations")
                .keepAccessible()
                .primary((p, _) -> runMenuCommand(p, "announcements"));
        menu.control("afk", afkStatusLabel(player)).selected(afkService.isAfk(player.getUniqueId()))
                .region("quick")
                .keepAccessible()
                .primary((p, _) -> runMenuCommand(p, "afk"));
        var pvpToggle = menu.control("pvp-toggle", pvpToggleLabel(player))
                .selected(pvpModule.isEnabled(player))
                .region("quick")
                .keepAccessible()
                .primary((p, _) -> {
                    pvpModule.toggleQuickPvp(p);
                    redraw.run();
                });
        pvpModule.quickToggleDisabledReason(player).ifPresent(pvpToggle::disabled);
        menu.navigation("settings", simpleLabel(player, Message.MAIN_SETTINGS))
                .region("footer")
                .keepAccessible()
                .primary((p, _) -> settingsMenu.open(p));
        menu.close(
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("footer")
                .keepAccessible();
        return menu;
    }

    private final class Session implements AutoCloseable {
        private final Player player;
        private final MainMenuSidebar left;
        private final MainMenuSidebar right;
        private FloatingMenuHandle handle;
        private int destinationsPage;
        private boolean closed;

        Session(Player player) {
            this.player = player;
            left = new MainMenuSidebar("left", this::redraw, List.of(
                    new MainMenuSidebar.Widget("profile", () -> profilePanel(player), () -> profile(player)),
                    new MainMenuSidebar.Widget("announcements", announcements.latestPanel(player), announcements::revision)));
            right = new MainMenuSidebar("right", this::redraw, List.of(
                    new MainMenuSidebar.Widget("links", () -> linksPanel(player)),
                    new MainMenuSidebar.Widget("governance", governanceModule.activityPanel(player, this::redraw)),
                    new MainMenuSidebar.Widget("plot", plotModule.noticePanel(player), () -> plotModule.noticeBoardAt(player))));
        }

        MenuScene scene() {
            MenuScene leftScene = left.render();
            MenuScene rightScene = right.render();
            var lifecycle = FloatingMenuLifecycle.combine(leftScene.lifecycle(), rightScene.lifecycle());
            return buildMenu(player, leftScene.content(), rightScene.content(), this::redraw,
                    destinationsPage, page -> { destinationsPage = page; redraw(); })
                    .refreshWhenChanged(LIVE_STATE_REFRESH_TICKS, _ -> liveRevision(), (_, _) -> redraw())
                    .lifecycle((viewer, host, previous, next, reason) -> {
                        handle = host;
                        try {
                            if (next == FloatingMenuState.OPENING || next == FloatingMenuState.ACTIVE) {
                                left.attach(host);
                                right.attach(host);
                            }
                            lifecycle.changed(viewer, host, previous, next, reason);
                        } finally {
                            if (next == FloatingMenuState.CLOSING || next == FloatingMenuState.CLOSED) dispose();
                        }
                    }).build();
        }

        private MainMenuRevision liveRevision() {
            return new MainMenuRevision(afkStatusLabel(player), pvpToggleLabel(player),
                    pvpModule.quickToggleDisabledReason(player).orElse(Component.empty()), left.revision(), right.revision(), plotModule.destinationsRevision());
        }

        void redraw() {
            if (!closed && handle != null && handle.state() != FloatingMenuState.CLOSING && handle.state() != FloatingMenuState.CLOSED)
                handle.update(scene());
        }

        private void dispose() {
            if (closed) return;
            closed = true;
            var shutdown = new ShutdownSequence();
            shutdown.attempt("left sidebar", left::close);
            shutdown.attempt("right sidebar", right::close);
            try { shutdown.finish("main menu session"); }
            finally { sessions.remove(this); }
        }

        @Override public void close() {
            try { if (handle != null) handle.close(); }
            finally { dispose(); }
        }
    }

    private MenuScene profilePanel(Player player) {
        return MenuScene.screen("profile").content(profile(player).content(
                simpleLabel(player, Message.GOVERNANCE_MENU_IDENTITY_HINT),
                (viewer, handle) -> handle.openPopover(UiKey.named("identity"),
                        redraw -> governanceModule.identityPanel(viewer, redraw)))).build();
    }

    private MenuScene linksPanel(Player player) {
        return MenuScene.screen("links").content(UiTemplates.menu(
                Ui.heading(simpleLabel(player, Message.MAIN_LINKS)).key("links-heading").build(),
                Ui.column().key("links").gap(UiLength.ui(8))
                        .child(link(player, "website", Message.MAIN_WEBSITE, URL_WEBSITE), LinearSlot.stretch())
                        .child(link(player, "map", Message.MAIN_MAP, URL_MAP), LinearSlot.stretch())
                        .child(link(player, "wiki", Message.MAIN_WIKI, URL_WIKI), LinearSlot.stretch()).build())).build();
    }

    private UiNode link(Player player, String id, Message label, String url) {
        return Ui.button(simpleLabel(player, label)).key(id)
                .primary((viewer, handle) -> openUrl(viewer, handle, label, url)).build();
    }

    private void openUrl(Player player, FloatingMenuHandle handle, Message title, String url) {
        handle.close();
        Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                languageService.t(player, title), url, languageService));
    }

    private void runMenuCommand(Player player, String command) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    private MainMenuProfile profile(Player player) {
        Location location = player.getLocation();
        return new MainMenuProfile(Component.object(player), Component.text(player.getName(), NamedTextColor.WHITE),
                playerIdentity(player), List.of(
                        fact(player, "play-time", Message.STAT_PLAY_TIME, Component.text(formatPlayTime(player), NamedTextColor.GREEN)),
                        fact(player, "first-joined", Message.STAT_FIRST_JOINED, Component.text(formatFirstJoined(player), NamedTextColor.YELLOW)),
                        fact(player, "world", Message.STAT_WORLD, Component.text(player.getWorld().getName(), NamedTextColor.AQUA)),
                        fact(player, "location", Message.STAT_LOCATION, Component.text(location.getBlockX() + ", " + location.getBlockY() + ", "
                                + location.getBlockZ(), NamedTextColor.YELLOW)),
                        fact(player, "client-version", Message.STAT_CLIENT_VERSION, clientVersionValue(player)),
                        fact(player, "ping", Message.STAT_PING, languageService.text(player, Message.STAT_PING_VALUE,
                                NamedTextColor.GREEN, player.getPing()))));
    }

    private MainMenuProfile.Fact fact(Player player, String key, Message label, Component value) {
        return new MainMenuProfile.Fact(key, languageService.text(player, label, NamedTextColor.WHITE), value);
    }

    private Component playerIdentity(Player player) {
        PlayerRole role = PlayerRole.resolve(player);
        NamedTextColor color = switch (role) {
            case CUSTODIAN -> NamedTextColor.RED;
            case MODERATOR -> NamedTextColor.LIGHT_PURPLE;
            case MEMBER -> NamedTextColor.GOLD;
            case DEFAULT -> NamedTextColor.WHITE;
        };
        return statLine(player, Message.STAT_ROLE, languageService.t(player, role.label()), color);
    }

    private Component pvpToggleLabel(Player player) {
        boolean enabled = pvpModule.isEnabled(player);
        return Component.text(languageService.t(player, Message.MAIN_PVP_STATUS, enabled ? "on" : "off"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.WHITE);
    }

    private Component simpleLabel(Player player, Message title) {
        return Component.text(languageService.t(player, title), NamedTextColor.AQUA);
    }

    private Component afkStatusLabel(Player player) {
        boolean afk = afkService.isAfk(player.getUniqueId());
        return Component.text(languageService.t(player, Message.MAIN_AFK_STATUS, afk ? "afk" : "online"),
                afk ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
    }

    private Component statLine(Player player, Message label, String value, NamedTextColor valueColor) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.WHITE))
                .append(Component.text(value, valueColor))
                .build();
    }

    private Component clientVersionValue(Player player) {
        var version = clientVersions.version(player);
        String versionName = version.map(ClientVersions.Version::name)
                .orElseGet(() -> languageService.t(player, Message.UNKNOWN));
        boolean olderClient = version.map(ClientVersions.Version::outdated).orElse(false);
        Component value = Component.text(versionName, olderClient ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        if (olderClient) {
            value = Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.GOLD))
                    .append(value)
                    .build();
        }
        return value;
    }

    private String formatPlayTime(Player player) {
        Language language = languageService.language(player);
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        Duration duration = Duration.ofSeconds(ticks / 20L);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return languageService.t(language, Message.TIME_HOURS_MINUTES, hours, minutes);
        }
        return languageService.t(language, Message.TIME_MINUTES, minutes);
    }

    private String formatFirstJoined(Player player) {
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0) {
            return languageService.t(player, Message.UNKNOWN);
        }
        return FIRST_JOINED_FORMAT.format(Instant.ofEpochMilli(firstPlayed));
    }

    /** Structural components make unchanged samples free of entity metadata updates. */
    private record MainMenuRevision(
            Component afk,
            Component pvp,
            Component pvpDisabledReason,
            List<?> left,
            List<?> right,
            long destinations) {
    }

}
