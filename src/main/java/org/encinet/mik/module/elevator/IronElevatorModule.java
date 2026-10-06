package org.encinet.mik.module.elevator;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.runtime.WorldTextDisplayService;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class IronElevatorModule implements Listener {

    private static final long COOLDOWN_NANOS = 500_000_000L;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final IronElevatorFloors floors = new IronElevatorFloors();
    private final IronElevatorWallPanel wallPanel;
    private final IronElevatorPanelInteraction panelInteraction;
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private final Map<UUID, IronElevatorPlatform> platforms = new HashMap<>();
    private final Set<UUID> showingHint = new HashSet<>();
    private BukkitTask hintTask;
    private boolean enabled;

    public IronElevatorModule(JavaPlugin plugin, LanguageService languageService,
                              WorldTextDisplayService labels) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.wallPanel = new IronElevatorWallPanel(labels, floors,
                (player, height) -> request(player, player.getLocation(), 0, height, () -> true));
        this.panelInteraction = new IronElevatorPanelInteraction(wallPanel::click);
    }

    public void enable() {
        if (enabled) {
            return;
        }
        Path config = plugin.getDataFolder().toPath().resolve("iron-elevators.yml");
        if (!Files.exists(config)) {
            plugin.saveResource("iron-elevators.yml", false);
        }
        floors.load(config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(panelInteraction, plugin);
        enabled = true;
        hintTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshHints, 20, 20);
    }

    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(panelInteraction);
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        lastTeleport.clear();
        if (hintTask != null) {
            hintTask.cancel();
            hintTask = null;
        }
        for (UUID playerId : showingHint) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar(Component.empty());
            }
        }
        for (UUID playerId : platforms.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                wallPanel.clear(player);
            } else {
                wallPanel.forget(playerId);
            }
        }
        platforms.clear();
        showingHint.clear();
        floors.clear();
        wallPanel.clearGeometry();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location destination = event.getTo();
        if (destination == null || (event.getFrom().getX() == destination.getX()
                && event.getFrom().getY() == destination.getY()
                && event.getFrom().getZ() == destination.getZ())) {
            return;
        }
        updateHint(event.getPlayer(), destination, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            updateHint(event.getPlayer(), event.getTo(), true);
        }
    }

    private void refreshHints() {
        for (UUID playerId : List.copyOf(platforms.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                platforms.remove(playerId);
                showingHint.remove(playerId);
                wallPanel.forget(playerId);
            } else {
                updateHint(player, player.getLocation(), true);
            }
        }
    }

    private void updateHint(Player player, Location feet, boolean refresh) {
        UUID playerId = player.getUniqueId();
        Block standing = canUse(player) ? IronElevatorLanding.platformAt(feet) : null;
        if (standing == null) {
            platforms.remove(playerId);
            clearHint(player);
            return;
        }
        IronElevatorPlatform previous = platforms.get(playerId);
        if (!refresh && previous != null && previous.contains(standing)) {
            return;
        }
        IronElevatorPlatform platform = floors.platform(standing);
        if (platform == null) {
            platforms.remove(playerId);
            clearHint(player);
            return;
        }
        platforms.put(playerId, platform);
        IronElevatorLanding.Size playerSize = size(player);
        boolean up = floors.destination(standing, 1, null, feet, playerSize, player::wouldCollideUsing) != null;
        boolean down = floors.destination(standing, -1, null, feet, playerSize, player::wouldCollideUsing) != null;
        if (!up && !down) {
            clearHint(player);
            return;
        }
        player.sendActionBar(languageService.text(player, Message.IRON_ELEVATOR_UP,
                up ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                .append(Component.text("   "))
                .append(languageService.text(player, Message.IRON_ELEVATOR_DOWN,
                        down ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        showingHint.add(playerId);
        wallPanel.update(player, platform);
    }

    private void clearHint(Player player) {
        wallPanel.clear(player);
        if (showingHint.remove(player.getUniqueId())) {
            player.sendActionBar(Component.empty());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (request(event.getPlayer(), event.getFrom(), 1, () -> true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            request(event.getPlayer(), event.getPlayer().getLocation(), -1, () -> !event.isCancelled());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        wallPanel.invalidate(block);
        if (block.getType() != Material.IRON_BLOCK) {
            return;
        }
        floors.invalidate(block);
        Location origin = block.getLocation().add(0.5, 1, 0.5);
        if (IronElevatorLanding.on(block, origin, IronElevatorLanding.DEFAULT_SIZE, body -> false) != null
                && (floors.destination(block, 1, null, origin, IronElevatorLanding.DEFAULT_SIZE,
                body -> false) != null || floors.destination(block, -1, null, origin,
                IronElevatorLanding.DEFAULT_SIZE, body -> false) != null)) {
            block.getWorld().playSound(origin, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 1.5F);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        wallPanel.invalidate(event.getBlock());
        invalidateIfIron(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().forEach(wallPanel::invalidate);
        invalidateIfIron(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().forEach(wallPanel::invalidate);
        invalidateIfIron(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            wallPanel.invalidate(block);
            wallPanel.invalidate(block.getRelative(event.getDirection()));
        });
        invalidateIfIron(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> {
            wallPanel.invalidate(block);
            wallPanel.invalidate(block.getRelative(event.getDirection()));
        });
        invalidateIfIron(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        floors.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
        wallPanel.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    private void invalidateIfIron(List<Block> blocks) {
        blocks.stream().filter(block -> block.getType() == Material.IRON_BLOCK).forEach(floors::invalidate);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = pending.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        lastTeleport.remove(playerId);
        platforms.remove(playerId);
        showingHint.remove(playerId);
        wallPanel.clear(event.getPlayer());
    }

    private boolean request(Player player, Location origin, int direction, BooleanSupplier allowed) {
        return request(player, origin, direction, null, allowed);
    }

    private boolean request(Player player, Location origin, int direction, Integer targetY,
                            BooleanSupplier allowed) {
        UUID playerId = player.getUniqueId();
        Long previousTeleport = lastTeleport.get(playerId);
        if (!enabled || !canUse(player) || pending.containsKey(playerId)
                || (previousTeleport != null && System.nanoTime() - previousTeleport < COOLDOWN_NANOS)) {
            return false;
        }
        Block source = IronElevatorLanding.platformAt(origin);
        if (source == null || destination(player, source, direction, targetY, origin) == null) {
            return false;
        }
        IronElevatorPlatform sourcePlatform = floors.platform(source);
        if (sourcePlatform == null) return false;
        BukkitTask task = plugin.getServer().getScheduler().runTask(plugin, () -> {
            pending.remove(playerId);
            if (!enabled || !allowed.getAsBoolean() || !canUse(player)) {
                return;
            }
            Location departure = player.getLocation();
            Block current = IronElevatorLanding.platformAt(departure);
            if (current == null || !sourcePlatform.equals(floors.platform(current))) {
                return;
            }
            Location destination = destination(player, current, direction, targetY, departure);
            if (destination == null || !player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                return;
            }
            lastTeleport.put(playerId, System.nanoTime());
            player.setVelocity(new Vector());
            player.setFallDistance(0);
            particles(departure);
            particles(player.getLocation());
            updateHint(player, player.getLocation(), true);
        });
        pending.put(playerId, task);
        return true;
    }

    private static boolean canUse(Player player) {
        return player.isOnline() && !player.isDead() && !player.isInsideVehicle()
                && !player.isFlying() && !player.isGliding() && !player.isSwimming()
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private Location destination(Player player, Block source, int direction, Integer targetY,
                                        Location origin) {
        return floors.destination(source, direction, targetY, origin, size(player), player::wouldCollideUsing);
    }

    static IronElevatorLanding.Size size(Player player) {
        BoundingBox body = player.getBoundingBox();
        double standingHeight = Math.max(body.getHeight(), body.getWidthX() * 3);
        return new IronElevatorLanding.Size(body.getWidthX(), standingHeight, body.getWidthZ());
    }

    private static void particles(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 0.9, 0),
                40, 0.35, 0.65, 0.35, 0.1);
    }
}
