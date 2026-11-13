package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuAppearance;
import org.encinet.mik.shared.menu.ui.UiPointerBehavior;
import org.encinet.mik.shared.menu.runtime.motion.MenuTransition;

import org.encinet.mik.shared.menu.FloatingMenuCloseReason;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuSearch;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.WorldMenuScope;
import org.encinet.mik.shared.menu.MenuPopover;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import org.encinet.mik.shared.menu.FloatingMenuFeedback;
import org.encinet.mik.shared.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.shared.menu.FloatingMenuFocusAction;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuHandle;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuPlacement;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuRefresh;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuPreferences;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.MenuInputLease;
import org.encinet.mik.shared.menu.MenuQuerySession;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.FloatingMenuState;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.i18n.LanguageChangeListener;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.integration.axiom.AxiomImage;
import org.encinet.mik.integration.axiom.AxiomImageService;
import org.encinet.mik.shared.util.ShutdownSequence;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.encinet.mik.shared.menu.ui.UiKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Owns one declarative menu lifecycle and projects it to the viewer's client UI. */
public final class FloatingMenuService implements Listener, LanguageChangeListener {
    private static final double HOVER_SURFACE_MARGIN = 0.04D;
    private static final double SURFACE_DISTANCE_EPSILON = 1.0E-4D;
    private static final int MENU_STATUS_SYNC_INTERVAL_TICKS = 5;
    private static final FloatingMenuWorldInteractionGuard WORLD_INTERACTION_GUARD =
            FloatingMenuWorldInteractionGuard.UNIFIED;
    private final JavaPlugin plugin;
    private final VirtualMenuEntityRenderer virtualEntities = new VirtualMenuEntityRenderer();
    private final MenuSoundFeedback sounds = new MenuSoundFeedback();
    private final MenuTooltipController tooltips;
    private final AxiomGizmoService.Scope axiomGizmos;
    private final AxiomImageService axiomImages;
    private final MenuImageRenderer images;
    private final MenuUsageDisplayController menuStatusDisplays;
    private final LanguageService languageService;
    private final Predicate<Player> externalUiOpen;
    private final FloatingMenuSettingsStore settings;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final MenuWorldPanels<Session> worldPanels;
    private final Map<UUID, MenuInputGate> inputGates = new HashMap<>();
    private final Map<UUID, UUID> pointerOwners = new HashMap<>();
    private final Map<Integer, Target> targets = new HashMap<>();
    private final Set<UUID> scrollCaptures = new HashSet<>();
    private final MenuInputSessions inputs = new MenuInputSessions(this::inputFinished);
    private BukkitTask animationTask;
    private int menuStatusSyncTicks;
    private Consumer<Player> mainMenuOpener;
    private volatile boolean disabled;

    public FloatingMenuService(JavaPlugin plugin, AxiomGizmoService axiomGizmoService, AxiomImageService axiomImages,
                               LanguageService languageService) {
        this(plugin,axiomGizmoService,axiomImages,languageService,FloatingMenuService::inventoryOpen);
    }

    FloatingMenuService(JavaPlugin plugin, AxiomGizmoService axiomGizmoService, AxiomImageService axiomImages,
                        LanguageService languageService, Predicate<Player> externalUiOpen) {
        this.plugin = plugin;
        this.externalUiOpen = java.util.Objects.requireNonNull(externalUiOpen);
        AxiomGizmoService gizmoService = java.util.Objects.requireNonNull(
                axiomGizmoService, "axiomGizmoService");
        this.axiomGizmos = gizmoService.scope("floating-menu");
        this.axiomImages = java.util.Objects.requireNonNull(axiomImages, "axiomImages");
        this.images = new MenuImageRenderer(virtualEntities, axiomImages);
        this.tooltips = new MenuTooltipController(virtualEntities, gizmoService.scope("floating-menu-tooltips"));
        this.languageService = java.util.Objects.requireNonNull(
                languageService, "languageService");
        this.menuStatusDisplays = new MenuUsageDisplayController(
                this.languageService, gizmoService.scope("floating-menu-status"));
        this.settings = new FloatingMenuSettingsStore(plugin);
        this.worldPanels = new MenuWorldPanels<>(new MenuWorldPanels.Host<>() {
            public void requireThread(String operation) { requirePrimaryThread(operation); }
            public boolean enabled() { return !disabled; }
            public Session create(Player player, MenuScene scene, MenuWorldPanels.Mount mount) {
                return Session.create(player,UUID.randomUUID(),scene,List.of(),preferences(player),
                        new MenuPresentation(),mount,gate(player.getUniqueId()));
            }
            public void update(Session session, Player player, MenuScene scene, MenuWorldPanels.Mount mount) {
                session.inputGate.contentChanged();
                FloatingMenuService.this.update(session,player,scene,mount);
            }
            public void open(Session session, Player player) {
                session.inputGate.contentChanged();
                session.hostPaused = sessions.containsKey(session.playerId) || !MenuWorldPresentation.nearby(session.mount.surface(),player);
                if (!session.hostPaused) synchronizePlacementVisibility(session,player);
                transition(session,FloatingMenuState.CLOSED,FloatingMenuState.OPENING,null);
            }
            public void close(Session session, FloatingMenuCloseReason reason) { closeMounted(session,reason); }
            public FloatingMenuHandle handle(Session session) { return new Handle(session.id,session.playerId); }
        });
    }

    public void enable() {
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        if (animationTask != null) return;
        try {
            settings.enable().thenRun(() -> execute(() -> {
                for (Session session : allSessions()) {
                    Player player = Bukkit.getPlayer(session.playerId);
                    if (player == null) continue;
                    update(session, player, session.definition);
                }
            }));
            Bukkit.getPluginManager().registerEvents(this, plugin);
            languageService.addLanguageChangeListener(this);
            animationTask = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 1L, 1L);
            plugin.getLogger().info("Floating menu renderer: virtual entities");
        } catch (RuntimeException | LinkageError error) {
            try {
                disable();
            } catch (RuntimeException | LinkageError cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    public void disable() {
        if (disabled) return;
        disabled = true;
        ShutdownSequence shutdown = new ShutdownSequence();
        BukkitTask task = animationTask;
        animationTask = null;
        if (task != null) shutdown.attempt("floating menu animation task", task::cancel);
        shutdown.attempt("floating menu listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("floating menu language listener", () ->
                languageService.removeLanguageChangeListener(this));
        closeAllImmediately(FloatingMenuCloseReason.PLUGIN_DISABLE, shutdown);
        shutdown.attempt("world menu scopes",worldPanels::close);
        shutdown.attempt("menu status displays", menuStatusDisplays::disable);
        shutdown.attempt("floating menu Axiom gizmo scope", axiomGizmos::close);
        shutdown.attempt("tooltip Axiom gizmo scope", tooltips::close);
        shutdown.attempt("menu preference writer", settings::close);
        shutdown.finish("floating menu service");
    }

    public WorldMenuScope worldPanels() {
        return worldPanels.scope();
    }

    private MenuInputGate gate(UUID viewer) { return inputGates.computeIfAbsent(viewer,_ -> new MenuInputGate(System::nanoTime)); }

    private List<Session> allSessions() {
        var result = new ArrayList<>(sessions.values()); result.addAll(worldPanels.all()); return List.copyOf(result);
    }

    private Session lookup(UUID viewer, UUID id) {
        var current = sessions.get(viewer);
        if (current != null && current.id.equals(id)) return current;
        return worldPanels.find(viewer,id);
    }

    private Session handleSession(UUID viewer, UUID id) {
        var exact = lookup(viewer,id); return exact == null ? sessions.get(viewer) : exact;
    }

    private boolean active(Session session) { return lookup(session.playerId,session.id) == session; }

    private Session inputOwner(Player player) {
        if (inputBlocked(player)) return null;
        var foreground = sessions.get(player.getUniqueId());
        if (foreground != null) return !foreground.obstructed() ? foreground : null;
        var previous = pointerOwners.get(player.getUniqueId());
        var held = previous == null ? null : worldPanels.find(player.getUniqueId(),previous);
        if (held != null && !held.obstructed() && (tooltips.occludes(player) || tooltips.holds(player,held.totalTicks))
                && available(held,player)) return held;
        Session nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var candidate : worldPanels.views(player.getUniqueId())) {
            if (!candidate.playerId.equals(player.getUniqueId()) || candidate.state != FloatingMenuState.ACTIVE || candidate.obstructed()) continue;
            var hit = MenuWorldPresentation.hit(candidate.mount.surface(),candidate.origin,candidate.layoutSnapshot.frame().size(),player.getEyeLocation());
            if (hit.isEmpty() || hit.get().distance() >= distance || !available(candidate,player)) continue;
            nearest = candidate; distance = hit.get().distance();
        }
        return nearest;
    }

    private boolean available(Session session, Player player) {
        try { return session.mount.available().test(player); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,"World menu availability failed for " + session.id,error);
            closeMounted(session,FloatingMenuCloseReason.ERROR);
            return false;
        }
    }

    private int reservedEntities(Session owner) {
        return owner.mount == null ? 0 : worldPanels.reservedEntities(owner);
    }

    private void closeMounted(Session session, FloatingMenuCloseReason reason) {
        if (session.mount != null && active(session)) removeCurrentVisuals(session,FloatingMenuState.CLOSED,reason);
    }

    private void clearWorldViews(UUID viewer, FloatingMenuCloseReason reason) {
        try { worldPanels.clear(viewer,reason); }
        finally { pointerOwners.remove(viewer); }
    }

    public void setMainMenuOpener(Consumer<Player> opener) {
        this.mainMenuOpener = java.util.Objects.requireNonNull(opener, "opener");
    }

    public void clearMainMenuOpener() {
        mainMenuOpener = null;
    }

    public FloatingMenuScale scale(Player player) {
        return preferences(player).layout();
    }

    public FloatingMenuPreferences preferences(Player player) {
        return settings.preferences(java.util.Objects.requireNonNull(player, "player").getUniqueId());
    }

    public void openSettings(Player player) {
        requirePrimaryThread("openSettings");
        java.util.Objects.requireNonNull(player, "player");
        final class View implements Runnable {
            private final MenuPanel panel = settingsPanel(player, this);
            private FloatingMenuHandle handle;
            private boolean closed;
            @Override public void run() {
                if (closed) return;
                MenuScene content = panel.render();
                MenuScene scene = content.toBuilder().screen("interface-settings")
                        .content(UiTemplates.withBack(content.content(), Component.text(languageService.t(player, Message.BACK), NamedTextColor.GREEN)))
                        .lifecycle((_, _, _, current, _) -> {
                            if (current == FloatingMenuState.CLOSING || current == FloatingMenuState.CLOSED) {
                                closed = true;
                                panel.close();
                            }
                        }).build();
                if (handle == null) { handle = present(player, scene); panel.attach(handle); }
                else handle.update(scene);
            }
        }
        new View().run();
    }

    public MenuPanel settingsPanel(Player player, Runnable redraw) {
        requirePrimaryThread("settingsPanel");
        return new FloatingMenuSettingsPanel(player, languageService, () -> preferences(player),
                selected -> selectPreferences(player, selected), redraw);
    }

    private void selectPreferences(Player player, FloatingMenuPreferences selected) {
        requirePrimaryThread("selectPreferences");
        if (!selected.equals(preferences(player))) settings.setPreferences(player.getUniqueId(), selected);
        // The owner's redraw measures the requested settings before the visible view adopts them.
    }

    private void commitPreferences(Session session, FloatingMenuPreferences selected) {
        boolean changed = !selected.equals(session.preferences);
        if (selected.reducedMotion()) session.exits.clear(parts -> destroySnapshots(Bukkit.getPlayer(session.playerId),parts));
        if (!selected.theme().equals(session.preferences.theme())) {
            for (Decoration decoration : session.decorations) {
                decoration.iconPaint = null;
                decoration.rangePaint = null;
            }
            tooltips.clear(session.playerId);
        }
        session.preferences = selected;
        session.animation = selected.reducedMotion() ? session.definition.animation().reduced() : session.definition.animation();
        if (selected.reducedMotion()) session.reveal.snap(1, session.totalTicks);
        session.layoutDirty |= changed;
    }

