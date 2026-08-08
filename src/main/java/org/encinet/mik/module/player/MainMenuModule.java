package org.encinet.mik.module.player;

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
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.chat.ChatModule;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.pvp.PvpModule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.Locale;

public class MainMenuModule {

    private static final DateTimeFormatter FIRST_JOINED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Shanghai"));
    private static final String URL_WEBSITE = "https://mcmik.top";
    private static final String URL_MAP = "https://mcmik.top/map";
    private static final String URL_WIKI = "https://mcmik.top/wiki";
    private static final int LIVE_STATE_REFRESH_TICKS = 5;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final ChatModule chatModule;
    private final TeleportPreferenceModule teleportPreferenceModule;
    private final PvpModule pvpModule;
    private final LanguageService languageService;
    private final ClientVersionReminderModule clientVersionReminderModule;

    public MainMenuModule(JavaPlugin plugin, AfkService afkService, ChatModule chatModule,
                          TeleportPreferenceModule teleportPreferenceModule, PvpModule pvpModule,
                          LanguageService languageService, ClientVersionReminderModule clientVersionReminderModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.chatModule = chatModule;
        this.teleportPreferenceModule = teleportPreferenceModule;
        this.pvpModule = pvpModule;
        this.languageService = languageService;
        this.clientVersionReminderModule = clientVersionReminderModule;
    }

    public void enable() {
        FloatingMenus.setMainMenuOpener(this::openMenu);
        plugin.getLogger().info("MainMenuModule enabled");
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
        FloatingMenus.openRoot(player, buildMenu(player));
    }

    private FloatingMenuDefinition buildMenu(Player player) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("main")
                .framing(FloatingMenuFraming.PANORAMIC)
                .layout(FloatingMenuLayouts.sidecar(
                        FloatingMenuLayouts.panel("navigation",
                                FloatingMenuLayouts.menu(
                                        FloatingMenuLayouts.heading("heading"),
                                        FloatingMenuLayouts.actions("links", 3),
                                        FloatingMenuLayouts.actions("actions", 5),
                                        FloatingMenuLayouts.navigation("footer")),
                                "heading", "links", "actions", "footer"),
                        FloatingMenuLayouts.panel("profile",
                                FloatingMenuLayouts.orient(
                                        FloatingMenuLayouts.adaptiveColumn(0.0),
                                        -10.0, 0.0),
                                "profile"),
                        FloatingMenuLayouts.Side.LEFT, 0.48))
                .refreshWhenChanged(LIVE_STATE_REFRESH_TICKS,
                        this::liveRevision,
                        (p, handle) -> handle.update(buildMenu(p)));
        menu.information("player-info", playerSummary(player))
                .region("profile")
                .alignment(FloatingMenuDecoration.Alignment.RIGHT);
        menu.information("heading",
                        Component.text(languageService.t(player, Message.MAIN_MENU_TITLE),
                                NamedTextColor.DARK_PURPLE))
                .region("heading");
        menu.item("website", Material.COMPASS, simpleLabel(player, Message.MAIN_WEBSITE))
                .region("links")
                .primary((p, menuHandle) -> openUrl(p, Message.MAIN_WEBSITE, URL_WEBSITE));
        menu.item("map", Material.FILLED_MAP, simpleLabel(player, Message.MAIN_MAP))
                .region("links")
                .primary((p, menuHandle) -> openUrl(p, Message.MAIN_MAP, URL_MAP));
        menu.item("wiki", Material.BOOK, simpleLabel(player, Message.MAIN_WIKI))
                .region("links")
                .primary((p, menuHandle) -> openUrl(p, Message.MAIN_WIKI, URL_WIKI));
        menu.item("language", Material.WRITABLE_BOOK, languageMenuLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> languageService.openMenu(p));
        menu.item("interface-scale", Material.SPYGLASS, interfaceScaleLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> FloatingMenus.openSettings(p));
        menu.item("chat", Material.BELL, chatSettingsMenuLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> chatModule.openSettingsMenu(p));
        menu.item("teleport", Material.SHIELD, teleportMenuLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> teleportPreferenceModule.openMenu(p));
        menu.choice("afk", afkService.isAfk(player.getUniqueId()),
                        Material.CLOCK, afkStatusLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> runMenuCommand(p, "afk"));
        menu.item("nametag", Material.NAME_TAG, simpleLabel(player, Message.MAIN_NAME_TAG))
                .region("actions")
                .primary((p, menuHandle) -> runMenuCommand(p, "nametag"));
        menu.item("home", Material.RED_BED, simpleLabel(player, Message.MAIN_HOME))
                .region("actions")
                .primary((p, menuHandle) -> runMenuCommand(p, "home"));
        menu.item("announcements", Material.PAPER,
                        simpleLabel(player, Message.MAIN_ANNOUNCEMENTS))
                .region("actions")
                .primary((p, menuHandle) -> runMenuCommand(p, "announcements"));
        menu.item("music", Material.MUSIC_DISC_13, simpleLabel(player, Message.MAIN_MUSIC))
                .region("actions")
                .primary((p, menuHandle) -> runMenuCommand(p, "music"));
        menu.choice("pvp", pvpModule.isEnabled(player),
                        Material.IRON_SWORD, pvpMenuLabel(player))
                .region("actions")
                .primary((p, menuHandle) -> {
                    pvpModule.togglePvp(p);
                    menuHandle.update(buildMenu(p));
                })
                .secondary((p, menuHandle) -> pvpModule.openMenu(p));
        menu.close(
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("footer");
        return menu.build();
    }

