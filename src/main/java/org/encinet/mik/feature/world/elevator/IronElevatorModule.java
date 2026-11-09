package org.encinet.mik.feature.world.elevator;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Input;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import com.destroystokyo.paper.event.entity.EntityJumpEvent;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.WorldMenuScope;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class IronElevatorModule implements Listener {

    private final JavaPlugin plugin;
    private final IronElevatorCatalog catalog = new IronElevatorCatalog();
    private final IronElevatorView view;
    private final IronElevatorController controller;
    private final Map<UUID, Input> lastInputs = new HashMap<>();
    private BukkitTask workTask;
    private volatile boolean enabled;
    private volatile long generation;
    private volatile BukkitTask loadingTask;
    private volatile BukkitTask activationTask;

    public IronElevatorModule(JavaPlugin plugin, LanguageService languageService,
                              WorldMenuScope panels, TipScope tips) {
        this.plugin = plugin;
        this.view = new IronElevatorView(panels,languageService,new IronElevatorView.Actions() {
            @Override public void select(Player player, Object visit, int height) { controller.selectFloor(player,visit,height); }
            @Override public void visible(Player player, Object visit, IronElevatorCatalog.Result catalog,
                                          org.encinet.mik.shared.menu.FloatingMenuPage page) { controller.visibleFloors(player,visit,catalog,page); }
            @Override public void pause(Player player, Object visit) { controller.pausePanel(player,visit); }
        });
        this.controller = new IronElevatorController(catalog,view,id -> plugin.getServer().getPlayer(id),plugin.getLogger()::warning);
        tips.define("iron-elevator", Message.IRON_ELEVATOR_TIP_MM).ambient().build();
    }

    public void enable() {
        if (enabled) return;
        enabled = true;
        long expected = ++generation;
        loadingTask = plugin.getServer().getScheduler().runTaskAsynchronously(plugin,() -> {
            try {
                Path config = plugin.getDataFolder().toPath().resolve("iron-elevators.yml");
                if (!Files.exists(config)) {
                    Files.createDirectories(config.getParent());
                    try (var resource = plugin.getResource("iron-elevators.yml")) {
                        if (resource == null) throw new IllegalStateException("Missing iron-elevators.yml resource");
                        Files.copy(resource,config);
                    }
                }
                var rules = IronElevatorCatalog.readRules(config);
                if (!enabled || generation != expected) return;
                activationTask = plugin.getServer().getScheduler().runTask(plugin,() -> {
                    if (!enabled || generation != expected) return;
                    catalog.installRules(rules);
                    controller.enable();
                    plugin.getServer().getPluginManager().registerEvents(this,plugin);
                    workTask = plugin.getServer().getScheduler().runTaskTimer(plugin,controller::tick,1,1);
                });
                if (!enabled || generation != expected) activationTask.cancel();
            } catch (Exception error) {
                if (enabled && generation == expected) {
                    enabled = false;
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,"Iron elevator configuration failed; feature remains disabled",error);
                }
            }
        });
    }

    public void disable() {
        enabled = false;
        generation++;
        if (loadingTask != null) { loadingTask.cancel(); loadingTask = null; }
        if (activationTask != null) { activationTask.cancel(); activationTask = null; }
        HandlerList.unregisterAll(this);
        if (workTask != null) {
            workTask.cancel();
            workTask = null;
        }
        try { controller.clear(); }
        finally { lastInputs.clear(); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location destination = event.getTo();
        if (destination == null || (event.getFrom().getX() == destination.getX()
                && event.getFrom().getY() == destination.getY()
                && event.getFrom().getZ() == destination.getZ())) {
            return;
        }
        controller.observe(event.getPlayer(), destination, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            controller.observe(event.getPlayer(), event.getTo(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (controller.requestJump(event.getPlayer(), event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            controller.request(event.getPlayer(), event.getPlayer().getLocation(), IronElevatorController.Direction.DOWN, () -> !event.isCancelled());
        }
    }

    @EventHandler
    public void onMountedInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (!player.isInsideVehicle()) {
            lastInputs.remove(player.getUniqueId());
            return;
        }
        Input previous = lastInputs.put(player.getUniqueId(), event.getInput());
        Input input = event.getInput();
        if (input.isSneak() && (previous == null || !previous.isSneak())) {
            controller.request(player, player.getLocation(), IronElevatorController.Direction.DOWN, () -> true);
        } else if (input.isJump() && (previous == null || !previous.isJump())) {
            controller.request(player, player.getLocation(), IronElevatorController.Direction.UP, () -> true);
        }
        controller.observe(player, player.getLocation(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMountedDismount(EntityDismountEvent event) {
        if (event.isCancellable() && event.getEntity() instanceof Player player) {
            Input input = lastInputs.get(player.getUniqueId());
            if (controller.pending(player.getUniqueId()) && input != null && input.isSneak()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMountJump(EntityJumpEvent event) {
        if (hasPendingRider(event.getEntity())) event.setCancelled(true);
    }

    private boolean hasPendingRider(Entity vehicle) {
        java.util.ArrayDeque<Entity> remaining = new java.util.ArrayDeque<>(vehicle.getPassengers());
        Set<UUID> visited = new HashSet<>();
        while (!remaining.isEmpty() && visited.size() < 64) {
            Entity rider = remaining.removeFirst();
            if (!visited.add(rider.getUniqueId())) continue;
            if (rider instanceof Player && controller.pending(rider.getUniqueId())) return true;
            remaining.addAll(rider.getPassengers());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        controller.invalidate(block);
        if (block.getType() != Material.IRON_BLOCK) return;
        controller.placed(block, () -> block.getWorld().playSound(block.getLocation().add(0.5, 1, 0.5),
                Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 1.5F));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        invalidateBlocks(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        invalidateBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        invalidateBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        invalidatePiston(event.getBlocks(),event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        invalidatePiston(event.getBlocks(),event.getDirection());
    }

    private void invalidatePiston(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        var changed = new java.util.LinkedHashSet<Block>(blocks);
        blocks.forEach(block -> changed.add(block.getRelative(direction)));
        invalidateBlocks(List.copyOf(changed));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        controller.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        controller.invalidate(event.getWorld().getUID());
    }

    private void invalidateBlocks(List<Block> blocks) {
        blocks.stream().distinct().forEach(controller::invalidate);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastInputs.remove(playerId);
        controller.quit(playerId);
    }

}
