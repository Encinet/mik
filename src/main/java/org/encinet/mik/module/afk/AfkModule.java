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
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
    private static final int MAX_STATUS_LENGTH = 12;
    private static final String[] DEFAULT_MESSAGES = {
            "双手离开了键盘",
            "进入省电模式",
            "暂时切到后台",
            "正在原地发呆"
    };
    private static final String[] DEFAULT_ENTER_TEMPLATES = {
            "<gray>*</gray> <player> <yellow><status></yellow>"
    };
    private static final String[] CUSTOM_ENTER_TEMPLATES = {
            "<gray>*</gray> <player> <gray>开始挂机：</gray><status>",
            "<gray>*</gray> <player> <gray>留下了一张纸条：</gray><status>",
            "<gray>*</gray> <player> <gray>把状态改成了：</gray><status>"
    };
    private static final String[] EXIT_TEMPLATES = {
            "<gray>*</gray> <player> <green>回到了键盘前</green>",
            "<gray>*</gray> <player> <green>回来了</green>"
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
    private final Map<UUID, Long> lastActiveAt = new HashMap<>();
    private final Map<UUID, AfkState> states = new HashMap<>();
    private final Set<UUID> pendingAsyncActivity = ConcurrentHashMap.newKeySet();
    private final List<AfkStateListener> listeners = new CopyOnWriteArrayList<>();
    private final AfkDisplayController displayController = new AfkDisplayController();

    private BukkitTask updateTask;
    private int tickCounter;

    public AfkModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        displayController.removeOrphans();
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
                    "切换挂机状态",
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
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        states.remove(playerId);
        lastActiveAt.remove(playerId);
        pendingAsyncActivity.remove(playerId);
        displayController.remove(playerId);
        notifyListeners(player, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isMeaningfulActivity(event.getFrom(), event.getTo())) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            recordActivity(player);
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
                    "<red>挂机状态最多 <white><max></white> 个字</red>",
                    Placeholder.unparsed("max", Integer.toString(MAX_STATUS_LENGTH))));
            return Command.SINGLE_SUCCESS;
        }
        setAfk(player, message.isEmpty() ? null : message, false);
        return Command.SINGLE_SUCCESS;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(MINI_MESSAGE.deserialize("<red>该命令只能由玩家执行</red>"));
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
        String broadcastMessage = hasCustomMessage ? customMessage : randomDefaultMessage();
        AfkState state = new AfkState(playerId, hasCustomMessage ? customMessage : null, automatic, System.currentTimeMillis());
        states.put(playerId, state);
        displayController.update(player, state);
        notifyListeners(player, state);
        Bukkit.broadcast(enterMessage(player, broadcastMessage, hasCustomMessage));
    }

    private void clearAfk(Player player, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        if (states.remove(playerId) == null) {
            if (notifyPlayer) {
                player.sendMessage(MINI_MESSAGE.deserialize("<gray>你当前没有处于 <gold>挂机</gold> 状态</gray>"));
            }
            return;
        }

        lastActiveAt.put(playerId, System.currentTimeMillis());
        displayController.remove(playerId);
        notifyListeners(player, null);
        Bukkit.broadcast(exitMessage(player));
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

    private boolean isClearKeyword(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.equals("off")
                || lower.equals("clear")
                || lower.equals("cancel")
                || lower.equals("false")
                || lower.equals("取消");
    }

    private String randomDefaultMessage() {
        return randomFrom(DEFAULT_MESSAGES);
    }

    private Component enterMessage(Player player, String message, boolean customMessage) {
        String template = customMessage ? randomFrom(CUSTOM_ENTER_TEMPLATES) : randomFrom(DEFAULT_ENTER_TEMPLATES);
        return MINI_MESSAGE.deserialize(template,
                Placeholder.component("player", player.displayName()),
                Placeholder.component("status", renderStatusMessage(message)));
    }

    private Component exitMessage(Player player) {
        return MINI_MESSAGE.deserialize(randomFrom(EXIT_TEMPLATES),
                Placeholder.component("player", player.displayName()));
    }

    private Component renderStatusMessage(String message) {
        return MINI_MESSAGE.deserialize(
                "<yellow><message></yellow>",
                Placeholder.component("message", SAFE_MESSAGE.deserialize(message)));
    }

    private String randomFrom(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
