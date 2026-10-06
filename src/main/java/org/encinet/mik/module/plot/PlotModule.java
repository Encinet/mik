package org.encinet.mik.module.plot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.RolePermissions;
import org.encinet.mik.module.plot.board.PlotCommunityBoard;
import org.encinet.mik.module.plot.board.PlotBoardNotifications;
import org.encinet.mik.module.plot.integration.PlotAxiomHook;
import org.encinet.mik.module.plot.integration.PlotWorldEditHook;
import org.encinet.mik.module.plot.protection.PlotCommandGuard;
import org.encinet.mik.module.plot.protection.PlotProtection;
import org.encinet.mik.util.ShutdownSequence;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/** Project centered XYZ plots with bounded evidence-based growth and a spatial menu. */
public final class PlotModule implements Listener {
    private static final int PLOTS_PER_PAGE = 6;
    private static final int MEMBERS_PER_PAGE = 6;
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRepository store;
    private final PlotCommunityBoard board;
    private final PlotNoticePublisher notices;
    private final PlotRegistry registry;
    private final PlotEdits edits;
    private final PlotArrivalController arrivals;
    private final PlotAtmosphereController atmosphere;
    private final PlotCreationController creation;
    private final PlotProtection protection;
    private final PlotCommandGuard commandGuard;
    private final PlotSelectionState selections = new PlotSelectionState();
    private final PlotPreviewCache previewCache = new PlotPreviewCache();
    private final PlotPreflightCache preflightCache = new PlotPreflightCache(previewCache);
    private final PlotSpatialPreview spatialPreview = new PlotSpatialPreview(previewCache);
    private final PlotMiniaturePreview miniature = new PlotMiniaturePreview(previewCache);
    private final PlotSelectionController selectionController;
    private final Map<UUID, String> playerNames = new HashMap<>();
    private PlotWorldEditHook worldEdit;
    private PlotAxiomHook axiom;
    private final Map<UUID, PlotOwner> creationOwners = new HashMap<>();

    public PlotModule(JavaPlugin plugin, LanguageService language, PlotNoticePublisher notices) {
        this.plugin = plugin;
        this.language = language;
        this.notices = notices;
        store = new PlotRepository(PlotDataPaths.in(plugin.getDataFolder().toPath()).plotsDatabase());
        registry = new PlotRegistry(store);
        edits = new PlotEdits(registry);
        arrivals = new PlotArrivalController(plugin, language, registry);
        atmosphere = new PlotAtmosphereController(plugin, registry);
        board = new PlotCommunityBoard(plugin, language, notices, registry);
        creation = new PlotCreationController(plugin, language, notices, registry, selections,
                board, this::createdPlot);
        protection = new PlotProtection(registry, language);
        commandGuard = new PlotCommandGuard(registry, language);
        selectionController = new PlotSelectionController(selections,
                this::editorAllowed, (player, plotId) -> preview(player, registry.byId(plotId)), this::selectionHint,
                this::selectionStopped,
                this::openPlotEditor,
                (player, problem) -> send(player, NamedTextColor.RED, problem.message(), problem.args()));
    }

    public void enable() {
        try {
            store.open();
            registry.load();
            registry.enableAsyncWrites(action -> Bukkit.getScheduler().runTask(plugin, action));
            board.open();
            atmosphere.enable();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            selectionController.enable(plugin);
            miniature.enable(plugin);
            Bukkit.getPluginManager().registerEvents(protection, plugin);
            Bukkit.getPluginManager().registerEvents(commandGuard, plugin);
            if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
                worldEdit = new PlotWorldEditHook(registry);
                worldEdit.enable();
            }
            if (Bukkit.getPluginManager().isPluginEnabled("AxiomPaper")) {
                axiom = new PlotAxiomHook(plugin, registry);
                axiom.enable();
            }
            plugin.getLogger().info("PlotModule enabled: " + registry.all().size() + " projects");
        } catch (Exception | LinkageError error) {
            try {
                disable();
            } catch (RuntimeException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw new IllegalStateException("Could not enable plot module", error);
        }
    }

    private void createdPlot(Player player, UUID plotId) {
        selectionController.stop(player.getUniqueId());
        miniature.forget(player.getUniqueId());
        openPlotDetail(player, plotId);
    }

    public void disable() {
        registry.stopWrites();
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("plot atmosphere", atmosphere::disable);
        shutdown.attempt("plot selection", selectionController::disable);
        shutdown.attempt("plot miniature", miniature::disable);
        selections.clearAll();
        shutdown.attempt("plot preflight", preflightCache::close);
        shutdown.attempt("plot preview worker", previewCache::close);
        playerNames.clear();
        creationOwners.clear();
        if (worldEdit != null) {
            shutdown.attempt("WorldEdit plot hook", worldEdit::disable);
            worldEdit = null;
        }
        if (axiom != null) {
            shutdown.attempt("Axiom plot hook", axiom::disable);
            axiom = null;
        }
        shutdown.attempt("plot protection", () -> HandlerList.unregisterAll(protection));
        shutdown.attempt("plot command guard", () -> HandlerList.unregisterAll(commandGuard));
        shutdown.attempt("plot listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("plot board database", board::close);
        shutdown.attempt("plot database", store::close);
        shutdown.finish("plot module");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        selections.forget(playerId);
        creationOwners.remove(playerId);
        miniature.forget(playerId);
        preflightCache.forget(playerId);
    }

    public String publicBoardListJson() throws SQLException {
        return board.publicBoardListJson();
    }

    public String publicBoardDetailJson(String id) throws SQLException {
        return board.publicBoardDetailJson(id);
    }

    /** Current protected plot, including its optional local notice. */
    public PlotNoticeBoard noticeBoardAt(Player player) {
        Location position = player.getLocation();
        Plot plot = registry.at(position.getWorld().getUID(), position.getBlockX(),
                position.getBlockY(), position.getBlockZ());
        if (plot == null) return null;
        Plot parent = registry.parentOf(plot);
        return new PlotNoticeBoard(plot.id(), plot.name(), plot.owner(),
                memberName(plot.owner()), registry.noticeBoard(plot.id()),
                parent == null ? "" : parent.name());
    }

    public void openCurrentPlot(Player player) {
        PlotNoticeBoard current = noticeBoardAt(player);
        if (current == null) openMenu(player);
        else openPlotDetail(player, current.plotId());
    }

    public void openNoticeBoard(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) openMenu(player);
        else if (registry.noticeBoard(plotId).isEmpty()) openPlotDetail(player, plotId);
        else FloatingMenus.present(player, noticeBoardDefinition(player, plot, 0));
    }

