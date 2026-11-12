package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.menu.MenuInputForm;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.FloatingMenuState;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiNode;
import org.encinet.mik.shared.menu.MenuPlayerSearch;
import org.encinet.mik.shared.menu.MenuDialogs;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.world.plot.integration.PlotAxiomHook;
import org.encinet.mik.feature.world.plot.integration.PlotFaweHook;
import org.encinet.mik.feature.world.plot.protection.PlotCommandGuard;
import org.encinet.mik.feature.world.plot.protection.PlotProtection;
import org.encinet.mik.shared.util.ShutdownSequence;
import org.encinet.mik.shared.util.PlayerNames;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import org.encinet.mik.shared.menu.ui.UiKey;

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
import java.util.logging.Level;

/** Owns plot operations and connects their spatial menus to the protected-world state. */
public final class PlotModule implements Listener {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRepository store;
    private final FloatingMenuScreen<PlotDirectory> directoryScreen;
    private final PlotRegistry registry;
    private final PlotEdits edits;
    private final PlotDestinationController destinations;
    private final PlotDestinationMenus destinationMenus;
    private final PlotNoticeMenus noticeMenus;
    private final PlotGuideMenu guideMenu;
    private final PlotAccessChoiceMenus accessChoices;
    private final PlotAtmosphereMenu atmosphereMenu;
    private final PlotAtmosphereController atmosphere;
    private final PlotCreationController creation;
    private final PlotProtection protection;
    private final PlotCommandGuard commandGuard;
    private final PlotSelectionState selections = new PlotSelectionState();
    private final PlotPreviewCache previewCache = new PlotPreviewCache();
    private final PlotPreflightCache preflightCache = new PlotPreflightCache(previewCache);
    private final PlotSpatialPreview spatialPreview = new PlotSpatialPreview(previewCache);
    private final PlotEditorMap editorMap = new PlotEditorMap(previewCache);
    private final PlotNativeMapViewer nativeMap = new PlotNativeMapViewer(previewCache);
    private final PlotSelectionController selectionController;
    private final PlotEditorMapControls editorControls;
    private final Map<UUID, String> playerNames = new HashMap<>();
    private PlotFaweHook fawe;
    private PlotAxiomHook axiom;
    private final Map<UUID, PlotOwner> creationOwners = new HashMap<>();

    public PlotModule(JavaPlugin plugin, LanguageService language) {
        this.plugin = plugin;
        this.language = language;
        store = new PlotRepository(PlotDataPaths.in(plugin.getDataFolder().toPath()).plotsDatabase());
        registry = new PlotRegistry(store);
        directoryScreen = new FloatingMenuScreen<>("plots",context -> directoryDefinition(context.player(),context.state()));
        edits = new PlotEdits(registry);
        destinations = new PlotDestinationController(plugin, language, registry);
        destinationMenus = new PlotDestinationMenus(plugin, language, registry, destinations);
        noticeMenus = new PlotNoticeMenus(id -> noticeBoard(registry.byId(id)), this::openPlotDetail, language);
        guideMenu = new PlotGuideMenu(language);
        accessChoices = new PlotAccessChoiceMenus(this::accessChoiceDefinition);
        atmosphereMenu = new PlotAtmosphereMenu(this::atmosphereDefinition);
        atmosphere = new PlotAtmosphereController(plugin, registry);
        creation = new PlotCreationController(plugin, language, registry, selections, this::createdPlot);
        protection = new PlotProtection(registry);
        commandGuard = new PlotCommandGuard(registry, language);
        selectionController = new PlotSelectionController(selections,
                this::editorAllowed, (player, plotId) -> preview(player, registry.byId(plotId)), this::selectionHint,
                this::selectionStopped,
                this::openPlotEditor,
                (player, problem) -> send(player, NamedTextColor.RED, problem.message(), problem.args()), this::stopPreview);
        editorControls = new PlotEditorMapControls(selections, editorMap, language, this::bindEditor,
                (player, plotId) -> editorDefinition(player, plotId == null ? null : registry.byId(plotId)),
                (player, failure) -> send(player, NamedTextColor.RED, failure.message(), failure.args()),
                this::openNativeMap);
    }