    private void toggleMainMenu(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.definition.popover() != null) dismissPopover(session, player);
        else if (session != null) close(player, FloatingMenuCloseReason.SHORTCUT);
        else if (mainMenuOpener != null) mainMenuOpener.accept(player);
    }

    /** Opens a declarative menu and returns a stable handle for external control. */
    public FloatingMenuHandle open(Player player, MenuScene definition) {
        requirePrimaryThread("open");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(definition, "definition");
        Session current = sessions.get(player.getUniqueId());
        if (current != null && current.definition.popover() != null) {
            dismissPopover(current, player);
            current = sessions.get(player.getUniqueId());
        }
        List<Frame> ancestors = new ArrayList<>();
        boolean suspendCurrent = current != null && current.state != FloatingMenuState.CLOSING;
        if (suspendCurrent
                && current.definition != null) {
            ancestors.addAll(current.ancestors);
            ancestors.add(new Frame(current.id, current.definition,
                    current.refreshRevision, current.presentation));
        }
        return openDefinition(player, definition, ancestors, suspendCurrent, false);
    }

    /** Replaces the complete hierarchy and establishes a new root screen. */
    public FloatingMenuHandle openRoot(Player player, MenuScene definition) {
        requirePrimaryThread("openRoot");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(definition, "definition");
        return openDefinition(player, definition, List.of(), false, false);
    }

    public FloatingMenuHandle present(Player player, MenuScene definition) {
        requirePrimaryThread("present");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(definition, "definition");
        Session current = sessions.get(player.getUniqueId());
        if (current != null && current.state != FloatingMenuState.CLOSING
                && definition.screenId() != null
                && definition.screenId().equals(current.definition.screenId())) {
            update(current, player, definition);
            return new Handle(current.id, current.playerId);
        }
        if (current != null && current.state != FloatingMenuState.CLOSING
                && definition.screenId() != null) {
            for (int index = current.ancestors.size() - 1; index >= 0; index--) {
                Frame frame = current.ancestors.get(index);
                if (definition.screenId().equals(frame.definition.screenId())) {
                    List<Frame> remaining = new ArrayList<>(current.ancestors.subList(0, index));
                    openConfigured(player, frame.id, definition, remaining,
                            false, true, FloatingMenuCloseReason.REPLACED);
                    return new Handle(frame.id, player.getUniqueId());
                }
            }
        }
        return open(player, definition);
    }

    public void execute(Runnable task) {
        if (disabled || !plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, () -> { if (!disabled) task.run(); });
    }

    public Optional<FloatingMenuHandle> current(Player player) {
        requirePrimaryThread("current");
        Session session = sessions.get(player.getUniqueId());
        if (session != null) return Optional.of(new Handle(session.id, session.playerId));
        return Optional.empty();
    }

    public Predicate<Location> worldOverlayVisibility(Player player) {
        requirePrimaryThread("worldOverlayVisibility");
        Session session = inputOwner(player);
        if (session == null || session.obstructed() || session.state == FloatingMenuState.CLOSED || session.state == FloatingMenuState.SUSPENDED)
            return _ -> true;
        return worldOverlayMask(session,player);
    }

    private MenuWorldOverlayMask worldOverlayMask(Session session, Player player) {
        var surfaces = new ArrayList<>(session.tooltipSurfaces());
        tooltips.surface(player).ifPresent(surfaces::add);
        return new MenuWorldOverlayMask(player.getEyeLocation(),surfaces);
    }

    private static void maskWorldText(Decoration decoration, Location at, float yaw, float pitch, MenuWorldOverlayMask mask) {
        boolean covered = mask != null && decoration.definition.worldAnchored()
                && decoration.definition.content() instanceof FloatingMenuDecoration.Text text
                && mask.overlaps(MenuWorldOverlayMask.textSurface(at,yaw,pitch,text));
        if (covered != decoration.interfaceOccluded) {
            decoration.interfaceOccluded = covered;
            decoration.metadata.invalidate();
        }
    }

    public MenuInputLease beginConfirmation(Player player) {
        return beginConfirmation(player, () -> { });
    }

    public MenuInputLease beginConfirmation(Player player, Runnable ended) {
        requirePrimaryThread("beginInput");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        UUID playerId = player.getUniqueId();
        Session owner = sessions.get(playerId);
        MenuInputLease lease = inputs.begin(playerId, () -> player.isOnline()
                && sessions.get(playerId) == owner, ended);
        tooltips.clear(playerId);
        scrollCaptures.remove(playerId);
        return lease;
    }

    private void inputFinished(UUID playerId, MenuInputSessions.Finish reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && reason == MenuInputSessions.Finish.INVALIDATED) player.closeDialog();
        Session session = sessions.get(playerId);
        if (session != null) {
            session.layoutDirty = true;
            updateScrollCapture(session);
        }
    }

    public MenuInputLease beginChatInput(Player player, Consumer<String> text, Runnable ended) {
        java.util.Objects.requireNonNull(text, "text");
        return beginChat(player, MenuInputSessions.Delivery.LATEST, text, ended);
    }

    private MenuInputLease beginChatOnce(Player player, Consumer<String> text, Runnable ended) {
        java.util.Objects.requireNonNull(text, "text");
        return beginChat(player, MenuInputSessions.Delivery.ONCE, text, ended);
    }

    private MenuInputLease beginChat(Player player, MenuInputSessions.Delivery delivery, Consumer<String> text, Runnable ended) {
        requirePrimaryThread("beginChatInput");
        java.util.Objects.requireNonNull(ended, "ended");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        UUID playerId = player.getUniqueId();
        Session owner = sessions.get(playerId);
        if (owner == null) throw new IllegalStateException("Chat input needs an open menu");
        var layerSource = owner.definition.popover() == null ? null : owner.definition.popover().source();
        java.util.function.BooleanSupplier active = () -> player.isOnline() && sessions.get(playerId) == owner
                && java.util.Objects.equals(layerSource, owner.definition.popover() == null ? null : owner.definition.popover().source())
                && (owner.state == FloatingMenuState.OPENING || owner.state == FloatingMenuState.ACTIVE);
        Runnable finished = () -> {
            try { ended.run(); }
            catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Menu chat input cleanup failed for " + playerId, error);
            }
        };
        return switch (delivery) {
            case LATEST -> inputs.beginChat(playerId, active, raw -> deliverChat(playerId, () -> text.accept(raw)), finished);
            case ONCE -> inputs.beginChatOnce(playerId, active, raw -> deliverChat(playerId, () -> text.accept(raw)), finished);
            case NONE -> throw new IllegalArgumentException("Chat input needs a delivery mode");
        };
    }

    private void deliverChat(UUID playerId, Runnable delivery) {
        try { delivery.run(); }
        catch (RuntimeException error) {
            inputs.invalidate(playerId);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Menu chat input failed for " + playerId, error);
        }
    }

    public void input(Player player, org.encinet.mik.shared.menu.MenuInputForm form) {
        requirePrimaryThread("input");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        MenuInputEditor.open(this, languageService, player, java.util.Objects.requireNonNull(form, "form"));
    }

    org.encinet.mik.shared.menu.MenuDialogSession editText(Player player, Component title, MenuInputForm.Field field, String value,
            Component problem, Component done, Component cancel, Consumer<String> accepted, Runnable cancelled) {
        return org.encinet.mik.shared.menu.MenuDialogs.editText(plugin, player, title, field, value, problem, done, cancel, accepted, cancelled);
    }

    public org.encinet.mik.shared.menu.MenuFormSession inlineInput(Player player,
            org.encinet.mik.shared.menu.MenuInputForm form, Runnable changed, Runnable finished) {
        requirePrimaryThread("inlineInput");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        var editor = new MenuInputEditor(this, languageService, player, java.util.Objects.requireNonNull(form));
        editor.inline(changed, finished);
        return editor;
    }

    public MenuQuerySession queryInput(Player player, Component placeholder, int maximumLength,
                                       Consumer<String> update, Runnable redraw) {
        requirePrimaryThread("queryInput");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        return queryEditor(player, placeholder, maximumLength, update, redraw, () -> { });
    }

    public <T> void search(Player player, MenuSearch<T> search) {
        requirePrimaryThread("search");
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        var labels = new MenuSearchWindow.Labels(languageService.text(player,Message.MENU_SEARCH_LOADING,NamedTextColor.YELLOW),
                languageService.text(player,Message.MENU_SEARCH_EMPTY,NamedTextColor.WHITE),
                languageService.text(player,Message.MENU_SEARCH_FAILED,NamedTextColor.RED),
                languageService.text(player,Message.MENU_SEARCH_RETRY,NamedTextColor.AQUA),
                languageService.text(player,Message.BACK,NamedTextColor.WHITE),
                (shown,total) -> languageService.text(player,Message.MENU_SEARCH_LIMIT,NamedTextColor.YELLOW,shown,total));
        new MenuSearchWindow<>(this,player,java.util.Objects.requireNonNull(search),labels,
                error -> plugin.getLogger().log(java.util.logging.Level.WARNING,"Menu search failed for "+player.getUniqueId(),error)).open();
    }

    MenuQueryEditor queryEditor(Player player, Component placeholder, int maximumLength,
                                Consumer<String> update, Runnable redraw, Runnable cancelled) {
        var labels = new MenuQueryEditor.Labels(
                languageService.rich(player, Message.MENU_QUERY_HINT_RICH, NamedTextColor.AQUA,
                        RichArg.component("chatKey", Component.keybind("key.chat", NamedTextColor.WHITE))),
                languageService.text(player, Message.MENU_INPUT_CLEAR, NamedTextColor.WHITE),
                languageService.text(player, Message.MENU_QUERY_CANCEL, NamedTextColor.YELLOW),
                languageService.text(player, Message.MENU_QUERY_SINGLE_LINE, NamedTextColor.YELLOW),
                length -> languageService.text(player, Message.MENU_QUERY_TOO_LONG, NamedTextColor.YELLOW,
                        length, maximumLength));
        return new MenuQueryEditor(placeholder, maximumLength, labels,
                (text, ended) -> beginChatOnce(player, text, ended), update, redraw, cancelled);
    }

    /** Thread-safe ingress, wired before chat channel routing; accepted text is delivered by the next menu tick. */
    public boolean consumeChatInput(UUID playerId, String raw) {
        return !disabled && inputs.offerText(playerId, raw);
    }

    private boolean inputBlocked(Player player) {
        return inputs.blocking(player.getUniqueId()) || externalUiOpen.test(player);
    }

    private static boolean inventoryOpen(Player player) {
        InventoryType type = player.getOpenInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE;
    }

    private FloatingMenuHandle openDefinition(Player player, MenuScene definition,
                                              List<Frame> ancestors, boolean suspendCurrent,
                                              boolean resumed) {
        UUID id = UUID.randomUUID();
        openConfigured(player, id, definition, ancestors, suspendCurrent, resumed,
                FloatingMenuCloseReason.REPLACED);
        return new Handle(id, player.getUniqueId());
    }

    private void openConfigured(Player player, UUID id, MenuScene definition,
                                List<Frame> ancestors, boolean suspendCurrent,
                                boolean resumed, FloatingMenuCloseReason replacementReason) {
        if (disabled) throw new IllegalStateException("Floating menu service has been disabled");
        Session current = sessions.get(player.getUniqueId());
        Frame parent = resumed && current != null ? current.ancestor(id) : null;
        var shared = current == null || current.obstructed() || current.state == FloatingMenuState.CLOSING
                || !player.getWorld().equals(current.openedAt.getWorld()) ? Map.<UiKey,MenuSharedElements.Source>of()
                : MenuSharedElements.capture(current.layoutSnapshot,path -> {
                    var button = current.button(path); return button == null || !inputVisible(current,button) ? null : button.surface;
                });
        MenuPresentation presentation = parent == null ? new MenuPresentation() : parent.presentation;
        Session session = Session.create(player, id, definition, ancestors, preferences(player), presentation, null, gate(player.getUniqueId()));
        inputs.invalidate(player.getUniqueId());
        if (current != null) {
            List<Frame> framesToRetain = ancestors;
            if (suspendCurrent) {
                removeCurrentVisuals(current, FloatingMenuState.SUSPENDED, null);
            } else {
                removeCurrentVisuals(current, FloatingMenuState.CLOSED, replacementReason);
                framesToRetain = closeDiscardedAncestors(current, ancestors, id,
                        replacementReason);
            }
            // Focus and lifecycle callbacks may open another screen while the old
            // one is being removed. Keep that screen and close frames which the
            // abandoned navigation would otherwise leave suspended.
            if (sessions.containsKey(player.getUniqueId())) {
                for (Frame frame : framesToRetain) {
                    frame.presentation.clearMeasurements();
                    notifyLifecycle(frame.definition, player,
                            new Handle(frame.id, player.getUniqueId()),
                            FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED,
                            replacementReason);
                }
                return;
            }
        }
        sessions.put(player.getUniqueId(), session);
        for (var mounted : worldPanels.views(session.playerId)) pauseWorld(mounted,player,true);
        if (!active(session)) return;
        try {
            seedSharedElements(session,shared);
            menuStatusDisplays.update(player, session.definition.screenId());
            FloatingMenuState previous = resumed
                    ? FloatingMenuState.SUSPENDED : FloatingMenuState.CLOSED;
            synchronizePlacementVisibility(session, player);
            transition(session, previous, FloatingMenuState.OPENING, null);
        } catch (RuntimeException exception) {
            closeIfCurrent(session, FloatingMenuCloseReason.ERROR);
            throw exception;
        }
    }

    public void close(Player player) {
        close(player, FloatingMenuCloseReason.USER);
    }

    private void close(Player player, FloatingMenuCloseReason reason) {
        requirePrimaryThread("close");
        inputs.invalidate(player.getUniqueId());
        tooltips.clear(player.getUniqueId());
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.state != FloatingMenuState.CLOSING) {
            session.exits.clear(parts -> destroySnapshots(player,parts));
            disposePopoverPanel(session);
            FloatingMenuState previous = session.state;
            session.state = FloatingMenuState.CLOSING;
            session.clearPressFeedback();
            session.layoutDirty = true;
            session.closeReason = reason;
            session.age = 0;
            session.reveal.to(0, session.totalTicks, session.animation.closingTicks(), session.animation.closingEasing());
            notifyLifecycle(session.definition, player, new Handle(session.id, session.playerId),
                    previous, FloatingMenuState.CLOSING, null);
            if (!active(session)) return;
            updateScrollCapture(session);
        }
    }

    public void closeAllImmediately() {
        ShutdownSequence shutdown = new ShutdownSequence();
        closeAllImmediately(FloatingMenuCloseReason.PLUGIN_DISABLE, shutdown);
        shutdown.finish("floating menus");
    }

    private void closeAllImmediately(FloatingMenuCloseReason reason, ShutdownSequence shutdown) {
        shutdown.attempt("menu native input sessions", inputs::clear);
        shutdown.attempt("menu tooltips", tooltips::clearAll);
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            shutdown.attempt("menu for " + playerId,
                    () -> closeImmediately(playerId, reason));
        }
        shutdown.attempt("world menu views",() -> worldPanels.clear(reason));
        if (disabled) { inputGates.clear(); pointerOwners.clear(); }

    }

    private void tick() {
        inputs.tick();
        for (Session session : allSessions()) {
            try {
                tick(session);
                if (active(session) && session.visualsSpawned && !session.obstructed()
                        && (session.state == FloatingMenuState.OPENING || session.state == FloatingMenuState.ACTIVE)) {
                    var committed = session.layoutSnapshot;
                    session.windows.publish(committed.frame(),() -> active(session) && !session.obstructed()
                            && session.layoutSnapshot == committed,
                            error -> plugin.getLogger().log(java.util.logging.Level.WARNING,"Menu window observer failed",error));
                } else session.windows.clear();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Floating menu tick failed for " + session.playerId, exception);
                closeIfCurrent(session, FloatingMenuCloseReason.ERROR);
            }
        }
        for (var viewer : allSessions().stream().map(view -> view.playerId).distinct().toList()) {
            Player player = Bukkit.getPlayer(viewer);
            if (player == null || !player.isOnline()) continue;
            try {
                var owner = inputOwner(player);
                if (owner == null) pointerOwners.remove(viewer); else pointerOwners.put(viewer,owner.id);
                var foreground = sessions.get(viewer);
                if (foreground != null) updatePointer(foreground,player,owner == foreground);
                for (var view : worldPanels.views(viewer)) updatePointer(view,player,owner == view);
                if (owner != null && active(owner)) { updateTooltip(owner,player); updateScrollCapture(owner); }
                else { tooltips.clear(viewer); scrollCaptures.remove(viewer); }
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"World menu input refresh failed for " + viewer,error);
                tooltips.clear(viewer); scrollCaptures.remove(viewer);
            }
        }
        if (++menuStatusSyncTicks >= MENU_STATUS_SYNC_INTERVAL_TICKS) {
            menuStatusSyncTicks = 0;
            menuStatusDisplays.updateTrackedPlayers(activeScreenIds());
        }
    }

    private Map<UUID, String> activeScreenIds() {
        Map<UUID, String> screens = new HashMap<>();
        sessions.forEach((playerId, session) ->
                screens.put(playerId, session.definition.screenId()));
        return screens;
    }

    private void tick(Session session) {
        if (!active(session)) return;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) {
            closeIfCurrent(session, FloatingMenuCloseReason.QUIT);
            return;
        }
        if (!player.getWorld().equals(session.openedAt.getWorld())) {
            closeIfCurrent(session, FloatingMenuCloseReason.WORLD_CHANGE);
            return;
        }
        if (session.mount == null && session.definition.movementPolicy().exceeded(
                player.getEyeLocation().distanceSquared(session.openedAt))) {
            closeImmediately(session.playerId, FloatingMenuCloseReason.DISTANCE);
            return;
        }
        if (session.mount == null && session.definition.anchorMode().followsPosition()) {
            Vector previousOrigin = session.origin.toVector();
            if (FloatingMenuAnchorMotion.translate(session.anchorPlayerPosition, player.getLocation(),
                    session.openedAt, session.anchorView, session.origin)) {
                Vector displacement = session.origin.toVector().subtract(previousOrigin);
                for (Button button : session.buttons) if (button.surface != null)
                    button.surface = button.surface.translated(MenuSurfaceAttachment.point(displacement));
                session.anchorMoved = true;
            }
        }
        if (session.mount == null && session.definition.anchorMode() == FloatingMenuAnchorMode.INERTIAL_TOOLBAR) {
            session.followHead(player, session.pointedId != null || inputBlocked(player)
                    || tooltips.holds(player, session.totalTicks));
        }
        if (session.mount != null) {
            if (!session.preferences.equals(preferences(player))) update(session,player,session.definition);
            if (!active(session)) return;
            pauseWorld(session,player,sessions.containsKey(session.playerId) || !MenuWorldPresentation.nearby(session.mount.surface(),player));
            if (!active(session) || session.hostPaused) return;
        }
        session.totalTicks++;
        String pressedId = session.pressFeedback.elementId();
        if (session.pressFeedback.advance(session.totalTicks)) {
            session.releasePress(pressedId);
            session.layoutDirty = true;
        }
        session.age++;
        if (session.state == FloatingMenuState.OPENING && session.age >= session.animation.openingTicks()) {
            transition(session, FloatingMenuState.OPENING, FloatingMenuState.ACTIVE, null);
            if (!active(session)) return;
            session.age = 0;
            session.layoutDirty = true;
            // Re-send the complete typed metadata after the client has had time to
            // register every virtual entity. This also commits the exact final
            // opening position instead of leaving it at the last staggered frame.
            refresh(session, player);
        } else if (session.state == FloatingMenuState.CLOSING
                && session.age >= session.animation.closingTicks()) {
            closeImmediately(session.playerId,
                    session.closeReason == null ? FloatingMenuCloseReason.USER : session.closeReason);
            return;
        }
        refreshLiveDefinitions(session, player);
        if (!active(session)) return;
        synchronizeViewpoint(session, player);
        boolean followRefit = session.definition.anchorMode().followsPosition()
                && (session.definition.anchorMode() == FloatingMenuAnchorMode.INERTIAL_TOOLBAR
                ? session.totalTicks % 4 == 0 : session.anchorMoved || session.totalTicks % 4 == 0);
        if (session.mount == null && (session.spaceDirty || session.totalTicks % 4 == 0 || followRefit)
                && session.state != FloatingMenuState.CLOSING) {
            session.spaceDirty = false;
            refreshSpatialAnchor(session, player, true);
        }
        if (session.obstructed()) return;
        animate(session, player);
    }

    private void pauseWorld(Session session, Player player, boolean paused) {
        if (session.hostPaused == paused) return;
        session.hostPaused = paused;
        session.clearPressFeedback();
        if (paused) {
            if (session.id.equals(pointerOwners.get(session.playerId))) {
                pointerOwners.remove(session.playerId); tooltips.clear(session.playerId); scrollCaptures.remove(session.playerId);
            }
            session.inputGate.consume();
        }
        synchronizePlacementVisibility(session,player);
        notifyLifecycle(session.definition,player,new Handle(session.id,session.playerId),
                paused ? session.state : FloatingMenuState.SUSPENDED,
                paused ? FloatingMenuState.SUSPENDED : session.state,null);
    }

    private void updateTooltip(Session session, Player player) {
        updateTooltip(session, player, session.pointedId, false);
    }

    private void updateTooltip(Session session, Player player, String sourceKey, boolean immediate) {
        if (session.state != FloatingMenuState.ACTIVE || inputBlocked(player)) {
            tooltips.clear(session.playerId);
            return;
        }
        tooltips.update(player, sourceKey, session.totalTicks, session.spatialScale, session.preferences.typographyFactor(),
                session.preferences.fieldOfView(), session.preferences.theme(), immediate, session::tooltipSurfaces, key -> {
                    Button source = session.button(key);
                    MenuScene.Entry entry = source == null ? null : session.definition.entries().get(key);
                    if (entry == null || !inputVisible(session, source)) return null;
                    var surface = source.surface;
                    var angles = surface.rotation().angles();
                    var range = session.layoutSnapshot.ranges().get(key);
                    if (range != null && source.pointerBehavior == UiPointerBehavior.OPERATE) {
                        var pointer = horizontalFraction(session, player, source);
                        if (pointer.isEmpty()) return null;
                        Component preview = range.range().text(range.range().at(pointer.getAsDouble()));
                        if (entry.tooltip() != null) preview = preview.append(Component.newline()).append(entry.tooltip().text());
                        return new MenuTooltipController.Source(new org.encinet.mik.shared.menu.ui.UiTooltip(preview, 160, 3),
                                MenuSurfaceAttachment.vector(surface.center()), (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                                surface.scale() / session.spatialScale, surface.size(), true);
                    }
                    var tooltip = MenuTooltipContent.resolve(entry, session.layoutSnapshot.icons().containsKey(key) ? Component.empty() : source.label, text -> session.definition.entries().values().stream()
                            .filter(other -> !other.interactive() && !session.layoutSnapshot.icons().containsKey(other.id()) && MenuTooltipContent.same(text, other.label()))
                            .anyMatch(other -> {
                                Button inline = session.button(other.id());
                                return inline != null && inputVisible(session, inline);
                            }));
                    return tooltip == null ? null : new MenuTooltipController.Source(tooltip,
                            MenuSurfaceAttachment.vector(surface.center()), (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                            surface.scale() / session.spatialScale, surface.size(), false);
                });
    }

    private void refreshLiveDefinitions(Session session, Player player) {
        if (inputBlocked(player)) return;
        for (Frame frame : List.copyOf(session.ancestors)) {
            if (!refreshDue(session.totalTicks, frame.definition.refresh())) continue;
            refreshFrameDefinition(session, frame, player);
            if (!active(session)) return;
        }
        FloatingMenuRefresh refresh = session.definition.refresh();
        if (session.state != FloatingMenuState.ACTIVE
                || !refreshDue(session.totalTicks, refresh)) {
            return;
        }
        Object revision = refresh.revision(player);
        if (refresh.revision() != null
                && java.util.Objects.equals(revision, session.refreshRevision)) {
            return;
        }
        session.refreshRevision = revision;
        invokeRefresh(refresh, player, new Handle(session.id, session.playerId));
    }

    /** Revalidates a suspended frame without changing its navigation position. */
    private void refreshFrameDefinition(Session session, Frame frame, Player player) {
        FloatingMenuRefresh refresh = frame.definition.refresh();
        if (!refresh.enabled()) return;
        Object revision = refresh.revision(player);
        if (refresh.revision() != null
                && java.util.Objects.equals(revision, frame.refreshRevision)) {
            return;
        }
        frame.refreshRevision = revision;
        invokeRefresh(refresh, player, new Handle(frame.id, session.playerId));
    }

    private static boolean refreshDue(long ticks, FloatingMenuRefresh refresh) {
        return refresh.enabled() && ticks % refresh.intervalTicks() == 0;
    }

    private void invokeRefresh(FloatingMenuRefresh refresh, Player player,
                               FloatingMenuHandle handle) {
        try {
            refresh.action().execute(player, handle);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Floating menu live refresh failed for " + player.getName(), exception);
        }
    }

    private void acceptInput(Target target, FloatingMenuInteraction interaction) {
        Session session = lookup(target.playerId,target.sessionId);
        if (session == null || session.obstructed() || session.state != FloatingMenuState.ACTIVE) return;
        Button selected = session.button(target.elementId);
        if (selected == null) return;
        if (!session.inputGate.accept(target.elementId, interaction)) return;
        activate(target, interaction);
    }

    private void activate(Target target, FloatingMenuInteraction interaction) {
        Session session = lookup(target.playerId,target.sessionId);
        Player player = Bukkit.getPlayer(target.playerId);
        if (session == null || player == null || session.obstructed() || session.state != FloatingMenuState.ACTIVE) return;
        if (inputBlocked(player) || !session.inputGate.permits() || inputOwner(player) != session) return;
        if (session.layoutSnapshot.popover() != null) {
            var activation = session.popoverInput().activation(target.elementId,
                    session.popoverHit(player.getEyeLocation()).isPresent(), interaction.scroll());
            if (activation == MenuPopoverInput.Activation.CONSUME) { session.inputGate.consume(); return; }
            if (activation == MenuPopoverInput.Activation.DISMISS) {
                dismissPopover(session, player); session.inputGate.consume(); return;
            }
            if (activation == MenuPopoverInput.Activation.DISMISS_AND_ACT) {
                session.replacingPopover = true;
                try {
                    dismissPopover(session, player);
                    if (active(session)) activateElement(session, player, target, interaction);
                } finally {
                    session.replacingPopover = false;
                    if (session.definition.popover() == null) session.popoverAttachment.reset();
                    if (active(session)) renderPopoverCatcher(session, player);
                }
                return;
            }
        }
        activateElement(session, player, target, interaction);
    }

    private void activateElement(Session session, Player player, Target target, FloatingMenuInteraction interaction) {
        MenuScene.Entry selectedEntry = target.elementId == null
                ? null : session.definition.entries().get(target.elementId);
        Button selected = session.button(target.elementId);
        if (selected != null && !inputVisible(session, selected)) return;
        if (selectedEntry != null && !selectedEntry.enabled() && !interaction.scroll()) {
            updateTooltip(session, player, target.elementId, true);
            return;
        }
        FloatingMenuAction directAction = actionFor(session, target.elementId, interaction);
        if (directAction == null) {
            return;
        }
        session.selectPress(selected != null && selected.pointerBehavior == UiPointerBehavior.OPERATE ? target.elementId : null);
        session.layoutDirty = true;
        sounds.play(player,0.55F,session.feedback.pitch(interaction));
        if (selected != null && selected.pointerBehavior == UiPointerBehavior.OPERATE) {
            updateButton(session, player, selected, FloatingMenuElementState.PRESSED, selected.revealProgress);
        }
        try {
            session.inputGate.dispatch(() -> directAction.execute(player, new Handle(session.id, session.playerId), interaction));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Floating menu action failed for player " + player.getName()
                            + " at element " + target.elementId, exception);
            closeIfCurrent(session,FloatingMenuCloseReason.ERROR);
        }
        // A handler opening another menu replaces this session. Otherwise redraw
        // mutable toggles while retaining the same view and lifecycle.
        if (active(session)
                && session.state == FloatingMenuState.ACTIVE) {
            refresh(session, player);
        }
    }

    private FloatingMenuAction actionFor(Session session, String elementId,
                                         FloatingMenuInteraction interaction) {
        FloatingMenuAction direct = elementId == null ? null
                : Optional.ofNullable(session.actions.get(elementId))
                .map(actions -> actions.get(interaction)).orElse(null);
        var entry = elementId == null ? null : session.definition.entries().get(elementId);
        if (interaction.scroll() && entry != null && !entry.enabled()) direct = null;
        if (session.layoutSnapshot.popover() != null && interaction.scroll()) {
            Player player = Bukkit.getPlayer(session.playerId);
            boolean inside = player != null && session.popoverHit(player.getEyeLocation()).isPresent();
            return switch (session.popoverInput().scrollScope(elementId,inside)) {
                case BLOCKED -> null;
                case POPOVER -> layerAction(session, elementId, interaction, MenuPopover.owns(elementId) ? direct : null, true);
                case PARENT -> layerAction(session, elementId, interaction, direct, false);
            };
        }
        if (direct != null) return direct;
        if (session.definition.popover() != null) {
            if (MenuPopover.owns(elementId)) return layerAction(session, elementId, interaction, null, true);
            if (session.definition.popover().modal() || elementId == null) return null;
        }
        return layerAction(session, elementId, interaction, null, false);
    }

    private FloatingMenuAction layerAction(Session session, String elementId, FloatingMenuInteraction interaction,
                                           FloatingMenuAction direct, boolean popover) {
        if (direct != null) return direct;
        var layer = popover ? session.layoutSnapshot.popover().content() : session.layoutSnapshot;
        var fallback = popover ? layer.definition().triggers().get(interaction) : session.triggers.get(interaction);
        if (!interaction.scroll() || layer.frame().pagination().size() < 2) return fallback;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null) return null;
        Location eye = player.getEyeLocation();
        var paging = MenuPaginationInput.action(layer.pagination(), elementId, popover ? MenuPopover.scoped("") : "", interaction, region -> {
            var pose = popover ? session.popoverPose(region.pose()) : region.pose();
            double scale = session.spatialScale * pose.scale();
            var angles = session.orientation.compose(pose.rotation()).angles();
            return FloatingMenuSurfaceGeometry.intersect(eye.toVector(), eye.getDirection(), session.position(pose).toVector(),
                            (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                            region.size().width() * scale, region.size().height() * scale, 0)
                    .filter(hit -> FloatingMenuWorldOcclusion.clearToSurface(eye, hit.distance()));
        });
        return paging == null ? fallback : paging;
    }

    private void disposePopoverPanel(Session session) {
        var previous = session.popoverPanel;
        session.popoverPanel = null;
        if (previous == null) return;
        try { previous.close(); }
        catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Popover content cleanup failed for " + session.playerId, error);
        }
    }

    private void dismissPopover(Session session, Player player) {
        var popover = session.definition.popover();
        if (popover == null) return;
        disposePopoverPanel(session);
        tooltips.clear(session.playerId);
        try {
            session.inputGate.dispatch(() -> popover.dismissed().execute(player, new Handle(session.id, session.playerId)));
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Menu popover dismissal failed for " + player.getName(), error);
            closeIfCurrent(session, FloatingMenuCloseReason.ERROR);
            return;
        }
        if (active(session) && session.definition.popover() == popover)
            update(session, player, session.definition.toBuilder().popover(null).build());
    }

    private boolean routePointerClick(Player player, boolean primary) {
        Session session = inputOwner(player);
        if (session == null || session.obstructed() || session.state != FloatingMenuState.ACTIVE || inputBlocked(player)) return false;
        if (tooltips.consumeClick(player, primary)) { session.inputGate.consume(); return true; }
        String picked = pickPointerTarget(session, player);
        if (picked == null && session.definition.popover() == null) {
            if (session.mount == null) return false;
            session.inputGate.consume(); return true;
        }
        if (!session.inputGate.permits()) return true;
        if (picked != null) acceptInput(new Target(player.getUniqueId(), session.id, picked), primary ? FloatingMenuInteraction.PRIMARY : FloatingMenuInteraction.SECONDARY);
        else {
            if (session.popoverInput().activation(null, session.popoverHit(player.getEyeLocation()).isPresent(), false)
                    == MenuPopoverInput.Activation.DISMISS) dismissPopover(session, player);
            session.inputGate.consume();
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldClick(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getHand() != EquipmentSlot.HAND) {
            if (protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
            return;
        }
        // DENY can come from region protection. Still shield the backing block, but never invoke an action.
        if (event.getClickedBlock() != null && event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            if (protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
            return;
        }
        if (routePointerClick(event.getPlayer(), event.getAction().isLeftClick())
                || protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPopoverEntityClick(PlayerInteractEntityEvent event) {
        if (!event.isCancelled() && event.getHand() == EquipmentSlot.HAND && routePointerClick(event.getPlayer(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPopoverEntityAttack(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() && event.getDamager() instanceof Player player && routePointerClick(player, true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (!event.isAttack() && event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Session session = inputOwner(player);
        if (session == null || session.obstructed() || session.state != FloatingMenuState.ACTIVE || inputBlocked(player)
                || !session.inputGate.permits()) return;
        if (tooltips.consumeClick(player, event.isAttack())) {
            session.inputGate.consume();
            return;
        }
        Target catcher = targets.get(event.getEntityId());
        if ((catcher == null || !catcher.playerId.equals(player.getUniqueId()) || !catcher.sessionId.equals(session.id))
                && !tooltips.ownsEntity(player.getUniqueId(), event.getEntityId())) return;
        String picked = pickPointerTarget(session, player);
        if (picked == null) { routePointerClick(player, event.isAttack()); return; }
        acceptInput(new Target(player.getUniqueId(), session.id, picked), event.isAttack()
                ? FloatingMenuInteraction.PRIMARY : FloatingMenuInteraction.SECONDARY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        if (inputBlocked(event.getPlayer())) return;
        UUID playerId = event.getPlayer().getUniqueId();
        if (!scrollCaptures.contains(playerId)) return;
        Session session = inputOwner(event.getPlayer());
        int clockwise = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        if (clockwise != 1 && clockwise != 8) return;
        FloatingMenuInteraction interaction = clockwise == 1
                ? FloatingMenuInteraction.SCROLL_DOWN : FloatingMenuInteraction.SCROLL_UP;
        if (session == null || session.obstructed() || session.state != FloatingMenuState.ACTIVE) return;
        if (tooltips.occludes(event.getPlayer()) || !session.inputGate.permits()) {
            event.setCancelled(true);
            return;
        }
        String picked = pickPointerTarget(session,event.getPlayer());
        if (actionFor(session, picked, interaction) == null) {
            if (session.definition.popover() != null && (session.definition.popover().modal()
                    || session.popoverHit(event.getPlayer().getEyeLocation()).isPresent())) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        activate(new Target(playerId, session.id, picked), interaction);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (protectsWorldBehindMenu(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpaceBlockPlace(BlockPlaceEvent event) {
        invalidateSpace(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpaceBlockBreak(BlockBreakEvent event) {
        invalidateSpace(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpacePistonExtend(BlockPistonExtendEvent event) {
        invalidateSpace(event.getBlock().getLocation());
        event.getBlocks().forEach(block -> invalidateSpace(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpacePistonRetract(BlockPistonRetractEvent event) {
        invalidateSpace(event.getBlock().getLocation());
        event.getBlocks().forEach(block -> invalidateSpace(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpaceBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(block -> invalidateSpace(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpaceEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(block -> invalidateSpace(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpaceEntityChangeBlock(EntityChangeBlockEvent event) {
        invalidateSpace(event.getBlock().getLocation());
    }

    private void invalidateSpace(Location changed) {
        for (Session session : allSessions()) {
            if (session.spaceDirty) continue;
            if (changed.getWorld().equals(session.anchorView.getWorld())
                    && changed.distanceSquared(session.anchorView) < 64) session.spaceDirty = true;
        }
    }

    private boolean protectsWorldBehindMenu(Player player) {
        Session session = inputOwner(player);
        if (session == null || session.obstructed() || session.state == FloatingMenuState.CLOSED
                || session.state == FloatingMenuState.SUSPENDED) {
            return false;
        }
        if (session.mount != null || session.definition.popover() != null || tooltips.occludes(player) || !session.inputGate.permits()) return true;
        if (session.definition.spatialFrame().viewerCentered()) {
            Location eye = player.getEyeLocation();
            if (session.buttons.stream().anyMatch(button -> surfaceHit(session, eye, button).isPresent())) return true;
            return session.layoutSnapshot.bounds().surfaces().stream().anyMatch(surface -> {
                var hit = FloatingMenuSurfaceGeometry.intersect(eye.toVector(), eye.getDirection(),
                        session.position(surface.pose()).toVector(), session.presentationYaw + (float) surface.pose().yawDegrees(),
                        (float) surface.pose().pitchDegrees(), (float) surface.pose().rollDegrees(),
                        surface.size().width() * session.spatialScale * surface.pose().scale(),
                        surface.size().height() * session.spatialScale * surface.pose().scale(), HOVER_SURFACE_MARGIN * session.spatialScale * surface.pose().scale());
                return hit.isPresent() && FloatingMenuWorldOcclusion.clearToSurface(eye, hit.get().distance());
            });
        }
        return WORLD_INTERACTION_GUARD.contains(player.getEyeLocation(), session.origin,
                session.interactionFraming);
    }

    private void spawn(Session session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || session.obstructed()) return;
        session.visualsSpawned = true;
        // Axiom must receive the UUIDs before the entities are spawned, otherwise
        // its editor can briefly create a white gizmo for the first rendered frame.
        axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids());
        if (session.definition.titleVisible()) {
            virtualEntities.spawn(player, session.titleId, session.titleUuid,
                    VirtualMenuEntityRenderer.Kind.TEXT, session.headerPosition(), 0);
            session.titleSpawned = true;
            updateTitle(session, player, session.titleId, session.definition.title());
        }
        for (Decoration decoration : session.decorations) {
            spawnDecoration(session, player, decoration);
        }
        for (Button button : session.buttons) spawnButton(session, player, button);
        animate(session, player);
    }

    private void spawnButton(Session session, Player player, Button button) {
        Location center = session.position(button);
        var angles = session.orientation.compose(button.currentPose().rotation()).angles();
        float yaw = (float) angles.yaw();
        float pitch = (float) angles.pitch();
        Vector panelUp = FloatingMenuSurfaceGeometry.panelUp(yaw, pitch, (float) angles.roll());
        Vector panelNormal = FloatingMenuSurfaceGeometry.facing(yaw, pitch);
        FloatingMenuNodeGeometry.Placement placement = FloatingMenuNodeGeometry.placement(
                button.style, button.measurement.text().size().height() * typography(session, button),
                nodeScale(session, button), 0);
        double anchorDown = FloatingMenuNodeGeometry.textAnchorDown(button.measurement.text().renderedHeight(),
                nodeScale(session, button) * typography(session, button));
        Location textAt = center.clone().add(panelUp.clone().multiply(placement.textUp() - anchorDown))
                .add(panelNormal.clone().multiply(placement.textForward()));
        if (button.controlSurface) {
            virtualEntities.track(player,button.surfaceId);
            virtualEntities.spawn(player, button.surfaceId, button.surfaceUuid,
                    VirtualMenuEntityRenderer.Kind.TEXT, center, 0, 0);
        }
        virtualEntities.track(player,button.textId);
        virtualEntities.spawn(player, button.textId, button.textUuid,
                VirtualMenuEntityRenderer.Kind.TEXT, textAt, 0, 0);
        FloatingMenuElementState initialState = MenuNodeFeedback.state(button.pointerBehavior,button.enabled,button.selected,false,false);
        if (!button.textOnly) {
            virtualEntities.track(player,button.visualId);
            virtualEntities.spawn(player, button.visualId, button.visualUuid,
                    button.block ? VirtualMenuEntityRenderer.Kind.BLOCK
                            : VirtualMenuEntityRenderer.Kind.ITEM,
                    center.clone().add(panelUp.clone().multiply(placement.visualUp())), 0, 0);
        }
        updateButton(session, player, button, initialState, 0);
        if (button.pointerBehavior == UiPointerBehavior.OPERATE) {
            var proxy = interactionProxy(player,session,button);
            virtualEntities.spawn(player, button.hitboxId, button.hitboxUuid,
                    VirtualMenuEntityRenderer.Kind.INTERACTION,
                    proxyLocation(player,proxy), session.presentationYaw, 0);
            virtualEntities.interaction(player, button.hitboxId,
                    (float) proxy.width(), (float) proxy.height());
            button.interactionProxy = proxy;
            targets.put(button.hitboxId, new Target(session.playerId, session.id, button.id));
        }
    }

    private static Location proxyLocation(Player player, MenuInteractionProxy.Bounds proxy) {
        return new Location(player.getWorld(),proxy.base().right(),proxy.base().up(),proxy.base().forward());
    }

    private static MenuInteractionProxy.Bounds interactionProxy(Player player, Session session, Button button) {
        var surface = button.surface == null ? session.nodeSurface(button) : button.surface;
        return MenuInteractionProxy.project(MenuSurfaceAttachment.point(player.getEyeLocation().toVector()),
                surface.center(),interactionSize(button,session));
    }

    private void updateInteractionProxy(Session session, Player player, Button button) {
        var next = interactionProxy(player,session,button);
        var previous = button.interactionProxy;
        if (next.equals(previous)) return;
        if (previous == null || next.width() != previous.width() || next.height() != previous.height())
            virtualEntities.interaction(player,button.hitboxId,(float) next.width(),(float) next.height());
        if (previous == null || !next.base().equals(previous.base()))
            teleport(player,button.hitboxId,proxyLocation(player,next),0,0);
        button.interactionProxy = next;
    }

    private void renderPopoverCatcher(Session session, Player player) {
        var geometry = session.layoutSnapshot.popover();
        var placement = session.popoverAttachment.placement();
        if (geometry == null || placement == null) {
            if (session.replacingPopover) return;
            if (session.popoverCatcher != null) {
                virtualEntities.destroy(player, session.popoverHitboxId);
                targets.remove(session.popoverHitboxId);
                session.popoverCatcher = null;
            }
            return;
        }
        var angles = placement.rotation().angles();
        double scale = session.spatialScale * session.popoverAttachment.readingScale();
        var size = geometry.size();
        var bounds = FloatingMenuSurfaceGeometry.catcher((float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                size.width() * scale, size.height() * scale);
        var catcher = MenuInteractionProxy.project(MenuSurfaceAttachment.point(player.getEyeLocation().toVector()),placement.center(),bounds);
        if (catcher.equals(session.popoverCatcher)) return;
        Location at = proxyLocation(player,catcher);
        if (session.popoverCatcher == null) {
            virtualEntities.spawn(player, session.popoverHitboxId, session.popoverHitboxUuid, VirtualMenuEntityRenderer.Kind.INTERACTION, at, 0);
            targets.put(session.popoverHitboxId, new Target(session.playerId, session.id, null));
        } else virtualEntities.teleport(player, session.popoverHitboxId, at, 0, 0);
        virtualEntities.interaction(player, session.popoverHitboxId, (float) catcher.width(), (float) catcher.height());
        session.popoverCatcher = catcher;
    }

    private void spawnDecoration(Session session, Player player, Decoration decoration) {
        if (!decoration.definition.worldAnchored() && !(decoration.definition.content() instanceof FloatingMenuDecoration.Image))
            virtualEntities.track(player,decoration.entityId);
        FloatingMenuDecoration.Content content = decoration.definition.content();
        VirtualMenuEntityRenderer.Kind kind = decorationKind(content);
        Location spawnAt = decoration.definition.worldAnchored()
                ? session.position(decoration).subtract(0.0, 0.18, 0.0)
                : session.position(decoration);
        float spawnYaw = decoration.definition.placement() instanceof FloatingMenuPlacement.World world
                ? (float) world.yawDegrees() : 0;
        float spawnPitch = decoration.definition.placement() instanceof FloatingMenuPlacement.World world
                ? (float) world.pitchDegrees() : 0;
        virtualEntities.spawn(player, decoration.entityId, decoration.uuid, kind,
                spawnAt, spawnYaw, spawnPitch);
        decoration.visualRotation = decoration.definition.worldAnchored() ? UiRotation.IDENTITY : session.orientation.compose(decoration.renderedPose.rotation());
        if (decoration.definition.worldAnchored() && content instanceof FloatingMenuDecoration.Text)
            maskWorldText(decoration,spawnAt,spawnYaw,spawnPitch,worldOverlayMask(session,player));
        updateDecoration(session, player, decoration);
    }

    private void updateDecoration(Session session, Player player, Decoration decoration) {
        var context = decorationContext(session, decoration);
        decoration.metadata.publish(context, () -> {
            double coordinateScale = context.coordinateScale();
            double typographyScale = context.typographyScale();
            switch (decoration.definition.content()) {
                case FloatingMenuDecoration.IconPart part -> {
                    if (decoration.iconPaint == null) decoration.iconPaint = MenuIconRenderer.paint(part, 0, true, true, context.theme());
                    MenuIconRenderer.render(virtualEntities, player, decoration.entityId, decoration.iconPaint,
                            coordinateScale, decoration.lastReveal < 0 ? 1 : decoration.lastReveal, context.rotation());
                }
                case FloatingMenuDecoration.RangePart part -> {
                    if (decoration.rangePaint == null) decoration.rangePaint = MenuRangeRenderer.paint(part, false, OptionalDouble.empty(), context.theme());
                    MenuRangeRenderer.render(virtualEntities, player, decoration.entityId, decoration.rangePaint,
                            coordinateScale, decoration.lastReveal < 0 ? 1 : decoration.lastReveal, context.rotation());
                }
                case FloatingMenuDecoration.Image image -> images.fallback(player, decoration.entityId, image,
                        coordinateScale, typographyScale, context.rotation());
                case FloatingMenuDecoration.Surface surface -> {
                    virtualEntities.surface(player, decoration.entityId, surface.background(),
                            (float) (surface.width() * coordinateScale), (float) (surface.height() * coordinateScale),
                            decoration.lastReveal < 0 ? 1 : decoration.lastReveal, context.rotation());
                }
                case FloatingMenuDecoration.Text text -> virtualEntities.text(
                        player, decoration.entityId, decoration.interfaceOccluded ? Component.empty() : text.text(),
                        decoration.interfaceOccluded ? 0 : text.backgroundColor().argb(context.theme()),
                        text.displayWidth(), text.displayHeight(),
                        Math.max(80, Math.round(text.displayWidth() * 64.0F)),
                        (float) (text.scale() * coordinateScale * typographyScale),
                        text.alignment(), text.seeThrough(), 1, 1, context.rotation());
                case FloatingMenuDecoration.Visual visual -> virtualEntities.visual(
                        player, decoration.entityId, visual.item(), visual.block(),
                        (float) (visual.scale() * coordinateScale), context.rotation());
                case FloatingMenuDecoration.BlockVolume volume -> virtualEntities.volume(
                        player, decoration.entityId, volume.block(),
                        (float) (volume.width() * coordinateScale),
                        (float) (volume.height() * coordinateScale),
                        (float) (volume.depth() * coordinateScale), context.rotation());
            }
        });
    }

    private static MenuMetadataState.Context decorationContext(Session session, Decoration decoration) {
        return MenuMetadataState.Context.decoration(decoration.definition, decoration.renderedPose, decoration.visualRotation,
                session.spatialScale, session.preferences.typographyFactor(), session.preferences.theme());
    }

    private static VirtualMenuEntityRenderer.Kind decorationKind(
            FloatingMenuDecoration.Content content) {
        return switch (content) {
            case FloatingMenuDecoration.Text _ -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.Surface _ -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.Image _ -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.RangePart _ -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.IconPart _ -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.Visual visual -> visual.block()
                    ? VirtualMenuEntityRenderer.Kind.BLOCK : VirtualMenuEntityRenderer.Kind.ITEM;
            case FloatingMenuDecoration.BlockVolume _ -> VirtualMenuEntityRenderer.Kind.BLOCK;
        };
    }

    private static FloatingMenuPose decorationPose(FloatingMenuDecoration decoration, SceneGeometry geometry,
                                                   FloatingMenuSpatialFrame frame, FloatingMenuFieldOfView fov, double scale) {
        if (!(decoration.placement() instanceof FloatingMenuPlacement.Local local)) return null;
        return geometry.generatedDecorations().containsKey(decoration.id()) ? local.pose()
                : FloatingMenuViewProjection.pose(frame, fov, scale, local.pose());
    }

    private void destroyButton(Player player, Button button) {
        targets.remove(button.hitboxId);
        List<Integer> ids = new ArrayList<>(4);
        ids.add(button.textId);
        if (button.controlSurface) ids.add(button.surfaceId);
        if (!button.textOnly) ids.add(button.visualId);
        if (button.pointerBehavior == UiPointerBehavior.OPERATE) ids.add(button.hitboxId);
        virtualEntities.destroy(player, ids.stream().mapToInt(Integer::intValue).toArray());
    }

    private void destroySnapshots(Player player, List<MenuVisualSnapshot> parts) {
        if (player != null && player.isOnline()) virtualEntities.destroy(player,parts.stream().mapToInt(MenuVisualSnapshot::entityId).toArray());
    }

    private void retireButton(Session session, Player player, Button button, int liveEntities) {
        targets.remove(button.hitboxId);
        if (button.pointerBehavior == UiPointerBehavior.OPERATE) virtualEntities.destroy(player,button.hitboxId);
        var ids = new ArrayList<Integer>();
        ids.add(button.textId);
        if (button.controlSurface) ids.add(button.surfaceId);
        if (!button.textOnly) ids.add(button.visualId);
        var parts = virtualEntities.detach(player,ids.stream().mapToInt(Integer::intValue).toArray());
        var surface = button.surface;
        if (surface != null) surface = new MenuSurfaceAttachment.Surface(surface.center(),surface.rotation(),surface.size(),surface.scale(),
                FloatingMenuNodeGeometry.surfaceDepth(button.style));
        if (session.preferences.reducedMotion() || !session.visualsSpawned || session.state != FloatingMenuState.ACTIVE
                || !session.exits.retain(parts,surface,session.totalTicks,session.animation.closingTicks(),liveEntities)) {
            virtualEntities.destroy(player,ids.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private void retireDecoration(Session session, Player player, Decoration decoration, int liveEntities) {
        var parts = virtualEntities.detach(player,decoration.entityId);
        var pose = FloatingMenuPose.transformed(decoration.renderedPose.point(),session.orientation.inverse().compose(decoration.visualRotation),decoration.renderedPose.scale());
        var local = decoration.definition.worldAnchored() ? null : FloatingMenuAnchorResolver.decorationSurface(
                decoration.definition,pose,session.preferences.typographyFactor());
        var bounds = local == null ? null : session.worldSurface(local);
        String source = switch (decoration.definition.content()) {
            case FloatingMenuDecoration.RangePart part -> part.source();
            case FloatingMenuDecoration.IconPart part -> part.source();
            default -> null;
        };
        if (source != null) {
            var button = session.button(MenuPopover.owns(decoration.definition.id()) ? MenuPopover.scoped(source) : source);
            bounds = button == null ? null : button.surface;
        }
        if (session.preferences.reducedMotion() || !session.visualsSpawned || session.state != FloatingMenuState.ACTIVE
                || !session.exits.retain(parts,bounds,session.totalTicks,session.animation.closingTicks(),liveEntities))
            virtualEntities.destroy(player,decoration.entityId);
    }

    private void renderExits(Session session, Player player) {
        if (session.exits.size() == 0) return;
        int before = session.exits.size();
        var eye = player.getEyeLocation();
        double maximumRange = session.exits.bounds().stream().mapToDouble(surface ->
                new Vector(surface.center().right(),surface.center().up(),surface.center().forward()).distance(eye.toVector())
                        + Math.hypot(surface.size().width(),surface.size().height()) * surface.scale() + surface.depth()).max().orElse(0);
        var space = session.presentation.motionSpace(player,eye,Math.min(maximumRange,FloatingMenuWorldSpace.MAX_TRACE_DISTANCE));
        session.exits.advance(session.totalTicks,session.layoutSnapshot.entityCount() + reservedEntities(session),session.preferences.reducedMotion(),surface -> {
            var center = new Vector(surface.center().right(),surface.center().up(),surface.center().forward());
            double range = center.distance(eye.toVector()) + Math.hypot(surface.size().width(),surface.size().height()) * surface.scale() + surface.depth();
            if (range > FloatingMenuWorldSpace.MAX_TRACE_DISTANCE) return false;
            var right = surface.rotation().apply(new UiVector3(1,0,0));
            var up = surface.rotation().apply(new UiVector3(0,1,0));
            return space.limitSurface(eye,center,new Vector(right.right(),right.up(),right.forward()),new Vector(up.right(),up.up(),up.forward()),
                    surface.size().width() * surface.scale() / 2,surface.size().height() * surface.scale() / 2,
                    Math.max(.01,surface.depth() / 2) * surface.scale()) >= 1 - 1e-9;
        },parts -> destroySnapshots(player,parts),(part,remaining) -> part.paint().render(virtualEntities,player,part.entityId(),remaining));
        if (session.exits.size() != before) axiomGizmos.synchronize(player,session.id,session.virtualEntityUuids());
    }

    private static void seedSharedElements(Session session, Map<UiKey,MenuSharedElements.Source> sources) {
        if (sources.isEmpty() || session.obstructed() || session.preferences.reducedMotion() || session.definition.loading()) return;
        int paired = 0;
        for (var target : MenuSharedElements.targets(session.layoutSnapshot).entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().value())).toList()) {
            var source = sources.get(target.getKey());
            var button = session.button(target.getValue());
            if (source == null || button == null || source.style() != button.style || paired >= 8) continue;
            var size = session.layoutSnapshot.sizes().get(button.id);
            if (size.width() <= 0 || size.height() <= 0) continue;
            button.sharedTransition = true;
            button.initializeLayout(MenuSharedElements.local(source,MenuSurfaceAttachment.point(session.origin.toVector()),
                    session.presentationYaw,session.spatialScale,size),session.totalTicks);
            button.appearProgress = 1;
            button.appear.snap(1,session.totalTicks);
            paired++;
        }
        session.layoutMotion.clear();
        session.prepareLayout();
    }

    private void update(Session session, Player player, MenuScene requested) {
        update(session,player,requested,session.mount);
    }

    private void update(Session session, Player player, MenuScene requested, MenuWorldPanels.Mount mounted) {
        if (session.state == FloatingMenuState.CLOSING || session.state == FloatingMenuState.CLOSED) return;
        FloatingMenuPreferences selected = preferences(player);
        MenuAnchorView nextView = requested.anchorMode() != session.definition.anchorMode()
                ? MenuAnchorView.capture(player, requested) : null;
        Location readingEye = nextView == null ? session.anchorView.clone().add(0,
                requested.viewpoint().eyeHeight(player) - session.anchorEyeHeight, 0) : nextView.readingEye();
        var prepared = mounted != null ? MenuWorldPresentation.prepare(session.presentation,mounted.surface(),requested,
                selected.typographyFactor(),selected.theme(),worldPanels.availableEntities(session)) : session.presentation.prepare(player, readingEye,
                nextView == null ? session.requestedForward : nextView.forward(), requested,
                selected.typographyFactor(), selected.layout().factor(), selected.fieldOfView(), selected.theme(),
                (content, frame) -> {
                    if (session.popoverPanel == null) return content;
                    var composed = session.popoverPanel.compose(content, frame);
                    if (!session.popoverPanel.active()) session.popoverPanel = null;
                    return composed;
                });
        SceneGeometry nextLayout = prepared.geometry();
        if (nextLayout == session.layoutSnapshot) {
            session.mount = mounted;
            commitPreferences(session, selected);
            session.applyAnchor(prepared.anchor(), session.definition, nextLayout, false);
            synchronizePlacementVisibility(session, player);
            return;
        }
        MenuScene definition = nextLayout.definition();
        if (definition.popover() == null) disposePopoverPanel(session);
        var beforePopover = session.definition.popover();
        var afterPopover = definition.popover();
        if (beforePopover == null != (afterPopover == null) || beforePopover != null && afterPopover != null
                && (!beforePopover.source().equals(afterPopover.source()) || beforePopover.modal() != afterPopover.modal())) {
            tooltips.clear(session.playerId);
        }
        if (mounted == null) inputs.contentChanged(session.playerId);
        if (session.titleSpawned && !definition.titleVisible()) {
            virtualEntities.destroy(player, session.titleId);
            session.titleSpawned = false;
        }
        String pointedId = session.pointedId;
        FloatingMenuFocusAction previousFocus = pointedId == null
                ? null : session.focusActions.get(pointedId);
        MenuScene.Entry pointedEntry = pointedId == null
                ? null : definition.entries().get(pointedId);
        FloatingMenuFocusAction nextFocus = pointedEntry == null || !pointedEntry.enabled() ? null : pointedEntry.focusAction();
        if (previousFocus != null && previousFocus != nextFocus) {
            invokeFocus(previousFocus, player, new Handle(session.id, session.playerId), false);
            if (!active(session)) return;
        }
        Map<String, Button> previous = new HashMap<>();
        int liveEntities = nextLayout.entityCount() + reservedEntities(session);
        session.exits.trim(liveEntities,parts -> destroySnapshots(player,parts));
        for (Button button : session.buttons) previous.put(button.id, button);
        List<Button> nextButtons = new ArrayList<>();
        List<Button> buttonsToSpawn = new ArrayList<>();
        int index = 0;
        for (MenuScene.Entry entry : definition.entries().values()) {
            Button button = previous.remove(entry.id());
            if (button == null || button.style != entry.style()
                    || button.pointerBehavior != entry.pointerBehavior()
                    || button.controlSurface != controlSurface(nextLayout, entry)) {
                if (button != null) destroyButton(player, button);
                button = new Button(player, entry, index, nextLayout);
                button.appearProgress = session.state == FloatingMenuState.ACTIVE ? 0.0 : 1.0;
                button.appear.snap(button.appearProgress, session.totalTicks);
                buttonsToSpawn.add(button);
            }
            button.index = index++;
            nextButtons.add(button);
        }
        previous.values().forEach(button -> retireButton(session,player,button,liveEntities));

        Map<String, Decoration> previousDecorations = new HashMap<>();
        for (Decoration decoration : session.decorations) {
            previousDecorations.put(decoration.definition.id(), decoration);
        }
        List<Decoration> nextDecorations = new ArrayList<>();
        List<Decoration> decorationsToSpawn = new ArrayList<>();
        for (FloatingMenuDecoration definitionDecoration : nextLayout.decorations(definition).values()) {
            Decoration decoration = previousDecorations.remove(definitionDecoration.id());
            if (decoration != null
                    && decorationKind(decoration.definition.content())
                    != decorationKind(definitionDecoration.content())) {
                virtualEntities.destroy(player, decoration.entityId);
                decoration = null;
            }
            if (decoration == null) {
                decoration = new Decoration(player, definitionDecoration, decorationPose(definitionDecoration, nextLayout, definition.spatialFrame(), mounted == null ? selected.fieldOfView() : FloatingMenuFieldOfView.DEFAULT, mounted == null ? selected.layout().factor() : 1), session.totalTicks);
                decoration.appearProgress = session.state == FloatingMenuState.ACTIVE ? 0.0 : 1.0;
                decoration.appear.snap(decoration.appearProgress, session.totalTicks);
                decorationsToSpawn.add(decoration);
            } else {
                decoration.updateDefinition(definitionDecoration, decorationPose(definitionDecoration, nextLayout, definition.spatialFrame(), mounted == null ? selected.fieldOfView() : FloatingMenuFieldOfView.DEFAULT, mounted == null ? selected.layout().factor() : 1), session.totalTicks);
            }
            nextDecorations.add(decoration);
        }
        for (Decoration removed : previousDecorations.values()) {
            retireDecoration(session,player,removed,liveEntities);
        }

        session.buttons.clear();
        session.buttons.addAll(nextButtons);
        session.decorations.clear();
        session.decorations.addAll(nextDecorations);
        boolean anchorModeChanged = session.definition.anchorMode() != definition.anchorMode();
        session.definition = definition;
        session.mount = mounted;
        if (anchorModeChanged) session.captureAnchorView(player, nextView);
        session.refreshRevision = definition.refresh().revision(player);
        session.feedback = definition.feedback();
        session.appearance = definition.appearance();
        session.layoutSnapshot = nextLayout;
        commitPreferences(session, selected);
        // Old hitboxes cannot authorize actions for a new footprint before its first sampled frame.
        for (Button button : session.buttons) button.surface = null;
        session.synchronizeViewpoint(player);
        session.applyAnchor(prepared.anchor(), definition, nextLayout, false);
        session.prepareLayout();
        applyMotionFallback(session, player);
        session.actions = definition.entries().entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().triggers()));
        session.focusActions = definition.entries().entrySet().stream()
                .filter(value -> value.getValue().enabled() && value.getValue().focusAction() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().focusAction()));
        session.triggers = definition.triggers();
        session.pointedId = pointedEntry != null && pointedEntry.pointerBehavior().tracksPointer() ? pointedId : null;
        String pressedId = session.pressFeedback.elementId();
        MenuScene.Entry pressedEntry = pressedId == null ? null : definition.entries().get(pressedId);
        if (pressedEntry == null || pressedEntry.pointerBehavior() != UiPointerBehavior.OPERATE) session.clearPressFeedback();
        session.layoutDirty = true;
        if (session.obstructed() || !session.visualsSpawned) {
            refresh(session, player);
            synchronizePlacementVisibility(session, player);
        } else {
            // Reconcile the complete set first, then spawn additions. Axiom's payload
            // replaces its set rather than appending to it.
            axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids());
            if (definition.titleVisible() && !session.titleSpawned) {
                virtualEntities.spawn(player, session.titleId, session.titleUuid,
                        VirtualMenuEntityRenderer.Kind.TEXT, session.origin, 0);
                session.titleSpawned = true;
            }
            for (Decoration decoration : decorationsToSpawn) {
                spawnDecoration(session, player, decoration);
            }
            for (Button button : buttonsToSpawn) spawnButton(session, player, button);
            if (session.titleSpawned) {
                updateTitle(session, player, session.titleId, definition.title());
            }
            refresh(session, player);
        }
        if (!active(session)) return;
        if (session.visualsSpawned && !session.obstructed() && java.util.Objects.equals(session.pointedId, pointedId)
                && nextFocus != null && previousFocus != nextFocus) {
            invokeFocus(nextFocus, player, new Handle(session.id, session.playerId), true);
        }
        if (active(session)) updateScrollCapture(session);
    }

    private void refresh(Session session, Player player) {
        for (Button button : session.buttons) {
            MenuScene.Entry entry = session.definition.entries().get(button.id);
            if (entry == null) continue;
            ItemStack item = entry.item();
            boolean nextBlock = entry.style() == FloatingMenuElementStyle.BLOCK;
            boolean changedKind = nextBlock != button.block;
            button.item = item;
            button.block = nextBlock;
            button.label = session.layoutSnapshot.labels().get(button.id);
            button.measurement = session.layoutSnapshot.measurements().get(button.id);

            button.alignment = entry.alignment();
            button.selected = entry.selected();
            button.enabled = entry.enabled();
            button.keepAccessible = entry.keepAccessible();

            if (session.obstructed() || !session.visualsSpawned) continue;
            if (!button.textOnly && changedKind) {
                virtualEntities.destroy(player, button.visualId);
                virtualEntities.spawn(player, button.visualId, button.visualUuid,
                        button.block ? VirtualMenuEntityRenderer.Kind.BLOCK
                                : VirtualMenuEntityRenderer.Kind.ITEM,
                        session.position(button), 0, 0);
            }
            FloatingMenuElementState visual = elementState(session, button);
            updateButton(session, player, button, visual, button.revealProgress);
            if (button.pointerBehavior == UiPointerBehavior.OPERATE) updateInteractionProxy(session,player,button);
        }
        if (session.obstructed() || !session.visualsSpawned) return;
        for (Decoration decoration : session.decorations) {
            if (decoration.metadata.needsUpdate(decorationContext(session, decoration))) {
                updateDecoration(session, player, decoration);
            }
        }
    }

    private void synchronizeViewpoint(Session session, Player player) {
        if (session.state == FloatingMenuState.CLOSING
                || !session.synchronizeViewpoint(player)) {
            return;
        }
        refreshSpatialAnchor(session, player);
    }

    private void refreshSpatialAnchor(Session session, Player player) {
        refreshSpatialAnchor(session, player, false);
    }

    private void refreshSpatialAnchor(Session session, Player player, boolean recovering) {
        Location previousOrigin = session.origin;
        double previousScale = session.spatialScale;
        Vector previousForward = session.forward;
        boolean wasOccluded = session.obstructed();
        session.reanchor(session.definition, session.layoutSnapshot, recovering);
        session.prepareLayoutMotion();
        applyMotionFallback(session, player);
        session.preparePopover();
        if (wasOccluded && !session.obstructed()) {
            update(session, player, session.definition);
            return;
        }
        if (session.obstructed() || !session.visualsSpawned) {
            synchronizePlacementVisibility(session, player);
            return;
        }
        if (recovering && previousOrigin.distanceSquared(session.origin) < 1.0E-12
                && Math.abs(previousScale - session.spatialScale) < 1.0E-8
                && previousForward.distanceSquared(session.forward) < 1.0E-12) return;
        session.layoutDirty = true;
        refresh(session, player);
        if (session.titleSpawned) {
            updateTitle(session, player, session.titleId, session.definition.title());
        }
    }

    private void animate(Session session, Player player) {
        if (!session.visualsSpawned) return;
        session.prepareLayoutMotion();
        applyMotionFallback(session, player);
        if (session.occluded || !session.visualsSpawned) { synchronizePlacementVisibility(session, player); return; }
        boolean imageSupport = axiomImages.supported(player);
        if (session.imageSupport != imageSupport) {
            session.imageSupport = imageSupport;
            for (Decoration decoration : session.decorations)
                if (decoration.definition.content() instanceof FloatingMenuDecoration.Image) decoration.metadata.invalidate();
        }
        double sceneProgress = session.reveal.value(session.totalTicks);
        boolean layoutAnimation = session.state != FloatingMenuState.ACTIVE || session.layoutDirty;
        boolean anchorMotion = session.anchorMoved;
        if (!MenuFrameSampling.publish(session.buttons, button -> MenuPopover.owns(button.id),
                button -> sampleButton(session, button, sceneProgress),
                () -> { session.preparePopover(); return !session.obstructed(); },
                button -> renderButton(session, player, button, layoutAnimation, anchorMotion))) {
            applyPlacementVisibility(session, player);
            return;
        }
        session.layoutDirty = false;
        session.anchorMoved = false;
        renderExits(session,player);
        renderPopoverCatcher(session, player);
        String animatedIcon = MenuIconRenderer.animationOwner(session.layoutSnapshot.icons());
        Map<UUID, AxiomImage> imageFrame = new HashMap<>();
        MenuWorldOverlayMask worldOverlays = session.decorations.stream().anyMatch(decoration -> decoration.definition.worldAnchored()
                && decoration.definition.content() instanceof FloatingMenuDecoration.Text) ? worldOverlayMask(session,player) : null;
        for (Decoration decoration : session.decorations) {
            if (decoration.definition.placement() instanceof FloatingMenuPlacement.Local) {
                FloatingMenuPose projected = decorationPose(decoration.definition, session.layoutSnapshot,
                        session.definition.spatialFrame(), session.fieldOfView(), session.layoutScale());
                if (MenuPopover.owns(decoration.definition.id())) projected = session.popoverPose(projected);
                if (session.directLayout(decoration)) {
                    decoration.initializeLayout(projected, session.totalTicks);
                } else {
                    decoration.approachLayout(projected, session.totalTicks, session.animation.openingTicks(), session.animation.openingEasing());
                }
            }
            decoration.appearProgress = decoration.appear.step(1, session.totalTicks,
                    session.preferences.reducedMotion() ? 0 : session.animation.openingTicks(), session.animation.openingEasing());
            double decorationProgress = FloatingMenuMotion.progress(sceneProgress * decoration.appearProgress);
            if ((decoration.definition.content() instanceof FloatingMenuDecoration.Surface
                    || decoration.definition.content() instanceof FloatingMenuDecoration.RangePart
                    || decoration.definition.content() instanceof FloatingMenuDecoration.IconPart)
                    && changed(decoration.lastReveal, decorationProgress)) {
                decoration.lastReveal = decorationProgress;
                decoration.metadata.invalidate();
            }
            Location target = session.position(decoration);
            Location at;
            float yaw;
            float pitch;
            if (decoration.definition.placement() instanceof FloatingMenuPlacement.World world) {
                at = target.clone().subtract(0.0, session.preferences.reducedMotion() ? 0 : 0.18 * (1.0 - decorationProgress), 0.0);
                yaw = (float) world.yawDegrees();
                pitch = (float) world.pitchDegrees();
            } else {
                double positionFactor = FloatingMenuMotion.decorationPositionFactor(
                        session.definition.spatialFrame().viewerCentered()
                                || decoration.definition.content() instanceof FloatingMenuDecoration.Surface
                                || decoration.definition.content() instanceof FloatingMenuDecoration.Image
                                || decoration.definition.content() instanceof FloatingMenuDecoration.RangePart
                                || decoration.definition.content() instanceof FloatingMenuDecoration.IconPart
                                || decoration.definition.content() instanceof FloatingMenuDecoration.BlockVolume,
                        decoration.definition.transition(), decorationProgress);
                at = session.origin.clone().add(target.toVector()
                        .subtract(session.origin.toVector()).multiply(positionFactor));
                var angles = session.orientation.compose(decoration.renderedPose.rotation()).angles();
                yaw = (float) angles.yaw();
                pitch = (float) angles.pitch();
                if (decoration.definition.content() instanceof FloatingMenuDecoration.Surface
                        || decoration.definition.content() instanceof FloatingMenuDecoration.Image
                        || decoration.definition.content() instanceof FloatingMenuDecoration.RangePart
                        || decoration.definition.content() instanceof FloatingMenuDecoration.IconPart)
                    at.add(FloatingMenuSurfaceGeometry.facing(yaw, pitch).multiply(
                            FloatingMenuMotion.entryDepth(decorationProgress, session.animation) * session.spatialScale * decoration.renderedPose.scale()));
            }
            if (!session.preferences.reducedMotion() && decoration.definition.motion().bobs() && session.state == FloatingMenuState.ACTIVE) {
                double motionScale = decoration.definition.worldAnchored()
                        ? 1.0 : session.spatialScale * decoration.renderedPose.scale();
                double bob = Math.sin((session.totalTicks + decoration.phaseOffset) * 0.085)
                        * FloatingMenuNodeGeometry.scaledDistance(0.045, motionScale);
                at.add(0.0, bob, 0.0);
            }
            double spin = !session.preferences.reducedMotion() && decoration.definition.motion().spins()
                    ? (session.totalTicks * 2.4 + decoration.phaseOffset) % 360.0 : 0;
            if (decoration.definition.worldAnchored()) yaw += (float) spin;
            UiRotation rotation = decoration.definition.worldAnchored() ? UiRotation.IDENTITY
                    : session.orientation.compose(UiRotation.degrees(spin, 0, 0).compose(decoration.renderedPose.rotation()));
            decoration.visualRotation = rotation;
            String localSource = switch (decoration.definition.content()) {
                case FloatingMenuDecoration.RangePart range -> range.source();
                case FloatingMenuDecoration.IconPart icon -> icon.source();
                default -> null;
            };
            if (localSource != null) {
                String sourceKey = MenuPopover.owns(decoration.definition.id()) ? MenuPopover.scoped(localSource) : localSource;
                var source = session.button(sourceKey);
                boolean interactive = source != null && source.pointerBehavior == UiPointerBehavior.OPERATE;
                if (source != null && source.surface != null) {
                    var center = source.surface.center();
                    at = new Location(player.getWorld(), center.right(), center.up(), center.forward());
                    decoration.initializeLayout(source.currentPose(), session.totalTicks);
                    rotation = session.orientation.compose(source.currentPose().rotation());
                    decoration.visualRotation = rotation;
                    if (changed(decoration.lastReveal, source.revealProgress)) decoration.metadata.invalidate();
                    decoration.lastReveal = source.revealProgress;
                }
                double right, up, forward;
                if (decoration.definition.content() instanceof FloatingMenuDecoration.RangePart range) {
                    var pointer = interactive && sourceKey.equals(session.pointedId)
                            ? horizontalFraction(session, player, source) : OptionalDouble.empty();
                    var paint = MenuRangeRenderer.paint(range, interactive, pointer, session.preferences.theme());
                    if (!paint.equals(decoration.rangePaint)) { decoration.rangePaint = paint; decoration.metadata.invalidate(); }
                    right = paint.right(); up = paint.up(); forward = 0;
                } else {
                    var icon = (FloatingMenuDecoration.IconPart) decoration.definition.content();
                    boolean still = session.preferences.reducedMotion() || session.state != FloatingMenuState.ACTIVE || !sourceKey.equals(animatedIcon);
                    var paint = MenuIconRenderer.paint(icon, session.totalTicks, still, source == null || source.enabled, session.preferences.theme());
                    if (!paint.equals(decoration.iconPaint)) { decoration.iconPaint = paint; decoration.metadata.invalidate(); }
                    right = paint.right(); up = paint.up(); forward = paint.forward();
                }
                double coordinateScale = session.spatialScale * decoration.renderedPose.scale();
                var offset = rotation
                        .apply(new org.encinet.mik.shared.menu.ui.UiVector3(right, up, forward)).multiply(coordinateScale);
                at.add(offset.right(), offset.up(), offset.forward());
            }
            if (decoration.definition.content() instanceof FloatingMenuDecoration.Image image) {
                double coordinateScale = decoration.definition.worldAnchored() ? 1 : session.spatialScale * decoration.renderedPose.scale();
                var worldRotation = UiRotation.degrees(yaw, pitch, 0);
                if (!decoration.definition.worldAnchored()) worldRotation = rotation;
                imageFrame.put(decoration.uuid, MenuImageRenderer.annotation(image, at, worldRotation, coordinateScale));
                var angles = worldRotation.angles();
                at.subtract(FloatingMenuSurfaceGeometry.panelUp((float) angles.yaw(), (float) angles.pitch(), (float) angles.roll())
                        .multiply(images.fallbackAnchorDown(player, image, coordinateScale, session.preferences.typographyFactor())));
            }
            if (!decoration.definition.worldAnchored()
                    && decoration.definition.content() instanceof FloatingMenuDecoration.Text text) {
                double anchorDown = FloatingMenuNodeGeometry.textAnchorDown(text.displayHeight() * text.scale(),
                        session.spatialScale * decoration.renderedPose.scale() * session.preferences.typographyFactor());
                var angles = rotation.angles();
                at.subtract(FloatingMenuSurfaceGeometry.panelUp((float) angles.yaw(), (float) angles.pitch(),
                        (float) angles.roll()).multiply(anchorDown));
            }
            if (!decoration.definition.worldAnchored()) { yaw = 0; pitch = 0; }
            maskWorldText(decoration,at,yaw,pitch,worldOverlays);
            boolean metadataChanged = decoration.metadata.needsUpdate(decorationContext(session, decoration));
            if (metadataChanged) updateDecoration(session, player, decoration);
            boolean moved = decoration.shouldTeleport(at, yaw, pitch);
            if (moved) {
                teleport(player, decoration.entityId, at, yaw, pitch);
            }
            if (moved || metadataChanged) {
                session.definition.frameObserver().presented(player,
                        decoration.definition.id(), System.nanoTime());
            }
        }
        axiomImages.synchronize(player, session.id, imageFrame);
        if (session.titleSpawned && session.titleMetadata.needsUpdate(titleContext(session)))
            updateTitle(session, player, session.titleId, session.definition.title());
        if ((layoutAnimation || anchorMotion) && session.titleSpawned) {
            Location titleAt = session.headerPosition();
            teleport(session, player, session.titleId, titleAt.add(
                    FloatingMenuSurfaceGeometry.facing(session.presentationYaw, 0).multiply(
                            FloatingMenuMotion.entryDepth(sceneProgress, session.animation) * session.spatialScale)));
        }
    }

    private void sampleButton(Session session, Button button, double sceneProgress) {
        boolean layoutMotionChanged;
        if (session.directLayout(button)) {
            FloatingMenuPose pose = session.layoutPose(button);
            layoutMotionChanged = !pose.equals(button.currentPose());
            button.initializeLayout(pose, session.totalTicks);
        } else {
            layoutMotionChanged = button.approachLayout(session.layoutPose(button), session.totalTicks, session.animation.openingTicks(),
                    session.animation.openingEasing(), session.definition.spatialFrame().viewerCentered(),
                    session.definition.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER);
        }
        double previousHover = button.hoverProgress;
        double previousPress = button.pressProgress;
        double previousAppear = button.appearProgress;
        int feedbackTicks = session.feedbackTicks();
        button.hoverProgress = button.hover.step(MenuNodeFeedback.hover(button.pointerBehavior, button.id.equals(session.pointedId)),
                session.totalTicks, feedbackTicks, FloatingMenuEasing.CUBIC_OUT);
        button.pressProgress = button.press.step(button.pointerBehavior == UiPointerBehavior.OPERATE && session.pressFeedback.pressed(button.id) ? 1 : 0,
                session.totalTicks, feedbackTicks, FloatingMenuEasing.CUBIC_OUT);
        button.appearProgress = button.appear.step(1, session.totalTicks,
                session.preferences.reducedMotion() ? 0 : session.animation.openingTicks(), session.animation.openingEasing());
        boolean motionChanged = layoutMotionChanged
                || changed(previousHover, button.hoverProgress)
                || changed(previousPress, button.pressProgress)
                || changed(previousAppear, button.appearProgress);

        double buttonProgress = (button.sharedTransition ? 1 : sceneProgress) * button.appearProgress;
        buttonProgress = FloatingMenuMotion.progress(buttonProgress);
        button.revealProgress = buttonProgress;
        button.layoutMotionChanged = layoutMotionChanged;
        button.motionChanged = motionChanged;
        button.surface = session.nodeSurface(button);
        if (button.sharedTransition && button.currentPose().equals(session.layoutPose(button))) button.sharedTransition = false;
    }

    private void renderButton(Session session, Player player, Button button, boolean layoutAnimation, boolean anchorMotion) {
        var surface = button.surface;
        var angles = surface.rotation().angles();
        float yaw = (float) angles.yaw(), pitch = (float) angles.pitch();
        Vector panelNormal = FloatingMenuSurfaceGeometry.facing(yaw, pitch);
        Vector panelUp = FloatingMenuSurfaceGeometry.panelUp(yaw, pitch, (float) angles.roll());
        var center = surface.center();
        Location at = new Location(player.getWorld(), center.right(), center.up(), center.forward());
        double idle = session.state == FloatingMenuState.ACTIVE
                ? Math.sin((session.totalTicks + button.index * 2.3) * session.animation.idleSpeed())
                * session.animation.idleAmplitude() : 0.0;
        FloatingMenuNodeGeometry.Placement placement =
                FloatingMenuNodeGeometry.placement(button.style,
                        button.measurement.text().size().height() * typography(session, button),
                        nodeScale(session, button), idle);
        Location visualAt = at.clone().add(panelUp.clone().multiply(placement.visualUp()));
        double textAnchorDown = FloatingMenuNodeGeometry.textAnchorDown(
                button.measurement.text().renderedHeight(), nodeScale(session, button)
                        * typography(session, button));
        Location textAt = at.clone().add(panelUp.clone().multiply(
                placement.textUp() - textAnchorDown));
        textAt.add(panelNormal.clone().multiply(placement.textForward()));
        boolean moveAnchors = layoutAnimation || button.motionChanged || anchorMotion;
        boolean idleMotion = !button.textOnly && session.state == FloatingMenuState.ACTIVE
                && session.animation.idleAmplitude() > 0 && session.animation.idleSpeed() > 0;
        if (moveAnchors && button.controlSurface) {
            teleport(player, button.surfaceId, at.clone().subtract(panelNormal.clone().multiply(.008 * nodeScale(session, button))), 0, 0);
        }
        if (moveAnchors || idleMotion) teleport(player, button.textId, textAt, 0, 0);
        if (!button.textOnly && (moveAnchors || idleMotion)) {
            teleport(player, button.visualId, visualAt, 0, 0);
        }
        if (button.pointerBehavior == UiPointerBehavior.OPERATE) updateInteractionProxy(session,player,button);
        if (layoutAnimation || button.layoutMotionChanged || button.appearProgress < 1
                || button.metadata.needsUpdate(buttonContext(session, button))) {
            FloatingMenuElementState state = elementState(session, button);
            updateButton(session, player, button, state, button.revealProgress);
        }
    }

    private void closeImmediately(UUID playerId, FloatingMenuCloseReason reason) {
        inputs.invalidate(playerId);
        Session session = sessions.get(playerId);
        if (session == null) return;
        removeCurrentVisuals(session, FloatingMenuState.CLOSED, reason);
        if (!sessions.containsKey(playerId)) {
            menuStatusDisplays.remove(playerId);
        }
        Player player = Bukkit.getPlayer(playerId);
        for (Frame frame : session.ancestors) {
            frame.presentation.clearMeasurements();
            if (player != null) notifyLifecycle(frame.definition, player, new Handle(frame.id, playerId),
                        FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED, reason);
        }
    }

    private void closeIfCurrent(Session session, FloatingMenuCloseReason reason) {
        if (active(session)) {
            if (session.mount == null) closeImmediately(session.playerId,reason);
            else closeMounted(session,reason);
        }
    }

    private void updatePointer(Session session, Player player, boolean ownsInput) {
        if (!active(session)) return;
        String pointed = ownsInput && session.state == FloatingMenuState.ACTIVE
                ? pickOwnedTarget(session, player) : null;
        if (java.util.Objects.equals(pointed, session.pointedId)) return;
        Button previous = session.button(session.pointedId);
        Button next = session.button(pointed);
        session.pointedId = pointed;
        session.layoutDirty = true;
        if (previous != null && !session.pressFeedback.pressed(previous.id)) {
            FloatingMenuElementState previousState = elementState(session, previous);
            updateButton(session, player, previous, previousState, previous.revealProgress);
        }
        FloatingMenuHandle handle = new Handle(session.id, session.playerId);
        FloatingMenuFocusAction previousFocus = previous == null ? null : session.focusActions.get(previous.id);
        if (previousFocus != null) invokeFocus(previousFocus, player, handle, false);
        if (!active(session)) return;
        if (next != null) {
            FloatingMenuElementState nextState = elementState(session, next);
            updateButton(session, player, next, nextState, next.revealProgress);
        }
        if (next != null && next.pointerBehavior == UiPointerBehavior.OPERATE && System.currentTimeMillis() - session.lastHoverSoundAt >= 80L) {
            FloatingMenuFeedback feedback = session.feedback;
            sounds.play(player,feedback.hoverVolume(),feedback.hoverPitch());
            session.lastHoverSoundAt = System.currentTimeMillis();
        }
        FloatingMenuFocusAction nextFocus = next == null ? null : session.focusActions.get(next.id);
        if (nextFocus != null) invokeFocus(nextFocus, player, handle, true);
        if (active(session)) updateScrollCapture(session);
    }

    private String pickPointerTarget(Session session, Player player) {
        return inputOwner(player) == session ? pickOwnedTarget(session,player) : null;
    }

    private String pickOwnedTarget(Session session, Player player) {
        if (session.obstructed() || inputBlocked(player) || tooltips.occludes(player)) return null;
        Location eye = player.getEyeLocation();
        String picked = null;
        double nearest = Double.POSITIVE_INFINITY;
        double closestCenter = Double.POSITIVE_INFINITY;
        for (Button button : session.buttons) {
            if (!button.pointerBehavior.tracksPointer() || button.surface == null) continue;
            Optional<FloatingMenuSurfaceGeometry.Hit> intersection =
                    surfaceHit(session, eye, button);
            if (intersection.isEmpty()) continue;
            FloatingMenuSurfaceGeometry.Hit hit = intersection.get();
            boolean nearer = hit.distance() < nearest - SURFACE_DISTANCE_EPSILON;
            boolean samePlaneAndMoreCentral = Math.abs(hit.distance() - nearest)
                    <= SURFACE_DISTANCE_EPSILON
                    && hit.normalizedCenterDistance() < closestCenter;
            if (!nearer && !samePlaneAndMoreCentral) continue;
            picked = button.id;
            nearest = hit.distance();
            closestCenter = hit.normalizedCenterDistance();
        }
        return picked;
    }

    private static Optional<FloatingMenuSurfaceGeometry.Hit> surfaceHit(
            Session session, Location eye, Button button) {
        if (session.layoutSnapshot.popover() != null && !session.popoverInput().allows(button.id,
                session.popoverHit(eye).isPresent())) return Optional.empty();
        if (!inputVisible(session, button)) return Optional.empty();
        Optional<FloatingMenuSurfaceGeometry.Hit> hit = button.surface.intersect(eye, HOVER_SURFACE_MARGIN);
        if (hit.isPresent()
                && !(session.definition.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER
                && button.keepAccessible)
                && !FloatingMenuWorldOcclusion.clearToSurface(eye, hit.get().distance())) {
            return Optional.empty();
        }
        return hit;
    }

    private static boolean inputVisible(Session session, Button button) {
        var style = session.layoutSnapshot.styles().get(button.id);
        return button.surface != null && FloatingMenuMotion.inputReady(button.revealProgress, style == null ? 1 : style.contentAlpha());
    }

    private static FloatingMenuSize interactionSize(Button button, Session session) {
        var surface = button.surface == null ? session.nodeSurface(button) : button.surface;
        var angles = surface.rotation().angles();
        return FloatingMenuSurfaceGeometry.catcher((float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                (surface.size().width() + HOVER_SURFACE_MARGIN * 2) * surface.scale(),
                (surface.size().height() + HOVER_SURFACE_MARGIN * 2) * surface.scale());
    }
    private static double nodeScale(Session session, Button button) { return session.spatialScale * button.currentPose().scale(); }

    private void invokeFocus(FloatingMenuFocusAction action, Player player,
                             FloatingMenuHandle handle, boolean focused) {
        try {
            action.changed(player, handle, focused);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Floating menu focus callback failed for player " + player.getName(), exception);
        }
    }

    private void updateScrollCapture(Session session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && inputOwner(player) != session) return;
        boolean scoped = player != null && session.definition.popover() != null && (session.definition.popover().modal()
                || session.popoverHit(player.getEyeLocation()).isPresent());
        if (player != null && !session.obstructed() && !inputBlocked(player) && session.state == FloatingMenuState.ACTIVE
                && (scoped || actionFor(session,session.pointedId,FloatingMenuInteraction.SCROLL_UP) != null
                || actionFor(session,session.pointedId,FloatingMenuInteraction.SCROLL_DOWN) != null || tooltips.occludes(player))) {
            scrollCaptures.add(session.playerId);
        } else {
            scrollCaptures.remove(session.playerId);
        }
    }

    private void removeCurrentVisuals(Session session, FloatingMenuState nextState,
                                      FloatingMenuCloseReason reason) {
        var shutdown = new ShutdownSequence();
        boolean ownedInput = session.mount == null || session.id.equals(pointerOwners.get(session.playerId));
        if (session.mount == null) sessions.remove(session.playerId,session);
        else worldPanels.remove(session);
        if (ownedInput) {
            pointerOwners.remove(session.playerId);
            scrollCaptures.remove(session.playerId);
            shutdown.attempt("menu tooltip",() -> tooltips.clear(session.playerId));
        }
        disposePopoverPanel(session);
        if (session.mount == null || session.pointedId != null) session.inputGate.consume();
        session.clearPressFeedback();
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && session.pointedId != null) {
            FloatingMenuFocusAction focus = session.focusActions.get(session.pointedId);
            if (focus != null) invokeFocus(focus, player,
                    new Handle(session.id, session.playerId), false);
        }
        FloatingMenuState previous = session.hostPaused ? FloatingMenuState.SUSPENDED : session.state;
        session.state = nextState;
        session.layoutMotion.clear();
        session.presentation.discardPlacementSpace();
        if (nextState == FloatingMenuState.CLOSED) session.presentation.clearMeasurements();
        shutdown.attempt("menu displays",() -> despawnVisuals(session,player));
        if (player != null) notifyLifecycle(session.definition, player,
                new Handle(session.id, session.playerId), previous, nextState, reason);
        shutdown.finish("menu session " + session.id);
    }

    /** Recreate an unsafe visual path at its verified target before any new metadata is published. */
    private void applyMotionFallback(Session session, Player player) {
        if (!session.layoutMotionReset) return;
        session.layoutMotionReset = false;
        tooltips.clear(session.playerId);
        despawnVisuals(session, player);
    }

    /** Occlusion hides presentation only; navigation, drafts and pending business operations remain owned by the session. */
    private void synchronizePlacementVisibility(Session session, Player player) {
        session.preparePopover();
        applyPlacementVisibility(session, player);
    }

    private void applyPlacementVisibility(Session session, Player player) {
        if (!session.obstructed()) {
            if (!session.visualsSpawned) {
                session.prepareLayout();
                spawn(session);
                refresh(session, player);
            }
            return;
        }
        if (session.mount == null || session.id.equals(pointerOwners.get(session.playerId))) {
            tooltips.clear(session.playerId); scrollCaptures.remove(session.playerId);
        }
        String focused = session.pointedId;
        session.pointedId = null;
        FloatingMenuFocusAction focus = focused == null ? null : session.focusActions.get(focused);
        despawnVisuals(session, player);
        if (focus != null) invokeFocus(focus, player, new Handle(session.id, session.playerId), false);
    }

    private void despawnVisuals(Session session, Player player) {
        var shutdown = new ShutdownSequence();
        shutdown.attempt("menu exit displays",() -> session.exits.clear(parts -> destroySnapshots(player,parts)));
        if (!session.visualsSpawned) { shutdown.finish("menu displays"); return; }
        session.visualsSpawned = false;
        List<Integer> ids = new ArrayList<>(session.buttons.size() * 4
                + session.decorations.size() + 1);
        if (session.titleSpawned) ids.add(session.titleId);
        if (session.popoverCatcher != null) ids.add(session.popoverHitboxId);
        targets.remove(session.popoverHitboxId);
        for (Decoration decoration : session.decorations) ids.add(decoration.entityId);
        for (Button button : session.buttons) {
            targets.remove(button.hitboxId);
            ids.add(button.textId);
            if (button.controlSurface) ids.add(button.surfaceId);
            if (!button.textOnly) ids.add(button.visualId);
            if (button.pointerBehavior == UiPointerBehavior.OPERATE) ids.add(button.hitboxId);
        }
        int[] entityIds = ids.stream().mapToInt(Integer::intValue).toArray();
        session.titleSpawned = false;
        session.popoverCatcher = null;
        for (Button button : session.buttons) button.surface = null;
        shutdown.attempt("menu entity cache",() -> virtualEntities.forget(session.playerId,entityIds));
        if (player != null && player.isOnline()) {
            // Remove client entities before dropping their editor exclusions.
            shutdown.attempt("menu entity packets",() -> virtualEntities.destroy(player,entityIds));
            shutdown.attempt("menu gizmos",() -> axiomGizmos.remove(player,session.id));
            shutdown.attempt("menu images",() -> axiomImages.remove(player,session.id));
        } else {
            shutdown.attempt("menu gizmos",() -> axiomGizmos.forget(session.playerId,session.id));
            shutdown.attempt("menu images",() -> axiomImages.forget(session.playerId,session.id));
        }
        shutdown.finish("menu displays");
    }

    private List<Frame> closeDiscardedAncestors(Session previous, List<Frame> retained,
                                                UUID resumedId, FloatingMenuCloseReason reason) {
        Player player = Bukkit.getPlayer(previous.playerId);
        java.util.Set<UUID> retainedIds = retained.stream()
                .map(frame -> frame.id).collect(java.util.stream.Collectors.toSet());
        List<Frame> framesToRetain = new ArrayList<>();
        for (Frame frame : previous.ancestors) {
            if (frame.id.equals(resumedId) || retainedIds.contains(frame.id)) {
                framesToRetain.add(frame);
            } else {
                frame.presentation.clearMeasurements();
                if (player != null) notifyLifecycle(frame.definition, player,
                        new Handle(frame.id, previous.playerId),
                        FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED, reason);
            }
        }
        return framesToRetain;
    }

    private void transition(Session session, FloatingMenuState previous,
                            FloatingMenuState current, FloatingMenuCloseReason reason) {
        session.state = current;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) notifyLifecycle(session.definition, player,
                new Handle(session.id, session.playerId), previous,
                session.hostPaused ? FloatingMenuState.SUSPENDED : current, reason);
        if (active(session)) updateScrollCapture(session);
    }

    private void notifyLifecycle(MenuScene definition, Player player,
                                 FloatingMenuHandle handle, FloatingMenuState previous,
                                 FloatingMenuState current, FloatingMenuCloseReason reason) {
        try {
            definition.lifecycle().changed(player, handle, previous, current, reason);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Floating menu lifecycle callback failed for " + player.getName(), exception);
        }
    }

    private static void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Floating menu " + operation + " must run on the server thread");
        }
    }

    private static int allocateEntityId(Player player) {
        return VirtualMenuEntityRenderer.allocateId(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        closeImmediately(playerId, FloatingMenuCloseReason.QUIT);
        clearWorldViews(event.getPlayer().getUniqueId(),FloatingMenuCloseReason.QUIT);
        menuStatusDisplays.remove(playerId);
        menuStatusDisplays.forgetViewer(playerId);
        inputGates.remove(playerId);
    }

    @Override
    public void onLanguageChanged(Player player) {
        menuStatusDisplays.refreshViewerLanguage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (inputBlocked(player)) return;
        Session session = inputOwner(player);
        if (session != null && !session.obstructed() && session.state == FloatingMenuState.ACTIVE) {
            if (tooltips.occludes(player) || !session.inputGate.permits()) {
                event.setCancelled(true);
                return;
            }
            String picked = pickPointerTarget(session, player);
            if (actionFor(session, picked, FloatingMenuInteraction.HOTKEY) != null) {
                event.setCancelled(true);
                activate(new Target(player.getUniqueId(), session.id, picked), FloatingMenuInteraction.HOTKEY);
                return;
            }
        }
        if (!player.isSneaking() || mainMenuOpener == null) return;
        event.setCancelled(true);
        toggleMainMenu(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        closeImmediately(event.getPlayer().getUniqueId(), FloatingMenuCloseReason.WORLD_CHANGE);
        clearWorldViews(event.getPlayer().getUniqueId(),FloatingMenuCloseReason.WORLD_CHANGE);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        closeImmediately(event.getPlayer().getUniqueId(), FloatingMenuCloseReason.DEATH);
        clearWorldViews(event.getPlayer().getUniqueId(),FloatingMenuCloseReason.DEATH);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        closeImmediately(event.getPlayer().getUniqueId(), FloatingMenuCloseReason.DEATH);
        clearWorldViews(event.getPlayer().getUniqueId(),FloatingMenuCloseReason.DEATH);
    }

    private void teleport(Session session, Player player, int id, Location location) {
        teleport(player, id, location, 0);
    }

    private void teleport(Player player, int id, Location location, float yaw) {
        virtualEntities.teleport(player, id, location, yaw);
    }

    private void teleport(Player player, int id, Location location, float yaw, float pitch) {
        virtualEntities.teleport(player, id, location, yaw, pitch);
    }

    private static MenuMetadataState.Context buttonContext(Session session, Button button) {
        return MenuMetadataState.Context.node(session.spatialScale, button.currentPose(), typography(session, button), session.preferences.theme()).inFrame(session.orientation);
    }

    private void updateButton(Session session, Player player, Button button, FloatingMenuElementState state, double reveal) {
        var context = buttonContext(session, button);
        button.metadata.publish(context, () -> {
            updateButtonText(session, player, button, state, reveal, context);
            if (!button.textOnly) updateVisual(session, player, button, state, reveal, context);
        });
    }

    private void updateButtonText(Session session, Player player, Button button,
                                  FloatingMenuElementState state, double reveal, MenuMetadataState.Context context) {
        boolean graphical = session.layoutSnapshot.ranges().containsKey(button.id) || session.layoutSnapshot.icons().containsKey(button.id);
        FloatingMenuNodeSizing.TextLayout text = button.measurement.text();
        var style = session.layoutSnapshot.styles().get(button.id);
        int background = button.controlSurface ? 0
                : style != null && style.background().isPresent() ? session.appearance.elementBackground(state, context.theme(), style)
                : state == FloatingMenuElementState.NORMAL && session.layoutSnapshot.panelContents().contains(button.id)
                ? 0 : session.appearance.elementBackground(state, context.theme());
        Component label = graphical ? Component.empty() : button.enabled ? button.label
                : button.label.decorate(net.kyori.adventure.text.format.TextDecoration.STRIKETHROUGH);
        virtualEntities.text(player, button.textId, label, graphical ? 0 : background,
                text.displayWidth(), text.displayHeight(), text.lineWidthPixels(),
                (float) (FloatingMenuNodeSizing.TEXT_SCALE * context.coordinateScale() * context.typographyScale()),
                button.alignment,
                session.definition.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER
                        && button.keepAccessible, reveal, graphical ? 0 : style == null ? 1 : style.contentAlpha(), context.rotation());
        if (button.controlSurface) {
            FloatingMenuSize size = session.layoutSnapshot.sizes().get(button.id);
            int surface = session.appearance.elementBackground(state, context.theme(), style);
            virtualEntities.surface(player, button.surfaceId, surface,
                    (float) (size.width() * context.coordinateScale()), (float) (size.height() * context.coordinateScale()), reveal, context.rotation());
        }
    }

    private static boolean controlSurface(SceneGeometry geometry, MenuScene.Entry entry) {
        var style = geometry.styles().get(entry.id());
        return entry.interactive() && style != null && style.controlSurface();
    }

    private static double typography(Session session, Button button) {
        var style = session.layoutSnapshot.styles().get(button.id);
        return session.preferences.typographyFactor() * (style == null ? 1 : style.textScale());
    }

    private static MenuMetadataState.Context titleContext(Session session) {
        return MenuMetadataState.Context.node(session.spatialScale, FloatingMenuPose.ORIGIN, session.preferences.typographyFactor(), session.preferences.theme()).inFrame(session.orientation);
    }

    private void updateTitle(Session session, Player player, int entityId,
                             Component text) {
        var context = titleContext(session);
        session.titleMetadata.publish(context, () -> virtualEntities.text(player, entityId, text,
                session.appearance.titleBackground(context.theme()), 5.0F, 1.5F, 320,
                (float) (0.86F * context.coordinateScale() * context.typographyScale()),
                FloatingMenuDecoration.Alignment.CENTER, false));
    }

    private void updateVisual(Session session, Player player, Button button,
                              FloatingMenuElementState state, double reveal, MenuMetadataState.Context context) {
        float scale = (float) (FloatingMenuNodeGeometry.visualScale(button.style, state) * context.coordinateScale());
        var style = session.layoutSnapshot.styles().get(button.id);
        double alpha = style == null ? 1 : style.contentAlpha();
        // Vanilla item/block displays have no RGBA opacity. Shrink them within the stable allocated box.
        virtualEntities.visual(player, button.visualId, button.item, button.block,
                scale * (float) (FloatingMenuMotion.progress(reveal) * alpha), context.rotation());
    }

    private static FloatingMenuElementState elementState(Session session, Button button) {
        return MenuNodeFeedback.state(button.pointerBehavior, button.enabled, button.selected,
                button.id.equals(session.pointedId), session.pressFeedback.pressed(button.id));
    }



    private static boolean changed(double previous, double current) {
        return Math.abs(previous - current) > 0.0001;
    }



    private record Target(UUID playerId, UUID sessionId, String elementId) {
    }

    private static OptionalDouble horizontalFraction(Session session, Player player, Button selected) {
        if (selected == null) return OptionalDouble.empty();
        var hit = surfaceHit(session, player.getEyeLocation(), selected);
        if (hit.isEmpty()) return OptionalDouble.empty();
        var range = session.layoutSnapshot.ranges().get(selected.id);
        if (range != null) return range.geometry().pointer(hit.get().horizontalFraction());
        double contentWidth = selected.measurement.text().contentWidth() * typography(session, selected);
        if (contentWidth <= 0) return OptionalDouble.empty();
        double footprintWidth = session.layoutSnapshot.sizes().get(selected.id).width();
        return OptionalDouble.of(Math.clamp((hit.get().horizontalFraction() - .5) * footprintWidth / contentWidth + .5, 0, 1));
    }

    private final class Handle implements FloatingMenuHandle {
        private final UUID id;
        private final UUID playerId;

        private Handle(UUID id, UUID playerId) {
            this.id = id;
            this.playerId = playerId;
        }

        @Override public UUID id() { return id; }
        @Override public UUID playerId() { return playerId; }

        @Override
        public OptionalDouble horizontalFraction() {
            requirePrimaryThread("horizontalFraction");
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null) {
                return OptionalDouble.empty();
            }
            Button selected = session.button(session.pressFeedback.elementId());
            return FloatingMenuService.horizontalFraction(session, player, selected);
        }

        @Override
        public FloatingMenuState state() {
            requirePrimaryThread("state");
            Session session = handleSession(playerId,id);
            if (session != null && session.id.equals(id)) return session.hostPaused ? FloatingMenuState.SUSPENDED : session.state;
            if (session != null && session.ancestor(id) != null) return FloatingMenuState.SUSPENDED;
            return FloatingMenuState.CLOSED;
        }

        @Override
        public int depth() {
            requirePrimaryThread("depth");
            Session session = handleSession(playerId,id);
            if (session == null) return -1;
            if (session.id.equals(id)) return session.ancestors.size();
            for (int index = 0; index < session.ancestors.size(); index++) {
                if (session.ancestors.get(index).id.equals(id)) return index;
            }
            return -1;
        }

        @Override
        public void refresh() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::refresh);
                return;
            }
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
                session.inputGate.contentChanged();
                FloatingMenuService.this.refresh(session, player);
            }
        }

        @Override
        public void reanchor() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::reanchor);
                return;
            }
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null
                    || session.state == FloatingMenuState.CLOSING
                    || session.state == FloatingMenuState.CLOSED) {
                return;
            }
            session.presentation.reset();
            if (session.mount == null) session.captureAnchorView(player);
            FloatingMenuService.this.update(session, player, session.definition);
            FloatingMenuService.this.animate(session, player);
        }

        @Override
        public void update(MenuScene definition) {
            java.util.Objects.requireNonNull(definition, "definition");
            if (!Bukkit.isPrimaryThread()) {
                execute(() -> update(definition));
                return;
            }
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
                session.inputGate.contentChanged();
                FloatingMenuService.this.update(session, player,
                        java.util.Objects.requireNonNull(definition, "definition"));
            } else if (session != null) {
                Frame frame = session.ancestor(id);
                if (frame != null) {
                    frame.definition = java.util.Objects.requireNonNull(definition, "definition");
                    if (player != null) {
                        frame.refreshRevision = definition.refresh().revision(player);
                    }
                }
            }
        }

        @Override
        public FloatingMenuHandle openChild(MenuScene definition) {
            requirePrimaryThread("openChild");
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null) {
                throw new IllegalStateException("Cannot open a child from an inactive menu");
            }
            if (session.mount != null && (session.hostPaused || session.state != FloatingMenuState.ACTIVE))
                throw new IllegalStateException("Cannot open a child from a suspended world panel");
            if (session.definition.popover() != null) dismissPopover(session, player);
            return FloatingMenuService.this.open(player,
                    java.util.Objects.requireNonNull(definition, "definition"));
        }

        @Override
        public void openPopover(UiKey source, Function<Runnable, MenuPanel> create) {
            requirePrimaryThread("openPopover");
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) throw new IllegalStateException("Cannot open a popover for an offline player");
            openManagedPopover(source, create, languageService.text(player, Message.CLOSE, NamedTextColor.WHITE), false, (_,_) -> {});
        }

        @Override
        public void openPopover(MenuPopover definition) {
            java.util.Objects.requireNonNull(definition);
            openManagedPopover(definition.source(), _ -> new MenuPanel() {
                public MenuScene render() { return MenuScene.screen("popover-content", definition.title()).content(definition.content()).build(); }
                public org.encinet.mik.shared.menu.ui.UiNode headerActions() { return definition.actions(); }
            }, definition.closeLabel(), definition.modal(), definition.dismissed());
        }

        private void openManagedPopover(UiKey source, Function<Runnable, MenuPanel> create,
                                        Component closeLabel, boolean modal, FloatingMenuCommand dismissed) {
            requirePrimaryThread("openPopover");
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null || session.state != FloatingMenuState.ACTIVE)
                throw new IllegalStateException("Cannot open a popover from an inactive menu");
            if (session.mount != null) throw new IllegalStateException("Open a foreground child for world panel details or editors");
            disposePopoverPanel(session);
            session.popoverPanel = new MenuPopoverPanel(session.definition.screenId(), source, closeLabel, selected -> execute(() -> {
                if (active(session) && session.popoverPanel == selected && selected.active())
                    update(session.definition);
            }), modal, dismissed);
            var selection = session.popoverPanel;
            try {
                selection.initialize(java.util.Objects.requireNonNull(create));
                update(session.definition.toBuilder().popover(null).build());
                if (session.popoverPanel == selection) selection.attach(this);
            } catch (RuntimeException error) {
                disposePopoverPanel(session);
                throw error;
            }
        }

        @Override
        public void closePopover() {
            requirePrimaryThread("closePopover");
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) dismissPopover(session, player);
        }

        @Override
        public void closePopover(MenuPopover expected) {
            requirePrimaryThread("closePopover");
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null && expected.sameScope(session.definition.popover()))
                dismissPopover(session, player);
        }

        @Override
        public void back() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::back);
                return;
            }
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null) return;
            if (session.mount != null) { closeMounted(session,FloatingMenuCloseReason.BACK); return; }
            if (session.definition.popover() != null) { dismissPopover(session, player); return; }
            if (!session.ancestors.isEmpty()) {
                int last = session.ancestors.size() - 1;
                Frame parent = session.ancestors.get(last);
                // Parent definitions are snapshots. Revalidate live state before
                // resuming so a language or status change cannot flash stale UI.
                FloatingMenuService.this.refreshFrameDefinition(session, parent, player);
                if (!active(session)) return;
                FloatingMenuService.this.openConfigured(player, parent.id, parent.definition,
                        new ArrayList<>(session.ancestors.subList(0, last)),
                        false, true, FloatingMenuCloseReason.BACK);
            } else {
                FloatingMenuService.this.close(player);
            }
        }

        @Override
        public void close() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::close);
                return;
            }
            Session session = handleSession(playerId,id);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
                if (session.mount != null) closeMounted(session,FloatingMenuCloseReason.USER);
                else if (session.definition.popover() != null) dismissPopover(session, player);
                else FloatingMenuService.this.close(player);
            } else if (session != null) {
                Frame frame = session.ancestor(id);
                if (frame != null) {
                    session.ancestors.remove(frame);
                    frame.presentation.clearMeasurements();
                    if (player != null) notifyLifecycle(frame.definition, player, this,
                            FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED,
                            FloatingMenuCloseReason.USER);
                }
            }
        }

        @Override
        public void feedback(Component message, FloatingMenuFeedbackKind kind) {
            if (!Bukkit.isPrimaryThread()) {
                execute(() -> feedback(message, kind));
                return;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || state() != FloatingMenuState.ACTIVE) return;
            player.sendActionBar(message.colorIfAbsent(kind.color()));
            sounds.play(player,0.45F,kind.pitch());
        }
    }

    private static final class Button {
        private final String id;
        private int index;
        private final int textId;
        private final int visualId;
        private final int hitboxId;
        private final int surfaceId;
        private final UUID textUuid = UUID.randomUUID();
        private final UUID visualUuid = UUID.randomUUID();
        private final UUID hitboxUuid = UUID.randomUUID();
        private final UUID surfaceUuid = UUID.randomUUID();
        private boolean block;
        private final boolean textOnly;
        private final UiPointerBehavior pointerBehavior;
        private final boolean controlSurface;
        private final FloatingMenuElementStyle style;
        private ItemStack item;
        private Component label;
        private FloatingMenuNodeSizing.Measurement measurement;
        private FloatingMenuDecoration.Alignment alignment;
        private boolean selected;
        private boolean enabled;
        private boolean keepAccessible;
        private final MenuTransition appear = new MenuTransition(1);
        private final MenuTransition hover = new MenuTransition(0);
        private final MenuTransition press = new MenuTransition(0);
        private double appearProgress = 1.0;
        private double hoverProgress;
        private double pressProgress;
        private boolean layoutInitialized;
        private final MenuMetadataState metadata = new MenuMetadataState();
        private double revealProgress;
        private final MenuPoseTransition layoutTransition = new MenuPoseTransition();
        private FloatingMenuPose renderedPose = FloatingMenuPose.ORIGIN;
        private MenuSurfaceAttachment.Surface surface;
        private MenuInteractionProxy.Bounds interactionProxy;
        private boolean layoutMotionChanged;
        private boolean motionChanged;
        private boolean sharedTransition;

        private Button(Player player, MenuScene.Entry entry, int index, SceneGeometry geometry) {
            this.id = entry.id();
            this.index = index;

            this.textId = allocateEntityId(player);
            this.visualId = allocateEntityId(player);
            this.hitboxId = allocateEntityId(player);
            this.surfaceId = allocateEntityId(player);
            this.item = entry.item();
            this.style = entry.style();
            this.block = style == FloatingMenuElementStyle.BLOCK;
            this.textOnly = style == FloatingMenuElementStyle.TEXT;
            this.pointerBehavior = entry.pointerBehavior();
            this.controlSurface = controlSurface(geometry, entry);
            this.label = geometry.labels().get(entry.id());
            this.measurement = geometry.measurements().get(entry.id());
            this.alignment = entry.alignment();
            this.selected = entry.selected();
            this.enabled = entry.enabled();
            this.keepAccessible = entry.keepAccessible();

        }

        private void initializeLayout(FloatingMenuPose pose, long tick) {
            if (!pose.equals(renderedPose)) surface = null;
            layoutTransition.snap(pose, tick);
            renderedPose = pose;
            layoutInitialized = true;
        }

        private FloatingMenuPose currentPose() { return renderedPose; }

        private boolean approachLayout(FloatingMenuPose target, long tick, int ticks, FloatingMenuEasing easing,
                                       boolean circular, boolean continuousOrbit) {
            if (!layoutInitialized) { initializeLayout(target, tick); return true; }
            FloatingMenuPose previous = renderedPose;
            if (circular && !continuousOrbit && !FloatingMenuMotion.localRingTransition(previous.point(), target.point())) {
                initializeLayout(target, tick);
                appearProgress = 0;
                appear.snap(0, tick);
                return true;
            }
            renderedPose = layoutTransition.advance(target, tick, ticks, easing, continuousOrbit ? MenuPoseTransition.Path.ORBIT
                    : circular ? MenuPoseTransition.Path.RING : MenuPoseTransition.Path.LINEAR);
            return !previous.equals(renderedPose);
        }
    }

    private static final class Decoration {
        private FloatingMenuDecoration definition;
        private final int entityId;
        private final UUID uuid = UUID.randomUUID();
        private final double phaseOffset;
        private final MenuTransition appear = new MenuTransition(1);
        private double appearProgress = 1.0;
        private final MenuPoseTransition layoutTransition = new MenuPoseTransition();
        private FloatingMenuPose renderedPose = FloatingMenuPose.ORIGIN;
        private Location lastSentLocation;
        private float lastSentYaw;
        private float lastSentPitch;
        private final MenuMetadataState metadata = new MenuMetadataState();
        private boolean interfaceOccluded;
        private double lastReveal = -1;
        private UiRotation visualRotation = UiRotation.IDENTITY;
        private MenuRangeRenderer.Paint rangePaint;
        private MenuIconRenderer.Paint iconPaint;

        private Decoration(Player player, FloatingMenuDecoration definition, FloatingMenuPose projected, long tick) {
            this.definition = definition;
            this.entityId = allocateEntityId(player);
            this.phaseOffset = Math.floorMod(definition.id().hashCode(), 360);
            if (projected != null) initializeLayout(projected, tick);
        }

        private void initializeLayout(FloatingMenuPose pose, long tick) {
            layoutTransition.snap(pose, tick);
            renderedPose = pose;
        }

        private void updateDefinition(FloatingMenuDecoration next, FloatingMenuPose projected, long tick) {
            boolean wasLocal = definition.placement() instanceof FloatingMenuPlacement.Local;
            boolean contentChanged = !definition.content().equals(next.content());
            if (contentChanged || definition.worldAnchored() != next.worldAnchored()) metadata.invalidate();
            if (contentChanged) { iconPaint = null; rangePaint = null; }
            this.definition = next;
            if (!wasLocal && next.placement() instanceof FloatingMenuPlacement.Local) {
                initializeLayout(projected, tick);
            }
        }

        private void approachLayout(FloatingMenuPose target, long tick, int ticks, FloatingMenuEasing easing) {
            renderedPose = layoutTransition.advance(target, tick, ticks, easing, MenuPoseTransition.Path.LINEAR);
        }

        private boolean shouldTeleport(Location location, float yaw, float pitch) {
            if (lastSentLocation != null
                    && lastSentLocation.getWorld() == location.getWorld()
                    && lastSentLocation.distanceSquared(location) <= 1.0E-12
                    && Math.abs(lastSentYaw - yaw) <= 1.0E-5F
                    && Math.abs(lastSentPitch - pitch) <= 1.0E-5F) {
                return false;
            }
            lastSentLocation = location.clone();
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            return true;
        }
    }

    private static final class Session implements MenuWorldPanels.View {
        private boolean imageSupport;
        private final MenuPopoverAttachment popoverAttachment = new MenuPopoverAttachment();
        private final MenuExitVisuals exits = new MenuExitVisuals();
        private final int popoverHitboxId;
        private final UUID popoverHitboxUuid = UUID.randomUUID();
        private MenuInteractionProxy.Bounds popoverCatcher;
        private MenuPopoverPanel popoverPanel;
        private boolean replacingPopover;
        private final UUID id;
        private final UUID playerId;
        private Location origin;
        private Location openedAt;
        private Location anchorView;
        private Location anchorPlayerPosition;
        private InertialMenuAnchor inertialAnchor;
        private double anchorEyeHeight;
        private Vector requestedForward;
        private Vector right;
        private Vector up = new Vector(0, 1, 0);
        private UiRotation orientation = UiRotation.IDENTITY;
        private MenuWorldPanels.Mount mount;
        private boolean hostPaused;
        private Vector forward;
        private float presentationYaw;
        private double spatialScale;
        private double anchorDistance;
        private FloatingMenuPreferences preferences;
        private FloatingMenuFraming interactionFraming;
        private final MenuPresentation presentation;
        private final List<Button> buttons;
        private final List<Decoration> decorations;
        private Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions;
        private Map<FloatingMenuInteraction, FloatingMenuAction> triggers;
        private Map<String, FloatingMenuFocusAction> focusActions;
        private FloatingMenuAnimation animation;
        private final MenuTransition reveal = new MenuTransition(0);
        private FloatingMenuFeedback feedback;
        private FloatingMenuAppearance appearance;
        private MenuScene definition;
        private Object refreshRevision;
        private SceneGeometry layoutSnapshot;
        private final List<Frame> ancestors;
        private final int titleId;
        private final UUID titleUuid = UUID.randomUUID();
        private boolean titleSpawned;
        private final MenuMetadataState titleMetadata = new MenuMetadataState();
        private FloatingMenuState state = FloatingMenuState.OPENING;
        private int age;
        private final MenuPressFeedback pressFeedback = new MenuPressFeedback();
        private String pointedId;
        private long totalTicks;
        private boolean layoutDirty = true;
        private boolean anchorMoved;
        private boolean spaceDirty;
        private boolean occluded;
        private boolean popoverOccluded;
        private final MenuLayoutMotion layoutMotion = new MenuLayoutMotion();
        private final MenuWindowObserver windows = new MenuWindowObserver();
        private boolean layoutMotionReset;
        private boolean obstructed() { return occluded || popoverOccluded || hostPaused; }
        private boolean visualsSpawned;
        private long lastHoverSoundAt;
        private final MenuInputGate inputGate;
        private FloatingMenuCloseReason closeReason;

        private FloatingMenuFieldOfView fieldOfView() { return mount == null ? preferences.fieldOfView() : FloatingMenuFieldOfView.DEFAULT; }
        private double layoutScale() { return mount == null ? preferences.layout().factor() : 1; }

        @Override public UUID id() { return id; }
        @Override public UUID viewerId() { return playerId; }
        @Override public int liveEntities() { return layoutSnapshot.entityCount() + exits.size(); }

        private int feedbackTicks() { return preferences.reducedMotion() ? 0 : animation.pressTicks(); }

        private void selectPress(String elementId) {
            if (!pressFeedback.pressed(elementId)) clearPressFeedback();
            if (elementId == null) return;
            pressFeedback.select(elementId, totalTicks, animation.pressTicks());
            Button button = button(elementId);
            if (button != null) button.press.to(1, totalTicks, feedbackTicks(), FloatingMenuEasing.CUBIC_OUT);
        }

        private void releasePress(String elementId) {
            Button button = button(elementId);
            if (button != null) button.press.to(0, totalTicks, feedbackTicks(), FloatingMenuEasing.CUBIC_OUT);
        }

        private void clearPressFeedback() {
            releasePress(pressFeedback.elementId());
            pressFeedback.clear();
        }

        private Session(Player player, List<Button> buttons, List<Decoration> decorations, UUID id,
                        Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions,
                        Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                        Map<String, FloatingMenuFocusAction> focusActions,
                        FloatingMenuAnimation animation, FloatingMenuFeedback feedback,
                        FloatingMenuAppearance appearance,
                        MenuScene definition, SceneGeometry layoutSnapshot, MenuPresentation presentation,
                        List<Frame> ancestors,
                        FloatingMenuPreferences preferences, MenuAnchorView view,
                        Optional<FloatingMenuAnchorResolver.Anchor> preparedAnchor, MenuWorldPanels.Mount mount, MenuInputGate inputGate) {
            this.id = id;
            this.mount = mount;
            this.inputGate = inputGate;
            this.playerId = player.getUniqueId();
            this.popoverHitboxId = allocateEntityId(player);
            this.preferences = preferences;
            this.titleId = allocateEntityId(player);
            this.definition = definition;
            this.presentation = presentation;
            captureAnchorView(player, view);
            applyAnchor(preparedAnchor, definition, layoutSnapshot, false);
            this.buttons = buttons;
            this.decorations = decorations;
            this.actions = Map.copyOf(actions);
            this.triggers = Map.copyOf(triggers);
            this.focusActions = Map.copyOf(focusActions);
            this.animation = preferences.reducedMotion() ? animation.reduced() : animation;
            this.reveal.to(1, 0, this.animation.openingTicks(), this.animation.openingEasing());
            this.feedback = feedback;
            this.appearance = appearance;
            this.refreshRevision = definition.refresh().revision(player);
            this.layoutSnapshot = layoutSnapshot;
            this.ancestors = new ArrayList<>(ancestors);
            prepareLayout();
        }

        private void captureAnchorView(Player player) {
            captureAnchorView(player, MenuAnchorView.capture(player, definition));
        }

        private void captureAnchorView(Player player, MenuAnchorView view) {
            this.anchorPlayerPosition = view.playerPosition().clone();
            this.openedAt = view.eye().clone();
            this.anchorEyeHeight = view.eyeHeight();
            this.anchorView = view.readingEye().clone();
            this.inertialAnchor = definition.anchorMode() == FloatingMenuAnchorMode.INERTIAL_TOOLBAR
                    ? new InertialMenuAnchor(player.getYaw()) : null;
            this.requestedForward = view.forward().clone();
        }

        private void followHead(Player player, boolean engaged) {
            double previous = inertialAnchor.yaw();
            double next = inertialAnchor.step(player.getYaw(), engaged, preferences.reducedMotion());
            double difference = next - previous;
            if (Math.abs(difference) < 1e-5) return;
            double radians = -Math.toRadians(difference);
            Vector offset = origin.toVector().subtract(anchorView.toVector()).rotateAroundY(radians);
            origin = anchorView.clone().add(offset);
            requestedForward.rotateAroundY(radians);
            forward.rotateAroundY(radians);
            right.rotateAroundY(radians);
            presentationYaw += (float) difference;
            orientation = UiRotation.degrees(presentationYaw,0,0);
            anchorView.setYaw((float) next);
            anchorMoved = true;
        }

        private boolean synchronizeViewpoint(Player player) {
            if (mount != null) return false;
            double nextEyeHeight = definition.viewpoint().eyeHeight(player);
            if (Math.abs(nextEyeHeight - anchorEyeHeight) < 1.0E-6) return false;
            anchorView.add(0.0, nextEyeHeight - anchorEyeHeight, 0.0);
            anchorEyeHeight = nextEyeHeight;
            return true;
        }

        private void reanchor(MenuScene definition,
                              SceneGeometry layoutSnapshot, boolean recovering) {
            Optional<FloatingMenuAnchorResolver.Anchor> anchor;
            try {
                anchor = Optional.of(presentation.resolve(Bukkit.getPlayer(playerId), anchorView, requestedForward, definition,
                        layoutSnapshot, preferences.typographyFactor(), preferences.layout().factor(), preferences.fieldOfView()));
            } catch (MenuSpaceUnavailableException unavailable) {
                anchor = Optional.empty();
            }
            applyAnchor(anchor, definition, layoutSnapshot, recovering);
        }

        private void applyAnchor(Optional<FloatingMenuAnchorResolver.Anchor> planned, MenuScene definition,
                                 SceneGeometry layoutSnapshot, boolean recovering) {
            if (planned.isEmpty()) {
                occluded = true;
                if (origin == null) {
                    forward = requestedForward.clone().normalize();
                    right = new Vector(-forward.getZ(), 0, forward.getX());
                    origin = anchorView.clone().add(forward.clone().multiply(MenuViewportLayout.distance(preferences.fieldOfView())));
                    spatialScale = 1;
                    anchorDistance = MenuViewportLayout.distance(preferences.fieldOfView());
                    interactionFraming = definition.framing();
                    presentationYaw = (float) Math.toDegrees(Math.atan2(forward.getX(), -forward.getZ()));
                }
                return;
            }
            FloatingMenuAnchorResolver.Anchor anchor = planned.orElseThrow();
            occluded = false;
            if (recovering) {
                anchor = FloatingMenuAnchorMotion.recover(anchorView, anchor, spatialScale);
            }
            anchorMoved |= origin == null || origin.distanceSquared(anchor.origin()) > 1.0E-12
                    || Math.abs(spatialScale - anchor.spatialScale()) > 1.0E-8
                    || forward.distanceSquared(anchor.forward()) > 1.0E-12 || right.distanceSquared(anchor.right()) > 1.0E-12;
            this.forward = anchor.forward();
            this.right = anchor.right();
            this.presentationYaw = (float) Math.toDegrees(
                    Math.atan2(forward.getX(), -forward.getZ()));
            this.origin = anchor.origin();
            this.spatialScale = anchor.spatialScale();
            this.orientation = mount == null ? UiRotation.degrees(presentationYaw,0,0) : mount.surface().rotation();
            var localUp = orientation.apply(new UiVector3(0,1,0));
            this.up = new Vector(localUp.right(),localUp.up(),localUp.forward());
            this.anchorDistance = anchor.distance();
            this.spaceDirty = false;
            updateInteractionFraming(definition, layoutSnapshot);
        }

        private void updateInteractionFraming(MenuScene definition,
                                              SceneGeometry layoutSnapshot) {
            if (definition.spatialFrame().viewerCentered()) {
                this.interactionFraming = definition.framing();
                return;
            }
            FloatingMenuAnchorResolver.SceneBounds sceneBounds =
                    layoutSnapshot.bounds();
            updateInteractionFraming(sceneBounds);
        }

        private void updateInteractionFraming(
                FloatingMenuAnchorResolver.SceneBounds sceneBounds) {
            this.interactionFraming = sceneBounds.interactionFraming(
                    anchorDistance, spatialScale);
        }

        private static Session create(Player player, UUID id, MenuScene requested,
                                      List<Frame> ancestors,
                                      FloatingMenuPreferences preferences, MenuPresentation presentation, MenuWorldPanels.Mount mount, MenuInputGate inputGate) {
            double textScale = preferences.typographyFactor();
            MenuAnchorView view = MenuAnchorView.capture(player, requested);
            var prepared = mount == null ? presentation.prepare(player, view.readingEye(), view.forward(), requested, textScale,
                    preferences.layout().factor(), preferences.fieldOfView(), preferences.theme())
                    : MenuWorldPresentation.prepare(presentation,mount.surface(),requested,textScale,preferences.theme(),MenuEntityBudget.LIMIT);
            SceneGeometry layoutSnapshot = prepared.geometry();
            MenuScene definition = layoutSnapshot.definition();
            List<Button> buttons = new ArrayList<>();
            List<Decoration> decorations = new ArrayList<>();
            Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions = new HashMap<>();
            Map<String, FloatingMenuFocusAction> focusActions = new HashMap<>();
            int index = 0;
            for (MenuScene.Entry entry : definition.entries().values()) {
                Button button = new Button(player, entry, index, layoutSnapshot);
                buttons.add(button);
                index++;
                actions.put(entry.id(), entry.triggers());
                if (entry.enabled() && entry.focusAction() != null) focusActions.put(entry.id(), entry.focusAction());
            }
            for (FloatingMenuDecoration decoration : layoutSnapshot.decorations(definition).values()) {
                decorations.add(new Decoration(player, decoration, decorationPose(decoration, layoutSnapshot, definition.spatialFrame(),
                        mount == null ? preferences.fieldOfView() : FloatingMenuFieldOfView.DEFAULT, mount == null ? preferences.layout().factor() : 1), 0));
            }
            return new Session(player, buttons, decorations, id, actions,
                    definition.triggers(), focusActions,
                    definition.animation(), definition.feedback(), definition.appearance(),
                    definition, layoutSnapshot, presentation, ancestors, preferences, view, prepared.anchor(), mount, inputGate);
        }

        private Location position(Button button) {
            return origin.clone()
                    .add(right.clone().multiply(button.renderedPose.right() * spatialScale))
                    .add(up.clone().multiply(button.renderedPose.up() * spatialScale))
                    .add(forward.clone().multiply(-button.renderedPose.forward() * spatialScale));
        }

        private Location position(FloatingMenuPose pose) {
            return origin.clone().add(right.clone().multiply(pose.right() * spatialScale))
                    .add(up.clone().multiply(pose.up() * spatialScale))
                    .add(forward.clone().multiply(-pose.forward() * spatialScale));
        }

        private List<MenuSurfaceAttachment.Surface> tooltipSurfaces() {
            var result = new ArrayList<>(baseTooltipSurfaces());
            if (layoutSnapshot.popover() != null) for (var surface : layoutSnapshot.popover().content().bounds().surfaces())
                result.add(worldSurface(new FloatingMenuAnchorResolver.Surface(popoverPose(surface.pose()), surface.size(), surface.depth())));
            return List.copyOf(result);
        }

        private List<MenuSurfaceAttachment.Surface> baseTooltipSurfaces() {
            return layoutSnapshot.bounds().surfaces().stream().map(this::worldSurface).toList();
        }

        private MenuSurfaceAttachment.Surface worldSurface(FloatingMenuAnchorResolver.Surface surface) {
            var pose = surface.pose();
            return new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(position(pose).toVector()),
                    orientation.compose(pose.rotation()),
                    surface.size(), spatialScale * pose.scale(), surface.depth());
        }

        private Location position(Decoration decoration) {
            if (decoration.definition.placement() instanceof FloatingMenuPlacement.World world) {
                Location location = world.location();
                if (!origin.getWorld().equals(location.getWorld())) {
                    throw new IllegalStateException("World decoration is not in the menu viewer's world");
                }
                return location;
            }
            return origin.clone()
                    .add(right.clone().multiply(decoration.renderedPose.right() * spatialScale))
                    .add(up.clone().multiply(decoration.renderedPose.up() * spatialScale))
                    .add(forward.clone().multiply(-decoration.renderedPose.forward() * spatialScale));
        }

        private FloatingMenuPose layoutPose(Button button) {
            FloatingMenuPose pose = layoutSnapshot.poses().get(button.id);
            if (pose == null) throw new IllegalStateException("Missing layout pose for " + button.id);
            return MenuPopover.owns(button.id) ? popoverPose(pose) : pose;
        }

        private boolean directLayout(Button button) {
            return mount != null || preferences.reducedMotion() || !button.sharedTransition && (definition.spatialFrame() == FloatingMenuSpatialFrame.FRONT_ARC
                    || layoutSnapshot.transformedContents().contains(button.id));
        }

        private boolean directLayout(Decoration decoration) {
            return mount != null || MenuPopover.owns(decoration.definition.id()) || preferences.reducedMotion() || definition.spatialFrame().viewerCentered()
                    || decoration.definition.transition() == FloatingMenuDecoration.Transition.TRACKING;
        }

        /** Publish rigid content and its background at the same pose before sending new metadata. */
        private void prepareLayout() {
            prepareLayoutMotion();
            for (Button button : buttons) if (!MenuPopover.owns(button.id)) {
                if (!button.layoutInitialized || directLayout(button)) button.initializeLayout(layoutPose(button), totalTicks);
            }
            preparePopover();
            for (Button button : buttons) if (MenuPopover.owns(button.id)) {
                if (!button.layoutInitialized || directLayout(button)) button.initializeLayout(layoutPose(button), totalTicks);
            }
            for (Decoration decoration : decorations) {
                if (decoration.definition.placement() instanceof FloatingMenuPlacement.Local && directLayout(decoration)) {
                    var pose = decorationPose(decoration.definition, layoutSnapshot, definition.spatialFrame(), fieldOfView(), layoutScale());
                    decoration.initializeLayout(MenuPopover.owns(decoration.definition.id()) ? popoverPose(pose) : pose, totalTicks);
                }
            }
        }

        /** The target is already fitted; unsafe remaining motion adopts that target without moving the reading frame. */
        private void prepareLayoutMotion() {
            if (mount != null || occluded) return;
            var player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            var anchor = new FloatingMenuAnchorResolver.Anchor(origin, forward, right, spatialScale, anchorDistance, 0);
            if (layoutMotion.permits(layoutSnapshot, player.getEyeLocation(), anchor, presentation.worldEpoch(), this::layoutMoves,
                    range -> presentation.motionSpace(player, player.getEyeLocation(), range))) return;
            boolean changed = false;
            for (var button : buttons) if (!MenuPopover.owns(button.id)) {
                var target = layoutPose(button);
                if (!button.currentPose().equals(target)) { button.surface = null; changed = true; }
                button.initializeLayout(target, totalTicks);
                button.sharedTransition = false;
            }
            for (var decoration : decorations) if (decoration.definition.placement() instanceof FloatingMenuPlacement.Local
                    && !MenuPopover.owns(decoration.definition.id())) {
                var target = decorationPose(decoration.definition, layoutSnapshot, definition.spatialFrame(), fieldOfView(), layoutScale());
                changed |= !decoration.renderedPose.equals(target);
                decoration.initializeLayout(target, totalTicks);
            }
            layoutDirty |= changed;
            layoutMotionReset |= changed && visualsSpawned;
        }

        private List<MenuLayoutMotion.Move> layoutMoves() {
            var moves = new ArrayList<MenuLayoutMotion.Move>();
            var path = definition.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER ? MenuPoseTransition.Path.ORBIT
                    : definition.spatialFrame().viewerCentered() ? MenuPoseTransition.Path.RING : MenuPoseTransition.Path.LINEAR;
            for (var button : buttons) if (button.layoutInitialized && !directLayout(button)) {
                var size = layoutSnapshot.sizes().get(button.id);
                appendMoves(moves, button.layoutTransition, button.currentPose(), layoutPose(button),
                        new FloatingMenuSize(size.width() + .16, size.height() + .16), FloatingMenuNodeGeometry.surfaceDepth(button.style), path);
            }
            for (var decoration : decorations) if (decoration.definition.placement() instanceof FloatingMenuPlacement.Local && !directLayout(decoration)) {
                var target = decorationPose(decoration.definition, layoutSnapshot, definition.spatialFrame(), fieldOfView(), layoutScale());
                var surface = FloatingMenuAnchorResolver.decorationSurface(decoration.definition, target, preferences.typographyFactor());
                appendMoves(moves, decoration.layoutTransition, decoration.renderedPose, target, surface.size(), surface.depth(), MenuPoseTransition.Path.LINEAR);
            }
            return List.copyOf(moves);
        }

        private void appendMoves(List<MenuLayoutMotion.Move> moves, MenuPoseTransition transition, FloatingMenuPose rendered,
                                 FloatingMenuPose target, FloatingMenuSize size, double depth, MenuPoseTransition.Path path) {
            var sampled = transition.sample(totalTicks);
            if (!rendered.equals(sampled)) moves.add(new MenuLayoutMotion.Move(rendered, sampled, size, depth, transition.path()));
            if (!sampled.equals(target)) moves.add(new MenuLayoutMotion.Move(sampled, target, size, depth, path));
        }

        private MenuSurfaceAttachment.Surface nodeSurface(Button button) {
            var pose = button.layoutInitialized ? button.currentPose() : layoutSnapshot.poses().get(button.id);
            return MenuNodeSurface.sample(pose, position(pose).toVector(), orientation, spatialScale,
                    layoutSnapshot.sizes().get(button.id), animation,
                    new MenuNodeSurface.Feedback(button.hoverProgress, button.pressProgress, button.revealProgress, preferences.reducedMotion()));
        }

        private void preparePopover() {
            var geometry = layoutSnapshot.popover();
            if (geometry == null) {
                if (!replacingPopover) popoverAttachment.reset();
                popoverOccluded = false;
                presentation.discardPlacementSpace();
                return;
            }
            if (occluded) { popoverOccluded = false; presentation.discardPlacementSpace(); return; }
            var source = button(geometry.sourceId());
            var player = Bukkit.getPlayer(playerId);
            if (source == null || player == null) { popoverOccluded = true; presentation.discardPlacementSpace(); return; }
            var surface = nodeSurface(source);
            var size = geometry.size();
            Location eye = player.getEyeLocation();
            popoverAttachment.update(eye, source.id, surface,
                    new FloatingMenuSize(size.width() * spatialScale, size.height() * spatialScale), preferences.fieldOfView(), baseTooltipSurfaces(),
                    presentation.worldEpoch(), (placement, readingScale) -> {
                        if (size.width() == 0 || size.height() == 0) { presentation.discardPlacementSpace(); return 1; }
                        var envelope = MenuPopoverWorldPlacement.project(eye, placement, geometry.content().bounds(), spatialScale * readingScale);
                        if (!envelope.possible()) { presentation.discardPlacementSpace(); return 0; }
                        return envelope.fit(presentation.companionSpace(player, eye, envelope.range()));
                    });
            popoverOccluded = popoverAttachment.blocked();
            presentation.discardPlacementSpace();
        }

        private MenuPopoverInput popoverInput() {
            return new MenuPopoverInput(layoutSnapshot.popover().sourceId(), definition.popover().modal());
        }

        private FloatingMenuPose popoverPose(FloatingMenuPose local) {
            var placement = popoverAttachment.placement();
            if (placement == null) return local;
            double scale = popoverAttachment.readingScale();
            var offset = new UiVector3(local.right(), local.up(), local.forward()).multiply(spatialScale * scale);
            Vector delta = MenuSurfaceAttachment.vector(placement.center().add(placement.rotation().apply(offset))).subtract(origin.toVector());
            var point = new FloatingMenuPoint(delta.dot(right) / spatialScale, delta.dot(up) / spatialScale, -delta.dot(forward) / spatialScale);
            var rotation = UiRotation.degrees(-presentationYaw, 0, 0).compose(placement.rotation()).compose(local.rotation());
            return FloatingMenuPose.transformed(point, rotation, local.scale() * scale);
        }

        private Optional<FloatingMenuSurfaceGeometry.Hit> popoverHit(Location eye) {
            var geometry = layoutSnapshot.popover();
            var placed = popoverAttachment.placement();
            if (geometry == null || placed == null) return Optional.empty();
            var angles = placed.rotation().angles();
            var size = geometry.size();
            double scale = spatialScale * popoverAttachment.readingScale();
            return FloatingMenuSurfaceGeometry.intersect(eye.toVector(), eye.getDirection(), MenuSurfaceAttachment.vector(placed.center()),
                    (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(), size.width() * scale, size.height() * scale, 0)
                    .filter(hit -> FloatingMenuWorldOcclusion.clearToSurface(eye, hit.distance()));
        }

        private Location headerPosition() {
            FloatingMenuPose pose = layoutSnapshot.bounds().titlePose().orElseThrow();
            double anchorDown = FloatingMenuAnchorResolver.titleSize(preferences.typographyFactor()).height() / 2;
            return origin.clone().add(up.clone().multiply((pose.up() - anchorDown) * spatialScale))
                    .add(forward.clone().multiply(-pose.forward() * spatialScale));
        }

        private Button button(String id) {
            if (id == null) return null;
            for (Button button : buttons) if (button.id.equals(id)) return button;
            return null;
        }

        private Frame ancestor(UUID id) {
            for (Frame frame : ancestors) if (frame.id.equals(id)) return frame;
            return null;
        }

        private Set<UUID> virtualEntityUuids() {
            Set<UUID> ids = new LinkedHashSet<>();
            ids.addAll(exits.uuids());
            ids.add(popoverHitboxUuid);
            if (definition.titleVisible()) ids.add(titleUuid);
            for (Decoration decoration : decorations) ids.add(decoration.uuid);
            for (Button button : buttons) {
                ids.add(button.textUuid);
                if (button.controlSurface) ids.add(button.surfaceUuid);
                if (!button.textOnly) ids.add(button.visualUuid);
                // Axiom 5.5 applies the same gizmo system to Interaction entities.
                if (button.pointerBehavior == UiPointerBehavior.OPERATE) ids.add(button.hitboxUuid);
            }
            return ids;
        }
    }

    private static final class Frame {
        private final UUID id;
        private MenuScene definition;
        private Object refreshRevision;
        private final MenuPresentation presentation;

        private Frame(UUID id, MenuScene definition,
                      Object refreshRevision, MenuPresentation presentation) {
            this.id = id;
            this.definition = definition;
            this.refreshRevision = refreshRevision;
            this.presentation = presentation;
        }
    }
}
