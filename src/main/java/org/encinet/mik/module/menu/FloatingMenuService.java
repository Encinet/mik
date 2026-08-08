package org.encinet.mik.module.menu;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.encinet.mik.module.geyser.GeyserService;
import org.encinet.mik.module.i18n.LanguageChangeListener;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.presentation.AxiomGizmoService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Owns one declarative menu lifecycle and projects it to the viewer's client UI. */
public final class FloatingMenuService implements Listener, LanguageChangeListener {
    private static final double HOVER_SURFACE_MARGIN = 0.08D;
    private static final double SURFACE_DISTANCE_EPSILON = 1.0E-4D;
    private static final long INPUT_DEBOUNCE_MILLIS = 55L;
    private static final int MENU_STATUS_SYNC_INTERVAL_TICKS = 5;
    private static final FloatingMenuWorldInteractionGuard WORLD_INTERACTION_GUARD =
            FloatingMenuWorldInteractionGuard.UNIFIED;
    private static final LayoutSnapshot EMPTY_LAYOUT =
            new LayoutSnapshot(Map.of(), Map.of(), 0.0, 0.0);

    private final JavaPlugin plugin;
    private final VirtualMenuEntityRenderer virtualEntities = new VirtualMenuEntityRenderer();
    private final AxiomGizmoService.Scope axiomGizmos;
    private final MenuUsageDisplayController menuStatusDisplays;
    private final GeyserFloatingMenuPresenter nativeForms;
    private final LanguageService languageService;
    private final FloatingMenuSettingsStore settings;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<Integer, Target> targets = new HashMap<>();
    private final Set<UUID> scrollCaptures = new HashSet<>();
    private BukkitTask animationTask;
    private int menuStatusSyncTicks;
    private Consumer<Player> mainMenuOpener;

    public FloatingMenuService(JavaPlugin plugin, AxiomGizmoService axiomGizmoService,
                               GeyserService geyserService,
                               LanguageService languageService) {
        this.plugin = plugin;
        AxiomGizmoService gizmoService = java.util.Objects.requireNonNull(
                axiomGizmoService, "axiomGizmoService");
        this.axiomGizmos = gizmoService.scope("floating-menu");
        this.languageService = java.util.Objects.requireNonNull(
                languageService, "languageService");
        this.menuStatusDisplays = new MenuUsageDisplayController(
                this.languageService, gizmoService.scope("floating-menu-status"));
        this.settings = new FloatingMenuSettingsStore(plugin);
        this.nativeForms = new GeyserFloatingMenuPresenter(
                java.util.Objects.requireNonNull(geyserService, "geyserService"),
                this.languageService);
    }