    public void enable() {
        try {
            store.open();
            registry.load();
            registry.enableAsyncWrites(action -> Bukkit.getScheduler().runTask(plugin, action));
            atmosphere.enable();
            Bukkit.getPluginManager().registerEvents(this, plugin);
            selectionController.enable(plugin);
            spatialPreview.enable(plugin);
            editorMap.enable(plugin);
            nativeMap.enable(plugin);
            Bukkit.getPluginManager().registerEvents(protection, plugin);
            Bukkit.getPluginManager().registerEvents(commandGuard, plugin);
            if (Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                fawe = new PlotFaweHook(registry);
                fawe.enable();
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
        editorMap.forget(player.getUniqueId());
        openPlotDetail(player, plotId);
    }

    public void disable() {
        destinations.close();
        registry.stopWrites();
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("plot notice menus", noticeMenus::close);
        shutdown.attempt("plot guide menu", guideMenu::close);
        shutdown.attempt("plot access choices", accessChoices::close);
        shutdown.attempt("plot atmosphere menu", atmosphereMenu::close);
        shutdown.attempt("plot destination menus", destinationMenus::close);
        shutdown.attempt("plot directory menus", directoryScreen::forgetAll);
        shutdown.attempt("plot atmosphere", atmosphere::disable);
        shutdown.attempt("plot selection", selectionController::disable);
        shutdown.attempt("plot world preview", spatialPreview::disable);
        shutdown.attempt("plot native map", nativeMap::disable);
        shutdown.attempt("plot editor map", editorMap::disable);
        selections.clearAll();
        shutdown.attempt("plot preflight", preflightCache::close);
        shutdown.attempt("plot preview worker", previewCache::close);
        playerNames.clear();
        creationOwners.clear();
        if (fawe != null) {
            shutdown.attempt("FastAsyncWorldEdit plot hook", fawe::disable);
            fawe = null;
        }
        if (axiom != null) {
            shutdown.attempt("Axiom plot hook", axiom::disable);
            axiom = null;
        }
        shutdown.attempt("plot protection", () -> HandlerList.unregisterAll(protection));
        shutdown.attempt("plot command guard", () -> HandlerList.unregisterAll(commandGuard));
        shutdown.attempt("plot listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("plot database", store::close);
        shutdown.finish("plot module");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        destinations.forget(playerId);
        destinationMenus.forget(playerId);
        noticeMenus.forget(event.getPlayer());
        guideMenu.forget(event.getPlayer());
        accessChoices.forget(event.getPlayer());
        atmosphereMenu.forget(event.getPlayer());
        directoryScreen.forget(playerId);
        selections.forget(playerId);
        spatialPreview.forget(playerId);
        nativeMap.forget(playerId);
        creationOwners.remove(playerId);
        editorMap.forget(playerId);
        preflightCache.forget(playerId);
    }

    /** Current protected plot, including its optional local notice. */
    public PlotNoticeBoard noticeBoardAt(Player player) {
        Location position = player.getLocation();
        Plot plot = registry.at(position.getWorld().getUID(), position.getBlockX(),
                position.getBlockY(), position.getBlockZ());
        return noticeBoard(plot);
    }

    private PlotNoticeBoard noticeBoard(Plot plot) {
        if (plot == null) return null;
        Plot parent = registry.parentOf(plot);
        return new PlotNoticeBoard(plot.id(), plot.name(), plot.owner(),
                memberName(plot.owner()), registry.noticeBoard(plot.id()),
                parent == null ? "" : parent.name());
    }

    public MenuPanel noticePanel(Player player) {
        return () -> {
            var notice = noticeBoardAt(player);
            if (notice == null) return MenuScene.screen("plot-notice-preview").build();
            var source = PlotNoticeContent.source(notice.plotId());
            return MenuScene.screen("plot-notice-preview").content(PlotNoticeContent.preview(notice,
                    language.text(player, Message.PLOT_NOTICE_READ, NamedTextColor.AQUA), source,
                    (viewer, handle) -> handle.openPopover(source, redraw -> new PlotNoticeReader(
                            () -> noticeBoard(registry.byId(notice.plotId())),
                            () -> language.text(viewer, Message.PLOT_NOTICE_BOARD, NamedTextColor.GOLD), redraw)))).build();
        };
    }

    /** Read-only authorization capability for world devices, including remote control. */
    public boolean mayControlMusic(Player player, org.bukkit.Location location) {
        return location.getWorld() != null && registry.allowed(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), player.getUniqueId(),
                org.encinet.mik.shared.role.RolePermissions.isMember(player),
                org.encinet.mik.shared.role.RolePermissions.canModerate(player), "music_control");
    }

    public void openCurrentPlot(Player player) {
        PlotNoticeBoard current = noticeBoardAt(player);
        if (current == null) openMenu(player);
        else openPlotDetail(player, current.plotId());
    }

    public void openNoticeBoard(Player player, UUID plotId) {
        noticeMenus.open(player, plotId);
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
        openMenu(player, directoryScreen.state(player).orElseGet(PlotDirectory::initial));
    }

    private void openMenu(Player player, PlotDirectory state) {
        selectionController.stop(player.getUniqueId());
        directoryScreen.open(player,state);
    }

    private MenuScene directoryDefinition(Player player, PlotDirectory state) {
        long revision = registry.revision();
        boolean member = RolePermissions.isMember(player);
        boolean staff = RolePermissions.canModerate(player);
        Plot here = registry.at(player.getWorld().getUID(), player.getLocation().getBlockX(),
                player.getLocation().getBlockY(), player.getLocation().getBlockZ());
        List<Plot> plots = state.entries(registry.all(), player.getUniqueId());
        var cards = plotCards(player,plots,"plot:",state);
        Component empty = Component.text(t(player,state.query().isEmpty()
                ? Message.PLOT_DIRECTORY_EMPTY : Message.PLOT_DIRECTORY_NO_MATCH),NamedTextColor.WHITE);
        MenuScene.Builder menu = PlotMenuLayouts.screen("plots", FloatingMenuAppearance.DEFAULT,
                PlotMenuLayouts.directory(PlotCardList.content(cards,empty,state.page(),index -> {
                    if (directoryCurrent(player,state) && registry.revision() == revision)
                        directoryScreen.update(player,_ -> state.page(index));
                })))
                .refreshEvery(10, (viewer, handle) -> {
                    if (!FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false)) return;
                    Plot current = registry.at(viewer.getWorld().getUID(), viewer.getLocation().getBlockX(),
                            viewer.getLocation().getBlockY(), viewer.getLocation().getBlockZ());
                    if (current != here || registry.revision() != revision || member != RolePermissions.isMember(viewer)
                            || staff != RolePermissions.canModerate(viewer))
                        directoryScreen.flow(viewer).filter(flow -> flow.state() == state).ifPresent(flow -> flow.redraw());
                    previewInMenu(viewer, current);
                });
        menu.information("heading", Component.text(t(player, Message.PLOT_TITLE), NamedTextColor.GOLD)).region("heading").keepAccessible();
        for (var plot : plots) menu.sharedElement(UiKey.named("plot:" + plot.id()),UiKey.named("plot:" + plot.id()));
        if (here != null) menu.navigation("here", Component.text(t(player, Message.PLOT_MENU_HERE), NamedTextColor.WHITE)
                        .append(Component.newline()).append(Component.text(here.name(), NamedTextColor.AQUA)))
                .region("here").primary((viewer, _) -> openPlotDetail(viewer, here.id()));
        for (var filter : PlotDirectory.Filter.values()) {
            int count = new PlotDirectory(filter, "", 0).entries(registry.all(), player.getUniqueId()).size();
            menu.control("filter:" + filter, Component.text(t(player, filter.label, count), NamedTextColor.AQUA))
                    .region("filters").selected(state.filter() == filter)
                    .primary((viewer, _) -> {
                        if (directoryCurrent(viewer,state)) directoryScreen.update(viewer,_ -> state.filter(filter));
                    });
        }
        menu.control("search", Component.text(t(player, state.query().isEmpty()
                        ? Message.PLOT_DIRECTORY_SEARCH : Message.PLOT_DIRECTORY_SEARCH_VALUE, state.query()), NamedTextColor.YELLOW))
                .region("search").primary((viewer, _) -> textInput(viewer, Message.PLOT_DIRECTORY_SEARCH,
                        Message.PLOT_DIRECTORY_SEARCH_FIELD, state.query(), 64, false,
                        (responder, value) -> {
                            if (directoryScreen.flow(responder).filter(flow -> flow.state() == state
                                    && flow.handle().state() != FloatingMenuState.CLOSING).isPresent())
                                openMenu(responder,state.query(value));
                        }));
        if (!state.query().isEmpty()) menu.control("clear-search", Component.text(t(player, Message.PLOT_DIRECTORY_CLEAR_SEARCH), NamedTextColor.WHITE))
                .region("search").primary((viewer, _) -> {
                    if (directoryCurrent(viewer,state)) directoryScreen.update(viewer,_ -> state.query(""));
                });
        var create = menu.control("create", Component.text(t(player, Message.PLOT_MENU_CREATE), NamedTextColor.GREEN))
                .region("actions")
                .primary((viewer, _) -> beginCreate(viewer, new PlotOwner(viewer.getUniqueId(), viewer.getName())));
        if (!RolePermissions.isMember(player))
            create.disabled(Component.text(t(player, Message.PLOT_ERROR_MEMBER_REQUIRED)));
        if (RolePermissions.canModerate(player))
            menu.control("manage", Component.text(t(player, Message.PLOT_MENU_MANAGE), NamedTextColor.AQUA))
                    .region("actions")
                    .primary((p, _) -> FloatingMenus.search(p, MenuPlayerSearch.builder(
                            Component.text(t(p, Message.PLOT_MENU_MANAGE), NamedTextColor.GOLD),
                            Component.text(t(p, Message.PLOT_MENU_PLAYER), NamedTextColor.AQUA))
                            .find(query -> java.util.concurrent.CompletableFuture.completedFuture(MenuPlayerSearch.matching(query,MenuPlayerSearch.cached(p,query))))
                            .selected((viewer, target) -> {
                                try { openManagedPlots(viewer, PlotCreationController.managedPlayer(target.id().toString()), 0); }
                                catch (PlotProblem error) {
                                    send(viewer, NamedTextColor.RED, error.message(), error.args());
                                }
                            }).build()));
        menu.navigation("help", Component.text(t(player, Message.PLOT_HELP), NamedTextColor.YELLOW))
                .region("navigation").primary((viewer, _) -> openGuide(viewer));
        menu.dismiss(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    public MenuPanel publicDestinationsPanel(Player player, Runnable redraw) {
        return destinationMenus.publicPanel(player, redraw);
    }

    public long destinationsRevision() { return registry.revision(); }

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
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-manage",
                FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.browser(PlotCardList.content(
                        plotCards(staff,plots,"plot:",null),Component.text(t(staff,Message.PLOT_MENU_MANAGED_EMPTY),NamedTextColor.WHITE),
                        requestedPage,index -> { if (RolePermissions.canModerate(staff)) openManagedPlots(staff,target,index); })));
        menu.information("heading", Component.text(t(staff, Message.PLOT_MENU_MANAGED_TITLE,
                target.name()), NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.control("create", Component.text(t(staff, Message.PLOT_MENU_CREATE_FOR_PLAYER),
                                NamedTextColor.GREEN))
                .region("actions")
                .primary((viewer, _) -> beginCreate(viewer, target));
        menu.back(Component.text(t(staff, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(staff, menu.build());
    }

    private boolean directoryCurrent(Player player,PlotDirectory expected) {
        return directoryScreen.flow(player).filter(flow -> flow.state() == expected
                && (flow.handle().state() == FloatingMenuState.ACTIVE
                    || flow.handle().state() == FloatingMenuState.OPENING)).isPresent();
    }

    private List<PlotCardList.Card> plotCards(Player player,List<Plot> plots,String prefix,PlotDirectory directory) {
        return plots.stream().map(plot -> new PlotCardList.Card(UiKey.named(prefix + plot.id()),
                plotCard(player,plot),(viewer,_) -> {
                    if ((directory == null || directoryCurrent(viewer,directory)) && registry.byId(plot.id()) == plot)
                        openPlotDetail(viewer,plot.id());
                })).toList();
    }

    private Component plotCard(Player player, Plot plot) {
        Plot parent = registry.parentOf(plot);
        Component label = Component.text(plot.name(), NamedTextColor.AQUA);
        if (parent != null) label = label.append(Component.newline()).append(Component.text(
                t(player, Message.PLOT_MENU_SUBPLOT_OF, parent.name()), NamedTextColor.WHITE));
        return label.append(Component.newline()).append(Component.text(t(player, Message.PLOT_MENU_OWNER,
                        memberName(plot.owner())), NamedTextColor.WHITE))
                .append(Component.newline()).append(Component.text(t(player, Message.PLOT_ITEM_SUMMARY,
                        registry.horizontalArea(plot), PlotRoster.size(plot)), NamedTextColor.WHITE));
    }

    private void preview(Player player, Plot plot) {
        PlotSelectionState.Points points = selections.points(player.getUniqueId(), plot == null ? null : plot.id());
        PlotSelectionShape shape = selections.draft(player.getUniqueId(), plot == null ? null : plot.id()).base().candidate();
        spatialPreview.show(player, plot, points.first(), points.second(), shape);
        if (selectionController.mode(player.getUniqueId()) != null
                && FloatingMenus.current(player).isEmpty()
                && player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
            nativeMap.show(player, editorMap.scene(player, plot,
                    selections.draft(player.getUniqueId(), plot == null ? null : plot.id())));
        } else nativeMap.hide(player.getUniqueId());
    }

    private void stopPreview(UUID player) {
        spatialPreview.forget(player);
        nativeMap.forget(player);
        editorMap.release(player);
    }

    private void previewInMenu(Player player, Plot plot) {
        if (!selectionController.ownsPreview(player.getUniqueId(), plot == null ? null : plot.id()))
            preview(player, plot);
    }

    private void openPlotDetail(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) { openMenu(player); return; }
        selectionController.keepProject(player.getUniqueId(), plotId);
        FloatingMenus.present(player, detailDefinition(player, plot));
    }

    private MenuScene detailDefinition(Player player, Plot plot) {
        return detailDefinition(player,plot,UiReadingPosition.START);
    }

    private MenuScene detailDefinition(Player player,Plot plot,UiReadingPosition position) {
        UUID plotId = plot.id();
        long revision = registry.revision();
        UUID viewerWorld = player.getWorld().getUID();
        String role = myRole(player, registry.standing(plot, player.getUniqueId(), RolePermissions.canModerate(player)));
        List<PlotWorkspace.Task> tasks = workspaceTasks(player, plot);
        var menu = PlotMenuLayouts.screen("plot-detail:" + plotId, FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.workspace(position,next -> {
                    if (registry.revision() == revision) FloatingMenus.current(player).ifPresent(handle ->
                            handle.update(detailDefinition(player,plot,next)));
                }))
                .refreshEvery(10, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) { handle.back(); return; }
                    if (registry.revision() != revision || !viewerWorld.equals(viewer.getWorld().getUID()) || !tasks.equals(workspaceTasks(viewer, fresh))
                            || !role.equals(myRole(viewer, registry.standing(fresh, viewer.getUniqueId(), RolePermissions.canModerate(viewer)))))
                        handle.update(detailDefinition(viewer, fresh,position));
                    if (FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false)) previewInMenu(viewer, fresh);
                });
        Plot parent = registry.parentOf(plot);
        if (parent != null) menu.navigation("parent", Component.text(t(player, Message.PLOT_MENU_SUBPLOT_PARENT, parent.name()), NamedTextColor.AQUA))
                .region("context").primary((viewer, _) -> openPlotDetail(viewer, parent.id()));
        menu.information("heading", Component.text(plot.name(), NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.sharedElement(UiKey.named("plot:" + plotId),UiKey.named("heading"));
        menu.information("summary", Component.text(t(player, Message.PLOT_MENU_OWNER, memberName(plot.owner())), NamedTextColor.WHITE)
                        .append(Component.newline()).append(Component.text(t(player, Message.PLOT_ITEM_SUMMARY,
                                registry.horizontalArea(plot), PlotRoster.size(plot)), NamedTextColor.WHITE))
                        .append(Component.newline()).append(Component.text(t(player, Message.PLOT_WORKSPACE_ROLE, role), NamedTextColor.AQUA)))
                .region("summary").textWidth(FloatingMenuTextWidth.WIDE);
        String notice = registry.noticeBoard(plotId);
        if (!notice.isEmpty()) menu.control("notice", Component.text(t(player, Message.PLOT_NOTICE_BOARD), NamedTextColor.GOLD)
                        .append(Component.newline()).append(Component.text(PlotNoticeText.preview(notice), NamedTextColor.WHITE))
                        .append(Component.newline()).append(Component.text(t(player, Message.PLOT_NOTICE_READ), NamedTextColor.AQUA)))
                .region("notice").textWidth(FloatingMenuTextWidth.WIDE).primary((viewer, _) -> openNoticeBoard(viewer, plotId));
        for (var task : tasks) {
            var action = menu.navigation("task:" + task, Component.text(t(player, task.label), NamedTextColor.AQUA)
                        .append(Component.newline()).append(Component.text(t(player, task.hint), NamedTextColor.WHITE)))
                .region("tasks").textWidth(FloatingMenuTextWidth.WIDE).primary((viewer, _) -> {
                    switch (task) {
                        case DESTINATIONS -> destinationMenus.open(viewer, plotId, 0);
                        case AREA -> beginAreaEdit(viewer, plotId);
                        case SUBPLOTS -> openSubPlots(viewer, plotId, 0);
                        case ACCESS -> openPlotMembers(viewer, plotId, 0);
                        case SETTINGS -> openPlotSettings(viewer, plotId);
                    }
                });
            if (task == PlotWorkspace.Task.AREA && !plot.world().equals(viewerWorld))
                action.disabled(Component.text(t(player, Message.PLOT_WORKSPACE_OTHER_WORLD), NamedTextColor.WHITE));
        }
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN)).region("navigation").keepAccessible();
        return menu.build();
    }

    private List<PlotWorkspace.Task> workspaceTasks(Player player, Plot plot) {
        return PlotWorkspace.tasks(plot, permission -> permitted(player, plot, permission),
                registry.destinations(plot.id()).stream().anyMatch(point -> destinations.canUse(player, point)),
                !registry.childrenOf(plot.id()).isEmpty());
    }

    private boolean hasPlotSettings(Player player, Plot plot) {
        return permitted(player, plot, PlotPermission.MANAGE_SETTINGS) || permitted(player, plot, PlotPermission.TRANSFER)
                || permitted(player, plot, PlotPermission.DELETE);
    }

    private void openPlotSettings(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) { openMenu(player); return; }
        if (!hasPlotSettings(player, plot)) { openPlotDetail(player, plotId); return; }
        FloatingMenus.present(player, settingsDefinition(player, plot));
    }

    private MenuScene settingsDefinition(Player player, Plot plot) {
        return settingsDefinition(player,plot,0);
    }

    private MenuScene settingsDefinition(Player player,Plot plot,int page) {
        UUID plotId = plot.id();
        long revision = registry.revision();
        var menu = PlotMenuLayouts.screen("plot-settings:" + plotId, FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.settings(
                permitted(player,plot,PlotPermission.MANAGE_SETTINGS),page,index -> {
                    if (registry.revision() == revision) FloatingMenus.current(player).ifPresent(handle ->
                            handle.update(settingsDefinition(player,plot,index)));
                }))
                .refreshEvery(10, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null || !hasPlotSettings(viewer, fresh)) { handle.back(); return; }
                    if (registry.revision() != revision) handle.update(settingsDefinition(viewer, fresh,page));
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, Message.PLOT_MENU_SETTINGS), NamedTextColor.GOLD)).region("heading");
        if (permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) {
            menu.control("rename", Component.text(t(player, Message.PLOT_MENU_RENAME), NamedTextColor.YELLOW))
                    .region("settings").primary((viewer, _) -> textInput(viewer, Message.PLOT_MENU_RENAME,
                            Message.PLOT_MENU_NAME, plot.name(), 48, false, (responder, value) -> {
                                perform(responder, () -> rename(responder, plotId.toString(), value.strip()));
                                openPlotSettings(responder, plotId);
                            }));
            menu.control("notice-edit", Component.text(t(player, Message.PLOT_NOTICE_EDIT), NamedTextColor.YELLOW))
                    .region("settings").primary((viewer, _) -> editNoticeBoard(viewer, plotId));
            menu.navigation("atmosphere", atmosphereLabel(player, plot, registry.atmosphere(plotId)))
                    .region("settings").primary((viewer, _) -> openPlotAtmosphere(viewer, plotId));
        }
        if (permitted(player, plot, PlotPermission.TRANSFER) || permitted(player, plot, PlotPermission.DELETE)) {
            menu.information("ownership-heading", Component.text(t(player, Message.PLOT_WORKSPACE_OWNERSHIP), NamedTextColor.GOLD)).region("ownership-heading");
            menu.information("owner", Component.text(t(player, Message.PLOT_MENU_OWNER, memberName(plot.owner())), NamedTextColor.WHITE)).region("owner");
        }
        if (permitted(player, plot, PlotPermission.TRANSFER)) menu.navigation("transfer", Component.text(t(player, Message.PLOT_MENU_TRANSFER), NamedTextColor.GOLD))
                .region("ownership").primary((viewer, _) -> transferInput(viewer, plotId));
        addReleaseAction(player, menu, plot);
        menu.information("identifier", Component.text(t(player, Message.PLOT_WORKSPACE_ID, plotId.toString()), NamedTextColor.WHITE)).region("identifier");
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN)).region("navigation").keepAccessible();
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
                throw new PlotProblem(Message.PLOT_WORKSPACE_OTHER_WORLD);
            selectionController.stop(player.getUniqueId());
            selections.bindContext(player.getUniqueId(), context, plot.world());
            editorMap.forget(player.getUniqueId());
            openPlotEditor(player, plot.id());
        });
    }

    private void addSubPlotCreateAction(Player player, MenuScene.Builder menu, Plot parent,
                                       String region) {
        if (parent.subPlot()) return;
        var create = menu.control("create-child", Component.text(t(player, Message.PLOT_PERMISSION_CREATE_SUBPLOT), NamedTextColor.GREEN))
                .region(region).primary((viewer, _) -> beginSubPlot(viewer, parent.id()));
        if (!permitted(player, parent, PlotPermission.CREATE_SUBPLOT))
            create.disabled(Component.text(t(player, Message.PLOT_ERROR_OWNER_ONLY)));
        else if (!parent.world().equals(player.getWorld().getUID()))
            create.disabled(Component.text(t(player, Message.PLOT_WORKSPACE_OTHER_WORLD)));
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
            throw new PlotProblem(Message.PLOT_WORKSPACE_OTHER_WORLD);
        if (!editorAllowed(player, plotId)) throw new PlotProblem(plotId == null
                ? Message.PLOT_ERROR_MEMBER_REQUIRED : Message.PLOT_ERROR_OWNER_ONLY);
        selections.resume(player.getUniqueId(), plotId, player.getWorld().getUID());
    }

    private MenuScene editorDefinition(Player player, Plot plot) {
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
        PlotEditorMap.Scene scene = editorMap.scene(player, plot, draft);
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-editor:" + context,
                        FloatingMenuAppearance.DEFAULT, PlotEditorComposition.create(editorControls.map(player, plotId, context, world, draft, scene),
                                regionList(player,plotId,draft,scene.regions())))
                .viewport(PlotEditorComposition.VIEWPORT)
                .lifecycle((viewer, _, _, after, _) -> {
                    if (after == FloatingMenuState.ACTIVE) nativeMap.hide(viewer.getUniqueId());
                    if (after == FloatingMenuState.SUSPENDED
                            || after == FloatingMenuState.CLOSING
                            || after == FloatingMenuState.CLOSED)
                        editorMap.release(viewer.getUniqueId());
                })
                .refreshEvery(10, (viewer, handle) -> {
                    if (!FloatingMenus.current(viewer).map(current -> current.id().equals(handle.id())).orElse(false))
                        return;
                    if (!context.equals(selections.context(viewer.getUniqueId())) || !editorAllowed(viewer, plotId)) {
                        handle.close();
                        selectionController.stop(viewer.getUniqueId());
                        return;
                    }
                    Plot fresh = plotId == null ? null : registry.byId(plotId);
                    PlotEditorMap.Scene currentScene = world.equals(viewer.getWorld().getUID())
                            ? editorMap.scene(viewer, fresh, selections.draft(viewer.getUniqueId(), plotId)) : null;
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
        PlotOwner creatingOwner = creationOwners.getOrDefault(player.getUniqueId(), new PlotOwner(player.getUniqueId(), player.getName()));
        menu.information("context", Component.text(plot == null ? t(player, Message.PLOT_MENU_OWNER, creatingOwner.name()) : plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, plot == null ? Message.PLOT_MENU_CREATE
                        : context.subPlot() ? Message.PLOT_PERMISSION_CREATE_SUBPLOT : Message.PLOT_WORKSPACE_AREA), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        menu.information("selection", selectionSummary(player, plot, failure))
                .region("selection").textWidth(FloatingMenuTextWidth.WIDE);
        Component status = editorMapHint(player, scene);
        if (!status.equals(Component.empty()))
            menu.information("hint", status).region("hint").textWidth(FloatingMenuTextWidth.WIDE);
        menu.information("legend", Component.text(t(player, Message.PLOT_MAP_LEGEND), NamedTextColor.WHITE))
                .region("legend").textWidth(FloatingMenuTextWidth.WIDE);
        if (scene.regions().open()) {
            regionControls(player, menu, plotId, draft, scene.regions());
        } else {
            var regions = menu.navigation("region-manager", Component.text(t(player, Message.PLOT_REGION_OPEN,
                            scene.regions().items().size()), NamedTextColor.AQUA))
                    .region("points").primary((viewer, _) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        editorMap.openRegions(viewer, plotId, true);
                        openPlotEditor(viewer, plotId);
                    }));
            if (scene.regions().items().isEmpty())
                regions.disabled(Component.text(t(player, Message.PLOT_SELECTION_INCOMPLETE)));
        }
        editorControls.add(player, menu, plotId, context, world, draft, scene);
        if (!scene.regions().open()) {
            for (PlotSelectionShape.Operation operation : List.of(
                    PlotSelectionShape.Operation.ADD, PlotSelectionShape.Operation.SUBTRACT)) {
                Message label = switch (operation) {
                    case ADD -> Message.PLOT_SELECTION_ADD;
                    case SUBTRACT -> Message.PLOT_SELECTION_SUBTRACT;
                    case REPLACE -> Message.PLOT_SELECTION_REPLACE;
                };
                var action = menu.control("compose:" + operation, Component.text(t(player, label), NamedTextColor.LIGHT_PURPLE))
                        .region("composition").primary((viewer, _) -> perform(viewer, () -> {
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
                var create = menu.control("create-child", Component.text(t(player, Message.PLOT_PERMISSION_CREATE_SUBPLOT), NamedTextColor.GREEN))
                        .region("commit").primary((viewer, _) -> perform(viewer,
                                () -> createSubPlotInput(viewer, plotId)));
                if (preflight == null) create.disabled(Component.text(t(player, Message.PLOT_SELECTION_VALIDATING)));
                else if (failure != null) create.disabled(problemLabel(player, failure));
            }
        }
        if (!scene.regions().open() || scene.regions().editing()) {
            if (plot != null && !context.subPlot() && !scene.regions().editing() && !draft.history().isEmpty())
                menu.control("fit-selection", Component.text(t(player, Message.PLOT_SELECTION_FIT), NamedTextColor.AQUA))
                    .region("points").primary((viewer, _) -> perform(viewer, () -> {
                        fitSelection(viewer, plotId);
                        openPlotEditor(viewer, plotId);
                    }));
            menu.control("clear-selection", Component.text(t(player,
                            Message.PLOT_SELECTION_CLEAR), NamedTextColor.WHITE))
                    .region("points").primary((viewer, _) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.discardBrush(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    }));
        }
        if (!scene.regions().editing() && !context.subPlot()) {
            boolean replacing = draft.base().shape() != null
                    && points.first() != null && points.second() != null;
            var save = menu.control("save-area", Component.text(t(player,
                            plot == null ? Message.PLOT_MENU_CREATE : replacing
                                    ? Message.PLOT_AREA_REPLACE_SAVE : Message.PLOT_AREA_SAVE), NamedTextColor.GREEN))
                    .region("commit").primary((viewer, _) -> perform(viewer,
                            () -> confirmSaveArea(viewer, plotId)));
            if (failure != null) save.disabled(problemLabel(player, failure));
            if (preflight == null) save.disabled(Component.text(t(player, Message.PLOT_SELECTION_VALIDATING)));
            if (plot != null && !permitted(player, plot, PlotPermission.MANAGE_AREA))
                save.disabled(Component.text(t(player, Message.PLOT_ERROR_SUBPLOT_OWNER)));
        }
        if (!draft.history().isEmpty()) menu.control("undo-selection", Component.text(t(player,
                        Message.PLOT_SELECTION_UNDO), NamedTextColor.YELLOW))
                .region("history").primary((viewer, _) -> perform(viewer, () -> {
                    bindEditor(viewer, plotId);
                    selections.undo(viewer.getUniqueId());
                    openPlotEditor(viewer, plotId);
                }));
        if (!draft.future().isEmpty()) menu.control("redo-selection", Component.text(t(player,
                        Message.PLOT_SELECTION_REDO), NamedTextColor.YELLOW))
                .region("history").primary((viewer, _) -> perform(viewer, () -> {
                    bindEditor(viewer, plotId);
                    selections.redo(viewer.getUniqueId());
                    openPlotEditor(viewer, plotId);
                }));
        menu.navigation("back", Component.text(t(player, scene.regions().editing() ? Message.PLOT_REGION_CANCEL : Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible().primary((viewer, _) -> perform(viewer, () -> {
                    if (scene.regions().editing()) {
                        bindEditor(viewer, plotId);
                        selections.requireRevision(viewer.getUniqueId(), viewer.getWorld().getUID(), draft.revision());
                        selections.cancelRegionEdit(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    } else if (scene.regions().open()) {
                        editorMap.openRegions(viewer, plotId, false);
                        openPlotEditor(viewer, plotId);
                    } else {
                        selectionController.stop(viewer.getUniqueId());
                        viewer.sendActionBar(Component.empty());
                        if (plotId == null) openMenu(viewer);
                        else openPlotDetail(viewer, plotId);
                    }
                }));
        return menu.build();
    }

    private UiNode regionList(Player player, UUID plotId, PlotSelectionState.Draft draft, PlotEditorMap.Regions regions) {
        return PlotRegionList.content(regions,index -> {
            var region = regions.items().get(index);
            return PlotRegionList.row(region,regionLabel(region,index+1,false),region.equals(regions.selected()),
                    (viewer,_) -> perform(viewer,() -> {
                        bindEditor(viewer,plotId);
                        selections.requireRegion(viewer.getUniqueId(),viewer.getWorld().getUID(),region,draft.revision());
                        editorMap.selectRegion(viewer,plotId,region);
                        openPlotEditor(viewer,plotId);
                    }));
        },index -> perform(player,() -> {
            bindEditor(player,plotId);
            selections.requireRevision(player.getUniqueId(),player.getWorld().getUID(),draft.revision());
            editorMap.regionPosition(player,plotId,index);
            openPlotEditor(player,plotId);
        }));
    }

    private void regionControls(Player player, MenuScene.Builder menu, UUID plotId,
                                PlotSelectionState.Draft draft, PlotEditorMap.Regions regions) {
        var selected = regions.selected();
        menu.information("region-heading", Component.text(regions.editing()
                ? t(player, Message.PLOT_REGION_ADJUST)
                : t(player, Message.PLOT_REGION_OPEN, regions.items().size()), NamedTextColor.GOLD))
                .region("region-heading");
        if (selected == null) {
            menu.information("region-selection", Component.text(t(player, Message.PLOT_REGION_SELECT_HINT),
                    NamedTextColor.WHITE)).region("region-selection");
            return;
        }
        menu.information("region-selection", regionLabel(selected, regions.selectedNumber(), true))
                .region("region-selection").textWidth(FloatingMenuTextWidth.WIDE);
        if (regions.editing()) {
            var apply = menu.control("region-apply", Component.text(t(player, Message.PLOT_REGION_APPLY), NamedTextColor.GREEN))
                    .region("region-actions").primary((viewer, _) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.requireRevision(viewer.getUniqueId(), viewer.getWorld().getUID(), draft.revision());
                        selections.finishRegionEdit(viewer.getUniqueId());
                        openPlotEditor(viewer, plotId);
                    }));
            if (draft.base().candidate() == null)
                apply.disabled(Component.text(t(player, Message.PLOT_SELECTION_INCOMPLETE)));
        } else {
            menu.control("region-adjust", Component.text(t(player, Message.PLOT_REGION_ADJUST), NamedTextColor.AQUA))
                    .region("region-actions").primary((viewer, _) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.beginRegionEdit(viewer.getUniqueId(), viewer.getWorld().getUID(), selected, draft.revision());
                        openPlotEditor(viewer, plotId);
                    }));
            menu.control("region-delete", Component.text(t(player, Message.PLOT_REGION_DELETE), NamedTextColor.RED))
                    .region("region-actions").primary((viewer, _) -> perform(viewer, () -> {
                        bindEditor(viewer, plotId);
                        selections.requireRegion(viewer.getUniqueId(), viewer.getWorld().getUID(), selected, draft.revision());
                        MenuDialogs.openConfirm(plugin, viewer,
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE), NamedTextColor.RED),
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE_CONFIRM, regions.selectedNumber()),
                                                NamedTextColor.WHITE).append(Component.newline())
                                        .append(regionLabel(selected, regions.selectedNumber(), true)),
                                Component.text(t(viewer, Message.PLOT_REGION_DELETE), NamedTextColor.RED),
                                Component.text(t(viewer, Message.BACK), NamedTextColor.WHITE), responder -> {
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
                        + bounds.maximumX() + ", " + bounds.maximumY() + ", " + bounds.maximumZ(), NamedTextColor.WHITE));
        return label;
    }

    private Component editorMapHint(Player player, PlotEditorMap.Scene scene) {
        List<Component> status = new ArrayList<>();
        if (scene.preparing()) status.add(Component.text(t(player, Message.PLOT_SELECTION_VALIDATING), NamedTextColor.WHITE));
        if (!scene.terrain().ready()) status.add(Component.text(t(player, Message.PLOT_MAP_PROGRESS,
                scene.terrain().progress()), NamedTextColor.WHITE));
        if (scene.terrain().unknown() > 0) status.add(Component.text(t(player, Message.PLOT_MAP_UNKNOWN,
                scene.terrain().unknown()), NamedTextColor.YELLOW));
        return Component.join(JoinConfiguration.newlines(), status);
    }

    private void openNativeMap(Player player, UUID plotId) {
        var scene = editorMap.scene(player, plotId == null ? null : registry.byId(plotId),
                selections.draft(player.getUniqueId(), plotId));
        selectionController.start(player, plotId, scene.regions().open() && !scene.regions().editing()
                ? PlotSelectionController.Mode.PREVIEW : PlotSelectionController.Mode.PICKING);
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
        if (preflight == null) throw new PlotProblem(Message.PLOT_SELECTION_VALIDATING);
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
                Component.text(t(player, Message.PLOT_AREA_CONFIRM, plot.name(), count), NamedTextColor.WHITE),
                Component.text(t(player, Message.PLOT_AREA_SAVE), NamedTextColor.GREEN),
                Component.text(t(player, Message.BACK), NamedTextColor.WHITE), viewer -> {
                    perform(viewer, () -> {
                        UUID actor = viewer.getUniqueId();
                        selections.requireSubmission(actor, viewer.getWorld().getUID(), submitted);
                        boolean staff = RolePermissions.canModerate(viewer);
                        persistGeometry(viewer, staged -> {
                            staged.resize(plot, actor, staff, submitted.shape());
                            return null;
                        }, _ -> {
                            boolean cleared = selections.clearIfMatches(actor, plotId, submitted);
                            atmosphere.refreshAll();
                            Plot updated = registry.byId(plotId);
                            send(viewer, NamedTextColor.GREEN, Message.PLOT_AREA_SAVED, updated.name());
                            if (cleared && selections.context(actor).equals(submitted.context())
                                    && viewer.isOnline() && viewer.getWorld().getUID().equals(plot.world())) {
                                selectionController.stop(actor);
                                editorMap.forget(actor);
                                openPlotDetail(viewer, plotId);
                            }
                        });
                    });
                });
    }

    private void selectionStopped(Player player) {
        player.sendActionBar(Component.empty());
        send(player, NamedTextColor.WHITE, Message.PLOT_SELECTION_STOPPED);
    }

    private void selectionHint(Player player) {
        UUID plotId = selectionController.plotId(player.getUniqueId());
        PlotSelectionState.Points points = selections.points(player.getUniqueId(), plotId);
        Component status = Component.text(PlotSelectionCommands.status(points), NamedTextColor.AQUA);
        var progress = nativeMap.progress(player.getUniqueId());
        if (progress.isPresent()) status = status.append(Component.text(" · " +
                t(player, Message.PLOT_MAP_PROGRESS, progress.getAsInt()), NamedTextColor.WHITE));
        player.sendActionBar(status);
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
            if (bounds == null) return Component.text(t(player, Message.PLOT_SELECTION_VALIDATING), NamedTextColor.WHITE);
            if (!shape.composite()) bounds = shape.alignedBounds();
            summary = Component.text(t(player, Message.PLOT_SELECTION_SIZE,
                    bounds.width(), bounds.height(), bounds.depth()), NamedTextColor.GOLD);
            summary = summary.append(Component.newline()).append(Component.text(
                    bounds.minimumX() + ", " + bounds.minimumY() + ", " + bounds.minimumZ()
                            + " → " + bounds.maximumX() + ", " + bounds.maximumY() + ", " + bounds.maximumZ(),
                    NamedTextColor.WHITE));
            if (failure != null && failure.message() != Message.PLOT_REGION_FINISH_FIRST)
                summary = summary.append(Component.newline()).append(problemLabel(player, failure));
        } catch (PlotProblem problem) {
            summary = Component.text(t(player, points.first() == null && points.second() == null
                    ? Message.PLOT_SELECTION_EMPTY : Message.PLOT_SELECTION_INCOMPLETE), NamedTextColor.WHITE);
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
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-subplots:" + parentId,
                FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.browser(PlotCardList.content(
                        plotCards(player,children,"child:",null),Component.text(t(player,Message.PLOT_DIRECTORY_EMPTY),NamedTextColor.WHITE),
                        requestedPage,index -> openSubPlots(player,parentId,index))))
                .refreshEvery(20, (viewer, _) -> {
                    Plot fresh = registry.byId(parentId);
                    if (fresh == null) openMenu(viewer);
                    else if (fresh != parent || !children.equals(registry.childrenOf(parentId))
                            || mayCreate != permitted(viewer, fresh, PlotPermission.CREATE_SUBPLOT)
                            || !world.equals(viewer.getWorld().getUID()))
                        openSubPlots(viewer, parentId, requestedPage);
                });
        menu.information("context", Component.text(parent.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, Message.PLOT_MENU_SUBPLOTS, children.size()), NamedTextColor.GOLD))
                .region("heading").keepAccessible();
        if (mayCreate) addSubPlotCreateAction(player, menu, parent, "actions");
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        FloatingMenus.present(player, menu.build());
    }

    private String myRole(Player player, PlotRegistry.Standing standing) {
        Message role = switch (standing) {
            case STAFF -> Message.PLOT_ROLE_SERVER_STAFF;
            case PARENT_OWNER -> Message.PLOT_ROLE_PARENT_OWNER;
            case OWNER -> Message.PLOT_ROLE_OWNER;
            case ADMIN -> Message.PLOT_ROLE_ADMIN;
            case COLLABORATOR -> Message.PLOT_ROLE_COLLABORATOR;
            case OUTSIDER -> RolePermissions.isMember(player)
                    ? Message.PLOT_MENU_GROUP_MEMBER : Message.PLOT_MENU_GROUP_NEWCOMER;
        };
        return t(player, role);
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
        if (preflight == null) throw new PlotProblem(Message.PLOT_SELECTION_VALIDATING);
        if (preflight.failure() != null) throw preflight.failure();
        selectionController.pause(player.getUniqueId());
        FloatingMenus.input(player, MenuInputForm.builder(Component.text(t(player, Message.PLOT_PERMISSION_CREATE_SUBPLOT), NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("name", Component.text(t(player, Message.PLOT_MENU_NAME), NamedTextColor.WHITE), "", 48))
                .field(MenuInputForm.Field.text("owner", Component.text(t(player, Message.PLOT_MENU_PLAYER), NamedTextColor.WHITE), memberName(parent.owner()), 36))
                .cancel(Component.text(t(player, Message.BACK), NamedTextColor.WHITE))
                .submit(Component.text(t(player, Message.PLOT_PERMISSION_CREATE_SUBPLOT), NamedTextColor.GREEN), (viewer, values) -> {
                    perform(viewer, () -> {
                        selections.requireSubmission(viewer.getUniqueId(), viewer.getWorld().getUID(), area);
                        openPlotEditor(viewer, parentId);
                        createSubPlot(viewer, parent, values.text("name").strip(), values.text("owner").strip(), area);
                    });
                }).build());
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
            if (cleared && selections.context(actor).equals(area.context()) && player.isOnline()
                    && player.getWorld().getUID().equals(parent.world())) {
                selectionController.stop(actor);
                editorMap.forget(actor);
                openPlotDetail(player, child.id());
            }
        });
    }

    private void editNoticeBoard(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_ID);
            return;
        }
        if (!permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        FloatingMenus.input(player, PlotNoticeForm.create(registry.noticeBoard(plotId),
                (message, args) -> Component.text(t(player, message, args), NamedTextColor.WHITE),
                viewer -> {
                    Plot current = registry.byId(plotId);
                    if (current == null) return Component.text(t(viewer, Message.PLOT_ERROR_ID), NamedTextColor.RED);
                    return permitted(viewer, current, PlotPermission.MANAGE_SETTINGS) ? null
                            : Component.text(t(viewer, Message.PLOT_ERROR_OWNER_ONLY), NamedTextColor.RED);
                }, (viewer, value) -> perform(viewer, () -> saveNoticeBoard(viewer, plotId, value))));
    }

    private void saveNoticeBoard(Player player, UUID plotId, String body) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> staged.setNoticeBoard(plot, player.getUniqueId(), RolePermissions.canModerate(player), body), saved -> {
            send(player, NamedTextColor.GREEN, saved.isEmpty()
                    ? Message.PLOT_NOTICE_CLEARED : Message.PLOT_NOTICE_SAVED);
            if (player.isOnline()) openPlotSettings(player, plotId);
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
                        NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(t(player, Message.PLOT_MENU_WEATHER) + " · " + weather,
                        NamedTextColor.WHITE));
    }

    private void openPlotAtmosphere(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            openPlotDetail(player, plotId);
            return;
        }
        atmosphereMenu.open(player, plotId);
    }

    private MenuScene.Builder atmosphereDefinition(FloatingMenuContext<PlotAtmosphereMenu.State> context) {
        Player player = context.player();
        var state = context.state();
        UUID plotId = state.plotId();
        Plot plot = registry.byId(plotId);
        if (plot == null || !permitted(player, plot, PlotPermission.MANAGE_SETTINGS)) {
            var failure = Ui.text(Component.text(t(player, plot == null ? Message.PLOT_ERROR_ID : Message.PLOT_ERROR_OWNER_ONLY),
                    NamedTextColor.WHITE)).key("unavailable").singleLine().build();
            var menu = PlotMenuLayouts.screen("plot-atmosphere:" + plotId, FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.help(failure));
            menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN)).region("navigation");
            return menu.screen(PlotAtmosphereMenu.SCREEN_ID);
        }
        PlotAtmosphere setting = registry.atmosphere(plotId);
        var locale = language.language(player);
        var actions = new PlotAtmosphereMenu.Actions(next -> atmosphereMenu.page(context, next),
                section -> atmosphereMenu.view(context, section), ticks -> {
                    if (!atmosphereMenu.current(player, state)) return;
                    perform(player, () -> setPlotTime(player, plotId, ticks));
                    atmosphereMenu.redraw(context);
                }, weather -> {
                    if (!atmosphereMenu.current(player, state)) return;
                    perform(player, () -> setPlotWeather(player, plotId, weather));
                    atmosphereMenu.redraw(context);
                }, () -> editPlotTime(player, state, setting.timeTicks() == null ? "" : PlotAtmosphere.clock(setting.timeTicks())));
        return PlotAtmosphereMenu.compose(language, locale, plot.name(), plot.subPlot(), setting, state, actions)
                .refreshEvery(10, (viewer, _) -> {
                    if (!atmosphereMenu.current(viewer, state)) return;
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null || !permitted(viewer, fresh, PlotPermission.MANAGE_SETTINGS)) context.back();
                    else if (fresh != plot || !setting.equals(registry.atmosphere(plotId)) || language.language(viewer) != locale) context.redraw();
                });
    }

    private void editPlotTime(Player player, PlotAtmosphereMenu.State source, String initial) {
        if (!atmosphereMenu.current(player, source)) return;
        FloatingMenus.input(player, PlotAtmosphereMenu.timeInput(language, language.language(player), initial, viewer -> {
            Plot plot = registry.byId(source.plotId());
            if (!atmosphereMenu.current(viewer, source) || plot == null || !permitted(viewer, plot, PlotPermission.MANAGE_SETTINGS))
                return Component.text(t(viewer, Message.PLOT_ERROR_OWNER_ONLY), NamedTextColor.RED);
            return null;
        }, (viewer, ticks) -> perform(viewer, () -> setPlotTime(viewer, source.plotId(), ticks))));
    }

    private void setPlotTime(Player player, UUID plotId, Integer ticks) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> { staged.setTime(plot, player.getUniqueId(), RolePermissions.canModerate(player), ticks); return null; }, _ -> {
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_ATMOSPHERE_SAVED);
        });
    }

    private void setPlotWeather(Player player, UUID plotId, PlotAtmosphere.Weather weather)
            throws SQLException {
        Plot plot = managed(player, plotId.toString());
        persist(player, staged -> { staged.setWeather(plot, player.getUniqueId(), RolePermissions.canModerate(player), weather); return null; }, _ -> {
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_ATMOSPHERE_SAVED);
        });
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
                .append(Component.newline()).append(Component.text(source, NamedTextColor.WHITE));
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
                Component.text(t(player, Message.BACK), NamedTextColor.WHITE),
                responder -> perform(responder, () -> commitAccessChange(responder, plan, after)));
    }

    private void commitAccessChange(Player player, PlotAccessPlan plan, Consumer<Player> after) throws SQLException {
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        persist(player, staged -> {
            staged.applyAccess(plan, actor, staff);
            return null;
        }, _ -> after.accept(player));
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
                        Message.PLOT_PERMISSION_CHANGE_MEMBERS, namesSummary(player, players)), NamedTextColor.WHITE))
                .append(Component.newline()).append(Component.text(t(player,
                        Message.PLOT_PERMISSION_CHANGE_CHILDREN, namesSummary(player, children)), NamedTextColor.WHITE));
        if (plan.request() instanceof PlotAccessRequest.Member member) {
            String overrides = member.overrides().isEmpty() ? t(player, Message.PLOT_PERMISSION_STATE_DEFAULT)
                    : String.join(" · ", java.util.Arrays.stream(PlotPermission.values())
                            .filter(member.overrides()::containsKey)
                            .map(permission -> t(player, permission.label()) + " " + t(player,
                                    member.overrides().get(permission) ? Message.PLOT_ALLOWED : Message.PLOT_DENIED))
                            .toList());
            body = body.append(Component.newline()).append(Component.text(
                    t(player, Message.PLOT_PERMISSION_PERSONAL) + ": " + overrides, NamedTextColor.WHITE));
        }
        if (plan.effects().isEmpty()) body = body.append(Component.newline()).append(Component.text(
                t(player, Message.PLOT_PERMISSION_CHANGE_NO_EFFECT), NamedTextColor.WHITE));
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

    private MenuScene.Builder accessChoiceDefinition(FloatingMenuContext<PlotAccessChoiceMenus.State> context,
            PlotAccessChoiceMenus.Reader reader) {
        var target = context.state().target();
        Plot plot = registry.byId(target.plotId());
        Message failure = plot == null ? Message.PLOT_ERROR_ID : !canManage(context.player(), plot) ? Message.PLOT_ERROR_OWNER_ONLY
                : target.subject().player() != null && !plot.members().containsKey(target.subject().player()) ? Message.PLOT_ERROR_MEMBER_MISSING : null;
        if (failure != null) {
            var content = Ui.text(Component.text(t(context.player(), failure), NamedTextColor.WHITE)).key("unavailable").singleLine().build();
            var menu = PlotMenuLayouts.screen(target.scope(), FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.help(content));
            menu.back(Component.text(t(context.player(), Message.BACK), NamedTextColor.GREEN)).region("navigation")
                    .primary((_, _) -> reader.back(context));
            return menu;
        }
        return switch (target) {
            case PlotAccessChoiceMenus.Setting setting -> permissionSettingDefinition(context, reader, plot, setting);
            case PlotAccessChoiceMenus.Subject subject -> permissionSubjectDefinition(context, reader, plot, subject);
        };
    }

    private MenuScene.Builder permissionSettingDefinition(FloatingMenuContext<PlotAccessChoiceMenus.State> context,
            PlotAccessChoiceMenus.Reader reader, Plot plot, PlotAccessChoiceMenus.Setting target) {
        Player player = context.player();
        var subject = target.subject(); var permission = target.permission(); var category = target.category();
        var locale = language.language(player);
        boolean staff = RolePermissions.canModerate(player);
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        MenuScene.Builder menu = PlotMenuLayouts.screen(target.scope(),
                FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.choices(context.state().page(), next -> reader.page(context, next)))
                .refreshEvery(20, (viewer, _) -> {
                    Plot fresh = registry.byId(plotId);
                    if (!reader.current(context.state())) return;
                    if (fresh == null || !canManage(viewer, fresh) || subject.player() != null && !fresh.members().containsKey(subject.player()))
                        reader.back(context);
                    else if (fresh != plot || registry.parentOf(fresh) != parent || language.language(viewer) != locale || RolePermissions.canModerate(viewer) != staff)
                        context.redraw();
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("subject", Component.text(subject.player() == null ? t(player, groupLabel(subject.group()))
                : memberName(subject.player()), NamedTextColor.WHITE)).region("subject");
        menu.information("heading", Component.text(t(player, Message.PLOT_PERMISSION_SELECT_STATE,
                t(player, permission.label())), NamedTextColor.GOLD)).region("heading");
        for (PlotAccessPolicy.Setting setting : PlotAccessPolicy.Setting.values()) {
            PlotAccessRequest request = new PlotAccessRequest.Permission(subject, permission, setting.value());
            PlotAccessPlan plan = edits.planAccess(plot, request);
            PlotAccessPolicy.View access = PlotAccessPolicy.view(plan.after(), plan.parent(), subject, permission.key());
            var option = menu.control("state:" + setting, permissionStatus(player, plan.after(), access))
                    .region("rows").selected(setting == PlotAccessPolicy.setting(plot.flags(), subject, permission.key()))
                    .primary((viewer, _) -> perform(viewer, () -> submitAccessChange(viewer, plotId, request,
                            responder -> perform(responder, () -> {
                                if (reader.current(context.state())) reader.returnTo(context,
                                        permissionListDefinition(responder, managed(responder, plotId.toString()), subject, category, context.state().parentPage()));
                            }))));
            PlotProblem failure = accessProblem(player, plot, request);
            if (failure != null) option.disabled(problemLabel(player, failure));
        }
        menu.navigation("return", Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").primary((_, _) -> reader.back(context));
        return menu;
    }

    private MenuScene memberDraftDefinition(Player player, Plot plot, PlotMemberDraft draft,
                                                          PlotPermission.Category category) {
        return memberDraftDefinition(player,plot,draft,category,0);
    }

    private MenuScene memberDraftDefinition(Player player,Plot plot,PlotMemberDraft draft,
                                           PlotPermission.Category category,int page) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        PlotAccessPlan plan = edits.planAccess(plot, draft.request());
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-member-draft:" + plotId + ":" + draft.player(),
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.permissionTable(plot, category,page,index -> {
                            if (registry.byId(plotId) == plot) FloatingMenus.current(player).ifPresent(handle ->
                                    handle.update(memberDraftDefinition(player,plot,draft,category,index)));
                        }))
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!permitted(viewer, fresh, PlotPermission.MANAGE_MEMBERS)) openPlotDetail(viewer, plotId);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(memberDraftDefinition(viewer, fresh, draft, category,page));
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
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
                            next == category ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                    .region("categories").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT).selected(next == category).keepAccessible()
                    .primary((viewer, handle) -> perform(viewer, () -> handle.update(memberDraftDefinition(viewer,
                            managed(viewer, plotId.toString()), draft, next))));
        }
        menu.information("operation-heading", Component.text(t(player, Message.PLOT_PERMISSION_OPERATION), NamedTextColor.WHITE))
                .region("operation-heading").alignment(FloatingMenuDecoration.Alignment.LEFT);
        menu.information("result-heading", Component.text(t(player, Message.PLOT_PERMISSION_RESULT), NamedTextColor.WHITE))
                .region("result-heading").alignment(FloatingMenuDecoration.Alignment.LEFT);
        menu.information("hint", Component.text(t(player, Message.PLOT_MEMBER_DRAFT_HINT), NamedTextColor.WHITE))
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
                .primary((viewer, _) -> perform(viewer, () -> submitAccessChange(viewer, plotId, draft.request(), responder -> {
                    send(responder, NamedTextColor.GREEN, Message.PLOT_INVITED, t(responder, roleMessage(draft.role())));
                    openPlotMembers(responder, plotId, 0);
                })));
        PlotProblem failure = accessProblem(player, plot, draft.request());
        if (failure != null) submit.disabled(problemLabel(player, failure));
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.WHITE)).region("navigation").keepAccessible();
        return menu.build();
    }

    private MenuScene permissionListDefinition(Player player, Plot plot,
            PlotAccessPolicy.Subject subject, PlotPermission.Category category) {
        return permissionListDefinition(player,plot,subject,category == null ? PlotPermission.Category.BUILD : category,0);
    }

    private MenuScene permissionListDefinition(Player player,Plot plot,PlotAccessPolicy.Subject subject,
                                              PlotPermission.Category category,int page) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        MenuScene.Builder menu = PlotMenuLayouts.screen(
                        "plot-permissions:" + plotId + ":" + subject.key("") + ":" + category,
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.permissionTable(plot, category,page,index -> {
                            if (registry.byId(plotId) == plot) FloatingMenus.current(player).ifPresent(handle ->
                                    handle.update(permissionListDefinition(player,plot,subject,category,index)));
                        }))
                .refreshEvery(20, (viewer, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(viewer);
                    else if (!canManage(viewer, fresh)) openPlotDetail(viewer, plotId);
                    else if (subject.player() != null && (!fresh.members().containsKey(subject.player())))
                        openPlotMembers(viewer, plotId, 0);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(permissionListDefinition(viewer, fresh, subject, category,page));
                });
        String subjectName = subject.player() == null ? t(player, groupLabel(subject.group()))
                : memberName(subject.player());
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, Message.PLOT_WORKSPACE_PERMISSIONS), NamedTextColor.GOLD))
                .region("heading").textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        menu.control("subject", Component.text(subjectName + " ▾", NamedTextColor.AQUA))
                .region("subject").alignment(FloatingMenuDecoration.Alignment.LEFT)
                .textWidth(FloatingMenuTextWidth.COMPACT).keepAccessible()
                .primary((viewer, handle) -> perform(viewer, () -> {
                    managed(viewer, plotId.toString());
                    accessChoices.open(viewer, new PlotAccessChoiceMenus.Subject(plotId, subject, category), handle, page);
                }));
        menu.information("section", Component.text(t(player, category.label()), NamedTextColor.WHITE))
                .region("section").alignment(FloatingMenuDecoration.Alignment.LEFT)
                .textWidth(FloatingMenuTextWidth.WIDE);
        for (PlotPermission.Category next : PlotPermission.Category.values()) {
            menu.control("category:" + next.name(), Component.text(
                            (next == category ? "› " : "  ") + t(player, next.label()),
                            next == category ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                    .region("categories").alignment(FloatingMenuDecoration.Alignment.LEFT)
                    .textWidth(FloatingMenuTextWidth.COMPACT).selected(next == category).keepAccessible()
                    .primary((viewer, handle) -> perform(viewer,
                            () -> refreshPermissionList(viewer, plotId, subject, next, handle)));
        }
        menu.information("operation-heading", Component.text(t(player, Message.PLOT_PERMISSION_OPERATION),
                        NamedTextColor.WHITE)).region("operation-heading")
                .alignment(FloatingMenuDecoration.Alignment.LEFT);
        menu.information("result-heading", Component.text(t(player, Message.PLOT_PERMISSION_RESULT),
                        NamedTextColor.WHITE)).region("result-heading")
                .alignment(FloatingMenuDecoration.Alignment.LEFT);
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
                            accessChoices.open(viewer, new PlotAccessChoiceMenus.Setting(plotId, subject, action, category), handle, page);
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
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private MenuScene.Builder permissionSubjectDefinition(FloatingMenuContext<PlotAccessChoiceMenus.State> context,
            PlotAccessChoiceMenus.Reader reader, Plot plot, PlotAccessChoiceMenus.Subject target) {
        Player player = context.player();
        var subject = target.subject(); var category = target.category();
        var locale = language.language(player);
        boolean staff = RolePermissions.canModerate(player);
        UUID plotId = plot.id();
        MenuScene.Builder menu = PlotMenuLayouts.screen(target.scope(),
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.choices(context.state().page(), next -> reader.page(context, next)))
                .refreshEvery(20, (viewer, _) -> {
                    Plot fresh = registry.byId(plotId);
                    if (!reader.current(context.state())) return;
                    if (fresh == null || !canManage(viewer, fresh) || subject.player() != null && !fresh.members().containsKey(subject.player()))
                        reader.back(context);
                    else if (fresh != plot || language.language(viewer) != locale || RolePermissions.canModerate(viewer) != staff) context.redraw();
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, Message.PLOT_PERMISSION_SWITCH), NamedTextColor.GOLD)).region("heading").keepAccessible();
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            menu.control("subject:" + group.name(), Component.text(t(player, groupLabel(group)), NamedTextColor.AQUA))
                    .region("rows").selected(group == subject.group())
                    .primary((viewer, _) -> perform(viewer, () -> reader.returnTo(context, permissionListDefinition(viewer, managed(viewer, plotId.toString()),
                            PlotAccessPolicy.Subject.group(group), category, context.state().tablePage(PlotAccessPolicy.Subject.group(group))))));
        }
        if (subject.player() != null) {
            menu.control("personal", Component.text(memberName(subject.player()), NamedTextColor.AQUA))
                    .region("rows").selected(true)
                    .primary((viewer, _) -> perform(viewer, () -> reader.returnTo(context,
                            permissionListDefinition(viewer, managed(viewer, plotId.toString()), subject, category, context.state().parentPage()))));
        }
        menu.control("members", Component.text(t(player, Message.PLOT_MENU_MEMBERS, PlotRoster.size(plot)),
                        NamedTextColor.AQUA)).region("rows")
                .primary((viewer, _) -> perform(viewer, () -> openPlotMembers(viewer, plotId, 0)));
        if (PlotAccessPolicy.hasSubjectOverrides(plot.flags(), subject, null)
                && registry.canEditSubject(plot, player.getUniqueId(), RolePermissions.canModerate(player), subject)) {
            menu.navigation("reset", Component.text(t(player, Message.PLOT_PERMISSION_RESET)
                            + " · " + t(player, Message.PLOT_PERMISSION_ALL_ACTIONS), NamedTextColor.YELLOW))
                    .region("navigation").keepAccessible()
                    .primary((viewer, _) -> perform(viewer, () -> submitAccessChange(viewer, plotId,
                            new PlotAccessRequest.Reset(subject, null), responder -> perform(responder, () -> {
                                if (reader.current(context.state())) reader.returnTo(context,
                                        permissionListDefinition(responder, managed(responder, plotId.toString()), subject, category, context.state().parentPage()));
                            }))));
        }
        menu.navigation("return", Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible()
                .primary((_, _) -> reader.back(context));
        return menu;
    }

    private void confirmPermissionReset(Player player, UUID plotId, PlotAccessPolicy.Subject subject,
            PlotPermission.Category scope, PlotPermission.Category returnCategory,
            org.encinet.mik.shared.menu.FloatingMenuHandle handle) {
        perform(player, () -> submitAccessChange(player, plotId, new PlotAccessRequest.Reset(subject, scope),
                responder -> refreshPermissionList(responder, plotId, subject, returnCategory, handle)));
    }

    private void refreshPermissionList(Player player, UUID plotId, PlotAccessPolicy.Subject subject,
                                       PlotPermission.Category category,
                                       org.encinet.mik.shared.menu.FloatingMenuHandle handle) {
        Plot fresh = registry.byId(plotId);
        if (fresh == null) openMenu(player);
        else if (!canManage(player, fresh)) openPlotDetail(player, plotId);
        else if (subject.player() != null && (!fresh.members().containsKey(subject.player()))) openPlotMembers(player, plotId, 0);
        else handle.update(permissionListDefinition(player, fresh, subject, category));
    }

    private static Message groupLabel(PlotAccessPolicy.Group group) {
        return switch (group) {
            case COLLABORATOR -> Message.PLOT_MENU_GROUP_COLLABORATOR;
            case OWNER -> Message.PLOT_ROLE_OWNER;
            case ADMIN -> Message.PLOT_ROLE_ADMIN;
            case NEWCOMER -> Message.PLOT_MENU_GROUP_NEWCOMER;
            case MEMBER -> Message.PLOT_MENU_GROUP_MEMBER;
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

    private void addReleaseAction(Player player, MenuScene.Builder menu, Plot plot) {
        if (!registry.canRelease(plot, player.getUniqueId(), RolePermissions.canModerate(player))) return;
        UUID plotId = plot.id();
        var release = menu.control("delete", Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED))
                .region("ownership")
                .primary((viewer, _) -> perform(viewer,
                        () -> confirmRelease(viewer, plotId.toString())));
        if (!registry.childrenOf(plotId).isEmpty()) release.disabled(
                Component.text(t(player, Message.PLOT_ERROR_SUBPLOT_CHILDREN)));
    }

    private void confirmRelease(Player player, String plotPrefix) {
        Plot fresh = edits.releasable(player.getUniqueId(),
                RolePermissions.canModerate(player), plotPrefix);
        UUID plotId = fresh.id();
        if (!registry.childrenOf(plotId).isEmpty())
            throw new PlotProblem(Message.PLOT_ERROR_SUBPLOT_CHILDREN);
        MenuDialogs.openConfirm(plugin, player,
                Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED),
                Component.text(t(player, Message.PLOT_MENU_CONFIRM_DELETE,
                        fresh.name() + " · #" + plotId.toString().substring(0, 8)), NamedTextColor.WHITE),
                Component.text(t(player, Message.PLOT_MENU_DELETE), NamedTextColor.RED),
                Component.text(t(player, Message.BACK), NamedTextColor.WHITE),
                responder -> perform(responder, () -> delete(responder, plotId.toString())));
    }

    private void openPlotMembers(Player player, UUID plotId, int requestedPage) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManageAccess(player, plot)) {
            send(player, NamedTextColor.RED, Message.PLOT_ERROR_OWNER_ONLY);
            return;
        }
        FloatingMenus.present(player, membersDefinition(player, plot, requestedPage));
    }

    private MenuScene membersDefinition(Player player, Plot plot, int requestedPage) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        List<MemberCard> members = PlotRoster.entries(plot).stream()
                .map(entry -> new MemberCard(entry.id(), memberName(entry.id()), entry.group()))
                .sorted(Comparator.comparing(MemberCard::group)
                        .thenComparing(MemberCard::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MemberCard::id))
                .toList();
        var cards = members.stream().map(member -> new PlotCardList.Card(UiKey.named("member:" + member.id()),
                memberLabel(member.id(),Component.text(member.name(),NamedTextColor.AQUA).append(Component.newline())
                        .append(Component.text(t(player,memberRoleMessage(member.group())),member.group() == PlotAccessPolicy.Group.OWNER ? NamedTextColor.GOLD : NamedTextColor.WHITE))),
                (viewer,_) -> {
                    if (registry.byId(plotId) == plot && canManageAccess(viewer,plot)) openPlotMember(viewer,plotId,member.id(),requestedPage);
                })).toList();
        var directory = PlotCardList.content(cards,Component.text(t(player,Message.PLOT_MENU_MEMBERS,0)),requestedPage,
                index -> { if (registry.byId(plotId) == plot && canManageAccess(player,plot)) openPlotMembers(player,plotId,index); });
        MenuScene.Builder menu = PlotMenuLayouts.screen("plot-members:" + plotId,
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.members(directory))
                .refreshEvery(20, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(p);
                    else if (!canManageAccess(p, fresh)) openPlotDetail(p, plotId);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(membersDefinition(p, fresh, requestedPage));
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(t(player, Message.PLOT_MENU_ACCESS), NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("members-heading", Component.text(t(player, Message.PLOT_MENU_MEMBERS, members.size()), NamedTextColor.GOLD)).region("members-heading");
        menu.information("groups-heading", Component.text(t(player, Message.PLOT_WORKSPACE_GROUPS), NamedTextColor.GOLD)).region("groups-heading");
        for (var group : PlotAccessPolicy.Group.values()) menu.navigation("group:" + group,
                        Component.text(t(player, groupLabel(group)), NamedTextColor.AQUA))
                .region("groups").primary((viewer, _) -> perform(viewer,
                        () -> openPermissionList(viewer, plotId, PlotAccessPolicy.Subject.group(group), null)));
        if (parent != null)
            menu.information("supervisor", Component.text(t(player, Message.PLOT_MENU_PARENT_SUPERVISOR,
                    memberName(parent.owner())), NamedTextColor.WHITE)).region("hint");
        if (permitted(player, plot, PlotPermission.MANAGE_MEMBERS))
            menu.control("invite", Component.text(t(player, Message.PLOT_MENU_ADD_MEMBER), NamedTextColor.GREEN))
                    .region("actions").primary((viewer, _) -> memberInput(viewer, plotId));
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void openPlotMember(Player player, UUID plotId, UUID memberId, int listPage) {
        Plot plot = registry.byId(plotId);
        if (plot == null) {
            openMenu(player);
            return;
        }
        if (!canManageAccess(player, plot)) {
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

    private MenuScene memberDefinition(Player player, Plot plot, UUID memberId,
                                                    int listPage) {
        UUID plotId = plot.id();
        Plot parent = registry.parentOf(plot);
        PlotRoster.Entry member = PlotRoster.entry(plot, memberId);
        Plot.Role role = plot.members().get(memberId);
        String name = memberName(memberId);
        MenuScene.Builder menu = PlotMenuLayouts.screen(
                        "plot-member:" + plotId + ":" + memberId,
                        FloatingMenuAppearance.DEFAULT, PlotMenuLayouts.member())
                .refreshEvery(20, (p, handle) -> {
                    Plot fresh = registry.byId(plotId);
                    if (fresh == null) openMenu(p);
                    else if (!canManageAccess(p, fresh)) openPlotDetail(p, plotId);
                    else if (PlotRoster.entry(fresh, memberId) == null)
                        openPlotMembers(p, plotId, listPage);
                    else if (fresh != plot || parent != registry.parentOf(fresh))
                        handle.update(memberDefinition(p, fresh, memberId, listPage));
                });
        menu.information("context", Component.text(plot.name(), NamedTextColor.AQUA)).region("context");
        menu.information("heading", Component.text(name, NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("identity", memberLabel(memberId, Component.text(name, NamedTextColor.AQUA)
                        .append(Component.newline())
                        .append(Component.text(t(player, memberRoleMessage(member.group())),
                                member.owner() ? NamedTextColor.GOLD : NamedTextColor.WHITE))))
                .region("identity");
        menu.navigation("permissions", Component.text(t(player, Message.PLOT_MEMBER_PERMISSIONS), NamedTextColor.AQUA))
                .region("actions").primary((viewer, _) -> perform(viewer, () -> {
                    Plot latest = managed(viewer, plotId.toString());
                    PlotRoster.Entry current = PlotRoster.entry(latest, memberId);
                    if (current == null) openPlotMembers(viewer, plotId, listPage);
                    else openPermissionList(viewer, plotId, current.permissionSubject(), null);
                }));
        if (!member.owner() && permitted(player, plot, PlotPermission.MANAGE_MEMBERS)) {
            for (Plot.Role next : Plot.Role.values()) {
                if (next == role) continue;
                var change = menu.control("role:" + next.name(), Component.text(t(player, Message.PLOT_MENU_SET_ROLE,
                                        t(player, roleMessage(next))), NamedTextColor.GREEN))
                        .region("actions").primary((viewer, _) -> perform(viewer,
                                () -> setMemberRole(viewer, plotId, memberId, next)));
                PlotProblem failure = accessProblem(player, plot, new PlotAccessRequest.Member(memberId, next,
                        PlotAccessPolicy.personalOverrides(plot, memberId)));
                if (failure != null) change.disabled(problemLabel(player, failure));
            }
            var remove = menu.control("remove", Component.text(t(player, Message.PLOT_MENU_REMOVE_MEMBER), NamedTextColor.RED))
                    .region("actions").primary((viewer, _) -> perform(viewer,
                            () -> removeMember(viewer, plotId, memberId)));
            PlotProblem failure = accessProblem(player, plot, new PlotAccessRequest.Remove(memberId));
            if (failure != null) remove.disabled(problemLabel(player, failure));
        }
        menu.back(Component.text(t(player, Message.BACK), NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private boolean canManageAccess(Player player, Plot plot) {
        return permitted(player, plot, PlotPermission.MANAGE_MEMBERS) || permitted(player, plot, PlotPermission.MANAGE_PERMISSIONS);
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

    private String memberName(UUID id) {
        String name = PlayerNames.resolve(id,playerNames.getOrDefault(id,id.toString().substring(0,8)));
        playerNames.put(id,name);
        return name;
    }

    private record MemberCard(UUID id, String name, PlotAccessPolicy.Group group) { }

    private static Component memberLabel(UUID id, Component label) {
        return Component.object(ObjectContents.playerHead(id)).append(Component.space()).append(label);
    }

    private void memberInput(Player player, UUID plotId) {
        FloatingMenus.search(player, MenuPlayerSearch.builder(Component.text(t(player,Message.PLOT_MENU_ADD_MEMBER),NamedTextColor.GOLD),
                        Component.text(t(player,Message.PLOT_MENU_PLAYER),NamedTextColor.AQUA))
                .find(query -> java.util.concurrent.CompletableFuture.completedFuture(MenuPlayerSearch.matching(query,MenuPlayerSearch.cached(player,query))))
                .selected((viewer,target) -> perform(viewer,
                        () -> invite(viewer,plotId.toString(),target.id().toString(),Plot.Role.COLLABORATOR))).build());
    }

    private void transferInput(Player player, UUID plotId) {
        FloatingMenus.search(player, MenuPlayerSearch.builder(Component.text(t(player,Message.PLOT_MENU_TRANSFER),NamedTextColor.GOLD),
                        Component.text(t(player,Message.PLOT_MENU_PLAYER),NamedTextColor.AQUA))
                .find(query -> java.util.concurrent.CompletableFuture.completedFuture(MenuPlayerSearch.matching(query,MenuPlayerSearch.cached(player,query))))
                .selected((viewer, target) -> {
                    try {
                        confirmTransfer(viewer, plotId,
                                PlotCreationController.managedPlayer(target.id().toString()));
                    } catch (PlotProblem error) {
                        send(viewer, NamedTextColor.RED, error.message(), error.args());
                    }
                }).build());
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
                        plot.name(), successor.name()), NamedTextColor.WHITE),
                Component.text(t(player, Message.PLOT_MENU_TRANSFER), NamedTextColor.GOLD),
                Component.text(t(player, Message.BACK), NamedTextColor.WHITE),
                viewer -> {
                    perform(viewer, () -> transfer(viewer, plotId, successor));
                    openMenu(viewer);
                });
    }

    private void textInput(Player player, Message title, Message label, String initial,
                           int maximumLength, boolean multiline,
                           java.util.function.BiConsumer<Player, String> submit) {
        FloatingMenus.input(player, MenuInputForm.builder(Component.text(t(player, title), NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("value", Component.text(t(player, label), NamedTextColor.WHITE), initial, maximumLength).multiline(multiline))
                .cancel(Component.text(t(player, Message.BACK), NamedTextColor.WHITE))
                .submit(Component.text(t(player, Message.PLOT_MENU_SUBMIT), NamedTextColor.GREEN), (responder, inputValues) -> submit.accept(responder, inputValues.text("value").strip())).build());
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
            case "rename" -> rename(player, arg(args, 1),
                    arg(args, 2) + (args.length > 3 ? " " + args[3] : ""));
            case "delete" -> confirmRelease(player, arg(args, 1));
            case "list" -> list(player);
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
        guideMenu.open(player);
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
        submitAccessChange(player, plotId, new PlotAccessRequest.Remove(memberId), responder -> {
            send(responder, NamedTextColor.GREEN, Message.PLOT_MEMBER_REMOVED);
            openPlotMembers(responder, plotId, 0);
        });
    }

    private void setMemberRole(Player player, UUID plotId, UUID memberId, Plot.Role role) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        if (!plot.members().containsKey(memberId)) throw new PlotProblem(Message.PLOT_ERROR_MEMBER_MISSING);
        submitAccessChange(player, plotId, new PlotAccessRequest.Member(memberId, role,
                PlotAccessPolicy.personalOverrides(plot, memberId)), responder -> {
            send(responder, NamedTextColor.GREEN, Message.PLOT_INVITED, t(responder, roleMessage(role)));
            openPlotMember(responder, plotId, memberId, 0);
        });
    }

    private void transfer(Player player, UUID plotId, PlotOwner successor) throws SQLException {
        Plot plot = managed(player, plotId.toString());
        UUID actor = player.getUniqueId();
        persist(player, staged -> { staged.transfer(plot, actor, RolePermissions.canModerate(player), successor.id()); return null; }, _ -> {
            send(player, NamedTextColor.GREEN, Message.PLOT_TRANSFERRED,
                    plot.name(), successor.name());
        });
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

    private void rename(Player player, String plotId, String name) throws SQLException {
        Plot plot = managed(player, plotId);
        persist(player, staged -> { staged.rename(plot, player.getUniqueId(), RolePermissions.canModerate(player), name); return null; },
                _ -> send(player, NamedTextColor.GREEN, Message.PLOT_RENAMED));
    }

    private void delete(Player player, String plotId) throws SQLException {
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        Plot plot = edits.releasable(actor, staff, plotId);
        Plot parent = registry.parentOf(plot);
        UUID origin = FloatingMenus.current(player).map(handle -> handle.id()).orElse(null);
        persistGeometry(player, staged -> { staged.delete(plot, actor, staff); return null; }, _ -> {
            selections.removePlot(plot.id());
            atmosphere.refreshAll();
            send(player, NamedTextColor.GREEN, Message.PLOT_DELETED);
            if (origin != null && player.isOnline() && FloatingMenus.current(player).map(handle -> handle.id().equals(origin)).orElse(false)) {
                selectionController.stop(player.getUniqueId());
                FloatingMenus.openRoot(player, directoryDefinition(player, PlotDirectory.initial()));
                if (parent != null && registry.byId(parent.id()) != null) openPlotDetail(player, parent.id());
            }
        });
    }

    private void list(Player player) {
        var location = player.getLocation();
        Plot here = registry.at(player.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (here == null) send(player, NamedTextColor.WHITE, Message.PLOT_NO_HERE);
        else send(player, NamedTextColor.GREEN, Message.PLOT_HERE, here.name(), here.id());
        for (Plot plot : registry.all()) {
            if (plot.owner().equals(player.getUniqueId()) || plot.members().containsKey(player.getUniqueId()))
                send(player, NamedTextColor.WHITE, Message.PLOT_LIST_ITEM, plot.name(), plot.id(),
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
