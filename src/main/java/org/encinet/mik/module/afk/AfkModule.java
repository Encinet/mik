package org.encinet.mik.module.afk;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageChangeListener;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.presentation.AxiomGizmoService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.util.PlayerDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class AfkModule implements Listener, AfkService, LanguageChangeListener {

    private static final long UPDATE_INTERVAL_TICKS = 5L;
    private static final int AUTO_CHECK_TICKS = 4;
    private static final long SUSPENDED_TRACKER_RETENTION_MILLIS = 30L * 60L * 1_000L;
    public static final int MAX_STATUS_LENGTH = 20;
    private static final String DEFAULT_STATUSES = "afk-default-statuses";
    private static final String DEFAULT_ENTER_TEMPLATES = "afk-enter-default-templates";
    private static final String CUSTOM_ENTER_TEMPLATES = "afk-enter-custom-templates";
    private static final String EXIT_TEMPLATES = "afk-exit-templates";
    private static final long AFK_AUTO_ENTER_GRACE_MILLIS = 1_000L;
    private static final long AFK_AUTO_ENTER_COOLDOWN_MILLIS = 1_250L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage SAFE_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()
            ))
            .build();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Map<UUID, AfkActivityTracker> activityTrackers = new HashMap<>();
    private final Map<UUID, SuspendedTracker> suspendedTrackers = new HashMap<>();
    private final Map<UUID, AfkState> states = new ConcurrentHashMap<>();
    private final Map<UUID, AutomaticAfkGate> automaticAfkGates = new HashMap<>();
    private final Set<UUID> pendingAsyncActivity = ConcurrentHashMap.newKeySet();
    private final List<AfkStateListener> listeners = new CopyOnWriteArrayList<>();
    private final AfkDisplayController displayController;
    private final AfkCollisionController collisionController = new AfkCollisionController();

    private BukkitTask updateTask;
    private int tickCounter;

    public AfkModule(JavaPlugin plugin, LanguageService languageService,
                     AxiomGizmoService axiomGizmoService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.displayController = new AfkDisplayController(languageService,
                java.util.Objects.requireNonNull(axiomGizmoService, "axiomGizmoService").scope("afk"));
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        languageService.addLanguageChangeListener(this);
        long now = activityTimeMillis();
        Bukkit.getOnlinePlayers().forEach(player -> activityTrackers.put(
                player.getUniqueId(), newTracker(now, player.getLocation())));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
        plugin.getLogger().info("AfkModule enabled");
    }

    public void disable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = null;
        languageService.removeLanguageChangeListener(this);
        collisionController.clear();
        states.clear();
        automaticAfkGates.clear();
        activityTrackers.clear();
        suspendedTrackers.clear();
        pendingAsyncActivity.clear();
        displayController.disable();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("afk")
                            .executes(ctx -> cmdEnter(requirePlayer(ctx.getSource().getSender()), null))
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> cmdEnter(
                                            requirePlayer(ctx.getSource().getSender()),
                                            StringArgumentType.getString(ctx, "message"))))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.AFK_COMMAND_DESCRIPTION),
                    List.of("away")
            );
        });
    }

    @Override
    public boolean isAfk(UUID playerId) {
        return states.containsKey(playerId);
    }

    @Override
    public boolean isActivityEligible(UUID playerId) {
        if (isAfk(playerId)) {
            return false;
        }
        AfkActivityTracker tracker = activityTrackers.get(playerId);
        return tracker != null && tracker.isActivityEligible(activityTimeMillis());
    }

    @Override
    public Optional<AfkState> getState(UUID playerId) {
        return Optional.ofNullable(states.get(playerId));
    }

    @Override
    public void addListener(AfkStateListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(AfkStateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onLanguageChanged(Player player) {
        displayController.refreshViewerLanguage(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = activityTimeMillis();
        Location location = player.getLocation();
        SuspendedTracker suspended = suspendedTrackers.remove(player.getUniqueId());
        AfkActivityTracker tracker;
        if (suspended == null || suspended.expiresAt() < now) {
            tracker = newTracker(now, location);
        } else {
            tracker = suspended.tracker();
            tracker.resumeSession(now, worldId(location), location.getX(), location.getY(), location.getZ());
        }
        activityTrackers.put(player.getUniqueId(), tracker);
        clearAutomaticAfkGate(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> collisionController.syncViewer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        clearAutomaticAfkGate(playerId);
        boolean wasAfk = states.remove(playerId) != null;
        AfkActivityTracker tracker = activityTrackers.remove(playerId);
        if (tracker != null) {
            long now = activityTimeMillis();
            if (wasAfk) {
                Location location = player.getLocation();
                tracker.resumeFromAfk(
                        now, worldId(location), location.getX(), location.getY(), location.getZ());
            }
            tracker.suspendSession(now);
            suspendedTrackers.put(playerId,
                    new SuspendedTracker(tracker, now + SUSPENDED_TRACKER_RETENTION_MILLIS));
        }
        pendingAsyncActivity.remove(playerId);
        restoreAfkProtection(player);
        collisionController.forgetViewer(player);
        displayController.remove(playerId);
        displayController.forgetViewer(playerId);
        notifyListeners(player, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isAfk(player.getUniqueId())) {
            return;
        }
        if (event instanceof PlayerTeleportEvent
                && isPositionChange(event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAcceptedPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }

        Location observed = event.getTo();
        long now = activityTimeMillis();
        Player player = event.getPlayer();
        AfkActivityTracker tracker = tracker(player, now);
        boolean positionChange = isPositionChange(event.getFrom(), observed);
        boolean meaningfulRotation = isMeaningfulRotation(
                event.getFrom().getYaw(), event.getFrom().getPitch(),
                observed.getYaw(), observed.getPitch());

        if (positionChange && isAfk(player.getUniqueId())) {
            boolean freshMovementIntent = tracker.recordMovementInput(
                    hasMovementInput(player.getCurrentInput()),
                    worldId(observed), observed.getX(), observed.getY(), observed.getZ(), now);
            if (freshMovementIntent || tracker.canMovementClearAfk()) {
                clearAfk(player, false, true);
            }
        }

        tracker.recordObservation(
                worldId(observed), observed.getX(), observed.getY(), observed.getZ(), observed.getYaw(),
                now);
        if (positionChange) {
            tracker.recordMovement(
                    worldId(observed), observed.getX(), observed.getY(), observed.getZ(), now);
        }
        if (meaningfulRotation) {
            tracker.recordLightActivity(now);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        long now = activityTimeMillis();
        Location location = player.getLocation();
        AfkActivityTracker tracker = tracker(player, now);
        boolean movementInput = hasMovementInput(event.getInput());
        boolean freshMovementIntent = tracker.recordMovementInput(
                movementInput,
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ(),
                now);
        if (freshMovementIntent && isAfk(player.getUniqueId())) {
            clearAfk(player, false, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isSubstantialInteraction(event.getAction())) {
            Block block = event.getClickedBlock();
            if (block != null) {
                recordAction(event.getPlayer(), new AfkActionEvidence(
                        AfkActionEvidence.Type.BLOCK_INTERACTION, blockTarget(block)));
            }
        } else {
            recordLightActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        recordAction(event.getPlayer(), new AfkActionEvidence(
                AfkActionEvidence.Type.ENTITY_INTERACTION,
                event.getRightClicked().getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isAfkCommand(event.getMessage())) {
            recordLightActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        pendingAsyncActivity.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordAction(event.getPlayer(), new AfkActionEvidence(
                AfkActionEvidence.Type.BLOCK_CHANGE, blockTarget(event.getBlock())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recordAction(event.getPlayer(), new AfkActionEvidence(
                AfkActionEvidence.Type.BLOCK_CHANGE, blockTarget(event.getBlockPlaced())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        recordLightActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSprint(PlayerToggleSprintEvent event) {
        recordLightActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (isSubstantialInventoryAction(event.getAction())) {
                recordAction(player, new AfkActionEvidence(
                        AfkActionEvidence.Type.INVENTORY, inventoryTarget(event)));
            } else {
                recordLightActivity(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target && isAfk(target.getUniqueId())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player damager && !damager.getUniqueId().equals(target.getUniqueId())) {
                recordLightActivity(damager);
            }
            return;
        }
        if (event.getDamager() instanceof Player player) {
            recordAction(player, new AfkActionEvidence(
                    AfkActionEvidence.Type.COMBAT, event.getEntity().getUniqueId().toString()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTargetAfkPlayer(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isAfk(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFishAfkPlayer(PlayerFishEvent event) {
        if (event.getCaught() instanceof Player target && isAfk(target.getUniqueId())) {
            event.setCancelled(true);
            recordLightActivity(event.getPlayer());
        }
    }

    private int cmdEnter(Player player, String rawMessage) {
        if (player == null) {
            return 0;
        }
        if (isAfk(player.getUniqueId())) {
            notifyAlreadyAfk(player);
            return Command.SINGLE_SUCCESS;
        }

        String normalizedMessage = rawMessage == null ? null : normalizeMessage(rawMessage);
        if (normalizedMessage != null && normalizedMessage.codePointCount(0, normalizedMessage.length()) > MAX_STATUS_LENGTH) {
            player.sendMessage(MINI_MESSAGE.deserialize(
                    languageService.t(player, Message.AFK_STATUS_TOO_LONG_MM, MAX_STATUS_LENGTH)));
            return Command.SINGLE_SUCCESS;
        }
        String finalMessage = normalizedMessage == null || normalizedMessage.isEmpty() ? null : normalizedMessage;
        setAfk(player, finalMessage, AfkSource.MANUAL, true);
        return Command.SINGLE_SUCCESS;
    }

    private void notifyAlreadyAfk(Player player) {
        player.sendMessage(MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_ALREADY_AFK_MM)));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY),
                net.kyori.adventure.text.format.NamedTextColor.RED));
        return null;
    }

    private void tick() {
        flushPendingActivity();

        tickCounter++;
        if (tickCounter < AUTO_CHECK_TICKS) {
            return;
        }
        tickCounter = 0;

        checkAutoAfk();
        displayController.updateTrackedDisplays(states.values());
    }

    private void flushPendingActivity() {
        if (pendingAsyncActivity.isEmpty()) {
            return;
        }
        for (UUID playerId : pendingAsyncActivity) {
            pendingAsyncActivity.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                recordLightActivity(player);
            }
        }
    }

    private void checkAutoAfk() {
        long now = activityTimeMillis();
        suspendedTrackers.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        List<Player> newlyAfk = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (states.containsKey(playerId)) {
                clearAutomaticAfkGate(playerId);
                continue;
            }

            AfkActivityTracker tracker = tracker(player, now);
            AfkActivityTracker.CheckResult result = tracker.check(now);
            boolean inactivityReached = switch (result) {
                case ACTIVE -> false;
                case AFK_IDLE, AFK_PASSIVE -> true;
                case ACTIVITY_REWARD_LOCKED -> {
                    plugin.getLogger().info("Excluded " + player.getName()
                            + " (" + playerId + ") from activity rewards after sustained automated movement patterns");
                    yield false;
                }
            };
            boolean eligible = inactivityReached && canEnterAutomaticAfk(player);
            if (automaticAfkGate(playerId).shouldEnter(now, eligible, tracker.activityVersion())) {
                newlyAfk.add(player);
            }
        }
        setAutomaticAfk(newlyAfk, now);
    }

    private void clearAutomaticAfkGate(UUID playerId) {
        automaticAfkGates.remove(playerId);
    }

    private AutomaticAfkGate automaticAfkGate(UUID playerId) {
        return automaticAfkGates.computeIfAbsent(playerId, ignored -> new AutomaticAfkGate(
                AFK_AUTO_ENTER_GRACE_MILLIS, AFK_AUTO_ENTER_COOLDOWN_MILLIS));
    }

    private void recordLightActivity(Player player) {
        long now = activityTimeMillis();
        tracker(player, now).recordLightActivity(now);
    }

    private void syncMovementInput(
            AfkActivityTracker tracker,
            Player player,
            Location location,
            long now
    ) {
        tracker.recordMovementInput(
                hasMovementInput(player.getCurrentInput()),
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ(),
                now);
    }

    private void recordAction(Player player, AfkActionEvidence evidence) {
        long now = activityTimeMillis();
        AfkActivityTracker tracker = tracker(player, now);
        tracker.recordAction(evidence, now);
        if (isAfk(player.getUniqueId())) {
            clearAfk(player, false, true);
        }
    }

    private static String blockTarget(Block block) {
        return "block:" + block.getWorld().getUID()
                + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static String inventoryTarget(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();
        if (inventory == null) {
            return "inventory:outside:" + event.getRawSlot();
        }

        Location location = inventory.getLocation();
        String owner;
        if (inventory.getHolder() instanceof org.bukkit.entity.Entity entity) {
            owner = entity.getUniqueId().toString();
        } else if (location != null && location.getWorld() != null) {
            owner = location.getWorld().getUID()
                    + ":" + location.getBlockX()
                    + ":" + location.getBlockY()
                    + ":" + location.getBlockZ();
        } else {
            owner = inventory.getType() + ":" + System.identityHashCode(inventory);
        }
        return "inventory:" + owner + ":" + event.getSlot();
    }

    private record SuspendedTracker(AfkActivityTracker tracker, long expiresAt) {
    }

    private AfkActivityTracker tracker(Player player, long now) {
        UUID playerId = player.getUniqueId();
        AfkActivityTracker tracker = activityTrackers.get(playerId);
        if (tracker == null) {
            tracker = newTracker(now, player.getLocation());
            activityTrackers.put(playerId, tracker);
        }
        return tracker;
    }

    private static AfkActivityTracker newTracker(long now, Location location) {
        return new AfkActivityTracker(
                now,
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    private static UUID worldId(Location location) {
        return location.getWorld() == null ? null : location.getWorld().getUID();
    }

    private static long activityTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public void setAfkFromSkript(Player player, String customMessage, boolean broadcast) {
        String message = normalizeMessage(customMessage);
        if (message.codePointCount(0, message.length()) > MAX_STATUS_LENGTH) {
            throw new IllegalArgumentException("AFK message cannot exceed " + MAX_STATUS_LENGTH + " characters");
        }
        AfkState current = states.get(player.getUniqueId());
        if (current != null) {
            clearAutomaticAfkGate(player.getUniqueId());
            AfkState updated = new AfkState(
                    current.playerId(), message.isEmpty() ? null : message,
                    AfkSource.SKRIPT, current.sinceMillis());
            states.put(current.playerId(), updated);
            displayController.update(player, updated);
            notifyListeners(player, updated);
            return;
        }
        setAfk(player, message.isEmpty() ? null : message, AfkSource.SKRIPT, broadcast);
    }

    public boolean clearAfkFromSkript(Player player, boolean broadcast) {
        return clearAfk(player, false, broadcast);
    }

    private void setAfk(Player player, String customMessage, AfkSource source, boolean broadcast) {
        long now = activityTimeMillis();
        UUID playerId = player.getUniqueId();
        clearAutomaticAfkGate(playerId);
        boolean hasCustomMessage = customMessage != null && !customMessage.isBlank();
        AfkState state = new AfkState(
                playerId,
                hasCustomMessage ? customMessage : null,
                source,
                System.currentTimeMillis());
        states.put(playerId, state);
        tracker(player, now).suspendForAfk(now, hasMovementInput(player.getCurrentInput()));
        applyAfkProtection(player);
        displayController.update(player, state);
        notifyListeners(player, state);
        if (broadcast) {
            broadcastEnterMessage(player, customMessage, hasCustomMessage);
        }
    }

    private void setAutomaticAfk(List<Player> players, long now) {
        if (players.isEmpty()) {
            return;
        }

        long sinceMillis = System.currentTimeMillis();
        Map<UUID, AfkState> newStates = new HashMap<>(players.size());
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            AfkState state = new AfkState(playerId, null, AfkSource.AUTOMATIC, sinceMillis);
            states.put(playerId, state);
            newStates.put(playerId, state);
            tracker(player, now).suspendForAfk(now, hasMovementInput(player.getCurrentInput()));
            clearNearbyMobTargets(player);
        }
        collisionController.addAll(players);

        for (Player player : players) {
            clearAutomaticAfkGate(player.getUniqueId());
            AfkState state = newStates.get(player.getUniqueId());
            notifyListeners(player, state);
        }
        broadcastAutomaticEnterMessages(players);
    }

    private boolean clearAfk(Player player, boolean notifyPlayer, boolean broadcast) {
        UUID playerId = player.getUniqueId();
        if (states.remove(playerId) == null) {
            if (notifyPlayer) {
                player.sendMessage(MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_NOT_AFK_MM)));
            }
            return false;
        }

        long now = activityTimeMillis();
        automaticAfkGate(playerId).recordExit(now);
        Location location = player.getLocation();
        AfkActivityTracker tracker = tracker(player, now);
        tracker.resumeFromAfk(
                now, worldId(location), location.getX(), location.getY(), location.getZ());
        syncMovementInput(tracker, player, location, now);
        restoreAfkProtection(player);
        displayController.remove(playerId);
        notifyListeners(player, null);
        if (broadcast) {
            broadcastExitMessage(player);
        }
        return true;
    }

    private void notifyListeners(Player player, AfkState state) {
        for (AfkStateListener listener : listeners) {
            listener.onAfkStateChanged(player, state);
        }
    }

    static boolean isMeaningfulRotation(
            float fromYaw,
            float fromPitch,
            float toYaw,
            float toPitch
    ) {
        return angularDelta(fromYaw, toYaw) >= 8.0F
                && Math.abs(fromPitch - toPitch) >= 8.0F;
    }

    static boolean isSubstantialInteraction(Action action) {
        return action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK;
    }

    static boolean isSubstantialInventoryAction(InventoryAction action) {
        return action != InventoryAction.NOTHING && action != InventoryAction.UNKNOWN;
    }

    private boolean isPositionChange(Location from, Location to) {
        if (to == null) return false;
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }

    static boolean hasMovementInput(Input input) {
        return input.isForward()
                || input.isBackward()
                || input.isLeft()
                || input.isRight()
                || input.isJump();
    }

    private static boolean canEnterAutomaticAfk(Player player) {
        return !player.isFlying()
                && !player.isGliding()
                && !player.isRiptiding()
                && !player.isInsideVehicle()
                && player.getFallDistance() == 0.0F
                && player.getVelocity().lengthSquared() < 0.01D
                && !hasMovementInput(player.getCurrentInput());
    }

    private static float angularDelta(float a, float b) {
        float delta = Math.abs(a - b) % 360.0F;
        return delta > 180.0F ? 360.0F - delta : delta;
    }

    private boolean isAfkCommand(String message) {
        String trimmed = message.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        String firstToken = trimmed.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceIndex = firstToken.indexOf(':');
        if (namespaceIndex >= 0) {
            firstToken = firstToken.substring(namespaceIndex + 1);
        }
        return firstToken.equals("afk") || firstToken.equals("away");
    }

    private String normalizeMessage(String rawMessage) {
        return rawMessage == null ? "" : rawMessage.replaceAll("\\s+", " ").trim();
    }

    private void applyAfkProtection(Player player) {
        collisionController.add(player);
        clearNearbyMobTargets(player);
    }

    private void restoreAfkProtection(Player player) {
        collisionController.remove(player);
    }

    private void clearNearbyMobTargets(Player player) {
        for (var entity : player.getNearbyEntities(48.0D, 32.0D, 48.0D)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    private void broadcastEnterMessage(Player player, String customMessage, boolean customMessagePresent) {
        Map<Language, Optional<Component>> localizedMessages = new EnumMap<>(Language.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Language language = languageService.language(viewer);
            localizedMessages.computeIfAbsent(language,
                            ignored -> enterMessage(language, player, customMessage, customMessagePresent))
                    .ifPresent(viewer::sendMessage);
        }
    }

    private void broadcastAutomaticEnterMessages(List<? extends Player> players) {
        Map<Language, List<Player>> viewersByLanguage = new EnumMap<>(Language.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewersByLanguage.computeIfAbsent(languageService.language(viewer), ignored -> new ArrayList<>())
                    .add(viewer);
        }

        for (Player player : players) {
            for (Map.Entry<Language, List<Player>> entry : viewersByLanguage.entrySet()) {
                enterMessage(entry.getKey(), player, null, false)
                        .ifPresent(message -> entry.getValue().forEach(viewer -> viewer.sendMessage(message)));
            }
        }
    }

    private Optional<Component> enterMessage(Language language, Player player, String customMessage,
                                             boolean customMessagePresent) {
        String templateList = customMessagePresent ? CUSTOM_ENTER_TEMPLATES : DEFAULT_ENTER_TEMPLATES;
        Optional<String> template = randomAttribute(language, templateList);
        Optional<String> status = customMessagePresent
                ? Optional.of(customMessage)
                : randomAttribute(language, DEFAULT_STATUSES);
        if (template.isEmpty() || status.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(MINI_MESSAGE.deserialize(template.get(),
                Placeholder.component("player", PlayerDisplay.name(player)),
                Placeholder.component("status", renderStatusMessage(status.get()))));
    }

    private void broadcastExitMessage(Player player) {
        Map<Language, Optional<Component>> localizedMessages = new EnumMap<>(Language.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Language language = languageService.language(viewer);
            localizedMessages.computeIfAbsent(language, ignored -> exitMessage(language, player))
                    .ifPresent(viewer::sendMessage);
        }
    }

    private Optional<Component> exitMessage(Language language, Player player) {
        return randomAttribute(language, EXIT_TEMPLATES)
                .map(template -> MINI_MESSAGE.deserialize(template,
                        Placeholder.component("player", PlayerDisplay.name(player))));
    }

    private Component renderStatusMessage(String message) {
        return MINI_MESSAGE.deserialize(
                "<yellow><message></yellow>",
                Placeholder.component("message", SAFE_MESSAGE.deserialize(message)));
    }

    private Optional<String> randomAttribute(Language language, String messageId) {
        Optional<String> localized = randomAttributeWithoutFallback(language, messageId);
        if (localized.isPresent() || language == Language.DEFAULT) {
            return localized;
        }
        return randomAttributeWithoutFallback(Language.DEFAULT, messageId);
    }

    private Optional<String> randomAttributeWithoutFallback(Language language, String messageId) {
        List<String> attributes = languageService.attributeNames(language, messageId);
        if (attributes.isEmpty()) {
            return Optional.empty();
        }
        String attribute = attributes.get(ThreadLocalRandom.current().nextInt(attributes.size()));
        return languageService.attribute(language, messageId, attribute);
    }
}
