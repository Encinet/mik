package org.encinet.mik.shell;

import org.encinet.mik.module.role.RolePermissions;

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
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.chat.ChatModule;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuHandle;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.player.ClientVersionReminderModule;
import org.encinet.mik.module.player.TeleportPreferenceModule;
import org.encinet.mik.module.pvp.PvpModule;
import org.encinet.mik.module.plot.PlotModule;
import org.encinet.mik.module.plot.PlotNoticeBoard;
import org.encinet.mik.module.governance.GovernanceModule;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.dashboard.GovernanceDashboardSnapshot;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuPage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

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
    private final LanguageMenu languageMenu;
    private final ClientVersionReminderModule clientVersionReminderModule;
    private final PlotModule plotModule;
    private final GovernanceModule governanceModule;
    private final GovernanceText governanceText;

    public MainMenuModule(JavaPlugin plugin, AfkService afkService, ChatModule chatModule,
                          TeleportPreferenceModule teleportPreferenceModule, PvpModule pvpModule,
                          LanguageService languageService, LanguageMenu languageMenu,
                          ClientVersionReminderModule clientVersionReminderModule,
                          PlotModule plotModule, GovernanceModule governanceModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.chatModule = chatModule;
        this.teleportPreferenceModule = teleportPreferenceModule;
        this.pvpModule = pvpModule;
        this.languageService = languageService;
        this.languageMenu = languageMenu;
        this.clientVersionReminderModule = clientVersionReminderModule;
        this.plotModule = plotModule;
        this.governanceModule = governanceModule;
        this.governanceText = new GovernanceText(languageService);
    }

    public void enable() {
        FloatingMenus.setMainMenuOpener(this::openMenu);
        plugin.getLogger().info("MainMenuModule enabled");
    }

    public void disable() {
        FloatingMenus.clearMainMenuOpener();
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
        GovernanceState governance = new GovernanceState();
        FloatingMenuHandle handle = FloatingMenus.openRoot(player, buildMenu(player, governance));
        governance.handleId = handle.id();
        loadGovernance(player, governance);
    }

    private FloatingMenuDefinition buildMenu(Player player, GovernanceState governance) {
        PlotNoticeBoard plotNotice = plotModule.noticeBoardAt(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("main")
                .appearance(FloatingMenuAppearance.HUB)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(new MainMenuArcLayout())
                .refreshWhenChanged(LIVE_STATE_REFRESH_TICKS,
                        p -> liveRevision(p, governance),
                        (p, handle) -> handle.update(buildMenu(p, governance)));
        menu.information("profile-heading", Component.text(
                        languageService.t(player, Message.MAIN_PROFILE), NamedTextColor.AQUA)
                .append(Component.text(" · " + player.getName(), NamedTextColor.WHITE)))
                .region("profile-heading")
                .textWidth(FloatingMenuTextWidth.WIDE);
        menu.information("player-info", playerSummary(player))
                .region("profile")
                .textWidth(FloatingMenuTextWidth.WIDE)
                .alignment(FloatingMenuDecoration.Alignment.LEFT);
        menu.information("links-heading", simpleLabel(player, Message.MAIN_LINKS))
                .region("links-heading");
        menu.item("website", Material.COMPASS, simpleLabel(player, Message.MAIN_WEBSITE))
                .region("links")
                .primary((p, handle) -> openUrl(p, handle, Message.MAIN_WEBSITE, URL_WEBSITE));
        menu.item("map", Material.FILLED_MAP, simpleLabel(player, Message.MAIN_MAP))
                .region("links")
                .primary((p, handle) -> openUrl(p, handle, Message.MAIN_MAP, URL_MAP));
        menu.item("wiki", Material.BOOK, simpleLabel(player, Message.MAIN_WIKI))
                .region("links")
                .primary((p, handle) -> openUrl(p, handle, Message.MAIN_WIKI, URL_WIKI));
        if (plotNotice != null) {
            Component board = Component.text(plotNotice.name(), NamedTextColor.GOLD)
                    .append(Component.newline())
                    .append(Component.text(languageService.t(player, Message.PLOT_MENU_OWNER,
                            plotNotice.ownerName()), NamedTextColor.GRAY));
            if (!plotNotice.parentName().isEmpty()) {
                board = board.append(Component.newline())
                        .append(Component.text(languageService.t(player,
                                Message.PLOT_MENU_SUBPLOT_OF, plotNotice.parentName()),
                                NamedTextColor.DARK_AQUA));
            }
            if (!plotNotice.body().isEmpty()) {
                board = board.append(Component.newline())
                        .append(Component.text(plotNotice.preview(), NamedTextColor.WHITE))
                        .append(Component.newline()).append(Component.text(languageService.t(player,
                                Message.PLOT_NOTICE_READ), NamedTextColor.AQUA));
            }
            menu.item("plot-notice", Material.OAK_SIGN, board)
                    .region("plot-notice")
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .primary((p, handle) -> {
                        if (plotNotice.body().isEmpty()) plotModule.openCurrentPlot(p);
                        else plotModule.openNoticeBoard(p, plotNotice.plotId());
                    });
        }
        addGovernance(menu, player, governance);
        menu.information("heading",
                        Component.text(languageService.t(player, Message.MAIN_MENU_TITLE),
                                NamedTextColor.LIGHT_PURPLE))
                .region("heading");
        menu.item("home", Material.RED_BED, simpleLabel(player, Message.MAIN_HOME))
                .region("destinations")
                .keepAccessible()
                .primary((p, menuHandle) -> runMenuCommand(p, menuHandle, "home"));
        menu.item("plots", Material.GRASS_BLOCK, simpleLabel(player, Message.PLOT_TITLE))
                .region("plot-actions")
                .keepAccessible()
                .primary((p, menuHandle) -> plotModule.openMenu(p));
        menu.item("governance", Material.LECTERN,
                        simpleLabel(player, Message.GOVERNANCE_MENU_TITLE))
                .region("governance-actions")
                .keepAccessible()
                .primary((p, menuHandle) -> governanceModule.openMenu(p));
        menu.item("music", Material.MUSIC_DISC_13, simpleLabel(player, Message.MAIN_MUSIC))
                .region("destinations")
                .keepAccessible()
                .primary((p, menuHandle) -> runMenuCommand(p, menuHandle, "music"));
        menu.item("announcements", Material.PAPER,
                        simpleLabel(player, Message.MAIN_ANNOUNCEMENTS))
                .region("destinations")
                .keepAccessible()
                .primary((p, menuHandle) -> runMenuCommand(p, menuHandle, "announcements"));
        menu.choice("afk", afkService.isAfk(player.getUniqueId()),
                        Material.CLOCK, afkStatusLabel(player))
                .region("quick")
                .keepAccessible()
                .primary((p, menuHandle) -> runMenuCommand(p, menuHandle, "afk"));
        menu.choice("pvp", pvpModule.isEnabled(player),
                        Material.IRON_SWORD, pvpMenuLabel(player))
                .region("quick")
                .keepAccessible()
                .primary((p, menuHandle) -> {
                    pvpModule.togglePvp(p);
                    menuHandle.update(buildMenu(p, governance));
                })
                .secondary((p, menuHandle) -> pvpModule.openMenu(p));
        menu.item("settings", Material.COMPARATOR, simpleLabel(player, Message.MAIN_SETTINGS))
                .region("more")
                .keepAccessible()
                .primary((p, handle) -> FloatingMenus.open(p, buildSettingsMenu(p)));
        menu.close(
                        Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED))
                .region("footer")
                .keepAccessible();
        return menu.build();
    }

    private void addGovernance(FloatingMenuDefinition.Builder menu, Player player,
                               GovernanceState state) {
        Language language = languageService.language(player);
        menu.information("governance-heading", simpleLabel(player,
                        Message.GOVERNANCE_MENU_VOTES))
                .region("governance-heading");
        if (state.snapshot == null) {
            menu.information("governance-status", simpleLabel(player,
                            state.error ? Message.GOVERNANCE_OPERATION_ERROR
                                    : Message.GOVERNANCE_MENU_LOADING))
                    .region("governance-vote").textWidth(FloatingMenuTextWidth.EXPANDED);
            menu.information("governance-results-heading", simpleLabel(player,
                            Message.GOVERNANCE_MENU_RECENT_RESULTS))
                    .region("governance-results-heading");
            menu.information("governance-results-status", simpleLabel(player,
                            state.error ? Message.GOVERNANCE_OPERATION_ERROR
                                    : Message.GOVERNANCE_MENU_LOADING))
                    .region("governance-results")
                    .textWidth(FloatingMenuTextWidth.EXPANDED);
            if (state.error) {
                menu.item("governance-retry", Material.CLOCK,
                                simpleLabel(player, Message.GOVERNANCE_MENU_REFRESH))
                        .region("governance-pages")
                        .primary((p, handle) -> loadGovernance(p, state));
            }
            return;
        }

        FloatingMenuPage page = new FloatingMenuPage(state.votePage,
                state.snapshot.openVotes().size(), 1);
        state.votePage = page.index();
        if (state.snapshot.openVotes().isEmpty()) {
            menu.information("governance-empty", simpleLabel(player, Message.GOVERNANCE_NONE))
                    .region("governance-vote");
        } else {
            GovernanceDashboardSnapshot.OpenVote card =
                    state.snapshot.openVotes().get(page.fromIndex());
            menu.information("governance-current-vote", governanceVoteDetail(language, card))
                    .region("governance-vote")
                    .textWidth(FloatingMenuTextWidth.EXPANDED)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT);
            if (page.count() > 1) {
                menu.pagination("governance-pages", page, index -> {
                    state.votePage = index;
                    updateMainMenu(player, state);
                });
            }
            if (card.voter() && card.vote().isOpenAt(Instant.now())) {
                addGovernanceChoices(menu, player, state, card);
            }
        }

        menu.information("governance-results-heading", simpleLabel(player,
                        Message.GOVERNANCE_MENU_RECENT_RESULTS))
                .region("governance-results-heading");
        if (state.snapshot.recentResults().isEmpty()) {
            menu.information("governance-results-empty",
                            simpleLabel(player, Message.GOVERNANCE_NONE))
                    .region("governance-results");
        }
        for (GovernanceVote vote : state.snapshot.recentResults()) {
            Component result = Component.text("#" + vote.id() + " · "
                            + governanceText.kind(language, vote.kind()) + " · "
                            + vote.subjectName(), NamedTextColor.WHITE)
                    .append(Component.newline())
                    .append(Component.text(governanceText.result(language, vote),
                            governanceText.resultColor(vote)));
            menu.information("governance-result:" + vote.id(), result)
                    .region("governance-results")
                    .textWidth(FloatingMenuTextWidth.EXPANDED)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT);
        }
    }

    private Component governanceVoteDetail(Language language,
                                            GovernanceDashboardSnapshot.OpenVote card) {
        GovernanceVote vote = card.vote();
        Component detail = Component.text("#" + vote.id() + " · "
                        + governanceText.kind(language, vote.kind()) + " · "
                        + vote.subjectName(), NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(governanceText.t(language,
                        Message.GOVERNANCE_VOTE_PERIOD,
                        governanceText.time(language, vote.opensAt()),
                        governanceText.time(language, vote.closesAt())), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(ownVoteStatus(language, card), NamedTextColor.AQUA));
        String proposal = governanceText.proposalDetails(language, vote.proposal());
        if (proposal != null) {
            detail = detail.append(Component.newline())
                    .append(Component.text(proposal, NamedTextColor.GRAY));
        }
        return detail;
    }

    private String ownVoteStatus(Language language,
                                 GovernanceDashboardSnapshot.OpenVote card) {
        if (!card.voter()) return governanceText.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        if (card.ownChoice() == null) {
            return governanceText.t(language, Message.GOVERNANCE_OWN_NOT_VOTED_OPEN);
        }
        return governanceText.t(language, Message.GOVERNANCE_OWN_CHOICE_OPEN,
                governanceText.choice(language, card.ownChoice()));
    }

    private void addGovernanceChoices(FloatingMenuDefinition.Builder menu, Player player,
                                      GovernanceState state,
                                      GovernanceDashboardSnapshot.OpenVote card) {
        for (VoteChoice choice : VoteChoice.values()) {
            Message message = switch (choice) {
                case YES -> Message.GOVERNANCE_CHOICE_YES;
                case NO -> Message.GOVERNANCE_CHOICE_NO;
                case ABSTAIN -> Message.GOVERNANCE_CHOICE_ABSTAIN;
            };
            Material material = switch (choice) {
                case YES -> Material.LIME_DYE;
                case NO -> Material.RED_DYE;
                case ABSTAIN -> Material.GRAY_DYE;
            };
            var button = menu.choice("governance-choice:" + choice,
                            card.ownChoice() == choice, material, simpleLabel(player, message))
                    .region("governance-ballot")
                    .primary((p, handle) -> castMainMenuBallot(p, state,
                            card.vote().id(), choice));
            if (state.busy) {
                button.disabled(simpleLabel(player, Message.GOVERNANCE_MENU_LOADING));
            }
        }
    }

    private void castMainMenuBallot(Player player, GovernanceState state,
                                    long voteId, VoteChoice choice) {
        if (state.busy || !isCurrentMainMenu(player, state)) return;
        state.busy = true;
        updateMainMenu(player, state);
        governanceModule.dashboard().castBallot(player.getUniqueId(), voteId, choice,
                outcome -> {
                    if (!player.isOnline()) return;
                    String feedback = outcome.recorded()
                            ? languageService.t(player, outcome.feedback(),
                                    governanceText.choice(languageService.language(player), choice))
                            : languageService.t(player, outcome.feedback());
                    Component message = Component.text(feedback,
                            outcome.recorded() ? NamedTextColor.GREEN : NamedTextColor.RED);
                    if (isCurrentMainMenu(player, state)) {
                        FloatingMenus.current(player).ifPresent(handle -> handle.feedback(message,
                                outcome.recorded() ? FloatingMenuFeedbackKind.SUCCESS
                                        : FloatingMenuFeedbackKind.ERROR));
                        loadGovernance(player, state);
                    } else {
                        state.busy = false;
                        player.sendMessage(message);
                    }
                });
    }

    private void loadGovernance(Player player, GovernanceState state) {
        boolean wasError = state.error;
        state.error = false;
        if (wasError) updateMainMenu(player, state);
        governanceModule.dashboard().load(player.getUniqueId(), snapshot -> {
            state.snapshot = snapshot;
            state.busy = false;
            state.revision++;
            updateMainMenu(player, state);
        }, () -> {
            state.snapshot = null;
            state.busy = false;
            state.error = true;
            state.revision++;
            updateMainMenu(player, state);
        });
    }

    private boolean isCurrentMainMenu(Player player, GovernanceState state) {
        return player.isOnline() && FloatingMenus.current(player)
                .filter(handle -> handle.id().equals(state.handleId)).isPresent();
    }

    private void updateMainMenu(Player player, GovernanceState state) {
        if (!player.isOnline()) return;
        FloatingMenus.current(player)
                .filter(handle -> handle.id().equals(state.handleId))
                .ifPresent(handle -> handle.update(buildMenu(player, state)));
    }

    private FloatingMenuDefinition buildSettingsMenu(Player player) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("main-settings")
                .appearance(FloatingMenuAppearance.HUB)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("heading"),
                        FloatingMenuLayouts.actions("settings", 3),
                        FloatingMenuLayouts.navigation("footer")));
        menu.information("heading", simpleLabel(player, Message.MAIN_SETTINGS))
                .region("heading");
        menu.item("language", Material.WRITABLE_BOOK, languageMenuLabel(player))
                .region("settings")
                .primary((p, menuHandle) -> languageMenu.open(p));
        menu.item("interface-scale", Material.SPYGLASS, interfaceScaleLabel(player))
                .region("settings")
                .primary((p, menuHandle) -> FloatingMenus.openSettings(p));
        menu.item("chat", Material.BELL, chatSettingsMenuLabel(player))
                .region("settings")
                .primary((p, menuHandle) -> chatModule.openSettingsMenu(p));
        menu.item("teleport", Material.SHIELD, teleportMenuLabel(player))
                .region("settings")
                .primary((p, menuHandle) -> teleportPreferenceModule.openMenu(p));
        menu.item("nametag", Material.NAME_TAG, simpleLabel(player, Message.MAIN_NAME_TAG))
                .region("settings")
                .primary((p, menuHandle) -> runMenuCommand(p, menuHandle, "nametag"));
        menu.back(Component.text(languageService.t(player, Message.BACK_TO_MAIN),
                        NamedTextColor.GREEN))
                .region("footer");
        return menu.build();
    }

    private void openUrl(Player player, FloatingMenuHandle handle, Message title, String url) {
        handle.close();
        Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                languageService.t(player, title), url, languageService));
    }

    private void runMenuCommand(Player player, FloatingMenuHandle handle, String command) {
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        boolean keepMenuOpen = switch (root) {
            case "home", "announcements", "announcement", "music", "afk" -> true;
            default -> false;
        };
        if (!keepMenuOpen) handle.close();
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
        if (player.hasPermission(RolePermissions.CUSTODIAN)) {
            return new RoleDisplay(Message.ROLE_CUSTODIAN, NamedTextColor.RED);
        }
        if (player.hasPermission(RolePermissions.MODERATOR)) {
            return new RoleDisplay(Message.ROLE_MODERATOR, NamedTextColor.LIGHT_PURPLE);
        }
        if (player.hasPermission(RolePermissions.MEMBER)) {
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
        FloatingMenuPreferences preferences = FloatingMenus.preferences(player);
        String current = languageService.t(player, Message.INTERFACE_SCALE_LAYOUT_TITLE)
                + " " + preferences.layout().percent() + "% · "
                + languageService.t(player, Message.INTERFACE_SCALE_TEXT_TITLE)
                + " " + preferences.text().percent() + "%";
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

    private MainMenuRevision liveRevision(Player player, GovernanceState governance) {
        return new MainMenuRevision(
                afkStatusLabel(player),
                pvpMenuLabel(player), governance.revision,
                plotModule.noticeBoardAt(player));
    }

    /** Structural components make unchanged samples free of entity metadata updates. */
    private record MainMenuRevision(
            Component afk,
            Component pvp,
            int governanceRevision,
            PlotNoticeBoard plotNotice) {
    }

    private static final class GovernanceState {
        private UUID handleId;
        private GovernanceDashboardSnapshot snapshot;
        private int votePage;
        private int revision;
        private boolean error;
        private boolean busy;
    }

    private record RoleDisplay(Message label, NamedTextColor color) {
    }
}