    private void openUrl(Player player, Message title, String url) {
        FloatingMenus.current(player).ifPresent(handle -> handle.close());
        Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                languageService.t(player, title), url, languageService));
    }

    private void runMenuCommand(Player player, String command) {
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        boolean opensChildMenu = switch (root) {
            case "home", "announcements", "announcement", "music" -> true;
            default -> false;
        };
        if (!opensChildMenu) FloatingMenus.current(player).ifPresent(handle -> handle.close());
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    private Component playerSummary(Player player) {
        Location location = player.getLocation();
        RoleDisplay role = roleDisplay(player);
        return statLine(player, Message.STAT_ROLE,
                        languageService.t(player, role.label()), role.color())
                .append(Component.newline()).append(clientVersionLine(player))
                .append(Component.newline()).append(statLine(player, Message.STAT_PLAY_TIME,
                        formatPlayTime(player), NamedTextColor.GREEN))
                .append(Component.newline()).append(statLine(player, Message.STAT_PING,
                        player.getPing() + "ms", NamedTextColor.GREEN))
                .append(Component.newline()).append(statLine(player, Message.STAT_FIRST_JOINED,
                        formatFirstJoined(player), NamedTextColor.YELLOW))
                .append(Component.newline()).append(statLine(player, Message.STAT_WORLD,
                        player.getWorld().getName(), NamedTextColor.AQUA))
                .append(Component.newline()).append(statLine(player, Message.STAT_LOCATION,
                        location.getBlockX() + ", " + location.getBlockY() + ", "
                                + location.getBlockZ(), NamedTextColor.YELLOW));
    }

    private RoleDisplay roleDisplay(Player player) {
        if (player.hasPermission("group." + Mik.GROUP_MANAGER)) {
            return new RoleDisplay(Message.ROLE_MANAGER, NamedTextColor.RED);
        }
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return new RoleDisplay(Message.ROLE_HELPER, NamedTextColor.LIGHT_PURPLE);
        }
        if (player.hasPermission("group." + Mik.GROUP_MEMBER)) {
            return new RoleDisplay(Message.ROLE_MEMBER, NamedTextColor.GOLD);
        }
        return new RoleDisplay(Message.ROLE_NEW_PLAYER, NamedTextColor.GRAY);
    }

    private Component chatSettingsMenuLabel(Player player) {
        Component label = Component.text(languageService.t(player,
                Message.CHAT_SETTINGS_MENU_TITLE), NamedTextColor.AQUA);
        for (Component line : chatModule.settingsSummary(player)) {
            label = label.append(Component.newline()).append(line.colorIfAbsent(NamedTextColor.GRAY));
        }
        return label;
    }

    private Component teleportMenuLabel(Player player) {
        return Component.text(languageService.t(player, Message.TELEPORT_MENU_TITLE),
                        NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(teleportPreferenceModule.summary(player),
                        NamedTextColor.GRAY));
    }

    private Component pvpMenuLabel(Player player) {
        return Component.text(languageService.t(player, Message.MAIN_PVP),
                        pvpModule.isEnabled(player) ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                .append(Component.newline())
                .append(pvpModule.stateLine(player, Message.PVP_STATE_LABEL,
                        pvpModule.isEnabled(player)));
    }

    private Component languageMenuLabel(Player player) {
        return Component.text(languageService.t(player, Message.MAIN_LANGUAGE), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(languageService.languageLabel(player), NamedTextColor.GRAY));
    }

    private Component interfaceScaleLabel(Player player) {
        FloatingMenuScale scale = FloatingMenus.scale(player);
        Message name = switch (scale) {
            case SMALL -> Message.INTERFACE_SCALE_SMALL;
            case NORMAL -> Message.INTERFACE_SCALE_NORMAL;
            case LARGE -> Message.INTERFACE_SCALE_LARGE;
        };
        String current = languageService.t(player, name)
                + " · " + scale.textPercent() + "%";
        return Component.text(languageService.t(player, Message.INTERFACE_SCALE_MENU_TITLE),
                        NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                Message.INTERFACE_SCALE_CURRENT,
                                current),
                        NamedTextColor.GRAY));
    }

    private Component simpleLabel(Player player, Message title) {
        return Component.text(languageService.t(player, title), NamedTextColor.AQUA);
    }

    private Component afkStatusLabel(Player player) {
        Message state = afkService.isAfk(player.getUniqueId())
                ? Message.MAIN_AFK_CURRENT_AFK : Message.MAIN_AFK_CURRENT_ONLINE;
        return Component.text(languageService.t(player, Message.MAIN_AFK_STATUS), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, state), NamedTextColor.GRAY));
    }

    private Component statLine(Player player, Message label, String value, NamedTextColor valueColor) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor))
                .build();
    }

    private Component statLine(Player player, Message label, Component value) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.GRAY))
                .append(value)
                .build();
    }

    private Component clientVersionLine(Player player) {
        String versionName = clientVersionReminderModule != null
                ? clientVersionReminderModule.clientVersionName(player)
                : languageService.t(player, Message.UNKNOWN);
        boolean outdated = clientVersionReminderModule != null && clientVersionReminderModule.isOutdated(player);
        Component value = Component.text(versionName, outdated ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        if (outdated) {
            value = Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.GOLD))
                    .append(value)
                    .build();
        }
        return statLine(player, Message.STAT_CLIENT_VERSION, value);
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

    private MainMenuRevision liveRevision(Player player) {
        return new MainMenuRevision(
                playerSummary(player),
                languageMenuLabel(player),
                interfaceScaleLabel(player),
                chatSettingsMenuLabel(player),
                teleportMenuLabel(player),
                afkStatusLabel(player),
                pvpMenuLabel(player));
    }

    /** Structural components make unchanged samples free of entity metadata updates. */
    private record MainMenuRevision(
            Component playerInformation,
            Component language,
            Component interfaceScale,
            Component chat,
            Component teleport,
            Component afk,
            Component pvp) {
    }

    private record RoleDisplay(Message label, NamedTextColor color) {
    }
}
