package org.encinet.mik.module.afk;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
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
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageChangeListener;
import org.encinet.mik.module.i18n.LanguageService;
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
    private static final int MAX_STATUS_LENGTH = 20;
    private static final String DEFAULT_STATUSES = "afk-default-statuses";
    private static final String DEFAULT_ENTER_TEMPLATES = "afk-enter-default-templates";
    private static final String CUSTOM_ENTER_TEMPLATES = "afk-enter-custom-templates";
    private static final String EXIT_TEMPLATES = "afk-exit-templates";
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
    private final Map<UUID, AfkState> states = new HashMap<>();
    private final Set<UUID> pendingAsyncActivity = ConcurrentHashMap.newKeySet();
    private final List<AfkStateListener> listeners = new CopyOnWriteArrayList<>();
    private final AfkDisplayController displayController;
    private final AfkCollisionController collisionController = new AfkCollisionController();

    private BukkitTask updateTask;
    private int tickCounter;

    public AfkModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.displayController = new AfkDisplayController(languageService);
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
        activityTrackers.clear();
        pendingAsyncActivity.clear();
        displayController.removeAll();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("afk")
                            .executes(ctx -> cmdToggle(requirePlayer(ctx.getSource().getSender())))
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> cmdSet(
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
        activityTrackers.put(player.getUniqueId(), newTracker(
                activityTimeMillis(), player.getLocation()));
        Bukkit.getScheduler().runTask(plugin, () -> collisionController.syncViewer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        states.remove(playerId);
        activityTrackers.remove(playerId);
        pendingAsyncActivity.remove(playerId);
        restoreAfkProtection(player);
        collisionController.forgetViewer(player);
        displayController.remove(playerId);
        displayController.forgetViewer(playerId);
        notifyListeners(player, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        boolean positionChange = isPositionChange(event.getFrom(), event.getTo());
        if (!positionChange && !isMeaningfulActivity(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (isAfk(playerId)) {
            if (positionChange) {
                event.setCancelled(true);
                AfkActivityTracker tracker = tracker(player, activityTimeMillis());
                if (tracker.hasActiveMovementGesture()) {
                    clearAfk(player, false);
                    startMovementGesture(tracker, player, activityTimeMillis());
                }
            } else {
                recordLightActivity(player);
            }
            return;
        }

        if (positionChange) {
            if (!(event instanceof PlayerTeleportEvent)) {
                recordMovement(player, event.getTo());
            }
        } else {
            recordLightActivity(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        long now = activityTimeMillis();
        Location location = player.getLocation();
        tracker(player, now).recordMovementInput(
                hasMovementInput(event.getInput()),
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ(),
                now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isSubstantialInteraction(event.getAction())) {
            recordAction(event.getPlayer());
        } else {
            recordLightActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        recordAction(event.getPlayer());
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
        recordAction(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recordAction(event.getPlayer());
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
                recordAction(player);
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
                recordAction(damager);
            }
            return;
        }
        if (event.getDamager() instanceof Player player) {
            recordAction(player);
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
            recordAction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAfkPlayerVelocity(PlayerVelocityEvent event) {
        if (isAfk(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAfkPlayerKnockback(EntityKnockbackByEntityEvent event) {
        if (event.getEntity() instanceof Player player && isAfk(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private int cmdToggle(Player player) {
        if (player == null) {
            return 0;
        }
        if (isAfk(player.getUniqueId())) {
            clearAfk(player, true);
        } else {
            setAfk(player, null, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int cmdSet(Player player, String rawMessage) {
        if (player == null) {
            return 0;
        }

        String message = normalizeMessage(rawMessage);
        if (isClearKeyword(message)) {
            clearAfk(player, true);
            return Command.SINGLE_SUCCESS;
        }

        if (message.codePointCount(0, message.length()) > MAX_STATUS_LENGTH) {
            player.sendMessage(MINI_MESSAGE.deserialize(
                    languageService.t(player, Message.AFK_STATUS_TOO_LONG_MM, MAX_STATUS_LENGTH)));
            return Command.SINGLE_SUCCESS;
        }
        setAfk(player, message.isEmpty() ? null : message, false);
        return Command.SINGLE_SUCCESS;
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
        List<Player> newlyAfk = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (states.containsKey(playerId)) {
                continue;
            }
            AfkActivityTracker tracker = tracker(player, now);
            switch (tracker.check(now)) {
                case ACTIVE -> {
                }
                case AFK_IDLE, AFK_PASSIVE -> newlyAfk.add(player);
            }
        }
        setAutomaticAfk(newlyAfk, now);
    }

    private void recordLightActivity(Player player) {
        long now = activityTimeMillis();
        tracker(player, now).recordLightActivity(now);
    }

    private void recordMovement(Player player, Location location) {
        long now = activityTimeMillis();
        tracker(player, now).recordMovement(
                worldId(location), location.getX(), location.getY(), location.getZ(), now);
    }

    private void startMovementGesture(AfkActivityTracker tracker, Player player, long now) {
        Location location = player.getLocation();
        tracker.recordMovementInput(
                true,
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ(),
                now);
    }

    private void recordAction(Player player) {
        long now = activityTimeMillis();
        tracker(player, now).recordAction(now);
        if (isAfk(player.getUniqueId())) {
            clearAfk(player, false);
        }
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

    private void setAfk(Player player, String customMessage, boolean automatic) {
        long now = activityTimeMillis();
        UUID playerId = player.getUniqueId();
        boolean hasCustomMessage = customMessage != null && !customMessage.isBlank();
        AfkState state = new AfkState(
                playerId,
                hasCustomMessage ? customMessage : null,
                automatic,
                System.currentTimeMillis());
        states.put(playerId, state);
        tracker(player, now).suspendMovementGesture();
        applyAfkProtection(player);
        displayController.update(player, state);
        notifyListeners(player, state);
        broadcastEnterMessage(player, customMessage, hasCustomMessage);
    }

    private void setAutomaticAfk(List<Player> players, long now) {
        if (players.isEmpty()) {
            return;
        }

        long sinceMillis = System.currentTimeMillis();
        Map<UUID, AfkState> newStates = new HashMap<>(players.size());
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            AfkState state = new AfkState(playerId, null, true, sinceMillis);
            states.put(playerId, state);
            newStates.put(playerId, state);
            tracker(player, now).suspendMovementGesture();
            clearNearbyMobTargets(player);
        }
        collisionController.addAll(players);

        for (Player player : players) {
            AfkState state = newStates.get(player.getUniqueId());
            notifyListeners(player, state);
        }
        broadcastAutomaticEnterMessages(players);
    }

    private void clearAfk(Player player, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        if (states.remove(playerId) == null) {
            if (notifyPlayer) {
                player.sendMessage(MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_NOT_AFK_MM)));
            }
            return;
        }

        long now = activityTimeMillis();
        Location location = player.getLocation();
        tracker(player, now).reset(
                now,
                worldId(location),
                location.getX(),
                location.getY(),
                location.getZ());
        restoreAfkProtection(player);
        displayController.remove(playerId);
        notifyListeners(player, null);
        broadcastExitMessage(player);
    }

    private void notifyListeners(Player player, AfkState state) {
        for (AfkStateListener listener : listeners) {
            listener.onAfkStateChanged(player, state);
        }
    }

    private boolean isMeaningfulActivity(Location from, Location to) {
        if (to == null) return false;
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.01D) return true;
        return isMeaningfulRotation(
                from.getYaw(), from.getPitch(), to.getYaw(), to.getPitch());
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

    private boolean hasMovementInput(Input input) {
        return input.isForward()
                || input.isBackward()
                || input.isLeft()
                || input.isRight()
                || input.isJump()
                || input.isSneak()
                || input.isSprint();
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

    private boolean isClearKeyword(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.equals("off")
                || lower.equals("clear")
                || lower.equals("cancel")
                || lower.equals("false")
                || lower.equals("取消");
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