    private FloatingMenuDefinition noticeBoardDefinition(Player player, Plot plot, int requestedPage) {
        UUID plotId = plot.id();
        String body = registry.noticeBoard(plotId);
        List<String> pages = PlotNoticeText.pages(body);
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, pages.size(), 1);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-notice:" + plotId,
                        FloatingMenuAppearance.ARCHIVE, PlotMenuLayouts.noticeBoard())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (registry.noticeBoard(plotId).isEmpty()) openPlotDetail(viewer, plotId);
                    else if (!body.equals(registry.noticeBoard(plotId)) || !plot.name().equals(fresh.name()))
                        handle.update(noticeBoardDefinition(viewer, fresh, page.index()));
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_NOTICE_BOARD) + " · " + plot.name(),
                        NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("body", Component.text(pages.get(page.index()), NamedTextColor.WHITE))
                .region("body").textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        if (page.count() > 1) menu.pagination("pagination", page, index -> {
            Plot fresh = registry.byId(plotId);
            if (fresh == null) openMenu(player);
            else if (registry.noticeBoard(plotId).isEmpty()) openPlotDetail(player, plotId);
            else FloatingMenus.current(player).ifPresent(handle -> handle.update(noticeBoardDefinition(player, fresh, index)));
        });
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("plot")
                        .executes(context -> {
                            if (context.getSource().getSender() instanceof Player player) openMenu(player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (context.getSource().getSender() instanceof Player player)
                                        execute(player, StringArgumentType.getString(context, "args"));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), language.t(Language.DEFAULT, Message.PLOT_COMMAND_DESCRIPTION)));
    }

    public void openMenu(Player player) {
        openMenu(player, 0);
    }

    private void openMenu(Player player, int requestedPage) {
        selectionController.stop(player.getUniqueId());
        long revision = registry.revision();
        Plot here = registry.at(player.getWorld().getUID(), player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        List<Plot> ownedOrShared = registry.all().stream()
                .filter(plot -> (plot.owner().equals(player.getUniqueId())
                        || plot.members().containsKey(player.getUniqueId()))
                        && (here == null || !here.id().equals(plot.id())))
                .sorted(Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Plot::id))
                .toList();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage,
                ownedOrShared.size(), PLOTS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plots",
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.browser("plots", "summary"))
                .refreshEvery(10, (p, handle) -> {
                    if (FloatingMenus.current(p).map(current -> current.id().equals(handle.id()))
                            .orElse(false)) {
                        Plot current = registry.at(p.getWorld().getUID(),
                                p.getLocation().getBlockX(), p.getLocation().getBlockY(),
                                p.getLocation().getBlockZ());
                        if (current != here || registry.revision() != revision) {
                            openMenu(p, page.index());
                            return;
                        }
                        previewInMenu(p, current);
                    }
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_TITLE), NamedTextColor.GOLD)).region("heading").keepAccessible();
        if (here == null)
            menu.information("summary", Component.text(t(player, Message.PLOT_EMPTY_SUMMARY),
                    NamedTextColor.GRAY)).region("summary");
        if (here != null) {
            menu.item("here", here.publicProject() ? Material.BEACON : Material.GRASS_BLOCK,
                    plotCard(player, here)).region("here").keepAccessible()
                    .primary((p, handle) -> openPlotDetail(p, here.id()));
        }
        for (Plot plot : page.slice(ownedOrShared)) {
            menu.item("plot:" + plot.id(), plot.publicProject() ? Material.BEACON : Material.GRASS_BLOCK,
                    plotCard(player, plot))
                    .region("plots")
                    .primary((p, handle) -> openPlotDetail(p, plot.id()));
        }
        if (page.count() > 1) {
            menu.pagination("pagination", page, index -> openMenu(player, index));
        }
        var create = menu.item("create", Material.OAK_SIGN,
                        Component.text(t(player, Message.PLOT_MENU_CREATE), NamedTextColor.GREEN))
                .region("actions")
                .primary((viewer, handle) -> beginCreate(viewer, new PlotOwner(viewer.getUniqueId(), viewer.getName())));
        if (!RolePermissions.isMember(player))
            create.disabled(Component.text(t(player, Message.PLOT_ERROR_MEMBER_REQUIRED)));
        if (RolePermissions.canModerate(player))
            menu.item("manage", Material.PLAYER_HEAD,
                            Component.text(t(player, Message.PLOT_MENU_MANAGE), NamedTextColor.AQUA))
                    .region("actions")
                    .primary((p, handle) -> textInput(p, Message.PLOT_MENU_MANAGE,
                            Message.PLOT_MENU_PLAYER, "", 36, false,
                            (viewer, value) -> {
                                try { openManagedPlots(viewer, PlotCreationController.managedPlayer(value), 0); }
                                catch (PlotProblem error) {
                                    openMenu(viewer);
                                    send(viewer, NamedTextColor.RED, error.message(), error.args());
                                }
                            }));
        menu.item("public-destinations", Material.LODESTONE,
                        Component.text(t(player, Message.PLOT_MENU_PUBLIC_DESTINATIONS),
                                NamedTextColor.AQUA))
                .region("actions")
                .primary((p, handle) -> openPublicDestinations(p, 0));
        menu.dismiss(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(player, menu.build());
    }

    private void openPublicDestinations(Player player, int requestedPage) {
        long revision = registry.revision();
        List<Plot> destinations = registry.all().stream()
                .filter(plot -> plot.publicProject() && registry.arrival(plot.id()) != null)
                .sorted(Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Plot::id))
                .toList();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage,
                destinations.size(), PLOTS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-public-destinations",
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.browser("plots", "summary"))
                .refreshEvery(20, (p, handle) -> {
                    if (registry.revision() != revision
                            && FloatingMenus.current(p).map(current ->
                                    current.id().equals(handle.id())).orElse(false)) {
                        openPublicDestinations(p, page.index());
                    }
                });
        menu.information("heading", Component.text(
                t(player, Message.PLOT_MENU_PUBLIC_DESTINATIONS), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        if (destinations.isEmpty()) {
            menu.information("empty", Component.text(
                    t(player, Message.PLOT_MENU_PUBLIC_DESTINATIONS_EMPTY),
                    NamedTextColor.GRAY)).region("plots");
        }
        for (Plot plot : page.slice(destinations)) {
            menu.item("plot:" + plot.id(), Material.LODESTONE, plotCard(player, plot))
                    .region("plots")
                    .primary((p, handle) -> openPlotDetail(p, plot.id()));
        }
        if (page.count() > 1) {
            menu.pagination("pagination", page,
                    index -> openPublicDestinations(player, index));
        }
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(player, menu.build());
    }

    private void openManagedPlots(Player staff, PlotOwner target, int requestedPage) {
        if (!RolePermissions.canModerate(staff)) {
            send(staff, NamedTextColor.RED, Message.PLOT_ERROR_STAFF_ONLY);
            return;
        }
        List<Plot> plots = registry.all().stream()
                .filter(plot -> plot.owner().equals(target.id()))
                .sorted(Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Plot::id))
                .toList();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, plots.size(), PLOTS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-manage",
                FloatingMenuAppearance.SURVEY, PlotMenuLayouts.browser("plots", "summary"));
        menu.information("heading", Component.text(t(staff, Message.PLOT_MENU_MANAGED_TITLE,
                target.name()), NamedTextColor.GOLD)).region("heading").keepAccessible();
        if (plots.isEmpty())
            menu.information("empty", Component.text(t(staff, Message.PLOT_MENU_MANAGED_EMPTY),
                    NamedTextColor.GRAY)).region("summary");
        for (Plot plot : page.slice(plots))
            menu.item("plot:" + plot.id(), plot.publicProject() ? Material.BEACON : Material.GRASS_BLOCK,
                            plotCard(staff, plot))
                    .region("plots")
                    .primary((p, handle) -> openPlotDetail(p, plot.id()));
        if (page.count() > 1)
            menu.pagination("pagination", page,
                    index -> openManagedPlots(staff, target, index));
        menu.item("create", Material.OAK_SIGN,
                        Component.text(t(staff, Message.PLOT_MENU_CREATE_FOR_PLAYER),
                                NamedTextColor.GREEN))
                .region("actions")
                .primary((viewer, handle) -> beginCreate(viewer, target));
        menu.back(Component.text(t(staff, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(staff, menu.build());
    }

    private Component plotCard(Player player, Plot plot) {
        Plot parent = registry.parentOf(plot);
        Component label = Component.text(plot.name(), NamedTextColor.AQUA);
        if (parent != null)
            label = label.append(Component.newline()).append(Component.text(
                    t(player, Message.PLOT_MENU_SUBPLOT_OF, parent.name()), NamedTextColor.GRAY));
        return label
                .append(Component.newline())
                .append(Component.text("#" + plot.id().toString().substring(0, 8)
                        + " · " + t(player, Message.PLOT_ITEM_SUMMARY,
                        registry.horizontalArea(plot), PlotRoster.size(plot)),
                        NamedTextColor.GRAY));
    }

    private void preview(Player player, Plot plot) {
        PlotSelectionState.Points points = selections.points(player.getUniqueId(), plot == null ? null : plot.id());
        PlotSelectionShape shape = selections.draft(player.getUniqueId(), plot == null ? null : plot.id()).base().candidate();
        spatialPreview.show(player, plot, points.first(), points.second(), shape);
    }

    private void previewInMenu(Player player, Plot plot) {
        if (!selectionController.ownsPreview(player.getUniqueId(), plot == null ? null : plot.id()))
            preview(player, plot);
    }

    private boolean standingIn(Player player, Plot plot) {
        Plot current = registry.at(player.getWorld().getUID(), player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        return current != null && current.id().equals(plot.id());
    }

    private void openPlotDetail(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (canManage(player, plot)) selections.bind(player.getUniqueId(), plotId, plot.world());
        selectionController.keepProject(player.getUniqueId(), plotId);
        FloatingMenus.present(player, detailDefinition(player, plot));
    }

    private FloatingMenuDefinition detailDefinition(Player player, Plot plot) {
        UUID plotId = plot.id();
        PlotArrival arrival = registry.arrival(plotId);
        String notice = registry.noticeBoard(plotId);
        PlotAtmosphere ambience = registry.atmosphere(plotId);
        PlotAtmosphere effectiveAmbience = registry.effectiveAtmosphere(plotId);
        Plot parent = registry.parentOf(plot);
        List<Plot> children = registry.childrenOf(plotId);
        boolean staff = RolePermissions.canModerate(player);
        PlotRegistry.Standing standing = registry.standing(plot, player.getUniqueId(), staff);
        String currentAccess = myAccess(player, standing);
        boolean manager = canManage(player, plot);
        boolean local = standingIn(player, plot);
        UUID selectionWorld = player.getWorld().getUID();
        PlotPosition firstPoint = selections.first(player.getUniqueId());
        PlotPosition secondPoint = selections.second(player.getUniqueId());
        long draftRevision = selections.draft(player.getUniqueId(), plotId).revision();
        long selectionRevision = registry.revision();
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-detail",
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.detail())
                .refreshEvery(10, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) {
                        openMenu(p);
                        return;
                    }
                    if (fresh != plot || local != standingIn(p, fresh)
                            || manager != canManage(p, fresh)
                            || staff != RolePermissions.canModerate(p)
                            || !currentAccess.equals(myAccess(p, registry.standing(fresh,
                                    p.getUniqueId(), RolePermissions.canModerate(p))))
                            || !Objects.equals(parent, registry.parentOf(fresh))
                            || !Objects.equals(arrival, registry.arrival(plotId))
                            || !notice.equals(registry.noticeBoard(plotId))
                            || !ambience.equals(registry.atmosphere(plotId))
                            || !effectiveAmbience.equals(registry.effectiveAtmosphere(plotId))
                            || !children.equals(registry.childrenOf(plotId))
                            || !selectionWorld.equals(p.getWorld().getUID())
                            || manager && (selectionRevision != registry.revision()
                                    || !Objects.equals(firstPoint, selections.first(p.getUniqueId()))
                                    || !Objects.equals(secondPoint, selections.second(p.getUniqueId()))
                                    || draftRevision != selections.draft(p.getUniqueId(), plotId).revision()))
                        handle.update(detailDefinition(p, fresh));
                    if (FloatingMenus.current(p).map(current -> current.id().equals(handle.id()))
                            .orElse(false)) previewInMenu(p, fresh);
                });
        menu.information("heading", Component.text(plot.name(), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        Component summary = Component.text(t(player,
                        plot.publicProject() ? Message.PLOT_PUBLIC : Message.PLOT_PRIVATE),
                        NamedTextColor.AQUA)
                .append(Component.text(" · " + plot.id().toString().substring(0, 8), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_ITEM_SUMMARY,
                        registry.horizontalArea(plot), PlotRoster.size(plot)), NamedTextColor.GRAY));
        if (!plot.owner().equals(player.getUniqueId())) {
            summary = summary.append(Component.newline()).append(Component.text(
                    t(player, Message.PLOT_MENU_OWNER, memberName(plot.owner())),
                    NamedTextColor.GRAY));
        }
        if (parent != null) summary = summary.append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_MENU_SUBPLOT_OF,
                        parent.name()), NamedTextColor.DARK_AQUA));
        menu.information("summary", summary.append(Component.newline())
                .append(Component.text(currentAccess, NamedTextColor.AQUA))).region("summary")
                .textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        if (!notice.isEmpty()) {
            menu.control("notice", Component.text(
                            t(player, Message.PLOT_NOTICE_BOARD), NamedTextColor.GOLD)
                    .append(Component.newline())
                    .append(Component.text(PlotNoticeText.preview(notice), NamedTextColor.WHITE))
                    .append(Component.newline()).append(Component.text(t(player, Message.PLOT_NOTICE_READ), NamedTextColor.AQUA)))
                    .region("notice").textWidth(FloatingMenuTextWidth.WIDE)
                    .primary((viewer, handle) -> openNoticeBoard(viewer, plotId));
        }
        if (manager || !effectiveAmbience.followsWorld()) {
            var atmosphereCard = menu.item("atmosphere", Material.CLOCK,
                            atmosphereLabel(player, plot, ambience)).region("settings");
            if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) atmosphereCard.primary((p, handle) -> openPlotAtmosphere(p, plotId));
            else atmosphereCard.passive();
        }
        if (manager) {
            menu.information("settings-heading", Component.text(
                    t(player, Message.PLOT_MENU_SETTINGS), NamedTextColor.AQUA))
                    .region("settings-heading");
            if (permitted(player, plot, PlotPermission.MANAGE_AREA)) menu.item("construction", Material.SCAFFOLDING,
                            Component.text(t(player, Message.PLOT_SELECTION_OPEN), NamedTextColor.GREEN))
                    .region("construction")
                    .primary((p, handle) -> beginAreaEdit(p, plotId));
            menu.item("access", Material.IRON_DOOR,
                            Component.text(t(player, Message.PLOT_MENU_ACCESS), NamedTextColor.AQUA))
                    .region("settings")
                    .primary((p, handle) -> openPlotAccess(p, plotId));
            if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) menu.item("rename", Material.NAME_TAG,
                            Component.text(t(player, Message.PLOT_MENU_RENAME), NamedTextColor.YELLOW))
                    .region("settings")
                    .primary((p, handle) -> textInput(p, Message.PLOT_MENU_RENAME,
                            Message.PLOT_MENU_NAME, plot.name(), 48, false,
                            (viewer, value) -> {
                                perform(viewer, () -> rename(viewer, plotId.toString(), value.strip()));
                                openPlotDetail(viewer, plotId);
                            }));
            if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) menu.item("notice-edit", Material.OAK_SIGN,
                            Component.text(t(player, Message.PLOT_NOTICE_EDIT),
                                    NamedTextColor.YELLOW))
                    .region("settings")
                    .primary((p, handle) -> editNoticeBoard(p, plotId));
        }
        addReleaseAction(player, menu, plot);
        if (parent == null) {
            menu.information("subplots-heading", Component.text(
                    t(player, Message.PLOT_MENU_SUBPLOTS, children.size()), NamedTextColor.GOLD))
                    .region("subplots-heading");
        }
        if (parent != null) {
            menu.item("parent", Material.COMPASS,
                            Component.text(t(player, Message.PLOT_MENU_SUBPLOT_PARENT,
                                    parent.name()), NamedTextColor.AQUA))
                    .region("subplots")
                    .primary((p, handle) -> openPlotDetail(p, parent.id()));

        } else {
            addSubPlotCreateAction(player, menu, plot, "subplots");
            for (Plot child : children.stream().limit(3).toList()) {
                menu.item("child:" + child.id(), Material.OAK_SIGN, plotCard(player, child))
                        .region("subplots")
                        .primary((p, handle) -> openPlotDetail(p, child.id()));
            }
            if (children.size() > 3)
                menu.item("all-children", Material.BOOK,
                                Component.text(t(player, Message.PLOT_MENU_SUBPLOTS,
                                        children.size()), NamedTextColor.AQUA))
                        .region("subplots")
                        .primary((p, handle) -> openSubPlots(p, plotId, 0));

        }
        Component arrivalHeading = Component.text(t(player, Message.PLOT_MENU_ARRIVAL),
                NamedTextColor.GOLD);
        if (arrival != null) {
            arrivalHeading = arrivalHeading.append(Component.text(" · "
                    + arrival.blockX() + ", " + arrival.blockY() + ", "
                    + arrival.blockZ(), NamedTextColor.GRAY));
        } else {
            arrivalHeading = arrivalHeading.append(Component.text(" · "
                    + t(player, Message.PLOT_ERROR_ARRIVAL_MISSING),
                    NamedTextColor.GRAY));
        }
        menu.information("arrival-heading", arrivalHeading).region("arrival-heading");
        if (arrival != null && arrivals.canVisit(player, plot)) {
            menu.item("visit", Material.ENDER_PEARL,
                            Component.text(t(player, Message.PLOT_MENU_VISIT),
                                    NamedTextColor.GREEN))
                    .region("arrival-actions")
                    .primary((p, handle) -> {
                        handle.close();
                        perform(p, () -> arrivals.visit(p, plotId));
                    });
        }
        if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) {
            var set = menu.item("set-arrival", Material.LODESTONE,
                            Component.text(t(player, Message.PLOT_MENU_SET_ARRIVAL),
                                    NamedTextColor.AQUA))
                    .region("arrival-actions")
                    .primary((p, handle) -> {
                        perform(p, () -> arrivals.setHere(p, plotId));
                        openPlotDetail(p, plotId);
                    });
            if (!arrivals.canSetHere(player, plot)) {
                set.disabled(Component.text(t(player, Message.PLOT_ERROR_ARRIVAL_LOCATION)));
            }
            if (arrival != null) {
                menu.item("arrival-text", Material.WRITABLE_BOOK,
                                Component.text(t(player, Message.PLOT_MENU_ARRIVAL_TEXT),
                                        NamedTextColor.YELLOW)
                                        .append(Component.newline())
                                        .append(arrivals.titlePreview(plot, arrival)))
                        .region("arrival-actions")
                        .primary((p, handle) -> editArrivalText(p, plotId));
                menu.item("remove-arrival", Material.BARRIER,
                                Component.text(t(player, Message.PLOT_MENU_REMOVE_ARRIVAL),
                                        NamedTextColor.RED))
                        .region("arrival-actions")
                        .primary((p, handle) -> {
                            perform(p, () -> arrivals.clear(p, plotId));
                            openPlotDetail(p, plotId);
                        });
            }
        }
        menu.item("help", Material.BOOK,
                        Component.text(t(player, Message.PLOT_HELP), NamedTextColor.YELLOW))
                .region("navigation").keepAccessible()
                .primary((p, handle) -> openGuide(p));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private boolean editorAllowed(Player player, UUID plotId) {
        if (plotId == null) {
            PlotOwner owner = creationOwners.get(player.getUniqueId());
            return owner != null && !owner.id().equals(player.getUniqueId())
                    ? RolePermissions.canModerate(player) : RolePermissions.isMember(player);
        }
        Plot plot = registry.byId(plotId);
        PlotEditorContext context = selections.context(player.getUniqueId(), plotId);
        return plot != null && plot.world().equals(player.getWorld().getUID())
                && (!context.subPlot() || !plot.subPlot())
                && permitted(player, plot, context.permission());
    }

    private void beginAreaEdit(Player player, UUID plotId) {
        beginEditor(player, PlotEditorContext.area(plotId));
    }

    private void beginSubPlot(Player player, UUID parentId) {
        beginEditor(player, PlotEditorContext.subPlot(parentId));
    }

    private void beginEditor(Player player, PlotEditorContext context) {
        perform(player, () -> {
            Plot plot = registry.byId(context.plotId());
            if (plot == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
            if (context.subPlot() && plot.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
            if (!permitted(player, plot, context.permission()))
                throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
            if (!plot.world().equals(player.getWorld().getUID()))
                throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
            selectionController.stop(player.getUniqueId());
            selections.bindContext(player.getUniqueId(), context, plot.world());
            miniature.forget(player.getUniqueId());
            openPlotEditor(player, plot.id());
        });
    }

    private void addSubPlotCreateAction(Player player, FloatingMenuDefinition.Builder menu, Plot parent,
                                       String region) {
        if (parent.subPlot()) return;
        var create = menu.item("create-child", Material.OAK_FENCE,
                        Component.text(t(player, Message.PLOT_MENU_SUBPLOT_CREATE), NamedTextColor.GREEN))
                .region(region).primary((viewer, handle) -> beginSubPlot(viewer, parent.id()));
        if (!permitted(player, parent, PlotPermission.CREATE_SUBPLOT))
            create.disabled(Component.text(t(player, Message.PLOT_ERROR_OWNER_ONLY)));
        else if (!parent.world().equals(player.getWorld().getUID()))
            create.disabled(Component.text(t(player, Message.PLOT_ERROR_SELECTION)));
    }

    private void beginCreate(Player player, PlotOwner owner) {
        perform(player, () -> {
            if (!owner.id().equals(player.getUniqueId())) requireModerator(player);
            else if (!RolePermissions.isMember(player)) throw new PlotProblem(Message.PLOT_ERROR_MEMBER_REQUIRED);
            selectionController.stop(player.getUniqueId());
            creationOwners.put(player.getUniqueId(), owner);
            selections.bind(player.getUniqueId(), null, player.getWorld().getUID());
            openPlotEditor(player, null);
        });
    }

    private void openPlotEditor(Player player, UUID plotId) {
        perform(player, () -> {
            selectionController.keepProject(player.getUniqueId(), plotId);
            Plot plot = plotId == null ? null : managed(player, plotId.toString());
            bindEditor(player, plotId);
            PlotSelectionState.Draft draft = selections.draft(player.getUniqueId(), plotId);
            if (plot != null && !selections.context(player.getUniqueId()).subPlot()
                    && draft.base().shape() == null && draft.base().points().first() == null
                    && draft.base().points().second() == null && draft.history().isEmpty())
                selections.initialize(player.getUniqueId(), plot.world(), plot.cells());
            if (selectionController.mode(player.getUniqueId()) == null)
                selectionController.start(player, plotId, PlotSelectionController.Mode.PREVIEW);
            FloatingMenus.present(player, editorDefinition(player, plot));
        });
    }

    private void bindEditor(Player player, UUID plotId) {
        Plot plot = plotId == null ? null : registry.byId(plotId);
        if (plot != null && !plot.world().equals(player.getWorld().getUID()))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        if (!editorAllowed(player, plotId)) throw new PlotProblem(plotId == null
                ? Message.PLOT_ERROR_MEMBER_REQUIRED : Message.PLOT_ERROR_OWNER_ONLY);
        selections.resume(player.getUniqueId(), plotId, player.getWorld().getUID());
    }

    private FloatingMenuDefinition editorDefinition(Player player, Plot plot) {
        UUID plotId = plot == null ? null : plot.id();
        PlotEditorContext context = selections.context(player.getUniqueId(), plotId);
        UUID world = player.getWorld().getUID();
        PlotSelectionState.Draft draft = selections.draft(player.getUniqueId(), plotId);
        PlotSelectionState.Points points = draft.base().points();
        long revision = registry.revision();
        PlotSelectionController.Mode editing = selectionController.mode(player.getUniqueId());
        PlotPreflightCache.Outcome preflight = preflightCache.request(player.getUniqueId(), world,
                plot, context, revision, draft, edits);
        PlotProblem failure = preflight == null ? null : preflight.failure();
        boolean spatial = FloatingMenus.supportsSpatialScenes(player);
        PlotMiniaturePreview.Scene scene = miniature.scene(player, plot, draft, spatial);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-editor:" + context,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.editor())
                .refreshEvery(10, (viewer, handle) -> {
                    if (!FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false))
                        return;
                    if (!context.equals(selections.context(viewer.getUniqueId())) || !editorAllowed(viewer, plotId)) {
                        handle.close();
                        selectionController.stop(viewer.getUniqueId());
                        return;
                    }
                    Plot fresh = plotId == null ? null : registry.byId(plotId);
                    PlotMiniaturePreview.Scene currentScene = world.equals(viewer.getWorld().getUID())
                            ? miniature.scene(viewer, fresh, selections.draft(viewer.getUniqueId(), plotId), spatial) : null;
                    var currentPreflight = preflightCache.request(viewer.getUniqueId(), viewer.getWorld().getUID(),
                            fresh, context, registry.revision(), selections.draft(viewer.getUniqueId(), plotId), edits);
                    if (fresh != plot || registry.revision() != revision
                            || !world.equals(viewer.getWorld().getUID())
                            || draft.revision() != selections.draft(viewer.getUniqueId(), plotId).revision()
                            || editing != selectionController.mode(viewer.getUniqueId())
                            || currentScene == null || !scene.stamp().equals(currentScene.stamp())
                            || preflight != currentPreflight)
                        handle.update(editorDefinition(viewer, fresh));
                    if (FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false))
                        previewInMenu(viewer, fresh);
                });
        menu.information("heading", Component.text(plot == null ? t(player, Message.PLOT_MENU_CREATE)
                : plot.name() + " · " + t(player, context.subPlot()
                        ? Message.PLOT_MENU_SUBPLOT_CREATE : Message.PLOT_SELECTION_OPEN), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        menu.information("selection", selectionSummary(player, plot, failure))
                .region("selection").textWidth(FloatingMenuTextWidth.WIDE);
        Component status = miniatureHint(player, scene);
        if (!status.equals(Component.empty()))
            menu.information("hint", status).region("hint").textWidth(FloatingMenuTextWidth.WIDE);
        contextHelp(menu, player, viewer -> miniatureInstructions(viewer, scene));
        miniature.decorate(menu, player, scene);
        if (scene.regions().open()) {
            regionControls(player, menu, plotId, draft, scene.regions());
        } else {
            var regions = menu.item("region-manager", Material.PAPER,
                            Component.text(t(player, Message.PLOT_REGION_OPEN, scene.regions().items().size()),
                                    NamedTextColor.AQUA))
                    .region("operations").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        miniature.openRegions(viewer, plotId, true);
                        openPlotEditor(viewer, plotId);
                    }));
            if (scene.regions().items().isEmpty())
                regions.disabled(Component.text(t(player, Message.PLOT_SELECTION_INCOMPLETE)));
        }
        var rotateLeft = menu.item("rotate-left", Material.ARROW, Component.text(t(player, Message.PLOT_MODEL_ROTATE_LEFT)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.rotate(viewer, plotId, -45);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        var rotateRight = menu.item("rotate-right", Material.ARROW, Component.text(t(player, Message.PLOT_MODEL_ROTATE_RIGHT)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.rotate(viewer, plotId, 45);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        if (scene.topDown()) {
            rotateLeft.disabled(Component.text(t(player, Message.PLOT_MODEL_NORTH_UP)));
            rotateRight.disabled(Component.text(t(player, Message.PLOT_MODEL_NORTH_UP)));
        }
        menu.item("slice-model", Material.GLASS, Component.text(t(player, scene.sliced()
                        ? Message.PLOT_MODEL_SHOW_ALL : Message.PLOT_MODEL_SLICE)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.slice(viewer, plotId);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        menu.item("focus-model", Material.COMPASS, Component.text(t(player, scene.playerFocus()
                        ? Message.PLOT_MODEL_FIT : Message.PLOT_MODEL_FOCUS)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.focus(viewer, plotId);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        menu.item("top-model", Material.MAP, Component.text(t(player, scene.topDown()
                        ? Message.PLOT_MODEL_PERSPECTIVE : Message.PLOT_MODEL_TOP)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.topDown(viewer, plotId);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        menu.item("terrain-model", Material.GLASS_PANE, Component.text(t(player, scene.terrain()
                        ? Message.PLOT_MODEL_WIREFRAME : Message.PLOT_MODEL_TERRAIN)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.terrain(viewer, plotId);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        menu.item("layer-up", Material.ARROW, Component.text(t(player, Message.PLOT_MODEL_LAYER_UP)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.layer(viewer, plotId, 1);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        menu.item("layer-down", Material.ARROW, Component.text(t(player, Message.PLOT_MODEL_LAYER_DOWN)))
                .region("view").spatialOnly().primary((viewer, handle) -> {
                    miniature.layer(viewer, plotId, -1);
                    handle.update(editorDefinition(viewer, plotId == null ? null : registry.byId(plotId)));
                });
        if (!scene.regions().open()) {
            for (PlotSelectionShape.Operation operation : List.of(
                    PlotSelectionShape.Operation.ADD, PlotSelectionShape.Operation.SUBTRACT)) {
                Message label = switch (operation) {
                    case ADD -> Message.PLOT_SELECTION_ADD;
                    case SUBTRACT -> Message.PLOT_SELECTION_SUBTRACT;
                    case REPLACE -> Message.PLOT_SELECTION_REPLACE;
                };
                var action = menu.item("compose:" + operation, Material.STRUCTURE_VOID,
                                Component.text(t(player, label), NamedTextColor.LIGHT_PURPLE))
                        .region("composition").primary((viewer, handle) -> perform(viewer, () -> {
                            bindEditor(viewer, plotId);
                            selections.apply(viewer.getUniqueId(), viewer.getWorld().getUID(), operation);
                            openPlotEditor(viewer, plotId);
                        }));
                if (points.first() == null || points.second() == null || !points.in(world))
                    action.disabled(Component.text(t(player, Message.PLOT_SELECTION_INCOMPLETE)));
                else if (operation == PlotSelectionShape.Operation.SUBTRACT && draft.base().shape() == null)
                    action.disabled(Component.text(t(player, Message.PLOT_SELECTION_EMPTY)));
            }
            if (context.subPlot()) {
                var create = menu.item("create-child", Material.OAK_FENCE,
                                Component.text(t(player, Message.PLOT_MENU_SUBPLOT_CREATE), NamedTextColor.GREEN))
                        .region("operations").primary((viewer, handle) -> perform(viewer,
                                () -> createSubPlotInput(viewer, plotId)));
                if (preflight == null) create.disabled(Component.text(t(player, Message.PLOT_MODEL_PREPARING)));
                else if (failure != null) create.disabled(problemLabel(player, failure));
            }
        }
        if (!scene.regions().open() || scene.regions().editing()) {
            menu.item("select-world", Material.WOODEN_AXE, Component.text(t(player,
                            Message.PLOT_SELECTION_MODE), NamedTextColor.GREEN))
                    .region("points").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        handle.close();
                        selectionController.start(viewer, plotId);
                    }));
            if (plot != null && !context.subPlot() && !scene.regions().editing()) menu.item("fit-selection", Material.STRUCTURE_VOID,
                            Component.text(t(player, Message.PLOT_SELECTION_FIT), NamedTextColor.AQUA))
                    .region("points").primary((viewer, handle) -> perform(viewer, () -> {
                        fitSelection(viewer, plotId);
                        openPlotEditor(viewer, plotId);
                    }));
            menu.item("clear-selection", Material.BARRIER, Component.text(t(player,
                            Message.PLOT_SELECTION_CLEAR), NamedTextColor.GRAY))
                    .region("points").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.discardBrush(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    }));
        }
        if (!scene.regions().editing() && !context.subPlot()) {
            boolean replacing = draft.base().shape() != null
                    && points.first() != null && points.second() != null;
            var save = menu.item("save-area", Material.GRASS_BLOCK, Component.text(t(player,
                            plot == null ? Message.PLOT_MENU_CREATE : replacing
                                    ? Message.PLOT_AREA_REPLACE_SAVE : Message.PLOT_AREA_SAVE), NamedTextColor.GREEN))
                    .region("operations").primary((viewer, handle) -> perform(viewer,
                            () -> confirmSaveArea(viewer, plotId)));
            if (failure != null) save.disabled(problemLabel(player, failure));
            if (preflight == null) save.disabled(Component.text(t(player, Message.PLOT_MODEL_PREPARING)));
            if (plot != null && !permitted(player, plot, PlotPermission.MANAGE_AREA))
                save.disabled(Component.text(t(player, Message.PLOT_ERROR_SUBPLOT_OWNER)));
        }
        var undo = menu.item("undo-selection", Material.ARROW, Component.text(t(player,
                        Message.PLOT_SELECTION_UNDO), NamedTextColor.YELLOW))
                .region("operations").primary((viewer, handle) -> perform(viewer, () -> {
                    bindEditor(viewer, plotId);
                    selections.undo(viewer.getUniqueId());
                    openPlotEditor(viewer, plotId);
                }));
        if (draft.history().isEmpty()) undo.disabled(Component.text(t(player, Message.PLOT_SELECTION_NO_UNDO)));
        var redo = menu.item("redo-selection", Material.SPECTRAL_ARROW, Component.text(t(player,
                        Message.PLOT_SELECTION_REDO), NamedTextColor.YELLOW))
                .region("operations").primary((viewer, handle) -> perform(viewer, () -> {
                    bindEditor(viewer, plotId);
                    selections.redo(viewer.getUniqueId());
                    openPlotEditor(viewer, plotId);
                }));
        if (draft.future().isEmpty()) redo.disabled(Component.text(t(player, Message.PLOT_SELECTION_NO_REDO)));
        if (editing != null) menu.item("exit-editor", Material.BARRIER,
                        Component.text(t(player, Message.PLOT_SELECTION_EXIT), NamedTextColor.RED))
                .region("navigation").primary((viewer, handle) -> {
                    selectionController.stop(viewer.getUniqueId());
                    selectionStopped(viewer);
                    if (plotId == null) openMenu(viewer);
                    else openPlotDetail(viewer, plotId);
                });
        menu.item("back", Material.ARROW, Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible().primary((viewer, handle) -> perform(viewer, () -> {
                    if (scene.regions().editing()) {
                        bindEditor(viewer, plotId);
                        selections.requireRevision(viewer.getUniqueId(), viewer.getWorld().getUID(), draft.revision());
                        selections.cancelRegionEdit(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    } else if (scene.regions().open()) {
                        miniature.openRegions(viewer, plotId, false);
                        openPlotEditor(viewer, plotId);
                    } else if (plotId == null) openMenu(viewer);
                    else openPlotDetail(viewer, plotId);
                }));
        return menu.build();
    }

    private void regionControls(Player player, FloatingMenuDefinition.Builder menu, UUID plotId,
                                PlotSelectionState.Draft draft, PlotMiniaturePreview.Regions regions) {
        var selected = regions.selected();
        menu.information("region-heading", Component.text(regions.editing()
                ? t(player, Message.PLOT_REGION_ADJUST)
                : t(player, Message.PLOT_REGION_OPEN, regions.items().size()), NamedTextColor.GOLD))
                .region("region-heading");
        if (!regions.editing()) {
            for (int index = regions.page().fromIndex(); index < regions.page().toIndex(); index++) {
                var region = regions.items().get(index);
                int number = index + 1;
                menu.choice("region:" + number, region.equals(selected), Material.PAPER,
                                regionLabel(region, number, false))
                        .region("regions").primary((viewer, handle) -> perform(viewer, () -> {
                            bindEditor(viewer, plotId);
                            selections.requireRegion(viewer.getUniqueId(), viewer.getWorld().getUID(), region, draft.revision());
                            miniature.selectRegion(viewer, plotId, region);
                            openPlotEditor(viewer, plotId);
                        }));
            }
            if (regions.page().count() > 1)
                menu.pagination("region-pagination", regions.page(), index -> perform(player, () -> {
                    bindEditor(player, plotId);
                    miniature.regionPage(player, plotId, index);
                    openPlotEditor(player, plotId);
                }));
        }
        if (selected == null) {
            menu.information("region-selection", Component.text(t(player, Message.PLOT_REGION_SELECT_HINT),
                    NamedTextColor.GRAY)).region("region-selection");
            return;
        }
        menu.information("region-selection", regionLabel(selected, regions.selectedNumber(), true))
                .region("region-selection").textWidth(FloatingMenuTextWidth.WIDE);
        if (regions.editing()) {
            var apply = menu.item("region-apply", Material.LIME_DYE,
                            Component.text(t(player, Message.PLOT_REGION_APPLY), NamedTextColor.GREEN))
                    .region("region-actions").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.requireRevision(viewer.getUniqueId(), viewer.getWorld().getUID(), draft.revision());
                        selections.finishRegionEdit(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    }));
            if (draft.base().candidate() == null)
                apply.disabled(Component.text(t(player, Message.PLOT_SELECTION_INCOMPLETE)));
            menu.item("region-cancel", Material.BARRIER,
                            Component.text(t(player, Message.PLOT_REGION_CANCEL), NamedTextColor.GRAY))
                    .region("region-actions").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.requireRevision(viewer.getUniqueId(), viewer.getWorld().getUID(), draft.revision());
                        selections.cancelRegionEdit(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    }));
        } else {
            menu.item("region-adjust", Material.WOODEN_AXE,
                            Component.text(t(player, Message.PLOT_REGION_ADJUST), NamedTextColor.AQUA))
                    .region("region-actions").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.beginRegionEdit(viewer.getUniqueId(), viewer.getWorld().getUID(), selected, draft.revision());
                        openPlotEditor(viewer, plotId);
                    }));
            menu.item("region-delete", Material.BARRIER,
                            Component.text(t(player, Message.PLOT_REGION_DELETE), NamedTextColor.RED))
                    .region("region-actions").primary((viewer, handle) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.requireRegion(viewer.getUniqueId(), viewer.getWorld().getUID(), selected, draft.revision());
                        MenuDialogs.openConfirm(plugin, viewer,
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE), NamedTextColor.RED),
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE_CONFIRM, regions.selectedNumber()),
                                                NamedTextColor.GRAY).append(Component.newline())
                                        .append(regionLabel(selected, regions.selectedNumber(), true)),
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE), NamedTextColor.RED),
                                Component.text(t(viewer, Message.PLOT_BACK), NamedTextColor.GRAY), responder -> {
                                    perform(responder, () -> {
                                        bindEditor(responder, plotId);
                                        selections.deleteRegion(responder.getUniqueId(), responder.getWorld().getUID(),
                                                selected, draft.revision());
                                    });
                                    openPlotEditor(responder, plotId);
                                });
                    }));
        }
    }

    private static Component regionLabel(PlotSelectionRegions.Region region, int number, boolean coordinates) {
        var bounds = region.bounds();
        Component label = Component.text("#" + number + " · " + bounds.width() + "×" + bounds.height()
                + "×" + bounds.depth(), NamedTextColor.AQUA);
        if (coordinates) label = label.append(Component.newline()).append(Component.text(
                bounds.minimumX() + ", " + bounds.minimumY() + ", " + bounds.minimumZ() + " → "
                        + bounds.maximumX() + ", " + bounds.maximumY() + ", " + bounds.maximumZ(), NamedTextColor.GRAY));
        return label;
    }

    private Component miniatureHint(Player player, PlotMiniaturePreview.Scene scene) {
        List<Component> status = new ArrayList<>();
        if (scene.spatial() && scene.sliced()) status.add(Component.text(t(player, Message.PLOT_MODEL_LEVEL,
                scene.projection().bounds().maximumY()), NamedTextColor.GRAY));
        if (!scene.snapshot().ready()) status.add(Component.text(t(player, Message.PLOT_MODEL_PROGRESS,
                scene.snapshot().progress()), NamedTextColor.GRAY));
        if (scene.preparing()) status.add(Component.text(t(player, Message.PLOT_MODEL_PREPARING), NamedTextColor.GRAY));
        if (scene.snapshot().unknown() > 0) status.add(Component.text(t(player, Message.PLOT_MODEL_UNKNOWN,
                scene.snapshot().unknown()), NamedTextColor.YELLOW));
        if (scene.limited()) status.add(Component.text(t(player, Message.PLOT_MODEL_LIMITED), NamedTextColor.YELLOW));
        return Component.join(JoinConfiguration.newlines(), status);
    }

    private Component miniatureInstructions(Player player, PlotMiniaturePreview.Scene scene) {
        Component instructions = Component.text(t(player, scene.regions().editing()
                ? Message.PLOT_REGION_EDIT_HINT : scene.regions().open()
                ? Message.PLOT_REGION_SELECT_HINT : Message.PLOT_AREA_HINT), NamedTextColor.WHITE)
                .append(Component.newline()).append(Component.text(t(player, Message.PLOT_MODEL_LEGEND), NamedTextColor.GRAY));
        return instructions.append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_SELECTION_SESSION_HINT), NamedTextColor.GRAY));
    }

    private void contextHelp(FloatingMenuDefinition.Builder menu, Player player,
                             Function<Player, Component> instructions) {
        menu.navigation("help", Component.text(t(player, Message.PLOT_HELP), NamedTextColor.GRAY))
                .region("navigation").primary((viewer, handle) -> {
                    FloatingMenuDefinition.Builder help = PlotMenuLayouts.screen("plot-context-help",
                            FloatingMenuAppearance.SURVEY, PlotMenuLayouts.help());
                    help.information("heading", Component.text(t(viewer, Message.PLOT_HELP), NamedTextColor.GOLD))
                            .region("heading").keepAccessible();
                    help.information("instructions", instructions.apply(viewer))
                            .region("instructions").textWidth(FloatingMenuTextWidth.WIDE);
                    help.back(Component.text(t(viewer, Message.PLOT_BACK), NamedTextColor.GREEN))
                            .region("navigation").keepAccessible();
                    FloatingMenus.present(viewer, help.build());
                });
    }

    private void confirmSaveArea(Player player, UUID plotId) {
        bindEditor(player, plotId);
        if (selections.context(player.getUniqueId()).subPlot())
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
        PlotSelectionState.Submission submitted = selections.submission(
                player.getUniqueId(), plotId, player.getWorld().getUID());
        var preflight = preflightCache.request(player.getUniqueId(), player.getWorld().getUID(),
                plotId == null ? null : registry.byId(plotId), submitted.context(), registry.revision(),
                selections.draft(player.getUniqueId(), plotId), edits);
        if (preflight == null) throw new PlotProblem(Message.PLOT_MODEL_PREPARING);
        if (preflight.failure() != null) throw preflight.failure();
        if (plotId == null) {
            PlotOwner owner = creationOwners.getOrDefault(player.getUniqueId(),
                    new PlotOwner(player.getUniqueId(), player.getName()));
            textInput(player, Message.PLOT_MENU_CREATE, Message.PLOT_MENU_NAME, "", 48, false,
                    (viewer, name) -> perform(viewer, () -> creation.create(viewer, owner, name.strip(),
                            submitted, !owner.id().equals(viewer.getUniqueId()))));
            return;
        }
        Plot plot = managed(player, plotId.toString());
        String count = preflight.cells().toString();
        MenuDialogs.openConfirm(plugin, player,
                Component.text(t(player, Message.PLOT_AREA_SAVE), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_AREA_CONFIRM, plot.name(), count), NamedTextColor.GRAY),
                Component.text(t(player, Message.PLOT_AREA_SAVE), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY), viewer -> {
                    perform(viewer, () -> {
                        UUID actor = viewer.getUniqueId();
                        selections.requireSubmission(actor, viewer.getWorld().getUID(), submitted);
                        boolean staff = RolePermissions.canModerate(viewer);
                        persistGeometry(viewer, staged -> {
                            staged.resize(plot, actor, staff, submitted.shape());
                            return null;
                        }, ignored -> {
                            boolean cleared = selections.clearIfMatches(actor, plotId, submitted);
                            atmosphere.refreshAll();
                            Plot updated = registry.byId(plotId);
                            send(viewer, NamedTextColor.GREEN, Message.PLOT_AREA_SAVED, updated.name());
                            if (!updated.owner().equals(actor))
                                notifyPlayer(updated.owner(), Message.PLOT_AREA_SAVED, updated.name());
                            if (cleared && selections.context(actor).equals(submitted.context())
                                    && viewer.isOnline() && viewer.getWorld().getUID().equals(plot.world())) {
                                selectionController.stop(actor);
                                miniature.forget(actor);
                                openPlotDetail(viewer, plotId);
                            }
                        });
                    });
                });
    }

    private void selectionStopped(Player player) {
        player.sendActionBar(Component.empty());
        send(player, NamedTextColor.GRAY, Message.PLOT_SELECTION_STOPPED);
    }

    private void selectionHint(Player player) {
        UUID plotId = selectionController.plotId(player.getUniqueId());
        PlotSelectionState.Points points = selections.points(player.getUniqueId(), plotId);
        player.sendActionBar(Component.text(PlotSelectionCommands.status(points), NamedTextColor.AQUA));
    }

    private Component selectionSummary(Player player, Plot plot, PlotProblem failure) {
        UUID plotId = plot == null ? null : plot.id();
        PlotSelectionState.Points points = selections.points(player.getUniqueId(), plotId);
        Component summary;
        try {
            PlotSelectionState.Base base = selections.draft(player.getUniqueId(), plotId).base();
            PlotSelectionShape shape = base.candidate();
            if (!base.in(player.getWorld().getUID()) || shape == null || shape.empty())
                throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
            PlotSelection.Bounds bounds = previewCache.bounds(shape);
            if (bounds == null) return Component.text(t(player, Message.PLOT_MODEL_PREPARING), NamedTextColor.GRAY);
            if (!shape.composite()) bounds = shape.alignedBounds();
            summary = Component.text(t(player, Message.PLOT_SELECTION_SIZE,
                    bounds.width(), bounds.height(), bounds.depth()), NamedTextColor.GOLD);
            summary = summary.append(Component.newline()).append(Component.text(
                    bounds.minimumX() + ", " + bounds.minimumY() + ", " + bounds.minimumZ()
                            + " → " + bounds.maximumX() + ", " + bounds.maximumY() + ", " + bounds.maximumZ(),
                    NamedTextColor.GRAY));
            if (failure != null && failure.message() != Message.PLOT_REGION_FINISH_FIRST)
                summary = summary.append(Component.newline()).append(problemLabel(player, failure));
        } catch (PlotProblem problem) {
            summary = Component.text(t(player, points.first() == null && points.second() == null
                    ? Message.PLOT_SELECTION_EMPTY : Message.PLOT_SELECTION_INCOMPLETE), NamedTextColor.GRAY);
        }
        return summary;
    }

    private Component problemLabel(Player player, PlotProblem problem) {
        return Component.text("✕ " + t(player, problem.message(), problem.args()), NamedTextColor.RED);
    }

    private void fitSelection(Player player, UUID plotId) {
        Plot plot = managed(player, plotId.toString());
        if (!plot.world().equals(player.getWorld().getUID()))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
        selections.resume(player.getUniqueId(), plotId, plot.world());
        selections.fit(player.getUniqueId(), plot.world(), plot.cells());
    }

    private void openSubPlots(Player player, UUID parentId, int requestedPage) {
        Plot parent = registry.byId(parentId);
        if (parent == null) {
            openMenu(player);
            return;
        }
        List<Plot> children = registry.childrenOf(parentId);
        boolean mayCreate = permitted(player, parent, PlotPermission.CREATE_SUBPLOT);
        UUID world = player.getWorld().getUID();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, children.size(), PLOTS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-subplots:" + parentId,
                FloatingMenuAppearance.SURVEY, PlotMenuLayouts.browser("plots", "summary"))
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(parentId);
                    if (fresh == null) openMenu(viewer);
                    else if (fresh != parent || !children.equals(registry.childrenOf(parentId))
                            || mayCreate != permitted(viewer, fresh, PlotPermission.CREATE_SUBPLOT)
                            || !world.equals(viewer.getWorld().getUID()))
                        openSubPlots(viewer, parentId, page.index());
                });
        menu.information("heading", Component.text(parent.name() + " · "
                + t(player, Message.PLOT_MENU_SUBPLOTS, children.size()), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        for (Plot child : page.slice(children))
            menu.item("child:" + child.id(), Material.OAK_SIGN, plotCard(player, child))
                    .region("plots").primary((p, handle) -> openPlotDetail(p, child.id()));
        if (page.count() > 1)
            menu.pagination("pagination", page,
                    index -> openSubPlots(player, parentId, index));
        addSubPlotCreateAction(player, menu, parent, "actions");
        menu.item("parent", Material.COMPASS,
                        Component.text(t(player, Message.PLOT_MENU_SUBPLOT_PARENT,
                                parent.name()), NamedTextColor.AQUA))
                .region("navigation").keepAccessible().primary((p, handle) -> openPlotDetail(p, parentId));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(player, menu.build());
    }

    private String myAccess(Player player, PlotRegistry.Standing standing) {
        Message role = switch (standing) {
            case STAFF -> Message.PLOT_ROLE_SERVER_STAFF;
            case PARENT_OWNER -> Message.PLOT_ROLE_PARENT_OWNER;
            case OWNER -> Message.PLOT_ROLE_OWNER;
            case ADMIN -> Message.PLOT_ROLE_ADMIN;
            case COLLABORATOR -> Message.PLOT_ROLE_COLLABORATOR;
            case OUTSIDER -> RolePermissions.isMember(player)
                    ? Message.PLOT_MENU_GROUP_MEMBER : Message.PLOT_MENU_GROUP_NEWCOMER;
        };
        Message capability = Message.PLOT_MENU_ACCESS_BY_SETTINGS;
        return t(player, Message.PLOT_MENU_MY_ACCESS,
                t(player, role), t(player, capability));
    }

    private void createSubPlotInput(Player player, UUID parentId) {
        Plot parent = registry.byId(parentId);
        if (parent == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (parent.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_DEPTH);
        if (!permitted(player, parent, PlotPermission.CREATE_SUBPLOT))
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_OWNER);
        if (!selections.context(player.getUniqueId()).equals(PlotEditorContext.subPlot(parentId)))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
        bindEditor(player, parentId);
        PlotSelectionState.Submission area = selection(player);
        var preflight = preflightCache.request(player.getUniqueId(), parent.world(), parent, area.context(),
                registry.revision(), selections.draft(player.getUniqueId(), parentId), edits);
        if (preflight == null) throw new PlotProblem(Message.PLOT_MODEL_PREPARING);
        if (preflight.failure() != null) throw preflight.failure();
        selectionController.pause(player.getUniqueId());
        MenuDialogs.openTwoTextInputs(plugin, player,
                Component.text(t(player, Message.PLOT_MENU_SUBPLOT_CREATE), NamedTextColor.GOLD),
                Component.text(t(player, Message.PLOT_MENU_NAME), NamedTextColor.WHITE),
                "", 48,
                Component.text(t(player, Message.PLOT_MENU_PLAYER), NamedTextColor.WHITE),
                memberName(parent.owner()), 36,
                Component.text(t(player, Message.PLOT_MENU_SUBPLOT_CREATE), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                (viewer, values) -> {
                    perform(viewer, () -> {
                        selections.requireSubmission(viewer.getUniqueId(), viewer.getWorld().getUID(), area);
                        openPlotEditor(viewer, parentId);
                        createSubPlot(viewer, parent, values.first().strip(), values.second().strip(), area);
                    });
                });
    }

    private void createSubPlot(Player player, Plot expectedParent, String name,
                               String ownerName, PlotSelectionState.Submission area) throws SQLException {
        UUID parentId = expectedParent.id();
        Plot parent = registry.byId(parentId);
        if (parent == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (!parent.equals(expectedParent)) throw new PlotProblem(Message.PLOT_ERROR_PERMISSION_CHANGED);
        if (!area.context().equals(PlotEditorContext.subPlot(parentId)))
            throw new PlotProblem(Message.PLOT_ERROR_SELECTION_CHANGED);
        selections.requireSubmission(player.getUniqueId(), player.getWorld().getUID(), area);
        PlotOwner owner = PlotCreationController.managedPlayer(ownerName);
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        persistGeometry(player, staged -> staged.createSubPlot(parent, actor, staff, owner.id(), name, area.shape()), child -> {
            boolean cleared = selections.clearIfMatches(actor, parentId, area);
            send(player, NamedTextColor.GREEN, Message.PLOT_SUBPLOT_CREATED,
                    child.name(), registry.horizontalArea(child));
            if (!owner.id().equals(actor))
                notifyPlayer(owner.id(), Message.PLOT_SUBPLOT_CREATED_FOR_YOU, child.name(), parent.name());
            if (!parent.owner().equals(actor) && !parent.owner().equals(owner.id()))
                notifyPlayer(parent.owner(), Message.PLOT_SUBPLOT_CREATED,
                        child.name(), registry.horizontalArea(child));
            if (cleared && selections.context(actor).equals(area.context()) && player.isOnline()
                    && player.getWorld().getUID().equals(parent.world())) {
                selectionController.stop(actor);
                miniature.forget(actor);
                openPlotDetail(player, child.id());
            }
        });
    }

    private void editArrivalText(Player player, UUID plotId) {
        PlotArrival arrival = registry.arrival(plotId);
        if (arrival == null) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_ARRIVAL_MISSING);
            return;
        }
        MenuDialogs.openTwoTextInputs(plugin, player,
                Component.text(t(player, Message.PLOT_MENU_ARRIVAL_TEXT), NamedTextColor.GOLD),
                Component.text(t(player, Message.PLOT_MENU_ARRIVAL_TITLE), NamedTextColor.WHITE),
                arrival.title(), 160,
                Component.text(t(player, Message.PLOT_MENU_ARRIVAL_SUBTITLE), NamedTextColor.WHITE),
                arrival.subtitle(), 160,
                Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                (viewer, values) -> {
                    perform(viewer, () -> arrivals.setText(viewer, plotId,
                            values.first(), values.second()));
                    openPlotDetail(viewer, plotId);
                });
    }

    private void editNoticeBoard(Player player, UUID plotId) {
        editNoticeBoard(player, plotId, registry.noticeBoard(plotId));
    }

    private void editNoticeBoard(Player player, UUID plotId, String initial) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_ID);
            return;
        }
        if (!canManage(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        MenuDialogs.openTextInput(plugin, player,
                Component.text(t(player, Message.PLOT_NOTICE_BOARD), NamedTextColor.GOLD),
                Component.text(t(player, Message.PLOT_NOTICE_INPUT, PlotNoticeText.MAX_LENGTH), NamedTextColor.GRAY),
                initial, PlotNoticeText.MAX_INPUT_LENGTH, TextDialogInput.MultilineOptions.create(null, 120),
                Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                (viewer, value) -> {
                    try {
                        PlotNoticeText.normalize(value);
                    } catch (PlotProblem error) {
                        send(viewer, NamedTextColor.RED, error.message(), error.args());
                        editNoticeBoard(viewer, plotId, value);
                        return;
                    }
                    perform(viewer, () -> saveNoticeBoard(viewer, plotId, value));
                });
    }

    private void saveNoticeBoard(Player player, UUID plotId, String body) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> staged.setNoticeBoard(plot, player.getUniqueId(), RolePermissions.canModerate(player), body), saved -> {
            send(player, NamedTextColor.GREEN, saved.isEmpty()
                    ? Message.PLOT_NOTICE_CLEARED : Message.PLOT_NOTICE_SAVED);
            if (player.isOnline()) openPlotDetail(player, plotId);
        });
    }

    private Component atmosphereLabel(Player player, Plot plot, PlotAtmosphere setting) {
        PlotAtmosphere inherited = plot.subPlot()
                ? registry.atmosphere(plot.parentId()) : PlotAtmosphere.WORLD;
        String time = setting.timeTicks() == null
                ? t(player, plot.subPlot() ? Message.PLOT_MENU_FOLLOW_PARENT
                        : Message.PLOT_MENU_FOLLOW_WORLD)
                        + (inherited.timeTicks() == null ? ""
                        : " · " + PlotAtmosphere.clock(inherited.timeTicks()))
                : PlotAtmosphere.clock(setting.timeTicks());
        String weather = switch (setting.weather()) {
            case null -> t(player, plot.subPlot() ? Message.PLOT_MENU_FOLLOW_PARENT
                    : Message.PLOT_MENU_FOLLOW_WORLD);
            case CLEAR -> t(player, Message.PLOT_MENU_CLEAR);
            case RAIN -> t(player, Message.PLOT_MENU_RAIN);
        };
        return Component.text(t(player, Message.PLOT_MENU_ATMOSPHERE), NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_MENU_TIME) + " · " + time,
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_MENU_WEATHER) + " · " + weather,
                        NamedTextColor.GRAY));
    }

    private void openPlotAtmosphere(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManage(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            openPlotDetail(player, plotId);
            return;
        }
        FloatingMenus.present(player, atmosphereDefinition(player, plot));
    }

    private FloatingMenuDefinition atmosphereDefinition(Player player, Plot plot) {
        UUID plotId = plot.id();
        PlotAtmosphere setting = registry.atmosphere(plotId);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-atmosphere:" + plotId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.atmosphere())
                .refreshEvery(10, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(p);
                    else if (!canManage(p, fresh)) openPlotDetail(p, plotId);
                    else if (fresh != plot || !setting.equals(registry.atmosphere(plotId)))
                        handle.update(atmosphereDefinition(p, fresh));
                });
        menu.information("time-heading", Component.text(
                        t(player, Message.PLOT_MENU_ATMOSPHERE) + " · "
                                + t(player, Message.PLOT_MENU_TIME), NamedTextColor.GOLD))
                .region("time-heading");
        Integer[] choices = {null, 0, 6_000, 12_000, 18_000};
        Material[] icons = {Material.CLOCK, Material.SUNFLOWER, Material.GLOWSTONE_DUST,
                Material.ORANGE_DYE, Material.BLACK_DYE};
        for (int i = 0; i < choices.length; i++) {
            Integer ticks = choices[i];
            String label = ticks == null ? t(player, plot.subPlot()
                    ? Message.PLOT_MENU_FOLLOW_PARENT : Message.PLOT_MENU_FOLLOW_WORLD)
                    : PlotAtmosphere.clock(ticks);
            menu.choice("time:" + i, Objects.equals(setting.timeTicks(), ticks),
                            icons[i], Component.text(label, NamedTextColor.AQUA))
                    .region("times")
                    .primary((p, handle) -> {
                        perform(p, () -> setPlotTime(p, plotId, ticks));
                        refreshAtmosphere(p, plotId, handle);
                    });
        }
        boolean custom = setting.timeTicks() != null
                && java.util.Arrays.stream(choices).noneMatch(setting.timeTicks()::equals);
        String customLabel = t(player, Message.PLOT_MENU_CUSTOM_TIME)
                + (custom ? " · " + PlotAtmosphere.clock(setting.timeTicks()) : "");
        menu.choice("time:custom", custom, Material.WRITABLE_BOOK,
                        Component.text(customLabel, NamedTextColor.AQUA))
                .region("times")
                .primary((p, handle) -> editPlotTime(p, plotId,
                        setting.timeTicks() == null ? "" : PlotAtmosphere.clock(setting.timeTicks())));
        menu.information("weather-heading", Component.text(
                        t(player, Message.PLOT_MENU_WEATHER), NamedTextColor.GOLD))
                .region("weather-heading");
        PlotAtmosphere.Weather[] weatherChoices = {null,
                PlotAtmosphere.Weather.CLEAR, PlotAtmosphere.Weather.RAIN};
        Material[] weatherIcons = {Material.COMPASS, Material.SUNFLOWER, Material.WATER_BUCKET};
        Message[] weatherLabels = {plot.subPlot() ? Message.PLOT_MENU_FOLLOW_PARENT
                : Message.PLOT_MENU_FOLLOW_WORLD,
                Message.PLOT_MENU_CLEAR, Message.PLOT_MENU_RAIN};
        for (int i = 0; i < weatherChoices.length; i++) {
            PlotAtmosphere.Weather weather = weatherChoices[i];
            menu.choice("weather:" + i, setting.weather() == weather,
                            weatherIcons[i], Component.text(t(player, weatherLabels[i]),
                                    NamedTextColor.AQUA))
                    .region("weather-options")
                    .primary((p, handle) -> {
                        perform(p, () -> setPlotWeather(p, plotId, weather));
                        refreshAtmosphere(p, plotId, handle);
                    });
        }
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void refreshAtmosphere(Player player, UUID plotId,
                                   org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        Plot current = registry.byId(plotId);
        if (current == null) openMenu(player);
        else if (!canManage(player, current)) openPlotDetail(player, plotId);
        else handle.update(atmosphereDefinition(player, current));
    }

    private void editPlotTime(Player player, UUID plotId, String initial) {
        textInput(player, Message.PLOT_MENU_CUSTOM_TIME, Message.PLOT_MENU_TIME_INPUT,
                initial, 5, false, (viewer, value) -> {
                    int ticks;
                    try { ticks = PlotAtmosphere.parseClock(value.strip()); }
                    catch (PlotProblem error) {
                        send(viewer, NamedTextColor.RED, error.message(), error.args());
                        editPlotTime(viewer, plotId, value);
                        return;
                    }
                    perform(viewer, () -> setPlotTime(viewer, plotId, ticks));
                    openPlotAtmosphere(viewer, plotId);
                });
    }

    private void setPlotTime(Player player, UUID plotId, Integer ticks) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> { staged.setTime(plot, player.getUniqueId(), RolePermissions.canModerate(player), ticks); return null; }, ignored -> {
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_ATMOSPHERE_SAVED);
        });
    }

    private void setPlotWeather(Player player, UUID plotId, PlotAtmosphere.Weather weather)
            throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> { staged.setWeather(plot, player.getUniqueId(), RolePermissions.canModerate(player), weather); return null; }, ignored -> {
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_ATMOSPHERE_SAVED);
        });
    }

    private void openPlotAccess(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManage(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        FloatingMenus.present(player, accessDefinition(player, plot));
    }

    private FloatingMenuDefinition accessDefinition(Player player, Plot plot) {
        UUID plotId = plot.id();
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-access:" + plotId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.access())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                    else if (fresh != plot) handle.update(accessDefinition(viewer, fresh));
                });
        menu.information("heading", Component.text(plot.name() + " · "
                + t(player, Message.PLOT_MENU_ACCESS), NamedTextColor.GOLD)).region("heading").keepAccessible();
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            menu.item("subject:" + group.name(), groupIcon(group),
                            Component.text(t(player, groupLabel(group)), NamedTextColor.AQUA))
                    .region("rows")
                    .primary((viewer, handle) -> openPermissionList(viewer, plotId,
                            PlotAccessPolicy.Subject.group(group), null));
        }
        menu.item("members", Material.PLAYER_HEAD,
                        Component.text(t(player, Message.PLOT_MENU_MEMBERS, PlotRoster.size(plot)), NamedTextColor.AQUA))
                .region("rows").primary((viewer, handle) -> openPlotMembers(viewer, plotId, 0));
        menu.item("management", Material.COMPARATOR,
                        Component.text(t(player, Message.PLOT_PERMISSION_MANAGEMENT), NamedTextColor.YELLOW))
                .region("rows").primary((viewer, handle) -> openPlotManagement(viewer, plotId));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void openPermissionList(Player player, UUID plotId, PlotAccessPolicy.Subject subject,
                                    PlotPermission.Category category) {
        Plot plot = managed(player, plotId.toString());
        if (subject.player() != null && (!plot.members().containsKey(subject.player()))) {
            openPlotMembers(player, plotId, 0);
            return;
        }
        FloatingMenus.present(player, permissionListDefinition(player, plot, subject, category));
    }

    private Component permissionStatus(Player player, Plot plot, PlotAccessPolicy.View access) {
        String result = t(player, access.allowed() ? Message.PLOT_ALLOWED : Message.PLOT_DENIED);
        String state = access.setting() == PlotAccessPolicy.Setting.DEFAULT
                ? t(player, Message.PLOT_PERMISSION_DEFAULT_STATUS, result) : result;
        PlotAccessPolicy.Origin origin = access.origin();
        String source;
        if (origin.group() == null) source = t(player, Message.PLOT_PERMISSION_PERSONAL_SOURCE);
        else {
            source = t(player, groupLabel(origin.group())) + " → " + t(player, sourceLabel(origin.source()));
            if (!origin.plot().equals(plot.id())) {
                Plot inherited = registry.byId(origin.plot());
                source = t(player, Message.PLOT_PERMISSION_SOURCE_PARENT) + " · "
                        + (inherited == null ? origin.plot().toString().substring(0, 8) : inherited.name())
                        + " → " + source;
            }
        }
        return Component.text(state, access.allowed() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.newline()).append(Component.text(source, NamedTextColor.GRAY));
    }

    private PlotProblem accessProblem(Player player, Plot plot, PlotAccessRequest request) {
        try {
            edits.authorizeAccess(edits.planAccess(plot, request), player.getUniqueId(), RolePermissions.canModerate(player));
            return null;
        } catch (PlotProblem problem) {
            return problem;
        }
    }

    private void submitAccessChange(Player player, UUID plotId, PlotAccessRequest request,
                                    Consumer<Player> after) throws SQLException {
        PlotAccessPlan plan = edits.planAccess(managed(player, plotId.toString()), request);
        edits.authorizeAccess(plan, player.getUniqueId(), RolePermissions.canModerate(player));
        if (!plan.requiresConfirmation()) {
            commitAccessChange(player, plan, after);
            return;
        }
        MenuDialogs.openConfirm(plugin, player,
                Component.text(t(player, Message.PLOT_PERMISSION_CHANGE_TITLE), NamedTextColor.GOLD),
                accessChangeBody(player, plan),
                Component.text(t(player, Message.PLOT_PERMISSION_CHANGE_CONFIRM), NamedTextColor.YELLOW),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                responder -> perform(responder, () -> commitAccessChange(responder, plan, after)));
    }

    private void commitAccessChange(Player player, PlotAccessPlan plan, Consumer<Player> after) throws SQLException {
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        persist(player, staged -> {
            staged.applyAccess(plan, actor, staff);
            return null;
        }, ignored -> after.accept(player));
    }

    private Component accessChangeBody(Player player, PlotAccessPlan plan) {
        PlotAccessPolicy.Subject subject = plan.request().subject();
        String subjectName = subject == null ? t(player, Message.PLOT_PERMISSION_ALL_ACTIONS)
                : subject.player() == null ? t(player, groupLabel(subject.group())) : memberName(subject.player());
        if (plan.request() instanceof PlotAccessRequest.Member member)
            subjectName += " · " + t(player, roleMessage(member.role()));
        if (plan.request() instanceof PlotAccessRequest.Remove)
            subjectName += " · " + t(player, Message.PLOT_MENU_REMOVE_MEMBER);
        if (plan.request() instanceof PlotAccessRequest.Permission permission)
            subjectName += " · " + t(player, permission.permission().label()) + " · "
                    + t(player, permission.allowed() == null ? Message.PLOT_PERMISSION_STATE_DEFAULT
                    : permission.allowed() ? Message.PLOT_ALLOWED : Message.PLOT_DENIED);
        if (plan.request() instanceof PlotAccessRequest.Reset reset)
            subjectName += " · " + t(player, Message.PLOT_PERMISSION_RESET) + " · " + t(player, reset.category() == null
                    ? Message.PLOT_PERMISSION_ALL_ACTIONS : reset.category().label());
        List<String> players = plan.affectedPlayers().stream().map(this::memberName).sorted().toList();
        List<String> children = plan.affectedChildren().stream().map(registry::byId)
                .filter(Objects::nonNull).map(Plot::name).sorted().toList();
        Component body = Component.text(plan.before().name(), NamedTextColor.GOLD)
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_SUBJECT, subjectName), NamedTextColor.WHITE))
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_GRANTED, permissionNames(player, plan.granted())), NamedTextColor.GREEN))
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_REVOKED, permissionNames(player, plan.revoked())), NamedTextColor.RED))
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_MEMBERS, namesSummary(player, players)), NamedTextColor.GRAY))
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_CHILDREN, namesSummary(player, children)), NamedTextColor.GRAY));
        if (plan.request() instanceof PlotAccessRequest.Member member) {
            String overrides = member.overrides().isEmpty() ? t(player, Message.PLOT_PERMISSION_STATE_DEFAULT)
                    : String.join(" · ", java.util.Arrays.stream(PlotPermission.values())
                            .filter(member.overrides()::containsKey)
                            .map(permission -> t(player, permission.label()) + " " + t(player,
                                    member.overrides().get(permission) ? Message.PLOT_ALLOWED : Message.PLOT_DENIED))
                            .toList());
            body = body.append(Component.newline()).append(Component.text(
                    t(player, Message.PLOT_PERMISSION_PERSONAL) + ": " + overrides, NamedTextColor.GRAY));
        }
        if (plan.effects().isEmpty()) body = body.append(Component.newline()).append(Component.text(
                t(player, Message.PLOT_PERMISSION_CHANGE_NO_EFFECT), NamedTextColor.GRAY));
        return body;
    }

    private String permissionNames(Player player, Set<PlotPermission> permissions) {
        if (permissions.isEmpty()) return t(player, Message.PLOT_PERMISSION_CHANGE_NONE);
        return String.join(" · ", java.util.Arrays.stream(PlotPermission.values())
                .filter(permissions::contains).map(permission -> t(player, permission.label())).toList());
    }

    private String namesSummary(Player player, List<String> names) {
        if (names.isEmpty()) return t(player, Message.PLOT_PERMISSION_CHANGE_NONE);
        String summary = String.join(" · ", names.stream().limit(6).toList());
        return names.size() <= 6 ? summary : summary + " · "
                + t(player, Message.PLOT_PERMISSION_CHANGE_MORE, names.size() - 6);
    }

    private FloatingMenuDefinition permissionSettingDefinition(Player player, Plot plot,
            PlotAccessPolicy.Subject subject, PlotPermission permission, PlotPermission.Category category) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-permission-state:" + plotId,
                FloatingMenuAppearance.SURVEY, PlotMenuLayouts.access())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                    else if (subject.player() != null && !fresh.members().containsKey(subject.player()))
                        openPlotMembers(viewer, plotId, 0);
                    else if (fresh != plot || registry.parentOf(fresh) != parent)
                        handle.update(permissionSettingDefinition(viewer, fresh, subject, permission, category));
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_PERMISSION_SELECT_STATE,
                t(player, permission.label())), NamedTextColor.GOLD)).region("heading");
        for (PlotAccessPolicy.Setting setting : PlotAccessPolicy.Setting.values()) {
            PlotAccessRequest request = new PlotAccessRequest.Permission(subject, permission, setting.value());
            PlotAccessPlan plan = edits.planAccess(plot, request);
            PlotAccessPolicy.View access = PlotAccessPolicy.view(plan.after(), plan.parent(), subject, permission.key());
            var option = menu.control("state:" + setting, permissionStatus(player, plan.after(), access))
                    .region("rows").selected(setting == PlotAccessPolicy.setting(plot.flags(), subject, permission.key()))
                    .primary((viewer, handle) -> perform(viewer, () -> submitAccessChange(viewer, plotId, request,
                            responder -> openPermissionList(responder, plotId, subject, category))));
            PlotProblem failure = accessProblem(player, plot, request);
            if (failure != null) option.disabled(problemLabel(player, failure));
        }
        menu.navigation("return", Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").primary((viewer, handle) -> perform(viewer,
                        () -> handle.update(permissionListDefinition(viewer, managed(viewer, plotId.toString()), subject, category))));
        return menu.build();
    }

    private FloatingMenuDefinition memberDraftDefinition(Player player, Plot plot, PlotMemberDraft draft,
                                                          PlotPermission.Category category) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        PlotAccessPlan plan = edits.planAccess(plot, draft.request());
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-member-draft:" + plotId + ":" + draft.player(),
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.permissionTable())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!permitted(viewer, fresh, PlotPermission.MANAGE_MEMBERS)) openPlotDetail(viewer, plotId);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(memberDraftDefinition(viewer, fresh, draft, category));
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_TITLE,
                memberName(draft.player())), NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.control("subject", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_ROLE,
                        t(player, roleMessage(draft.role()))) + " ▾", NamedTextColor.AQUA))
                .region("subject").alignment(FloatingMenuDecoration.Alignment.LEFT)
                .textWidth(FloatingMenuTextWidth.COMPACT).keepAccessible()
                .primary((viewer, handle) -> perform(viewer, () -> handle.update(memberDraftDefinition(viewer,
                        managed(viewer, plotId.toString()), draft.role(draft.role() == Plot.Role.ADMIN
                                ? Plot.Role.COLLABORATOR : Plot.Role.ADMIN), category))));
        menu.information("section", Component.text(t(player, category.label()), NamedTextColor.WHITE))
                .region("section").alignment(FloatingMenuDecoration.Alignment.LEFT).textWidth(FloatingMenuTextWidth.WIDE);
        for (PlotPermission.Category next : PlotPermission.Category.values()) {
            menu.control("category:" + next, Component.text((next == category ? "› " : "  ") + t(player, next.label()),
                            next == category ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .region("categories").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT).selected(next == category).keepAccessible()
                    .primary((viewer, handle) -> perform(viewer, () -> handle.update(memberDraftDefinition(viewer,
                            managed(viewer, plotId.toString()), draft, next))));
        }
        menu.information("operation-heading", Component.text(t(player, Message.PLOT_PERMISSION_OPERATION), NamedTextColor.GRAY))
                .region("operation-heading").alignment(FloatingMenuDecoration.Alignment.LEFT).spatialOnly();
        menu.information("result-heading", Component.text(t(player, Message.PLOT_PERMISSION_RESULT), NamedTextColor.GRAY))
                .region("result-heading").alignment(FloatingMenuDecoration.Alignment.LEFT).spatialOnly();
        menu.information("hint", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_HINT), NamedTextColor.GRAY))
                .region("hint").textWidth(FloatingMenuTextWidth.EXPANDED);
        for (PlotPermission permission : category.permissions()) {
            if (!permission.appliesTo(plot)) continue;
            String region = "permission-" + permission.key();
            menu.information(region + "-label", Component.text(t(player, permission.label()), NamedTextColor.WHITE))
                    .region(region + "-group-heading").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT);
            menu.control(region, permissionStatus(player, plan.after(), PlotAccessPolicy.view(plan.after(), parent,
                            draft.request().subject(), permission.key())))
                    .region(region).alignment(FloatingMenuDecoration.Alignment.LEFT).textWidth(FloatingMenuTextWidth.WIDE)
                    .primary((viewer, handle) -> perform(viewer, () -> handle.update(memberDraftDefinition(viewer,
                            managed(viewer, plotId.toString()), draft.cycle(permission), category))));
        }
        menu.navigation("reset", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_RESET), NamedTextColor.YELLOW))
                .region("navigation").primary((viewer, handle) -> perform(viewer,
                        () -> handle.update(memberDraftDefinition(viewer, managed(viewer, plotId.toString()), draft.reset(category), category))));
        var submit = menu.navigation("submit", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_SUBMIT), NamedTextColor.GREEN))
                .region("navigation").keepAccessible()
                .primary((viewer, handle) -> perform(viewer, () -> submitAccessChange(viewer, plotId, draft.request(), responder -> {
                    send(responder, NamedTextColor.GREEN, Message.PLOT_INVITED, t(responder, roleMessage(draft.role())));
                    notifyRole(draft.player(), plot.name(), draft.role());
                    openPlotMembers(responder, plotId, 0);
                })));
        PlotProblem failure = accessProblem(player, plot, draft.request());
        if (failure != null) submit.disabled(problemLabel(player, failure));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY)).region("navigation").keepAccessible();
        return menu.build();
    }

    private FloatingMenuDefinition permissionListDefinition(Player player, Plot plot,
            PlotAccessPolicy.Subject subject, PlotPermission.Category category) {
        if (category == null) return permissionListDefinition(player, plot, subject, PlotPermission.Category.BUILD);
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen(
                        "plot-permissions:" + plotId + ":" + subject.key("") + ":" + category,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.permissionTable())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                    else if (subject.player() != null && (!fresh.members().containsKey(subject.player())))
                        openPlotMembers(viewer, plotId, 0);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(permissionListDefinition(viewer, fresh, subject, category));
                });
        String subjectName = subject.player() == null ? t(player, groupLabel(subject.group()))
                : memberName(subject.player());
        menu.information("heading", Component.text(plot.name(), NamedTextColor.GOLD))
                .region("heading").textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        menu.control("subject", Component.text(subjectName + " ▾", NamedTextColor.AQUA))
                .region("subject").alignment(FloatingMenuDecoration.Alignment.LEFT)
                .textWidth(FloatingMenuTextWidth.COMPACT).keepAccessible()
                .primary((viewer, handle) -> perform(viewer, () -> handle.update(permissionSubjectDefinition(
                        viewer, managed(viewer, plotId.toString()), subject, category))));
        menu.information("section", Component.text(t(player, category.label()), NamedTextColor.WHITE))
                .region("section").alignment(FloatingMenuDecoration.Alignment.LEFT)
                .textWidth(FloatingMenuTextWidth.WIDE);
        for (PlotPermission.Category next : PlotPermission.Category.values()) {
            menu.control("category:" + next.name(), Component.text(
                            (next == category ? "› " : "  ") + t(player, next.label()),
                            next == category ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .region("categories").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT).selected(next == category).keepAccessible()
                    .primary((viewer, handle) -> perform(viewer,
                            () -> refreshPermissionList(viewer, plotId, subject, next, handle)));
        }
        menu.information("operation-heading", Component.text(t(player, Message.PLOT_PERMISSION_OPERATION),
                        NamedTextColor.GRAY)).region("operation-heading")
                .alignment(FloatingMenuDecoration.Alignment.LEFT).spatialOnly();
        menu.information("result-heading", Component.text(t(player, Message.PLOT_PERMISSION_RESULT),
                        NamedTextColor.GRAY)).region("result-heading")
                .alignment(FloatingMenuDecoration.Alignment.LEFT).spatialOnly();
        for (PlotPermission action : category.permissions()) {
            if (!action.appliesTo(plot)) continue;
            PlotAccessPolicy.View access = PlotAccessPolicy.view(plot, parent, subject, action.key());
            Component status = permissionStatus(player, plot, access);
            String region = "permission-" + action.key();
            menu.information(region + "-label", Component.text(t(player, action.label()), NamedTextColor.WHITE))
                    .region(region + "-group-heading").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT);
            if (!PlotAccessPolicy.editable(subject, action.key())) {
                menu.information(subject.key(action.key()), status)
                        .region(region).alignment(FloatingMenuDecoration.Alignment.LEFT)
                        .textWidth(FloatingMenuTextWidth.WIDE);
                continue;
            }
            var permissionControl = menu.control(subject.key(action.key()), status)
                    .region(region).alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.WIDE)
                    .primary((viewer, handle) -> perform(viewer, () -> {
                        Plot latest = managed(viewer, plotId.toString());
                        if (action.category() == PlotPermission.Category.MANAGEMENT) {
                            handle.update(permissionSettingDefinition(viewer, latest, subject, action, category));
                        } else {
                            PlotAccessPolicy.Setting next = PlotAccessPolicy.setting(latest.flags(), subject, action.key()).next();
                            submitAccessChange(viewer, plotId, new PlotAccessRequest.Permission(subject, action, next.value()),
                                    responder -> refreshPermissionList(responder, plotId, subject, category, handle));
                        }
                    }));
            if (!registry.canEditSubject(plot, player.getUniqueId(), RolePermissions.canModerate(player), subject))
                permissionControl.disabled(Component.text(t(player, Message.PLOT_ERROR_OWNER_ONLY)));
        }
        boolean overrides = PlotAccessPolicy.hasSubjectOverrides(plot.flags(), subject, category);
        if (overrides && registry.canEditSubject(plot, player.getUniqueId(), RolePermissions.canModerate(player), subject)) {
            menu.navigation("reset",
                            Component.text(t(player, Message.PLOT_PERMISSION_RESET), NamedTextColor.YELLOW))
                    .region("navigation").keepAccessible()
                    .primary((viewer, handle) -> confirmPermissionReset(viewer, plotId, subject,
                            category, category, handle));
        }
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private FloatingMenuDefinition permissionSubjectDefinition(Player player, Plot plot,
            PlotAccessPolicy.Subject subject, PlotPermission.Category category) {
        UUID plotId = plot.id();
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-permission-subject:" + plotId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.access())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh != plot) refreshPermissionList(viewer, plotId, subject, category, handle);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                });
        menu.information("heading", Component.text(plot.name() + " · "
                + t(player, Message.PLOT_MENU_ACCESS), NamedTextColor.GOLD)).region("heading").keepAccessible();
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            menu.control("subject:" + group.name(), Component.text(t(player, groupLabel(group)), NamedTextColor.AQUA))
                    .region("rows").selected(group == subject.group())
                    .primary((viewer, handle) -> perform(viewer, () -> refreshPermissionList(viewer, plotId,
                            PlotAccessPolicy.Subject.group(group), category, handle)));
        }
        if (subject.player() != null) {
            menu.control("personal", Component.text(memberName(subject.player()), NamedTextColor.AQUA))
                    .region("rows").selected(true)
                    .primary((viewer, handle) -> perform(viewer,
                            () -> refreshPermissionList(viewer, plotId, subject, category, handle)));
        }
        menu.control("members", Component.text(t(player, Message.PLOT_MENU_MEMBERS, PlotRoster.size(plot)),
                        NamedTextColor.AQUA)).region("rows")
                .primary((viewer, handle) -> perform(viewer, () -> openPlotMembers(viewer, plotId, 0)));
        if (PlotAccessPolicy.hasSubjectOverrides(plot.flags(), subject, null)
                && registry.canEditSubject(plot, player.getUniqueId(), RolePermissions.canModerate(player), subject)) {
            menu.navigation("reset", Component.text(t(player, Message.PLOT_PERMISSION_RESET)
                            + " · " + t(player, Message.PLOT_PERMISSION_ALL_ACTIONS), NamedTextColor.YELLOW))
                    .region("navigation").keepAccessible()
                    .primary((viewer, handle) -> confirmPermissionReset(viewer, plotId, subject,
                            null, category, handle));
        }
        menu.navigation("return", Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible()
                .primary((viewer, handle) -> perform(viewer,
                        () -> refreshPermissionList(viewer, plotId, subject, category, handle)));
        return menu.build();
    }

    private void confirmPermissionReset(Player player, UUID plotId, PlotAccessPolicy.Subject subject,
            PlotPermission.Category scope, PlotPermission.Category returnCategory,
            org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        perform(player, () -> submitAccessChange(player, plotId, new PlotAccessRequest.Reset(subject, scope),
                responder -> refreshPermissionList(responder, plotId, subject, returnCategory, handle)));
    }

    private void refreshPermissionList(Player player, UUID plotId, PlotAccessPolicy.Subject subject,
                                       PlotPermission.Category category,
                                       org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        Plot fresh = registry.byId(plotId);
        if (fresh == null) openMenu(player);
        else if (!canManage(player, fresh)) openPlotDetail(player, plotId);
        else if (subject.player() != null && (!fresh.members().containsKey(subject.player()))) openPlotMembers(player, plotId, 0);
        else handle.update(permissionListDefinition(player, fresh, subject, category));
    }

    private static Message groupLabel(PlotAccessPolicy.Group group) {
        return switch (group) {
            case COLLABORATOR -> Message.PLOT_MENU_GROUP_COLLABORATOR;
            case OWNER -> Message.PLOT_MENU_GROUP_OWNER;
            case ADMIN -> Message.PLOT_MENU_GROUP_ADMIN;
            case NEWCOMER -> Message.PLOT_MENU_GROUP_NEWCOMER;
            case MEMBER -> Message.PLOT_MENU_GROUP_MEMBER;
        };
    }

    private static Material groupIcon(PlotAccessPolicy.Group group) {
        return switch (group) {
            case OWNER -> Material.GOLDEN_HELMET;
            case ADMIN -> Material.COMPARATOR;
            case COLLABORATOR -> Material.IRON_PICKAXE;
            case NEWCOMER -> Material.WOODEN_PICKAXE;
            case MEMBER -> Material.PLAYER_HEAD;
        };
    }

    private static Message sourceLabel(PlotAccessPolicy.Source source) {
        return switch (source) {
            case LOCAL -> Message.PLOT_PERMISSION_SOURCE_LOCAL;
            case PARENT -> Message.PLOT_PERMISSION_SOURCE_PARENT;
            case ROLE -> Message.PLOT_PERMISSION_SOURCE_ROLE;
            case OWNER -> Message.PLOT_PERMISSION_SOURCE_OWNER;
            case GROUP -> Message.PLOT_PERMISSION_SOURCE_GROUP;
        };
    }

    private void openPlotManagement(Player player, UUID plotId) {
        Plot plot = managed(player, plotId.toString());
        FloatingMenus.present(player, managementDefinition(player, plot));
    }

    private FloatingMenuDefinition managementDefinition(Player player, Plot plot) {
        UUID plotId = plot.id();
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-management:" + plotId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.access())
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                    else if (fresh != plot) handle.update(managementDefinition(viewer, fresh));
                });
        menu.information("heading", Component.text(plot.name() + " · "
                + t(player, Message.PLOT_PERMISSION_MANAGEMENT), NamedTextColor.GOLD)).region("heading");
        menu.item("owner", memberHead(plot.owner()), Component.text(t(player, Message.PLOT_MENU_OWNER,
                memberName(plot.owner())), NamedTextColor.AQUA)).region("project").passive();
        if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) menu.choice("public", plot.publicProject(), Material.BEACON,
                        Component.text(t(player, Message.PLOT_MENU_PROJECT_KIND) + " · "
                                + t(player, plot.publicProject() ? Message.PLOT_PUBLIC : Message.PLOT_PRIVATE),
                                NamedTextColor.AQUA))
                .region("project").primary((viewer, handle) -> perform(viewer, () -> {
                    Plot latest = managed(viewer, plotId.toString());
                    persist(viewer, staged -> { staged.setPublic(latest, viewer.getUniqueId(), RolePermissions.canModerate(viewer), !latest.publicProject()); return null; },
                            ignored -> {
                                Plot fresh = registry.byId(plotId);
                                if (fresh == null) openMenu(viewer);
                                else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                                else handle.update(managementDefinition(viewer, fresh));
                            });
                }));
        if (permitted(player, plot, PlotPermission.TRANSFER)) {
            menu.item("transfer", Material.GOLDEN_HELMET,
                            Component.text(t(player, Message.PLOT_MENU_TRANSFER), NamedTextColor.GOLD))
                    .region("ownership").primary((viewer, handle) -> transferInput(viewer, plotId));
        }
        addReleaseAction(player, menu, plot);
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void addReleaseAction(Player player, FloatingMenuDefinition.Builder menu, Plot plot) {
        if (!registry.canRelease(plot, player.getUniqueId(), RolePermissions.canModerate(player))) return;
        UUID plotId = plot.id();
        var release = menu.item("delete", Material.TNT,
                        Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED))
                .region("ownership")
                .primary((viewer, handle) -> perform(viewer,
                        () -> confirmRelease(viewer, plotId.toString())));
        if (!registry.childrenOf(plotId).isEmpty()) release.disabled(
                Component.text(t(player, Message.PLOT_ERROR_SUBPLOT_CHILDREN)));
    }

    private void confirmRelease(Player player, String plotPrefix) {
        Plot fresh = edits.releasable(player.getUniqueId(),
                RolePermissions.canModerate(player), plotPrefix);
        UUID plotId = fresh.id();
        UUID parentId = fresh.parentId();
        if (!registry.childrenOf(plotId).isEmpty())
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_CHILDREN);
        MenuDialogs.openConfirm(plugin, player,
                Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED),
                Component.text(t(player, Message.PLOT_MENU_CONFIRM_DELETE,
                        fresh.name() + " · #" + plotId.toString().substring(0, 8)), NamedTextColor.GRAY),
                Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                responder -> perform(responder, () -> {
                    delete(responder, plotId.toString());
                    if (parentId != null && registry.byId(parentId) != null) openPlotDetail(responder, parentId);
                    else openMenu(responder);
                }));
    }

    private void openPlotMembers(Player player, UUID plotId, int requestedPage) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManage(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        FloatingMenus.present(player, membersDefinition(player, plot, requestedPage));
    }

    private FloatingMenuDefinition membersDefinition(Player player, Plot plot, int requestedPage) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        List<MemberCard> members = PlotRoster.entries(plot).stream()
                .map(entry -> new MemberCard(entry.id(), memberName(entry.id()), entry.group()))
                .sorted(Comparator.comparing(MemberCard::group)
                        .thenComparing(MemberCard::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MemberCard::id))
                .toList();
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, members.size(), MEMBERS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-members:" + plotId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.browser("members", "hint"))
                .refreshEvery(20, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(p);
                    else if (!canManage(p, fresh)) openPlotDetail(p, plotId);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(membersDefinition(p, fresh, page.index()));
                });
        menu.information("heading", Component.text(plot.name() + " · "
                + t(player, Message.PLOT_MENU_MEMBERS, members.size()), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        if (parent != null)
            menu.information("supervisor", Component.text(t(player, Message.PLOT_MENU_PARENT_SUPERVISOR,
                    memberName(parent.owner())), NamedTextColor.GRAY)).region("hint");
        for (MemberCard member : page.slice(members)) {
            menu.item("member:" + member.id(), memberHead(member.id()),
                            Component.text(member.name(), NamedTextColor.AQUA)
                                    .append(Component.newline())
                                    .append(Component.text(t(player, memberRoleMessage(member.group())),
                                            member.group() == PlotAccessPolicy.Group.OWNER
                                                    ? NamedTextColor.GOLD : NamedTextColor.GRAY)))
                    .region("members")
                    .primary((p, handle) -> openPlotMember(p, plotId, member.id(), page.index()));
        }
        if (page.count() > 1)
            menu.pagination("pagination", page, index -> openPlotMembers(player, plotId, index));
        if (permitted(player, plot, PlotPermission.MANAGE_MEMBERS)) {
            menu.item("admin", Material.GOLDEN_HELMET,
                            Component.text(t(player, Message.PLOT_MENU_ADMIN),
                                    NamedTextColor.GOLD))
                    .region("actions")
                    .primary((p, handle) -> memberInput(p, plotId, Plot.Role.ADMIN));
        }
        if (permitted(player, plot, PlotPermission.MANAGE_MEMBERS)) menu.item("collaborator", Material.IRON_PICKAXE,
                        Component.text(t(player, Message.PLOT_MENU_COLLABORATOR), NamedTextColor.GREEN))
                .region("actions")
                .primary((p, handle) -> memberInput(p, plotId, Plot.Role.COLLABORATOR));
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void openPlotMember(Player player, UUID plotId, UUID memberId, int listPage) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManage(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        if (PlotRoster.entry(plot, memberId) == null) {
            openPlotMembers(player, plotId, listPage);
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_MEMBER_MISSING);
            return;
        }
        FloatingMenus.present(player, memberDefinition(player, plot, memberId, listPage));
    }

    private FloatingMenuDefinition memberDefinition(Player player, Plot plot, UUID memberId,
                                                    int listPage) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        PlotRoster.Entry member = PlotRoster.entry(plot, memberId);
        Plot.Role role = plot.members().get(memberId);
        boolean mayTransfer = permitted(player, plot, PlotPermission.TRANSFER);
        String name = memberName(memberId);
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen(
                        "plot-member:" + plotId + ":" + memberId,
                        FloatingMenuAppearance.SURVEY, PlotMenuLayouts.member())
                .refreshEvery(20, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(p);
                    else if (!canManage(p, fresh)) openPlotDetail(p, plotId);
                    else if (PlotRoster.entry(fresh, memberId) == null)
                        openPlotMembers(p, plotId, listPage);
                    else if (fresh != plot || parent != registry.parentOf(fresh) || mayTransfer !=
                            permitted(p, fresh, PlotPermission.TRANSFER))
                        handle.update(memberDefinition(p, fresh, memberId, listPage));
                });
        menu.information("heading", Component.text(plot.name(), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        menu.item("identity", memberHead(memberId), Component.text(name, NamedTextColor.AQUA)
                        .append(Component.newline())
                        .append(Component.text(t(player, memberRoleMessage(member.group())),
                                member.owner() ? NamedTextColor.GOLD : NamedTextColor.GRAY)))
                .region("identity").passive();
        menu.item("permissions", Material.IRON_DOOR,
                        Component.text(t(player, Message.PLOT_MEMBER_PERMISSIONS), NamedTextColor.AQUA))
                .region("actions").primary((viewer, handle) -> perform(viewer, () -> {
                    Plot latest = managed(viewer, plotId.toString());
                    PlotRoster.Entry current = PlotRoster.entry(latest, memberId);
                    if (current == null) openPlotMembers(viewer, plotId, listPage);
                    else openPermissionList(viewer, plotId, current.permissionSubject(), null);
                }));
        if (!member.owner() && permitted(player, plot, PlotPermission.MANAGE_MEMBERS)) {
            for (Plot.Role next : Plot.Role.values()) {
                if (next == role) continue;
                var change = menu.item("role:" + next.name(), roleIcon(next),
                                Component.text(t(player, Message.PLOT_MENU_SET_ROLE,
                                        t(player, roleMessage(next))), NamedTextColor.GREEN))
                        .region("actions").primary((viewer, handle) -> perform(viewer,
                                () -> setMemberRole(viewer, plotId, memberId, next)));
                PlotProblem failure = accessProblem(player, plot, new PlotAccessRequest.Member(memberId, next,
                        PlotAccessPolicy.personalOverrides(plot, memberId)));
                if (failure != null) change.disabled(problemLabel(player, failure));
            }
            var remove = menu.item("remove", Material.BARRIER,
                            Component.text(t(player, Message.PLOT_MENU_REMOVE_MEMBER), NamedTextColor.RED))
                    .region("actions").primary((viewer, handle) -> perform(viewer,
                            () -> removeMember(viewer, plotId, memberId)));
            PlotProblem failure = accessProblem(player, plot, new PlotAccessRequest.Remove(memberId));
            if (failure != null) remove.disabled(problemLabel(player, failure));
        }
        if (mayTransfer) {
            menu.item("transfer", Material.GOLDEN_HELMET,
                            Component.text(t(player, Message.PLOT_MENU_TRANSFER),
                                    NamedTextColor.GOLD))
                    .region("actions")
                    .primary((p, handle) -> perform(p, () -> {
                        Plot latest = managed(p, plotId.toString());
                        PlotRoster.Entry current = PlotRoster.entry(latest, memberId);
                        if (current == null) openPlotMembers(p, plotId, listPage);
                        else if (current.owner()) transferInput(p, plotId);
                        else transferToMember(p, plotId, memberId);
                    }));
        }
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private boolean canManage(Player player, Plot plot) {
        return registry.canManage(plot, player.getUniqueId(),
                RolePermissions.canModerate(player));
    }

    private boolean permitted(Player player, Plot plot, PlotPermission permission) {
        return registry.permitted(plot, player.getUniqueId(), RolePermissions.canModerate(player), permission);
    }

    private static Message roleMessage(Plot.Role role) {
        return switch (role) {
            case ADMIN -> Message.PLOT_ROLE_ADMIN;
            case COLLABORATOR -> Message.PLOT_ROLE_COLLABORATOR;
        };
    }

    private static Message memberRoleMessage(PlotAccessPolicy.Group group) {
        return switch (group) {
            case OWNER -> Message.PLOT_ROLE_OWNER;
            case ADMIN -> Message.PLOT_ROLE_ADMIN;
            case COLLABORATOR -> Message.PLOT_ROLE_COLLABORATOR;
            default -> throw new IllegalArgumentException("Not a plot member: " + group);
        };
    }

    private static Material roleIcon(Plot.Role role) {
        return switch (role) {
            case ADMIN -> Material.GOLDEN_HELMET;
            case COLLABORATOR -> Material.IRON_PICKAXE;
        };
    }

    private String memberName(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            playerNames.put(id, online.getName());
            return online.getName();
        }
        return playerNames.computeIfAbsent(id, playerId -> {
            String name = Bukkit.getOfflinePlayer(playerId).getName();
            return name == null || name.isBlank()
                    ? playerId.toString().substring(0, 8) : name;
        });
    }

    private static ItemStack memberHead(UUID id) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            head.setItemMeta(skull);
        }
        return head;
    }

    private record MemberCard(UUID id, String name, PlotAccessPolicy.Group group) { }

    private void memberInput(Player player, UUID plotId, Plot.Role role) {
        textInput(player, role == Plot.Role.ADMIN ? Message.PLOT_MENU_ADMIN : Message.PLOT_MENU_COLLABORATOR,
                Message.PLOT_MENU_PLAYER, "", 36, false,
                (viewer, value) -> perform(viewer, () -> invite(viewer, plotId.toString(), value.strip(), role)));
    }

    private void transferInput(Player player, UUID plotId) {
        textInput(player, Message.PLOT_MENU_TRANSFER, Message.PLOT_MENU_PLAYER,
                "", 36, false, (viewer, value) -> {
                    try {
                        confirmTransfer(viewer, plotId,
                                PlotCreationController.managedPlayer(value));
                    } catch (PlotProblem error) {
                        send(viewer, NamedTextColor.RED, error.message(), error.args());
                    }
                });
    }

    private void transferToMember(Player player, UUID plotId, UUID memberId) {
        try {
            confirmTransfer(player, plotId,
                    PlotCreationController.managedPlayer(memberId.toString()));
        } catch (PlotProblem error) {
            send(player, NamedTextColor.RED, error.message(), error.args());
        }
    }

    private void confirmTransfer(Player player, UUID plotId, PlotOwner successor) {
        Plot plot = registry.byId(plotId);
        if (plot == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (!permitted(player, plot, PlotPermission.TRANSFER))
            throw new PlotProblem(Message.PLOT_ERROR_TRANSFER_OWNER_ONLY);
        if (plot.owner().equals(successor.id()))
            throw new PlotProblem(Message.PLOT_ERROR_TRANSFER_SELF);
        MenuDialogs.openConfirm(plugin, player,
                Component.text(t(player, Message.PLOT_MENU_TRANSFER), NamedTextColor.GOLD),
                Component.text(t(player, plot.subPlot()
                                ? Message.PLOT_MENU_CONFIRM_SUBPLOT_TRANSFER
                                : Message.PLOT_MENU_CONFIRM_TRANSFER,
                        plot.name(), successor.name()), NamedTextColor.GRAY),
                Component.text(t(player, Message.PLOT_MENU_TRANSFER), NamedTextColor.GOLD),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY),
                viewer -> {
                    perform(viewer, () -> transfer(viewer, plotId, successor));
                    openMenu(viewer);
                });
    }

    private void textInput(Player player, Message title, Message label, String initial,
                           int maximumLength, boolean multiline,
                           java.util.function.BiConsumer<Player, String> submit) {
        MenuDialogs.openTextInput(plugin, player,
                Component.text(t(player, title), NamedTextColor.GOLD),
                Component.text(t(player, label), NamedTextColor.GRAY),
                initial, maximumLength, multiline,
                Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN),
                Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GRAY), submit);
    }

    private void execute(Player player, String input) {
        perform(player, () -> executeCommand(player, input));
    }

    private void executeCommand(Player player, String input) throws SQLException {
        String[] args = input.trim().split("\\s+", 4);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> usage(player);
            case "pos1" -> mark(player, true, Message.PLOT_POS1);
            case "pos2" -> mark(player, false, Message.PLOT_POS2);
            case "edit" -> {
                if (args.length > 2) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
                if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
                    selectionController.stop(player.getUniqueId());
                    selectionStopped(player);
                    return;
                }
                UUID editingPlot = selectionController.plotId(player.getUniqueId());
                if (args.length == 1 && editingPlot == null && selectionController.mode(player.getUniqueId()) != null) {
                    openPlotEditor(player, null);
                    return;
                }
                Plot target = args.length == 2 ? managed(player, args[1]) : editingPlot == null
                        ? registry.at(player.getWorld().getUID(), player.getLocation().getBlockX(),
                                player.getLocation().getBlockY(), player.getLocation().getBlockZ())
                        : registry.byId(editingPlot);
                if (target == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
                openPlotEditor(player, target.id());
            }
            case "create" -> creation.create(player, tail(input, "create"));
            case "createfor" -> {
                requireModerator(player);
                String raw = input.substring(args[0].length()).trim();
                int separator = raw.indexOf(' ');
                if (separator < 1) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
                createFor(player, PlotCreationController.managedPlayer(raw.substring(0, separator)),
                        raw.substring(separator + 1).strip());
            }
            case "invite" -> invite(player, arg(args, 1), arg(args, 2), roleArg(args, 3));
            case "remove" -> removeMember(player, arg(args, 1), arg(args, 2));
            case "transfer" -> confirmTransfer(player,
                    managed(player, arg(args, 1)).id(),
                    PlotCreationController.managedPlayer(arg(args, 2)));
            case "flag" -> flag(player, arg(args, 1), arg(args, 2), booleanArg(args, 3));
            case "public" -> setPublic(player, arg(args, 1), booleanArg(args, 2));
            case "rename" -> rename(player, arg(args, 1),
                    arg(args, 2) + (args.length > 3 ? " " + args[3] : ""));
            case "delete" -> confirmRelease(player, arg(args, 1));
            case "object" -> board.postObjection(player, input.substring(args[0].length()).trim());
            case "withdraw" -> board.withdraw(player, input.substring(args[0].length()).trim());
            case "rule" -> board.ruleRaw(player, input.substring(args[0].length()).trim());
            case "list" -> list(player);
            case "board" -> {
                String id = input.substring(args[0].length()).trim();
                if (id.isBlank()) board.openMenu(player);
                else board.show(player, id);
            }
            default -> usage(player);
        }
    }

    private void perform(Player player, PlotAction action) {
        try {
            action.run();
        } catch (PlotProblem error) {
            send(player, NamedTextColor.RED, error.message(), error.args());
        } catch (IllegalArgumentException | SQLException error) {
            plugin.getLogger().log(Level.WARNING, "Plot action failed", error);
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_STORAGE);
        }
    }

    private <Result> void persist(Player player, PlotEdits.Mutation<Result> mutation,
                                  java.util.function.Consumer<Result> success) throws SQLException {
        PlotWriteFeedback.finish(plugin, language, registry, player, edits.writeBehind(mutation), success);
    }

    private <Result> void persistGeometry(Player player, PlotEdits.Mutation<Result> mutation,
                                          java.util.function.Consumer<Result> success) throws SQLException {
        PlotWriteFeedback.finish(plugin, language, registry, player, edits.writeAsync(mutation), success);
    }

    @FunctionalInterface
    private interface PlotAction {
        void run() throws SQLException;
    }

    private void createFor(Player player, PlotOwner target, String name) throws SQLException {
        requireModerator(player);
        creation.createFor(player, target, name);
    }

    private static void requireModerator(Player player) {
        if (!RolePermissions.canModerate(player))
            throw new PlotProblem(Message.PLOT_ERROR_STAFF_ONLY);
    }

    private void usage(Player player) {
        openGuide(player);
    }

    private void openGuide(Player player) {
        FloatingMenuDefinition.Builder menu = PlotMenuLayouts.screen("plot-guide",
                FloatingMenuAppearance.ARCHIVE, PlotMenuLayouts.help());
        menu.information("heading", Component.text(t(player, Message.PLOT_HELP),
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("instructions", Component.text(t(player, Message.PLOT_AREA_HINT), NamedTextColor.WHITE)
                        .append(Component.newline())
                        .append(Component.text(t(player, Message.PLOT_MODEL_LEGEND), NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text(t(player, Message.PLOT_SELECTION_SESSION_HINT), NamedTextColor.GRAY)))
                .region("instructions").textWidth(FloatingMenuTextWidth.WIDE);
        menu.back(Component.text(t(player, Message.PLOT_BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(player, menu.build());
    }

    private void mark(Player player, boolean first, Message label) {
        UUID playerId = player.getUniqueId();
        UUID world = player.getWorld().getUID();
        UUID contextId = selections.currentPlot(playerId);
        Plot context = contextId == null ? null : registry.byId(contextId);
        if (contextId != null && (context == null || !context.world().equals(world))) selections.unbind(playerId);
        PlotPosition point = new PlotPosition(world,
                player.getLocation().getBlockX(), player.getLocation().getBlockY(),
                player.getLocation().getBlockZ());
        selections.mark(playerId, first, point);
        send(player, NamedTextColor.GREEN, Message.PLOT_MARKED, t(player, label),
                format(point));
    }

    private void invite(Player player, String plotId, String guestName, Plot.Role role) {
        Plot plot = managed(player, plotId);
        if (!permitted(player, plot, PlotPermission.MANAGE_MEMBERS))
            throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
        PlotOwner target = PlotCreationController.managedPlayer(guestName);
        PlotMemberDraft draft = PlotMemberDraft.of(plot, target.id(), role);
        edits.planAccess(plot, draft.request());
        FloatingMenus.present(player, memberDraftDefinition(player, plot, draft, PlotPermission.Category.BUILD));
    }

    private void removeMember(Player player, String plotId, String guestName) throws SQLException {
        removeMember(player, managed(player, plotId).id(), playerId(guestName));
    }

    private void removeMember(Player player, UUID plotId, UUID memberId) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        submitAccessChange(player, plotId, new PlotAccessRequest.Remove(memberId), responder -> {
            send(responder, NamedTextColor.GREEN, Message.PLOT_MEMBER_REMOVED);
            notifyPlayer(memberId, Message.PLOT_ACCESS_REMOVED_FOR_YOU, plot.name());
            openPlotMembers(responder, plotId, 0);
        });
    }

    private void setMemberRole(Player player, UUID plotId, UUID memberId, Plot.Role role) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        if (!plot.members().containsKey(memberId)) throw new PlotProblem(Message.PLOT_ERROR_MEMBER_MISSING);
        submitAccessChange(player, plotId, new PlotAccessRequest.Member(memberId, role,
                PlotAccessPolicy.personalOverrides(plot, memberId)), responder -> {
            send(responder, NamedTextColor.GREEN, Message.PLOT_INVITED, t(responder, roleMessage(role)));
            notifyRole(memberId, plot.name(), role);
            openPlotMember(responder, plotId, memberId, 0);
        });
    }

    private void transfer(Player player, UUID plotId, PlotOwner successor) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        UUID actor = player.getUniqueId();
        persist(player, staged -> { staged.transfer(plot, actor, RolePermissions.canModerate(player), successor.id()); return null; }, ignored -> {
            send(player, NamedTextColor.GREEN, Message.PLOT_TRANSFERRED,
                    plot.name(), successor.name());
            notifyPlayer(successor.id(), Message.PLOT_TRANSFERRED_TO_YOU, plot.name(), player.getName());
            if (!plot.owner().equals(actor))
                notifyPlayer(plot.owner(), Message.PLOT_SUBPLOT_OWNER_CHANGED, plot.name(), successor.name());
            Plot parent = registry.parentOf(plot);
            if (parent != null && !parent.owner().equals(actor) && !parent.owner().equals(successor.id()))
                notifyPlayer(parent.owner(), Message.PLOT_SUBPLOT_OWNER_CHANGED, plot.name(), successor.name());
            for (Plot child : registry.childrenOf(plot.id())) {
                if (!child.owner().equals(successor.id()))
                    notifyPlayer(child.owner(), Message.PLOT_SUBPLOT_PARENT_TRANSFERRED, child.name(), successor.name());
            }
        });
    }

    private void notifyRole(UUID playerId, String plotName, Plot.Role role) {
        Player online = Bukkit.getPlayer(playerId);
        Language recipientLanguage = online == null
                ? language.preferredLanguage(playerId).orElse(Language.DEFAULT)
                : language.language(online);
        notifyPlayer(playerId, Message.PLOT_ROLE_CHANGED_FOR_YOU,
                plotName, language.t(recipientLanguage, roleMessage(role)));
    }

    private void notifyPlayer(UUID playerId, Message message, Object... args) {
        Player online = Bukkit.getPlayer(playerId);
        Language recipientLanguage = online == null
                ? language.preferredLanguage(playerId).orElse(Language.DEFAULT)
                : language.language(online);
        Object[] safeArgs = args.clone();
        for (int i = 0; i < safeArgs.length; i++) {
            if (safeArgs[i] instanceof String value)
                safeArgs[i] = PlotBoardNotifications.safeAlertText(value);
        }
        String notice = language.t(recipientLanguage, message, safeArgs);
        if (online != null)
            online.sendMessage(Component.text(notice, NamedTextColor.AQUA));
        try {
            notices.publish(playerId, online == null ? memberName(playerId) : online.getName(),
                    notice);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not send plot permission notice", error);
        }
    }

    private void flag(Player player, String plotId, String flag, boolean allowed) throws SQLException {
        String normalized = flag.toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('.');
        if (separator < 0) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        PlotAccessPolicy.Group group = PlotAccessPolicy.group(normalized.substring(0, separator));
        PlotPermission permission = PlotPermission.fromKey(normalized.substring(separator + 1));
        if (group == null || permission == null) throw new PlotProblem(Message.PLOT_ERROR_FLAG);
        Plot plot = managed(player, plotId);
        submitAccessChange(player, plot.id(), new PlotAccessRequest.Permission(
                PlotAccessPolicy.Subject.group(group), permission, allowed), responder ->
                send(responder, NamedTextColor.GREEN, Message.PLOT_FLAG_SET, normalized,
                        t(responder, allowed ? Message.PLOT_ALLOWED : Message.PLOT_DENIED)));
    }

    private void setPublic(Player player, String plotId, boolean value) throws SQLException {
        Plot plot = managed(player, plotId);
        persist(player, staged -> { staged.setPublic(plot, player.getUniqueId(), RolePermissions.canModerate(player), value); return null; },
                ignored -> send(player, NamedTextColor.GREEN, Message.PLOT_PUBLIC_SET,
                        t(player, value ? Message.PLOT_PUBLIC : Message.PLOT_PRIVATE)));
    }

    private void rename(Player player, String plotId, String name) throws SQLException {
        Plot plot = managed(player, plotId);
        persist(player, staged -> { staged.rename(plot, player.getUniqueId(), RolePermissions.canModerate(player), name); return null; },
                ignored -> send(player, NamedTextColor.GREEN, Message.PLOT_RENAMED));
    }

    private void delete(Player player, String plotId) throws SQLException {
        Plot plot = edits.releasable(player.getUniqueId(),
                RolePermissions.canModerate(player), plotId);
        Plot parent = registry.parentOf(plot);
        persistGeometry(player, staged -> { staged.delete(plot, player.getUniqueId(), RolePermissions.canModerate(player)); return null; }, ignored -> {
            selections.removePlot(plot.id());
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_DELETED);
            if (parent != null) {
                if (!plot.owner().equals(player.getUniqueId()))
                    notifyPlayer(plot.owner(), Message.PLOT_SUBPLOT_RELEASED, plot.name());
                if (!parent.owner().equals(player.getUniqueId()) && !parent.owner().equals(plot.owner()))
                    notifyPlayer(parent.owner(), Message.PLOT_SUBPLOT_RELEASED, plot.name());
            }
        });
    }

    private void list(Player player) {
        var location = player.getLocation();
        Plot here = registry.at(player.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (here == null) send(player, NamedTextColor.GRAY, Message.PLOT_NO_HERE);
        else send(player, NamedTextColor.GREEN, Message.PLOT_HERE, here.name(), here.id());
        for (Plot plot : registry.all()) {
            if (plot.owner().equals(player.getUniqueId()) || plot.members().containsKey(player.getUniqueId()))
                send(player, NamedTextColor.GRAY, Message.PLOT_LIST_ITEM, plot.name(), plot.id(),
                        registry.horizontalArea(plot));
        }
    }

    private Plot managed(Player player, String prefix) {
        return edits.managed(player.getUniqueId(), RolePermissions.canModerate(player), prefix);
    }

    private static UUID playerId(String name) {
        try { return UUID.fromString(name); }
        catch (IllegalArgumentException ignored) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) return player.getUniqueId();
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null && cached.getName() != null
                    && cached.getName().equalsIgnoreCase(name)) return cached.getUniqueId();
            throw new PlotProblem(Message.PLOT_ERROR_PLAYER_UNKNOWN);
        }
    }


    private PlotSelectionState.Submission selection(Player player) {
        return selections.submission(player.getUniqueId(), player.getWorld().getUID());
    }

    private static String arg(String[] args, int index) {
        if (index >= args.length || args[index].isBlank()) throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        return args[index];
    }

    private static boolean booleanArg(String[] args, int index) {
        String value = arg(args, index);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        return Boolean.parseBoolean(value);
    }

    private static Plot.Role roleArg(String[] args, int index) {
        return switch (arg(args, index).toLowerCase(Locale.ROOT)) {
            case "admin" -> Plot.Role.ADMIN;
            case "collaborator" -> Plot.Role.COLLABORATOR;
            default -> throw new PlotProblem(Message.PLOT_ERROR_ARGS);
        };
    }

    private static String tail(String input, String command) {
        String value = input.substring(command.length()).trim();
        PlotEdits.validName(value);
        return value;
    }

    private String t(Player player, Message message, Object... args) {
        return language.t(player, message, args);
    }

    private void send(Player player, NamedTextColor color, Message message, Object... args) {
        player.sendMessage(Component.text(t(player, message, args), color));
    }

    private static String format(PlotPosition point) {
        return point == null ? "—" : point.x() + ", " + point.y() + ", " + point.z();
    }
}