    public void enable() {
        settings.enable();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        languageService.addLanguageChangeListener(this);
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 1L, 1L);
        plugin.getLogger().info("Floating menu renderer: virtual entities with automatic Bedrock forms");
    }

    public void disable() {
        closeAllImmediately(FloatingMenuCloseReason.PLUGIN_DISABLE);
        if (animationTask != null) animationTask.cancel();
        languageService.removeLanguageChangeListener(this);
        menuStatusDisplays.disable();
        axiomGizmos.close();
    }

    public void setMainMenuOpener(Consumer<Player> opener) {
        this.mainMenuOpener = java.util.Objects.requireNonNull(opener, "opener");
    }

    public FloatingMenuScale scale(Player player) {
        return settings.scale(java.util.Objects.requireNonNull(player, "player").getUniqueId());
    }

    public void openSettings(Player player) {
        requirePrimaryThread("openSettings");
        java.util.Objects.requireNonNull(player, "player");
        FloatingMenuScale selected = scale(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "interface-scale",
                        Component.text(languageService.t(player, Message.INTERFACE_SCALE_MENU_TITLE),
                                NamedTextColor.DARK_PURPLE))
                .stableAnchor()
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("heading"),
                        FloatingMenuLayouts.actions("scales", 3),
                        FloatingMenuLayouts.navigation("navigation")));
        menu.information("description",
                        Component.text(languageService.t(player,
                                Message.INTERFACE_SCALE_DESCRIPTION), NamedTextColor.GRAY))
                .region("heading");
        for (FloatingMenuScale option : FloatingMenuScale.values()) {
            menu.choice("scale:" + option.id(), option == selected,
                            scaleMaterial(option), scaleLabel(player, option, option == selected))
                    .region("scales")
                    .primary((p, handle) -> selectScale(p, option));
        }
        FloatingMenuScale larger = selected.step(1);
        if (larger != selected) {
            menu.on(FloatingMenuInteraction.SCROLL_UP,
                    (p, handle, input) -> selectScale(p, larger));
        }
        FloatingMenuScale smaller = selected.step(-1);
        if (smaller != selected) {
            menu.on(FloatingMenuInteraction.SCROLL_DOWN,
                    (p, handle, input) -> selectScale(p, smaller));
        }
        menu.back(Component.text(languageService.t(player, Message.BACK_TO_MAIN),
                        NamedTextColor.GREEN))
                .region("navigation");
        present(player, menu.build());
    }

    private void selectScale(Player player, FloatingMenuScale selected) {
        settings.setScale(player.getUniqueId(), selected);
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.interfaceScale = selected.factor();
            session.typographyScale = selected.typographyFactor();
            if (!session.nativeForm) {
                session.layoutSnapshot = layoutSnapshot(session.definition,
                        session.typographyScale);
                session.reanchor(session.definition, session.layoutSnapshot);
                session.layoutDirty = true;
            }
        }
        openSettings(player);
    }

    private Component scaleLabel(Player player, FloatingMenuScale option, boolean selected) {
        Message name = switch (option) {
            case SMALL -> Message.INTERFACE_SCALE_SMALL;
            case NORMAL -> Message.INTERFACE_SCALE_NORMAL;
            case LARGE -> Message.INTERFACE_SCALE_LARGE;
        };
        return Component.text(languageService.t(player, name),
                        selected ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                .append(Component.text(" · " + option.textPercent() + "%",
                        NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                selected ? Message.INTERFACE_SCALE_SELECTED : Message.CLICK_SET),
                        NamedTextColor.GRAY));
    }

    private static Material scaleMaterial(FloatingMenuScale option) {
        return switch (option) {
            case SMALL -> Material.SMALL_AMETHYST_BUD;
            case NORMAL -> Material.MEDIUM_AMETHYST_BUD;
            case LARGE -> Material.AMETHYST_CLUSTER;
        };
    }

    private void toggleMainMenu(Player player) {
        if (sessions.containsKey(player.getUniqueId())) close(player, FloatingMenuCloseReason.SHORTCUT);
        else if (mainMenuOpener != null) mainMenuOpener.accept(player);
    }

    /** Opens a declarative menu and returns a stable handle for external control. */
    public FloatingMenuHandle open(Player player, FloatingMenuDefinition definition) {
        requirePrimaryThread("open");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(definition, "definition");
        Session current = sessions.get(player.getUniqueId());
        List<Frame> ancestors = new ArrayList<>();
        boolean suspendCurrent = current != null && current.state != FloatingMenuState.CLOSING;
        if (suspendCurrent
                && current.definition != null) {
            ancestors.addAll(current.ancestors);
            ancestors.add(new Frame(current.id, current.definition,
                    current.refreshRevision));
        }
        return openDefinition(player, definition, ancestors, suspendCurrent, false);
    }

    /** Replaces the complete hierarchy and establishes a new root screen. */
    public FloatingMenuHandle openRoot(Player player, FloatingMenuDefinition definition) {
        requirePrimaryThread("openRoot");
        java.util.Objects.requireNonNull(player, "player");
        java.util.Objects.requireNonNull(definition, "definition");
        return openDefinition(player, definition, List.of(), false, false);
    }

    public FloatingMenuHandle present(Player player, FloatingMenuDefinition definition) {
        requirePrimaryThread("present");
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

    void execute(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    public Optional<FloatingMenuHandle> current(Player player) {
        requirePrimaryThread("current");
        Session session = sessions.get(player.getUniqueId());
        if (session != null) return Optional.of(new Handle(session.id, session.playerId));
        return Optional.empty();
    }

    private FloatingMenuHandle openDefinition(Player player, FloatingMenuDefinition definition,
                                              List<Frame> ancestors, boolean suspendCurrent,
                                              boolean resumed) {
        UUID id = UUID.randomUUID();
        openConfigured(player, id, definition, ancestors, suspendCurrent, resumed,
                FloatingMenuCloseReason.REPLACED);
        return new Handle(id, player.getUniqueId());
    }

    private void openConfigured(Player player, UUID id, FloatingMenuDefinition definition,
                                List<Frame> ancestors, boolean suspendCurrent,
                                boolean resumed, FloatingMenuCloseReason replacementReason) {
        Session session = Session.create(player, id, definition, ancestors,
                definition.presentation().nativeFormCompatible()
                        && nativeForms.supports(player), scale(player));
        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            if (suspendCurrent) {
                removeCurrentVisuals(current, FloatingMenuState.SUSPENDED, null);
            } else {
                removeCurrentVisuals(current, FloatingMenuState.CLOSED, replacementReason);
                closeDiscardedAncestors(current, ancestors, id, replacementReason);
            }
        }
        sessions.put(player.getUniqueId(), session);
        try {
            menuStatusDisplays.update(player, session.definition.screenId());
            FloatingMenuState previous = resumed
                    ? FloatingMenuState.SUSPENDED : FloatingMenuState.CLOSED;
            if (session.nativeForm) {
                transition(session, previous, FloatingMenuState.OPENING, null);
                if (!showNativeMenu(session, player)) {
                    closeImmediately(session.playerId, FloatingMenuCloseReason.ERROR);
                    return;
                }
                if (sessions.get(session.playerId) == session) {
                    transition(session, FloatingMenuState.OPENING,
                            FloatingMenuState.ACTIVE, null);
                }
            } else {
                spawn(session);
                transition(session, previous, FloatingMenuState.OPENING, null);
            }
        } catch (RuntimeException exception) {
            closeImmediately(player.getUniqueId(), FloatingMenuCloseReason.ERROR);
            throw exception;
        }
    }

    public void close(Player player) {
        close(player, FloatingMenuCloseReason.USER);
    }

    private void close(Player player, FloatingMenuCloseReason reason) {
        requirePrimaryThread("close");
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.state != FloatingMenuState.CLOSING) {
            FloatingMenuState previous = session.state;
            session.state = FloatingMenuState.CLOSING;
            session.closeReason = reason;
            session.age = 0;
            notifyLifecycle(session.definition, player, new Handle(session.id, session.playerId),
                    previous, FloatingMenuState.CLOSING, null);
            updateScrollCapture(session);
            if (session.nativeForm) {
                closeImmediately(session.playerId, reason);
            }
        }
    }

    public void closeAllImmediately() {
        closeAllImmediately(FloatingMenuCloseReason.PLUGIN_DISABLE);
    }

    private void closeAllImmediately(FloatingMenuCloseReason reason) {
        for (UUID playerId : List.copyOf(sessions.keySet())) closeImmediately(playerId, reason);
    }

    private void tick() {
        for (Session session : List.copyOf(sessions.values())) {
            try {
                tick(session);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Floating menu tick failed for " + session.playerId, exception);
                closeImmediately(session.playerId, FloatingMenuCloseReason.ERROR);
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
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) {
            closeImmediately(session.playerId, FloatingMenuCloseReason.QUIT);
            return;
        }
        if (!player.getWorld().equals(session.openedAt.getWorld())) {
            closeImmediately(session.playerId, FloatingMenuCloseReason.WORLD_CHANGE);
            return;
        }
        if (session.definition.movementPolicy().exceeded(
                player.getEyeLocation().distanceSquared(session.openedAt))) {
            closeImmediately(session.playerId, FloatingMenuCloseReason.DISTANCE);
            return;
        }
        session.totalTicks++;
        if (session.nativeForm) {
            refreshLiveDefinitions(session, player);
            return;
        }
        session.age++;
        if (session.state == FloatingMenuState.OPENING && session.age >= session.animation.openingTicks()) {
            transition(session, FloatingMenuState.OPENING, FloatingMenuState.ACTIVE, null);
            if (sessions.get(session.playerId) != session) return;
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
        if (sessions.get(session.playerId) != session) return;
        synchronizeViewpoint(session, player);
        updateHover(session, player);
        updateScrollCapture(session);
        animate(session, player);
    }

    private void refreshLiveDefinitions(Session session, Player player) {
        for (Frame frame : List.copyOf(session.ancestors)) {
            if (!refreshDue(session.totalTicks, frame.definition.refresh())) continue;
            refreshFrameDefinition(session, frame, player);
            if (sessions.get(session.playerId) != session) return;
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
        if (session.nativeForm) session.nativeLiveRefresh = true;
        try {
            invokeRefresh(refresh, player, new Handle(session.id, session.playerId));
        } finally {
            session.nativeLiveRefresh = false;
        }
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

    private boolean showNativeMenu(Session session, Player player) {
        invalidateNativeForm(session, player);
        long revision = ++session.nativeFormRevision;
        session.nativeFormOpen = true;
        boolean sent = nativeForms.showMenu(player, session.definition,
                option -> receiveNativeCallback(() ->
                        acceptNativeOption(session.playerId, session.id, revision, option)),
                () -> receiveNativeCallback(() ->
                        closeNativeResponse(session.playerId, session.id, revision)));
        if (!sent) session.nativeFormOpen = false;
        return sent;
    }

    private boolean showNativeActions(Session session, Player player,
                                      BedrockMenuTranslator.Option option) {
        invalidateNativeForm(session, player);
        long revision = ++session.nativeFormRevision;
        session.nativeFormOpen = true;
        boolean sent = nativeForms.showActions(player, option,
                interaction -> receiveNativeCallback(() ->
                        acceptNativeAction(session.playerId, session.id, revision,
                                option.elementId(), interaction)),
                () -> receiveNativeCallback(() ->
                        backFromNativeActions(session.playerId, session.id, revision)));
        if (!sent) session.nativeFormOpen = false;
        return sent;
    }

    private void acceptNativeOption(UUID playerId, UUID sessionId, long revision,
                                    BedrockMenuTranslator.Option option) {
        Session session = nativeSession(playerId, sessionId, revision);
        Player player = Bukkit.getPlayer(playerId);
        if (session == null || player == null) return;
        session.nativeFormOpen = false;
        if (option.elementId() != null) {
            FloatingMenuDefinition.Entry current =
                    session.definition.entries().get(option.elementId());
            if (current == null) {
                showNativeMenuOrClose(session, player);
                return;
            }
            if (!current.enabled()) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.25F, 0.65F);
                if (current.disabledReason() != null) {
                    player.sendActionBar(current.disabledReason());
                }
                showNativeMenuOrClose(session, player);
                return;
            }
        }
        if (option.interactions().size() == 1) {
            activate(new Target(playerId, option.elementId()), option.interactions().getFirst());
            return;
        }
        if (!showNativeActions(session, player, option)) {
            closeImmediately(playerId, FloatingMenuCloseReason.ERROR);
        }
    }

    private void acceptNativeAction(UUID playerId, UUID sessionId, long revision,
                                    String elementId,
                                    FloatingMenuInteraction interaction) {
        Session session = nativeSession(playerId, sessionId, revision);
        if (session == null) return;
        session.nativeFormOpen = false;
        activate(new Target(playerId, elementId), interaction);
    }

    private void backFromNativeActions(UUID playerId, UUID sessionId, long revision) {
        Session session = nativeSession(playerId, sessionId, revision);
        Player player = Bukkit.getPlayer(playerId);
        if (session == null || player == null) return;
        session.nativeFormOpen = false;
        showNativeMenuOrClose(session, player);
    }

    private void closeNativeResponse(UUID playerId, UUID sessionId, long revision) {
        Session session = nativeSession(playerId, sessionId, revision);
        if (session == null) return;
        session.nativeFormOpen = false;
        closeImmediately(playerId, FloatingMenuCloseReason.USER);
    }

    private Session nativeSession(UUID playerId, UUID sessionId, long revision) {
        Session session = sessions.get(playerId);
        if (session == null || !session.nativeForm || !session.id.equals(sessionId)
                || session.nativeFormRevision != revision
                || (session.state != FloatingMenuState.ACTIVE
                && session.state != FloatingMenuState.OPENING)) {
            return null;
        }
        return session;
    }

    private void showNativeMenuOrClose(Session session, Player player) {
        if (!showNativeMenu(session, player)) {
            closeImmediately(session.playerId, FloatingMenuCloseReason.ERROR);
        }
    }

    private void invalidateNativeForm(Session session, Player player) {
        if (!session.nativeFormOpen) return;
        session.nativeFormOpen = false;
        session.nativeFormRevision++;
        nativeForms.close(player);
    }

    private void receiveNativeCallback(Runnable callback) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) {
            callback.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, callback);
        }
    }

    private void acceptInput(Target target, FloatingMenuInteraction interaction) {
        Session session = sessions.get(target.playerId);
        if (session == null || session.state != FloatingMenuState.ACTIVE) return;
        Button selected = session.button(target.elementId);
        if (selected == null) return;
        long now = System.currentTimeMillis();
        if (target.elementId.equals(session.lastInputId)
                && interaction == session.lastInputInteraction
                && now - session.lastInputAt < INPUT_DEBOUNCE_MILLIS) {
            return;
        }
        session.lastInputId = target.elementId;
        session.lastInputInteraction = interaction;
        session.lastInputAt = now;
        activate(target, interaction);
    }

    private void activate(Target target, FloatingMenuInteraction interaction) {
        Session session = sessions.get(target.playerId);
        Player player = Bukkit.getPlayer(target.playerId);
        if (session == null || player == null || session.state != FloatingMenuState.ACTIVE) return;
        FloatingMenuDefinition.Entry selectedEntry = target.elementId == null
                ? null : session.definition.entries().get(target.elementId);
        Button selected = session.nativeForm ? null : session.button(target.elementId);
        if (selectedEntry != null && !selectedEntry.enabled()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.25F, 0.65F);
            if (selectedEntry.disabledReason() != null) {
                player.sendActionBar(selectedEntry.disabledReason());
            }
            if (session.nativeForm) showNativeMenuOrClose(session, player);
            return;
        }
        FloatingMenuAction directAction = actionFor(session, target.elementId, interaction);
        if (directAction == null) {
            if (session.nativeForm) showNativeMenuOrClose(session, player);
            return;
        }
        long nativeRevisionBeforeAction = session.nativeFormRevision;
        session.selectedId = target.elementId;
        session.selectedVisualState = FloatingMenuElementState.PRESSED;
        session.selectedTicks = session.animation.pressTicks();
        session.layoutDirty = true;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55F,
                session.feedback.pitch(interaction));
        if (!session.nativeForm && selected != null) {
            updateButtonText(session, player, selected, FloatingMenuElementState.PRESSED);
            if (!selected.textOnly) updateVisual(session, player, selected.visualId,
                    selected.item, selected.block, FloatingMenuElementState.PRESSED);
        }
        try {
            directAction.execute(player, new Handle(session.id, session.playerId), interaction);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Floating menu action failed for player " + player.getName()
                            + " at element " + target.elementId, exception);
            close(player, FloatingMenuCloseReason.ERROR);
        }
        // A handler opening another menu replaces this session. Otherwise redraw
        // mutable toggles while retaining the same view and lifecycle.
        if (sessions.get(target.playerId) == session
                && (!session.nativeForm
                || session.nativeFormRevision == nativeRevisionBeforeAction)) {
            refresh(session, player);
        }
    }

    private FloatingMenuAction actionFor(Session session, String elementId,
                                         FloatingMenuInteraction interaction) {
        FloatingMenuAction direct = elementId == null ? null
                : Optional.ofNullable(session.actions.get(elementId))
                .map(actions -> actions.get(interaction)).orElse(null);
        return direct != null ? direct : session.triggers.get(interaction);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (!event.isAttack() && event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Target catcher = targets.get(event.getEntityId());
        if (catcher == null || !catcher.playerId.equals(player.getUniqueId())) return;
        Session session = sessions.get(player.getUniqueId());
        String picked = session == null ? null : pickElement(session, player);
        if (picked == null) return;
        acceptInput(new Target(player.getUniqueId(), picked), event.isAttack()
                ? FloatingMenuInteraction.PRIMARY : FloatingMenuInteraction.SECONDARY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!scrollCaptures.contains(playerId)) return;
        Session session = sessions.get(playerId);
        int clockwise = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        if (clockwise != 1 && clockwise != 8) return;
        FloatingMenuInteraction interaction = clockwise == 1
                ? FloatingMenuInteraction.SCROLL_DOWN : FloatingMenuInteraction.SCROLL_UP;
        if (session == null || session.state != FloatingMenuState.ACTIVE
                || actionFor(session, session.hoveredId, interaction) == null) return;
        event.setCancelled(true);
        activate(new Target(playerId, session.hoveredId), interaction);
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

    private boolean protectsWorldBehindMenu(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.state == FloatingMenuState.CLOSED
                || session.state == FloatingMenuState.SUSPENDED
                || session.nativeForm) {
            return false;
        }
        return WORLD_INTERACTION_GUARD.contains(player.getEyeLocation(), session.origin,
                session.definition.framing());
    }

    private void spawn(Session session) {
        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null) return;
        // Axiom must receive the UUIDs before the entities are spawned, otherwise
        // its editor can briefly create a white gizmo for the first rendered frame.
        axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids());
        if (session.definition.titleVisible()) {
            virtualEntities.spawn(player, session.titleId, session.titleUuid,
                    VirtualMenuEntityRenderer.Kind.TEXT, session.origin, session.presentationYaw);
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
        virtualEntities.spawn(player, button.textId, button.textUuid,
                VirtualMenuEntityRenderer.Kind.TEXT, session.origin, session.presentationYaw);
        FloatingMenuElementState initialState = !button.enabled
                ? FloatingMenuElementState.DISABLED
                : button.selected ? FloatingMenuElementState.SELECTED
                : FloatingMenuElementState.NORMAL;
        updateButtonText(session, player, button, initialState);
        if (!button.textOnly) {
            virtualEntities.spawn(player, button.visualId, button.visualUuid,
                    button.block ? VirtualMenuEntityRenderer.Kind.BLOCK
                            : VirtualMenuEntityRenderer.Kind.ITEM,
                    session.origin, session.presentationYaw);
            updateVisual(session, player, button.visualId,
                    button.item, button.block, initialState);
        }
        if (button.interactive) {
            virtualEntities.spawn(player, button.hitboxId, button.hitboxUuid,
                    VirtualMenuEntityRenderer.Kind.INTERACTION,
                    session.origin, session.presentationYaw);
            virtualEntities.interaction(player, button.hitboxId,
                    (float) interactionWidth(button, session),
                    (float) interactionHeight(button, session));
            targets.put(button.hitboxId, new Target(session.playerId, button.id));
        }
    }

    private void spawnDecoration(Session session, Player player, Decoration decoration) {
        FloatingMenuDecoration.Content content = decoration.definition.content();
        VirtualMenuEntityRenderer.Kind kind = decorationKind(content);
        Location spawnAt = decoration.definition.worldAnchored()
                ? session.position(decoration).subtract(0.0, 0.18, 0.0)
                : session.origin;
        float spawnYaw = decoration.definition.placement() instanceof FloatingMenuPlacement.World world
                ? (float) world.yawDegrees() : session.presentationYaw;
        float spawnPitch = decoration.definition.placement() instanceof FloatingMenuPlacement.World world
                ? (float) world.pitchDegrees() : 0.0F;
        virtualEntities.spawn(player, decoration.entityId, decoration.uuid, kind,
                spawnAt, spawnYaw, spawnPitch);
        updateDecoration(session, player, decoration);
    }

    private void updateDecoration(Session session, Player player, Decoration decoration) {
        double coordinateScale = decoration.definition.worldAnchored()
                ? 1.0 : session.spatialScale;
        double typographyScale = decoration.definition.worldAnchored()
                ? 1.0 : session.typographyScale;
        switch (decoration.definition.content()) {
            case FloatingMenuDecoration.Text text -> virtualEntities.text(
                    player, decoration.entityId, text.text(), text.background(),
                    text.displayWidth(), text.displayHeight(),
                    (float) (text.scale() * coordinateScale * typographyScale),
                    text.alignment());
            case FloatingMenuDecoration.Visual visual -> virtualEntities.visual(
                    player, decoration.entityId, visual.item(), visual.block(),
                    (float) (visual.scale() * coordinateScale));
        }
        decoration.metadataDirty = false;
    }

    private static VirtualMenuEntityRenderer.Kind decorationKind(
            FloatingMenuDecoration.Content content) {
        return switch (content) {
            case FloatingMenuDecoration.Text ignored -> VirtualMenuEntityRenderer.Kind.TEXT;
            case FloatingMenuDecoration.Visual visual -> visual.block()
                    ? VirtualMenuEntityRenderer.Kind.BLOCK : VirtualMenuEntityRenderer.Kind.ITEM;
        };
    }

    private void destroyButton(Player player, Button button) {
        targets.remove(button.hitboxId);
        List<Integer> ids = new ArrayList<>(3);
        ids.add(button.textId);
        if (!button.textOnly) ids.add(button.visualId);
        if (button.interactive) ids.add(button.hitboxId);
        virtualEntities.destroy(player, ids.stream().mapToInt(Integer::intValue).toArray());
    }

    private void update(Session session, Player player, FloatingMenuDefinition definition) {
        if (session.nativeForm) {
            updateNative(session, player, definition);
            return;
        }
        LayoutSnapshot nextLayout = layoutSnapshot(definition,
                session.typographyScale);
        if (session.titleSpawned && !definition.titleVisible()) {
            virtualEntities.destroy(player, session.titleId);
            session.titleSpawned = false;
        }
        String hoveredId = session.hoveredId;
        String selectedId = session.selectedId;
        FloatingMenuFocusAction previousFocus = hoveredId == null
                ? null : session.focusActions.get(hoveredId);
        FloatingMenuDefinition.Entry hoveredEntry = hoveredId == null
                ? null : definition.entries().get(hoveredId);
        FloatingMenuFocusAction nextFocus = hoveredEntry == null ? null : hoveredEntry.focusAction();
        if (previousFocus != null && previousFocus != nextFocus) {
            invokeFocus(previousFocus, player, new Handle(session.id, session.playerId), false);
        }
        Map<String, Button> previous = new HashMap<>();
        for (Button button : session.buttons) previous.put(button.id, button);
        List<Button> nextButtons = new ArrayList<>();
        List<Button> buttonsToSpawn = new ArrayList<>();
        int index = 0;
        for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
            Button button = previous.remove(entry.id());
            if (button == null || button.style != entry.style()
                    || button.interactive != entry.interactive()) {
                if (button != null) destroyButton(player, button);
                button = new Button(player, entry, index,
                        session.typographyScale);
                button.appearProgress = session.state == FloatingMenuState.ACTIVE ? 0.0 : 1.0;
                buttonsToSpawn.add(button);
            }
            button.index = index++;
            nextButtons.add(button);
        }
        previous.values().forEach(button -> destroyButton(player, button));

        Map<String, Decoration> previousDecorations = new HashMap<>();
        for (Decoration decoration : session.decorations) {
            previousDecorations.put(decoration.definition.id(), decoration);
        }
        List<Decoration> nextDecorations = new ArrayList<>();
        List<Decoration> decorationsToSpawn = new ArrayList<>();
        for (FloatingMenuDecoration definitionDecoration : definition.decorations().values()) {
            Decoration decoration = previousDecorations.remove(definitionDecoration.id());
            if (decoration != null
                    && decorationKind(decoration.definition.content())
                    != decorationKind(definitionDecoration.content())) {
                virtualEntities.destroy(player, decoration.entityId);
                decoration = null;
            }
            if (decoration == null) {
                decoration = new Decoration(player, definitionDecoration);
                decoration.appearProgress = session.state == FloatingMenuState.ACTIVE ? 0.0 : 1.0;
                decorationsToSpawn.add(decoration);
            } else {
                decoration.updateDefinition(definitionDecoration);
            }
            nextDecorations.add(decoration);
        }
        for (Decoration removed : previousDecorations.values()) {
            virtualEntities.destroy(player, removed.entityId);
        }

        session.buttons.clear();
        session.buttons.addAll(nextButtons);
        session.decorations.clear();
        session.decorations.addAll(nextDecorations);
        session.definition = definition;
        session.refreshRevision = definition.refresh().revision(player);
        session.animation = definition.animation();
        session.feedback = definition.feedback();
        session.appearance = definition.appearance();
        session.layoutSnapshot = nextLayout;
        boolean viewpointChanged = session.synchronizeViewpoint(player);
        if (viewpointChanged
                || definition.anchorMode() == FloatingMenuAnchorMode.ADAPTIVE) {
            session.reanchor(definition, nextLayout);
        }
        session.initializeMissingButtonPositions();
        session.actions = definition.entries().entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().triggers()));
        session.focusActions = definition.entries().entrySet().stream()
                .filter(value -> value.getValue().focusAction() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().focusAction()));
        session.triggers = definition.triggers();
        session.hoveredId = definition.entries().containsKey(hoveredId) ? hoveredId : null;
        session.selectedId = definition.entries().containsKey(selectedId) ? selectedId : null;
        session.layoutDirty = true;
        // Reconcile the complete set first, then spawn additions. Axiom's payload
        // replaces its set rather than appending to it.
        axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids());
        if (definition.titleVisible() && !session.titleSpawned) {
            virtualEntities.spawn(player, session.titleId, session.titleUuid,
                    VirtualMenuEntityRenderer.Kind.TEXT, session.origin, session.presentationYaw);
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
        if (nextFocus != null && previousFocus != nextFocus) {
            invokeFocus(nextFocus, player, new Handle(session.id, session.playerId), true);
        }
        updateScrollCapture(session);
    }

    private void updateNative(Session session, Player player,
                              FloatingMenuDefinition definition) {
        session.definition = definition;
        session.refreshRevision = definition.refresh().revision(player);
        session.animation = definition.animation();
        session.feedback = definition.feedback();
        session.appearance = definition.appearance();
        session.actions = definition.entries().entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().triggers()));
        session.focusActions = definition.entries().entrySet().stream()
                .filter(value -> value.getValue().focusAction() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        value -> value.getValue().focusAction()));
        session.triggers = definition.triggers();
        session.selectedId = definition.entries().containsKey(session.selectedId)
                ? session.selectedId : null;
        if ((session.state == FloatingMenuState.ACTIVE
                || session.state == FloatingMenuState.OPENING)
                && (!session.nativeLiveRefresh || !session.nativeFormOpen)) {
            showNativeMenuOrClose(session, player);
        }
    }

    private void refresh(Session session, Player player) {
        if (session.nativeForm) {
            showNativeMenuOrClose(session, player);
            return;
        }
        for (Button button : session.buttons) {
            FloatingMenuDefinition.Entry entry = session.definition.entries().get(button.id);
            if (entry == null) continue;
            ItemStack item = entry.item();
            boolean nextBlock = entry.style() == FloatingMenuElementStyle.BLOCK;
            boolean changedKind = nextBlock != button.block;
            button.item = item;
            button.block = nextBlock;
            button.label = entry.label();
            button.measurement = FloatingMenuNodeSizing.measure(entry,
                    session.typographyScale);
            button.region = entry.region();
            button.alignment = entry.alignment();
            button.selected = entry.selected();
            button.enabled = entry.enabled();
            button.disabledReason = entry.disabledReason();
            if (!button.textOnly && changedKind) {
                virtualEntities.destroy(player, button.visualId);
                virtualEntities.spawn(player, button.visualId, button.visualUuid,
                        button.block ? VirtualMenuEntityRenderer.Kind.BLOCK
                                : VirtualMenuEntityRenderer.Kind.ITEM,
                        session.position(button), session.yaw(button), session.pitch(button));
            }
            FloatingMenuElementState visual = elementState(session, button);
            updateButtonText(session, player, button, visual);
            if (button.interactive) {
                virtualEntities.interaction(player, button.hitboxId,
                        (float) interactionWidth(button, session),
                        (float) interactionHeight(button, session));
            }
            if (!button.textOnly) updateVisual(session, player, button.visualId,
                    button.item, button.block, visual);
        }
        for (Decoration decoration : session.decorations) {
            if (session.definition.anchorMode() == FloatingMenuAnchorMode.ADAPTIVE
                    || decoration.metadataDirty) {
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
        session.reanchor(session.definition, session.layoutSnapshot);
        session.layoutDirty = true;
        for (Decoration decoration : session.decorations) decoration.metadataDirty = true;
        refresh(session, player);
        if (session.titleSpawned) {
            updateTitle(session, player, session.titleId, session.definition.title());
        }
    }

    private void animate(Session session, Player player) {
        double phase = switch (session.state) {
            case OPENING -> session.age / (double) session.animation.openingTicks();
            case CLOSING -> session.age / (double) session.animation.closingTicks();
            case ACTIVE, SUSPENDED -> 1.0;
            case CLOSED -> 0.0;
        };
        double sceneProgress = switch (session.state) {
            case OPENING -> session.animation.openingEasing().apply(phase);
            case CLOSING -> 1.0 - session.animation.closingEasing().apply(phase);
            case ACTIVE, SUSPENDED -> 1.0;
            case CLOSED -> 0.0;
        };
        boolean layoutAnimation = session.state != FloatingMenuState.ACTIVE || session.layoutDirty;
        session.layoutDirty = false;
        for (int index = 0; index < session.buttons.size(); index++) {
            Button button = session.buttons.get(index);
            boolean layoutMotionChanged = button.approachLayout(
                    session.layoutPose(button), 0.30);
            double previousHover = button.hoverProgress;
            double previousPress = button.pressProgress;
            double previousAppear = button.appearProgress;
            button.hoverProgress = approach(button.hoverProgress,
                    button.id.equals(session.hoveredId) ? (button.enabled ? 1.0 : 0.45) : 0.0,
                    0.28);
            button.pressProgress = approach(button.pressProgress,
                    button.id.equals(session.selectedId) && session.selectedTicks > 0 ? 1.0 : 0.0, 0.38);
            button.appearProgress = approach(button.appearProgress, 1.0, 0.24);
            boolean motionChanged = layoutMotionChanged
                    || changed(previousHover, button.hoverProgress)
                    || changed(previousPress, button.pressProgress)
                    || changed(previousAppear, button.appearProgress);

            double buttonProgress = switch (session.state) {
                case OPENING -> stagger(phase, index, session.buttons.size(), true,
                        session.animation.openingEasing());
                case CLOSING -> stagger(phase, index, session.buttons.size(), false,
                        session.animation.closingEasing());
                case ACTIVE, SUSPENDED -> 1.0;
                case CLOSED -> 0.0;
            };
            buttonProgress *= button.appearProgress;
            float yaw = session.presentationYaw
                    + (float) (button.renderedYaw * buttonProgress);
            float pitch = (float) (button.renderedPitch * buttonProgress);
            Vector panelNormal = FloatingMenuSurfaceGeometry.facing(yaw, pitch);
            Vector panelUp = FloatingMenuSurfaceGeometry.panelUp(yaw, pitch);
            Location target = session.position(button);
            Location at = session.origin.clone().add(target.toVector().subtract(session.origin.toVector())
                    .multiply(buttonProgress));
            at.add(panelNormal.clone().multiply(FloatingMenuNodeGeometry.scaledDistance(
                    session.animation.hoverOffset() * FloatingMenuEasing.CUBIC_OUT.apply(button.hoverProgress)
                            - session.animation.pressOffset() * button.pressProgress,
                    session.spatialScale)));
            double idle = session.state == FloatingMenuState.ACTIVE
                    ? Math.sin((session.totalTicks + button.index * 2.3) * session.animation.idleSpeed())
                    * session.animation.idleAmplitude() : 0.0;
            FloatingMenuNodeGeometry.Placement placement =
                    FloatingMenuNodeGeometry.placement(button.style,
                            session.spatialScale, idle);
            Location visualAt = at.clone().add(panelUp.clone().multiply(placement.visualUp()));
            double textAnchorDown = FloatingMenuNodeGeometry.textAnchorDown(
                    button.measurement.text(), session.spatialScale
                            * session.typographyScale);
            Location textAt = at.clone().add(panelUp.clone().multiply(
                    placement.textUp() - textAnchorDown));
            textAt.add(panelNormal.clone().multiply(placement.textForward()));
            button.surfaceCenter = at.toVector();
            button.surfaceYaw = yaw;
            button.surfacePitch = pitch;
            boolean moveAnchors = layoutAnimation || motionChanged;
            if (moveAnchors) teleport(player, button.textId, textAt, yaw, pitch);
            if (!button.textOnly && (moveAnchors || session.totalTicks % 2L == 0L)) {
                teleport(player, button.visualId, visualAt, yaw, pitch);
            }
            if (moveAnchors && button.interactive) {
                Location hitboxAt = at.clone().subtract(0.0,
                        interactionHeight(button, session) * 0.5,
                        0.0);
                teleport(player, button.hitboxId, hitboxAt, yaw, pitch);
            }
        }
        for (Decoration decoration : session.decorations) {
            if (decoration.definition.placement() instanceof FloatingMenuPlacement.Local local) {
                if (decoration.definition.transition()
                        == FloatingMenuDecoration.Transition.TRACKING) {
                    decoration.initializeLayout(local.pose());
                } else {
                    decoration.approachLayout(local.pose(), 0.24);
                }
            }
            decoration.appearProgress = approach(decoration.appearProgress, 1.0, 0.18);
            double decorationProgress = sceneProgress * decoration.appearProgress;
            Location target = session.position(decoration);
            Location at;
            float yaw;
            float pitch;
            if (decoration.definition.placement() instanceof FloatingMenuPlacement.World world) {
                at = target.clone().subtract(0.0, 0.18 * (1.0 - decorationProgress), 0.0);
                yaw = (float) world.yawDegrees();
                pitch = (float) world.pitchDegrees();
            } else {
                at = session.origin.clone().add(target.toVector()
                        .subtract(session.origin.toVector()).multiply(decorationProgress));
                yaw = session.presentationYaw
                        + (float) (decoration.renderedYaw * decorationProgress);
                pitch = (float) (decoration.renderedPitch * decorationProgress);
            }
            if (decoration.definition.motion().bobs() && session.state == FloatingMenuState.ACTIVE) {
                double motionScale = decoration.definition.worldAnchored()
                        ? 1.0 : session.spatialScale;
                double bob = Math.sin((session.totalTicks + decoration.phaseOffset) * 0.085)
                        * FloatingMenuNodeGeometry.scaledDistance(0.045, motionScale);
                at.add(0.0, bob, 0.0);
            }
            if (decoration.definition.motion().spins()) {
                yaw += (float) ((session.totalTicks * 2.4 + decoration.phaseOffset) % 360.0);
            }
            teleport(player, decoration.entityId, at, yaw, pitch);
            session.definition.frameObserver().presented(player,
                    decoration.definition.id(), System.nanoTime());
        }
        if (layoutAnimation && session.titleSpawned) {
            Location titleAt = session.headerPosition();
            teleport(session, player, session.titleId, session.origin.clone().add(
                    titleAt.toVector().subtract(session.origin.toVector()).multiply(sceneProgress)));
        }
        if (session.selectedTicks > 0 && --session.selectedTicks == 0) {
            Button selected = session.button(session.selectedId);
            session.selectedId = null;
            session.selectedVisualState = FloatingMenuElementState.PRESSED;
            if (selected != null) {
                FloatingMenuElementState state = elementState(session, selected);
                updateButtonText(session, player, selected, state);
                if (!selected.textOnly) updateVisual(session, player, selected.visualId,
                        selected.item, selected.block, state);
            }
            session.layoutDirty = true;
        }
    }

    private void closeImmediately(UUID playerId, FloatingMenuCloseReason reason) {
        Session session = sessions.get(playerId);
        if (session == null) return;
        removeCurrentVisuals(session, FloatingMenuState.CLOSED, reason);
        if (!sessions.containsKey(playerId)) {
            menuStatusDisplays.remove(playerId);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            for (Frame frame : session.ancestors) {
                notifyLifecycle(frame.definition, player, new Handle(frame.id, playerId),
                        FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED, reason);
            }
        }
    }

    private void updateHover(Session session, Player player) {
        String hovered = session.state == FloatingMenuState.ACTIVE
                ? pickElement(session, player) : null;
        if (java.util.Objects.equals(hovered, session.hoveredId)) return;
        Button previous = session.button(session.hoveredId);
        Button next = session.button(hovered);
        session.hoveredId = hovered;
        session.layoutDirty = true;
        if (previous != null && !previous.id.equals(session.selectedId)) {
            FloatingMenuElementState previousState = elementState(session, previous);
            updateButtonText(session, player, previous, previousState);
            if (!previous.textOnly) updateVisual(session, player, previous.visualId,
                    previous.item, previous.block, previousState);
        }
        FloatingMenuHandle handle = new Handle(session.id, session.playerId);
        FloatingMenuFocusAction previousFocus = previous == null ? null : session.focusActions.get(previous.id);
        if (previousFocus != null) invokeFocus(previousFocus, player, handle, false);
        if (next != null) {
            FloatingMenuElementState nextState = elementState(session, next);
            updateButtonText(session, player, next, nextState);
            if (!next.textOnly) updateVisual(session, player, next.visualId,
                    next.item, next.block, nextState);
        }
        if (next != null && next.enabled && System.currentTimeMillis() - session.lastHoverSoundAt >= 80L) {
            FloatingMenuFeedback feedback = session.feedback;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                    feedback.hoverVolume(), feedback.hoverPitch());
            session.lastHoverSoundAt = System.currentTimeMillis();
        }
        FloatingMenuFocusAction nextFocus = next == null ? null : session.focusActions.get(next.id);
        if (nextFocus != null) invokeFocus(nextFocus, player, handle, true);
        updateScrollCapture(session);
    }

    private String pickElement(Session session, Player player) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector();
        Vector ray = eye.getDirection();
        double margin = HOVER_SURFACE_MARGIN * session.spatialScale;
        String picked = null;
        double nearest = Double.POSITIVE_INFINITY;
        double closestCenter = Double.POSITIVE_INFINITY;
        for (Button button : session.buttons) {
            if (!button.interactive || button.surfaceCenter == null) continue;
            Optional<FloatingMenuSurfaceGeometry.Hit> intersection =
                    FloatingMenuSurfaceGeometry.intersect(origin, ray, button.surfaceCenter,
                            button.surfaceYaw, button.surfacePitch,
                            button.measurement.footprint().width() * session.spatialScale,
                            button.measurement.footprint().height() * session.spatialScale,
                            margin);
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

    private static double interactionWidth(Button button, Session session) {
        return (button.measurement.footprint().width() + HOVER_SURFACE_MARGIN * 2.0)
                * session.spatialScale;
    }

    private static double interactionHeight(Button button, Session session) {
        return (button.measurement.footprint().height() + HOVER_SURFACE_MARGIN * 2.0)
                * session.spatialScale;
    }

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
        if (session.nativeForm) {
            scrollCaptures.remove(session.playerId);
            return;
        }
        boolean global = session.triggers.containsKey(FloatingMenuInteraction.SCROLL_UP)
                || session.triggers.containsKey(FloatingMenuInteraction.SCROLL_DOWN);
        Map<FloatingMenuInteraction, FloatingMenuAction> hovered = session.hoveredId == null
                ? null : session.actions.get(session.hoveredId);
        boolean local = hovered != null && (hovered.containsKey(FloatingMenuInteraction.SCROLL_UP)
                || hovered.containsKey(FloatingMenuInteraction.SCROLL_DOWN));
        if (session.state == FloatingMenuState.ACTIVE && (global || local)) {
            scrollCaptures.add(session.playerId);
        } else {
            scrollCaptures.remove(session.playerId);
        }
    }

    private void removeCurrentVisuals(Session session, FloatingMenuState nextState,
                                      FloatingMenuCloseReason reason) {
        sessions.remove(session.playerId, session);
        scrollCaptures.remove(session.playerId);
        Player player = Bukkit.getPlayer(session.playerId);
        if (session.nativeForm) {
            if (player != null && player.isOnline()) {
                invalidateNativeForm(session, player);
            } else {
                session.nativeFormOpen = false;
                session.nativeFormRevision++;
            }
            FloatingMenuState previous = session.state;
            session.state = nextState;
            if (player != null) notifyLifecycle(session.definition, player,
                    new Handle(session.id, session.playerId), previous, nextState, reason);
            return;
        }
        if (player != null && session.hoveredId != null) {
            FloatingMenuFocusAction focus = session.focusActions.get(session.hoveredId);
            if (focus != null) invokeFocus(focus, player,
                    new Handle(session.id, session.playerId), false);
        }
        List<Integer> ids = new ArrayList<>(session.buttons.size() * 3
                + session.decorations.size() + 1);
        if (session.titleSpawned) ids.add(session.titleId);
        for (Decoration decoration : session.decorations) ids.add(decoration.entityId);
        for (Button button : session.buttons) {
            targets.remove(button.hitboxId);
            ids.add(button.textId);
            if (!button.textOnly) ids.add(button.visualId);
            if (button.interactive) ids.add(button.hitboxId);
        }
        if (player != null && player.isOnline()) {
            virtualEntities.destroy(player, ids.stream().mapToInt(Integer::intValue).toArray());
            // Destroy first so clearing Axiom's set can never expose a surviving
            // menu entity to its editor controls.
            axiomGizmos.remove(player, session.id);
        } else {
            axiomGizmos.forget(session.playerId, session.id);
        }
        FloatingMenuState previous = session.state;
        session.state = nextState;
        if (player != null) notifyLifecycle(session.definition, player,
                new Handle(session.id, session.playerId), previous, nextState, reason);
    }

    private void closeDiscardedAncestors(Session previous, List<Frame> retained,
                                         UUID resumedId, FloatingMenuCloseReason reason) {
        Player player = Bukkit.getPlayer(previous.playerId);
        if (player == null) return;
        java.util.Set<UUID> retainedIds = retained.stream()
                .map(frame -> frame.id).collect(java.util.stream.Collectors.toSet());
        for (Frame frame : previous.ancestors) {
            if (frame.id.equals(resumedId) || retainedIds.contains(frame.id)) continue;
            notifyLifecycle(frame.definition, player, new Handle(frame.id, previous.playerId),
                    FloatingMenuState.SUSPENDED, FloatingMenuState.CLOSED, reason);
        }
    }

    private void transition(Session session, FloatingMenuState previous,
                            FloatingMenuState current, FloatingMenuCloseReason reason) {
        session.state = current;
        Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) notifyLifecycle(session.definition, player,
                new Handle(session.id, session.playerId), previous, current, reason);
        updateScrollCapture(session);
    }

    private void notifyLifecycle(FloatingMenuDefinition definition, Player player,
                                 FloatingMenuHandle handle, FloatingMenuState previous,
                                 FloatingMenuState current, FloatingMenuCloseReason reason) {
        try {
            definition.lifecycle().changed(player, handle, previous, current, reason);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Floating menu lifecycle callback failed for " + player.getName(), exception);
        }
    }

    private static LayoutSnapshot layoutSnapshot(FloatingMenuDefinition definition,
                                                 double typographyScale) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
            FloatingMenuNodeSizing.Measurement measurement =
                    FloatingMenuNodeSizing.measure(entry, typographyScale);
            nodes.add(new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(),
                    entry.region(), measurement.footprint()));
        }
        Map<String, FloatingMenuPose> poses = new java.util.LinkedHashMap<>();
        Map<String, FloatingMenuSize> sizes = new java.util.LinkedHashMap<>();
        int index = 0;
        double top = 0.0;
        double bottom = 0.0;
        for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
            FloatingMenuLayout.Context context = new FloatingMenuLayout.Context(index, nodes);
            FloatingMenuPose pose = java.util.Objects.requireNonNull(
                    definition.layout().pose(context),
                    "Layout returned no pose for '" + entry.id() + "'");
            poses.put(entry.id(), pose);
            FloatingMenuSize size = context.size();
            sizes.put(entry.id(), size);
            double nodeTop = pose.up() + size.height() * 0.5;
            double nodeBottom = pose.up() - size.height() * 0.5;
            top = index == 0 ? nodeTop : Math.max(top, nodeTop);
            bottom = index == 0 ? nodeBottom : Math.min(bottom, nodeBottom);
            index++;
        }
        return new LayoutSnapshot(Map.copyOf(poses), Map.copyOf(sizes), top, bottom);
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
        menuStatusDisplays.remove(playerId);
        menuStatusDisplays.forgetViewer(playerId);
        settings.forget(playerId);
    }

    @Override
    public void onLanguageChanged(Player player) {
        menuStatusDisplays.refreshViewerLanguage(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.state == FloatingMenuState.ACTIVE && !session.nativeForm) {
            String picked = pickElement(session, player);
            if (actionFor(session, picked, FloatingMenuInteraction.HOTKEY) != null) {
                event.setCancelled(true);
                activate(new Target(player.getUniqueId(), picked), FloatingMenuInteraction.HOTKEY);
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
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        closeImmediately(event.getPlayer().getUniqueId(), FloatingMenuCloseReason.DEATH);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        closeImmediately(event.getPlayer().getUniqueId(), FloatingMenuCloseReason.DEATH);
    }

    private void teleport(Session session, Player player, int id, Location location) {
        teleport(player, id, location, session.presentationYaw);
    }

    private void teleport(Player player, int id, Location location, float yaw) {
        virtualEntities.teleport(player, id, location, yaw);
    }

    private void teleport(Player player, int id, Location location, float yaw, float pitch) {
        virtualEntities.teleport(player, id, location, yaw, pitch);
    }

    private void updateButtonText(Session session, Player player, Button button,
                                  FloatingMenuElementState state) {
        FloatingMenuNodeSizing.TextLayout text = button.measurement.text();
        virtualEntities.text(player, button.textId, button.label,
                session.appearance.elementBackground(state),
                text.displayWidth(), text.displayHeight(), text.lineWidthPixels(),
                (float) (FloatingMenuNodeSizing.TEXT_SCALE * session.spatialScale
                        * session.typographyScale),
                button.alignment);
    }

    private void updateTitle(Session session, Player player, int entityId,
                             Component text) {
        virtualEntities.text(player, entityId, text,
                session.appearance.titleBackground(), 5.0F, 1.5F,
                (float) (0.86F * session.spatialScale
                        * session.typographyScale),
                FloatingMenuDecoration.Alignment.CENTER);
    }

    private void updateVisual(Session session, Player player, int entityId,
                              ItemStack item, boolean block,
                              FloatingMenuElementState state) {
        float baseScale = (float) ((block ? 0.32F : 0.50F) * session.spatialScale);
        float scale = baseScale * switch (state) {
            case NORMAL -> 1.0F;
            case SELECTED -> 1.10F;
            case HOVERED -> 1.22F;
            case PRESSED -> 0.88F;
            case DISABLED -> 0.92F;
        };
        virtualEntities.visual(player, entityId, item, block, scale);
    }

    private static FloatingMenuElementState elementState(Session session, Button button) {
        if (!button.enabled) return FloatingMenuElementState.DISABLED;
        if (button.id.equals(session.selectedId) && session.selectedTicks > 0) {
            return session.selectedVisualState;
        }
        if (button.id.equals(session.hoveredId)) return FloatingMenuElementState.HOVERED;
        if (button.selected) return FloatingMenuElementState.SELECTED;
        return FloatingMenuElementState.NORMAL;
    }

    private static double approach(double current, double target, double step) {
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }

    private static double approachSmooth(double current, double target, double fraction) {
        double delta = target - current;
        if (Math.abs(delta) < 0.001) return target;
        return current + delta * fraction;
    }

    private static double approachAngleSmooth(double current, double target, double fraction) {
        double delta = (target - current + 180.0) % 360.0;
        if (delta < 0.0) delta += 360.0;
        delta -= 180.0;
        if (Math.abs(delta) < 0.001) return target;
        return current + delta * fraction;
    }

    private static boolean changed(double previous, double current) {
        return Math.abs(previous - current) > 0.0001;
    }

    private static double stagger(double phase, int index, int count, boolean entering,
                                  FloatingMenuEasing easing) {
        if (count <= 1) {
            double eased = easing.apply(Math.clamp(phase, 0.0, 1.0));
            return entering ? eased : 1.0 - eased;
        }
        double order = index / (double) (count - 1);
        if (!entering) order = 1.0 - order;
        double local = Math.clamp(phase * 1.22 - order * 0.22, 0.0, 1.0);
        double eased = easing.apply(local);
        return entering ? eased : 1.0 - eased;
    }

    private record Target(UUID playerId, String elementId) {
    }

    private record LayoutSnapshot(Map<String, FloatingMenuPose> poses,
                                  Map<String, FloatingMenuSize> sizes,
                                  double top, double bottom) {
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
        public FloatingMenuState state() {
            requirePrimaryThread("state");
            Session session = sessions.get(playerId);
            if (session != null && session.id.equals(id)) return session.state;
            if (session != null && session.ancestor(id) != null) return FloatingMenuState.SUSPENDED;
            return FloatingMenuState.CLOSED;
        }

        @Override
        public int depth() {
            requirePrimaryThread("depth");
            Session session = sessions.get(playerId);
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
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
                FloatingMenuService.this.refresh(session, player);
            }
        }

        @Override
        public void reanchor() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::reanchor);
                return;
            }
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null
                    || session.nativeForm
                    || session.state == FloatingMenuState.CLOSING
                    || session.state == FloatingMenuState.CLOSED) {
                return;
            }
            session.captureAnchorView(player);
            FloatingMenuService.this.refreshSpatialAnchor(session, player);
            FloatingMenuService.this.animate(session, player);
        }

        @Override
        public void update(FloatingMenuDefinition definition) {
            java.util.Objects.requireNonNull(definition, "definition");
            if (!Bukkit.isPrimaryThread()) {
                execute(() -> update(definition));
                return;
            }
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
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
        public FloatingMenuHandle openChild(FloatingMenuDefinition definition) {
            requirePrimaryThread("openChild");
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null) {
                throw new IllegalStateException("Cannot open a child from an inactive menu");
            }
            return FloatingMenuService.this.open(player,
                    java.util.Objects.requireNonNull(definition, "definition"));
        }

        @Override
        public void back() {
            if (!Bukkit.isPrimaryThread()) {
                execute(this::back);
                return;
            }
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session == null || !session.id.equals(id) || player == null) return;
            if (!session.ancestors.isEmpty()) {
                int last = session.ancestors.size() - 1;
                Frame parent = session.ancestors.get(last);
                // Parent definitions are snapshots. Revalidate live state before
                // resuming so a language or status change cannot flash stale UI.
                FloatingMenuService.this.refreshFrameDefinition(session, parent, player);
                if (sessions.get(playerId) != session) return;
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
            Session session = sessions.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (session != null && session.id.equals(id) && player != null) {
                FloatingMenuService.this.close(player);
            } else if (session != null) {
                Frame frame = session.ancestor(id);
                if (frame != null) {
                    session.ancestors.remove(frame);
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
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45F, kind.pitch());
        }
    }

    private static final class Button {
        private final String id;
        private int index;
        private String region;
        private final int textId;
        private final int visualId;
        private final int hitboxId;
        private final UUID textUuid = UUID.randomUUID();
        private final UUID visualUuid = UUID.randomUUID();
        private final UUID hitboxUuid = UUID.randomUUID();
        private boolean block;
        private final boolean textOnly;
        private final boolean interactive;
        private final FloatingMenuElementStyle style;
        private ItemStack item;
        private Component label;
        private FloatingMenuNodeSizing.Measurement measurement;
        private FloatingMenuDecoration.Alignment alignment;
        private boolean selected;
        private boolean enabled;
        private Component disabledReason;
        private double appearProgress = 1.0;
        private double hoverProgress;
        private double pressProgress;
        private boolean layoutInitialized;
        private double renderedRight;
        private double renderedUp;
        private double renderedForward;
        private double renderedYaw;
        private double renderedPitch;
        private Vector surfaceCenter;
        private float surfaceYaw;
        private float surfacePitch;

        private Button(Player player, FloatingMenuDefinition.Entry entry, int index,
                       double typographyScale) {
            this.id = entry.id();
            this.index = index;
            this.region = entry.region();
            this.textId = allocateEntityId(player);
            this.visualId = allocateEntityId(player);
            this.hitboxId = allocateEntityId(player);
            this.item = entry.item();
            this.style = entry.style();
            this.block = style == FloatingMenuElementStyle.BLOCK;
            this.textOnly = style == FloatingMenuElementStyle.TEXT;
            this.interactive = entry.interactive();
            this.label = entry.label();
            this.measurement = FloatingMenuNodeSizing.measure(entry,
                    typographyScale);
            this.alignment = entry.alignment();
            this.selected = entry.selected();
            this.enabled = entry.enabled();
            this.disabledReason = entry.disabledReason();
        }

        private void initializeLayout(FloatingMenuPose pose) {
            renderedRight = pose.right();
            renderedUp = pose.up();
            renderedForward = pose.forward();
            renderedYaw = pose.yawDegrees();
            renderedPitch = pose.pitchDegrees();
            layoutInitialized = true;
        }

        private boolean approachLayout(FloatingMenuPose target, double fraction) {
            if (!layoutInitialized) {
                initializeLayout(target);
                return true;
            }
            double nextRight = approachSmooth(renderedRight, target.right(), fraction);
            double nextUp = approachSmooth(renderedUp, target.up(), fraction);
            double nextForward = approachSmooth(renderedForward, target.forward(), fraction);
            double nextYaw = approachAngleSmooth(renderedYaw, target.yawDegrees(), fraction);
            double nextPitch = approachSmooth(renderedPitch, target.pitchDegrees(), fraction);
            boolean changed = changed(renderedRight, nextRight)
                    || changed(renderedUp, nextUp)
                    || changed(renderedForward, nextForward)
                    || changed(renderedYaw, nextYaw)
                    || changed(renderedPitch, nextPitch);
            renderedRight = nextRight;
            renderedUp = nextUp;
            renderedForward = nextForward;
            renderedYaw = nextYaw;
            renderedPitch = nextPitch;
            return changed;
        }
    }

    private static final class Decoration {
        private FloatingMenuDecoration definition;
        private final int entityId;
        private final UUID uuid = UUID.randomUUID();
        private final double phaseOffset;
        private double appearProgress = 1.0;
        private double renderedRight;
        private double renderedUp;
        private double renderedForward;
        private double renderedYaw;
        private double renderedPitch;
        private boolean metadataDirty = true;

        private Decoration(Player player, FloatingMenuDecoration definition) {
            this.definition = definition;
            this.entityId = allocateEntityId(player);
            this.phaseOffset = Math.floorMod(definition.id().hashCode(), 360);
            if (definition.placement() instanceof FloatingMenuPlacement.Local local) {
                initializeLayout(local.pose());
            }
        }

        private void initializeLayout(FloatingMenuPose pose) {
            this.renderedRight = pose.right();
            this.renderedUp = pose.up();
            this.renderedForward = pose.forward();
            this.renderedYaw = pose.yawDegrees();
            this.renderedPitch = pose.pitchDegrees();
        }

        private void updateDefinition(FloatingMenuDecoration next) {
            boolean wasLocal = definition.placement() instanceof FloatingMenuPlacement.Local;
            metadataDirty |= !definition.content().equals(next.content())
                    || definition.worldAnchored() != next.worldAnchored();
            this.definition = next;
            if (!wasLocal && next.placement() instanceof FloatingMenuPlacement.Local local) {
                initializeLayout(local.pose());
            }
        }

        private void approachLayout(FloatingMenuPose target, double fraction) {
            renderedRight = approachSmooth(renderedRight, target.right(), fraction);
            renderedUp = approachSmooth(renderedUp, target.up(), fraction);
            renderedForward = approachSmooth(renderedForward, target.forward(), fraction);
            renderedYaw = approachAngleSmooth(renderedYaw, target.yawDegrees(), fraction);
            renderedPitch = approachSmooth(renderedPitch, target.pitchDegrees(), fraction);
        }
    }

    private static final class Session {
        private final UUID id;
        private final UUID playerId;
        private final boolean nativeForm;
        private Location origin;
        private Location openedAt;
        private Location anchorView;
        private double anchorEyeHeight;
        private Vector requestedForward;
        private Vector right;
        private final Vector up = new Vector(0, 1, 0);
        private Vector forward;
        private float presentationYaw;
        private double spatialScale;
        private double interfaceScale;
        private double typographyScale;
        private final List<Button> buttons;
        private final List<Decoration> decorations;
        private Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions;
        private Map<FloatingMenuInteraction, FloatingMenuAction> triggers;
        private Map<String, FloatingMenuFocusAction> focusActions;
        private FloatingMenuAnimation animation;
        private FloatingMenuFeedback feedback;
        private FloatingMenuAppearance appearance;
        private FloatingMenuDefinition definition;
        private Object refreshRevision;
        private LayoutSnapshot layoutSnapshot;
        private final List<Frame> ancestors;
        private final int titleId;
        private final UUID titleUuid = UUID.randomUUID();
        private boolean titleSpawned;
        private FloatingMenuState state = FloatingMenuState.OPENING;
        private int age;
        private String selectedId;
        private FloatingMenuElementState selectedVisualState = FloatingMenuElementState.PRESSED;
        private int selectedTicks;
        private String hoveredId;
        private long totalTicks;
        private boolean layoutDirty = true;
        private long lastHoverSoundAt;
        private String lastInputId;
        private FloatingMenuInteraction lastInputInteraction;
        private long lastInputAt;
        private FloatingMenuCloseReason closeReason;
        private long nativeFormRevision;
        private boolean nativeFormOpen;
        private boolean nativeLiveRefresh;

        private Session(Player player, List<Button> buttons, List<Decoration> decorations, UUID id,
                        Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions,
                        Map<FloatingMenuInteraction, FloatingMenuAction> triggers,
                        Map<String, FloatingMenuFocusAction> focusActions,
                        FloatingMenuAnimation animation, FloatingMenuFeedback feedback,
                        FloatingMenuAppearance appearance,
                        FloatingMenuDefinition definition, LayoutSnapshot layoutSnapshot,
                        List<Frame> ancestors, boolean nativeForm,
                        FloatingMenuScale selectedScale) {
            this.id = id;
            this.playerId = player.getUniqueId();
            this.nativeForm = nativeForm;
            this.interfaceScale = selectedScale.factor();
            this.typographyScale = selectedScale.typographyFactor();
            this.titleId = nativeForm ? -1 : allocateEntityId(player);
            this.definition = definition;
            captureAnchorView(player);
            Vector horizontal = requestedForward.clone();
            if (nativeForm) {
                // A Bedrock form is a client-native projection of the declarative
                // model. It must not depend on, ray trace, or validate the Java
                // client's spatial layout before it can open.
                this.forward = horizontal.normalize();
                this.right = new Vector(-forward.getZ(), 0.0, forward.getX());
                this.presentationYaw = (float) Math.toDegrees(
                        Math.atan2(forward.getX(), -forward.getZ()));
                this.origin = anchorView.clone();
                this.spatialScale = 1.0;
            } else {
                reanchor(definition, layoutSnapshot);
            }
            this.buttons = buttons;
            this.decorations = decorations;
            this.actions = Map.copyOf(actions);
            this.triggers = Map.copyOf(triggers);
            this.focusActions = Map.copyOf(focusActions);
            this.animation = animation;
            this.feedback = feedback;
            this.appearance = appearance;
            this.refreshRevision = definition.refresh().revision(player);
            this.layoutSnapshot = layoutSnapshot;
            this.ancestors = new ArrayList<>(ancestors);
            initializeMissingButtonPositions();
        }

        private void captureAnchorView(Player player) {
            this.openedAt = player.getEyeLocation().clone();
            double posedEyeHeight = player.getEyeHeight();
            this.anchorEyeHeight = definition.viewpoint().eyeHeight(
                    posedEyeHeight, player.getEyeHeight(true));
            this.anchorView = openedAt.clone()
                    .add(0.0, anchorEyeHeight - posedEyeHeight, 0.0);
            Vector horizontal = anchorView.getDirection().setY(0);
            if (!Double.isFinite(horizontal.getX()) || !Double.isFinite(horizontal.getZ())
                    || horizontal.lengthSquared() < 1.0E-8) {
                double yaw = Math.toRadians(anchorView.getYaw());
                horizontal = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            }
            this.requestedForward = horizontal.clone();
        }

        private boolean synchronizeViewpoint(Player player) {
            double nextEyeHeight = definition.viewpoint().eyeHeight(player);
            if (Math.abs(nextEyeHeight - anchorEyeHeight) < 1.0E-6) return false;
            anchorView.add(0.0, nextEyeHeight - anchorEyeHeight, 0.0);
            anchorEyeHeight = nextEyeHeight;
            return true;
        }

        private void reanchor(FloatingMenuDefinition definition,
                              LayoutSnapshot layoutSnapshot) {
            FloatingMenuAnchorResolver.SceneBounds sceneBounds =
                    FloatingMenuAnchorResolver.measure(definition,
                            layoutSnapshot.poses(), layoutSnapshot.sizes(),
                            typographyScale);
            FloatingMenuAnchorResolver.Anchor anchor = FloatingMenuAnchorResolver.resolve(
                    anchorView, requestedForward, sceneBounds, interfaceScale);
            this.forward = anchor.forward();
            this.right = anchor.right();
            this.presentationYaw = (float) Math.toDegrees(
                    Math.atan2(forward.getX(), -forward.getZ()));
            this.origin = anchor.origin();
            this.spatialScale = anchor.spatialScale();
        }

        private static Session create(Player player, UUID id, FloatingMenuDefinition definition,
                                      List<Frame> ancestors, boolean nativeForm,
                                      FloatingMenuScale selectedScale) {
            double typographyScale = selectedScale.typographyFactor();
            LayoutSnapshot layoutSnapshot = nativeForm
                    ? EMPTY_LAYOUT : FloatingMenuService.layoutSnapshot(
                            definition, typographyScale);
            List<Button> buttons = new ArrayList<>();
            List<Decoration> decorations = new ArrayList<>();
            Map<String, Map<FloatingMenuInteraction, FloatingMenuAction>> actions = new HashMap<>();
            Map<String, FloatingMenuFocusAction> focusActions = new HashMap<>();
            int index = 0;
            for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
                if (!nativeForm) buttons.add(new Button(player, entry, index,
                        typographyScale));
                index++;
                actions.put(entry.id(), entry.triggers());
                if (entry.focusAction() != null) focusActions.put(entry.id(), entry.focusAction());
            }
            if (!nativeForm) {
                for (FloatingMenuDecoration decoration : definition.decorations().values()) {
                    decorations.add(new Decoration(player, decoration));
                }
            }
            return new Session(player, buttons, decorations, id, actions,
                    definition.triggers(), focusActions,
                    definition.animation(), definition.feedback(), definition.appearance(),
                    definition, layoutSnapshot, ancestors, nativeForm, selectedScale);
        }

        private Location position(Button button) {
            return origin.clone()
                    .add(right.clone().multiply(button.renderedRight * spatialScale))
                    .add(up.clone().multiply(button.renderedUp * spatialScale))
                    .add(forward.clone().multiply(-button.renderedForward * spatialScale));
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
                    .add(right.clone().multiply(decoration.renderedRight * spatialScale))
                    .add(up.clone().multiply(decoration.renderedUp * spatialScale))
                    .add(forward.clone().multiply(-decoration.renderedForward * spatialScale));
        }

        private FloatingMenuPose layoutPose(Button button) {
            FloatingMenuPose pose = layoutSnapshot.poses.get(button.id);
            if (pose == null) throw new IllegalStateException("Missing layout pose for " + button.id);
            return pose;
        }

        private void initializeMissingButtonPositions() {
            for (Button button : buttons) {
                if (!button.layoutInitialized) button.initializeLayout(layoutPose(button));
            }
        }

        private float yaw(Button button) {
            return presentationYaw + (float) button.renderedYaw;
        }

        private float pitch(Button button) {
            return (float) button.renderedPitch;
        }

        private Location headerPosition() {
            return origin.clone().add(up.clone().multiply((layoutSnapshot.top + 0.55) * spatialScale));
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
            if (definition.titleVisible()) ids.add(titleUuid);
            for (Decoration decoration : decorations) ids.add(decoration.uuid);
            for (Button button : buttons) {
                ids.add(button.textUuid);
                if (!button.textOnly) ids.add(button.visualUuid);
                // Axiom 5.5 applies the same gizmo system to Interaction entities.
                if (button.interactive) ids.add(button.hitboxUuid);
            }
            return ids;
        }
    }

    private static final class Frame {
        private final UUID id;
        private FloatingMenuDefinition definition;
        private Object refreshRevision;

        private Frame(UUID id, FloatingMenuDefinition definition,
                      Object refreshRevision) {
            this.id = id;
            this.definition = definition;
            this.refreshRevision = refreshRevision;
        }
    }
}
