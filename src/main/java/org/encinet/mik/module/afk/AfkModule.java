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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
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
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.util.PlayerDisplay;

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

public class AfkModule implements Listener, AfkService {

    private static final long AFK_TIMEOUT_MILLIS = 3L * 60L * 1000L;
    private static final long UPDATE_INTERVAL_TICKS = 5L;
    private static final int AUTO_CHECK_TICKS = 4;
    private static final int MAX_STATUS_LENGTH = 20;
    private static final Message[] DEFAULT_MESSAGES = {
            Message.AFK_DEFAULT_STATUS_1,
            Message.AFK_DEFAULT_STATUS_2,
            Message.AFK_DEFAULT_STATUS_3,
            Message.AFK_DEFAULT_STATUS_4
    };
    private static final Message[] DEFAULT_ENTER_TEMPLATES = {
            Message.AFK_ENTER_DEFAULT_MM
    };
    private static final Message[] CUSTOM_ENTER_TEMPLATES = {
            Message.AFK_ENTER_CUSTOM_1_MM,
            Message.AFK_ENTER_CUSTOM_2_MM,
            Message.AFK_ENTER_CUSTOM_3_MM
    };
    private static final Message[] EXIT_TEMPLATES = {
            Message.AFK_EXIT_1_MM,
            Message.AFK_EXIT_2_MM
    };
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
    private final Map<UUID, Long> lastActiveAt = new HashMap<>();
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
        long now = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(player -> lastActiveAt.put(player.getUniqueId(), now));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
        plugin.getLogger().info("AfkModule 已启动。");
    }

    public void disable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = null;
        collisionController.clear();
        states.clear();
        lastActiveAt.clear();
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        lastActiveAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTask(plugin, () -> collisionController.syncViewer(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        states.remove(playerId);
        lastActiveAt.remove(playerId);
        pendingAsyncActivity.remove(playerId);
        restoreAfkProtection(player);
        displayController.remove(playerId);
        displayController.forgetViewer(playerId);
        notifyListeners(player, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isMeaningfulActivity(event.getFrom(), event.getTo())) {
            return;
        }

        if (isAfk(event.getPlayer().getUniqueId())) {
            if (isPositionChange(event.getFrom(), event.getTo())) {
                event.setCancelled(true);
            } else {
                recordActivity(event.getPlayer());
            }
            return;
        }

        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInput(PlayerInputEvent event) {
        if (hasMovementInput(event.getInput())) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!isAfkCommand(event.getMessage())) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        pendingAsyncActivity.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSprint(PlayerToggleSprintEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target && isAfk(target.getUniqueId())) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player damager && !damager.getUniqueId().equals(target.getUniqueId())) {
                recordActivity(damager);
            }
            return;
        }
        if (event.getDamager() instanceof Player player) {
            recordActivity(player);
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
            recordActivity(event.getPlayer());
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
        displayController.updateTrackedDisplays(states.values());

        tickCounter = (tickCounter + 1) % AUTO_CHECK_TICKS;
        if (tickCounter == 0) {
            checkAutoAfk();
        }
    }

    private void flushPendingActivity() {
        if (pendingAsyncActivity.isEmpty()) {
            return;
        }
        for (UUID playerId : pendingAsyncActivity) {
            pendingAsyncActivity.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                recordActivity(player);
            }
        }
    }

    private void checkAutoAfk() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (states.containsKey(playerId)) {
                continue;
            }
            long lastActive = lastActiveAt.computeIfAbsent(playerId, ignored -> now);
            if (now - lastActive >= AFK_TIMEOUT_MILLIS) {
                setAfk(player, null, true);
            }
        }
    }

    private void recordActivity(Player player) {
        lastActiveAt.put(player.getUniqueId(), System.currentTimeMillis());
        if (isAfk(player.getUniqueId())) {
            clearAfk(player, false);
        }
    }

    private void setAfk(Player player, String customMessage, boolean automatic) {
        UUID playerId = player.getUniqueId();
        boolean hasCustomMessage = customMessage != null && !customMessage.isBlank();
        Message defaultMessage = hasCustomMessage ? null : randomDefaultMessage();
        Message enterTemplate = hasCustomMessage ? randomFrom(CUSTOM_ENTER_TEMPLATES) : randomFrom(DEFAULT_ENTER_TEMPLATES);
        AfkState state = new AfkState(playerId, hasCustomMessage ? customMessage : null, automatic, System.currentTimeMillis());
        states.put(playerId, state);
        applyAfkProtection(player);
        displayController.update(player, state);
        notifyListeners(player, state);
        broadcastEnterMessage(player, enterTemplate, defaultMessage, customMessage, hasCustomMessage);
    }

    private void clearAfk(Player player, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        if (states.remove(playerId) == null) {
            if (notifyPlayer) {
                player.sendMessage(MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_NOT_AFK_MM)));
            }
            return;
        }

        lastActiveAt.put(playerId, System.currentTimeMillis());
        restoreAfkProtection(player);
        displayController.remove(playerId);
        notifyListeners(player, null);
        broadcastExitMessage(player, randomFrom(EXIT_TEMPLATES));
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
        return angularDelta(from.getYaw(), to.getYaw()) >= 8.0F
                || Math.abs(from.getPitch() - to.getPitch()) >= 8.0F;
    }

    private boolean isPositionChange(Location from, Location to) {
        if (to == null) return false;
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        return dx * dx + dy * dy + dz * dz > 0.01D;
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

    private float angularDelta(float a, float b) {
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

    private Message randomDefaultMessage() {
        return randomFrom(DEFAULT_MESSAGES);
    }

    private void broadcastEnterMessage(Player player, Message template, Message defaultMessage,
                                       String customMessage, boolean customMessagePresent) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            String status = customMessagePresent ? customMessage : languageService.t(viewer, defaultMessage);
            viewer.sendMessage(enterMessage(viewer, player, template, status));
        }
    }

    private Component enterMessage(Player viewer, Player player, Message template, String message) {
        return MINI_MESSAGE.deserialize(languageService.t(viewer, template),
                Placeholder.component("player", PlayerDisplay.name(player)),
                Placeholder.component("status", renderStatusMessage(message)));
    }

    private void broadcastExitMessage(Player player, Message template) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.sendMessage(exitMessage(viewer, player, template));
        }
    }

    private Component exitMessage(Player viewer, Player player, Message template) {
        return MINI_MESSAGE.deserialize(languageService.t(viewer, template),
                Placeholder.component("player", PlayerDisplay.name(player)));
    }

    private Component renderStatusMessage(String message) {
        return MINI_MESSAGE.deserialize(
                "<yellow><message></yellow>",
                Placeholder.component("message", SAFE_MESSAGE.deserialize(message)));
    }

    private Message randomFrom(Message[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
