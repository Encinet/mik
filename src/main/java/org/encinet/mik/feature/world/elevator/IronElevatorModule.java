package org.encinet.mik.feature.world.elevator;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Input;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import com.destroystokyo.paper.event.entity.EntityJumpEvent;
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
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.runtime.WorldTextDisplayService;

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
    private final IronElevatorFloors floors = new IronElevatorFloors();
    private final IronElevatorWallPanel wallPanel;
    private final IronElevatorPanelInteraction panelInteraction;
    private final IronElevatorController controller;
    private final Map<UUID, Input> lastInputs = new HashMap<>();
    private final Set<UUID> showingHint = new HashSet<>();
    private BukkitTask workTask;
    private boolean enabled;

    public IronElevatorModule(JavaPlugin plugin, LanguageService languageService,
                              WorldTextDisplayService labels) {
        this.plugin = plugin;
        this.wallPanel = new IronElevatorWallPanel(labels,
                (player, height) -> selectFloor(player, height));
        this.panelInteraction = new IronElevatorPanelInteraction(wallPanel::click);
        this.controller = new IronElevatorController(floors, new IronElevatorController.Presentation() {
            @Override
            public void hint(Player player, boolean up, boolean down) {
                player.sendActionBar(languageService.text(player, Message.IRON_ELEVATOR_UP,
                        up ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                        .append(Component.text("   "))
                        .append(languageService.text(player, Message.IRON_ELEVATOR_DOWN,
                                down ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
                showingHint.add(player.getUniqueId());
            }

            @Override
            public void clear(Player player) {
                wallPanel.clear(player);
                if (showingHint.remove(player.getUniqueId())) player.sendActionBar(Component.empty());
            }

            @Override
            public void forget(UUID player) {
                wallPanel.forget(player);
                showingHint.remove(player);
            }

            @Override
            public void panel(Player player, IronElevatorPlatform source, IronElevatorTravelGroup group,
                              List<IronElevatorFloors.Floor> levels) {
                wallPanel.update(player, source, group, levels);
            }

            @Override
            public void travelled(Player player, Location departure) {
                particles(departure);
                particles(player.getLocation());
            }
        }, task -> plugin.getServer().getScheduler().runTask(plugin, task),
                id -> plugin.getServer().getPlayer(id), plugin.getLogger()::warning);
    }

    private void selectFloor(Player player, int height) {
        controller.request(player, player.getLocation(), 0, height, () -> true);
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
        controller.enable();
        workTask = plugin.getServer().getScheduler().runTaskTimer(plugin, controller::tick, 1, 1);
    }

    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(panelInteraction);
        if (workTask != null) {
            workTask.cancel();
            workTask = null;
        }
        controller.clear();
        lastInputs.clear();
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
        if (controller.request(event.getPlayer(), event.getFrom(), 1, null, () -> true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            controller.request(event.getPlayer(), event.getPlayer().getLocation(), -1, null, () -> !event.isCancelled());
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
            controller.request(player, player.getLocation(), -1, null, () -> true);
        } else if (input.isJump() && (previous == null || !previous.isJump())) {
            controller.request(player, player.getLocation(), 1, null, () -> true);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseJump(HorseJumpEvent event) {
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
        wallPanel.invalidate(block);
        controller.invalidate(block);
        if (block.getType() != Material.IRON_BLOCK) return;
        floors.invalidate(block);
        controller.placed(block, () -> block.getWorld().playSound(block.getLocation().add(0.5, 1, 0.5),
                Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8F, 1.5F));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        wallPanel.invalidate(event.getBlock());
        invalidateBlocks(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().forEach(wallPanel::invalidate);
        invalidateBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().forEach(wallPanel::invalidate);
        invalidateBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> {
            wallPanel.invalidate(block);
            wallPanel.invalidate(block.getRelative(event.getDirection()));
        });
        invalidateBlocks(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> {
            wallPanel.invalidate(block);
            wallPanel.invalidate(block.getRelative(event.getDirection()));
        });
        invalidateBlocks(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        floors.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
        wallPanel.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
        controller.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    private void invalidateBlocks(List<Block> blocks) {
        blocks.stream().filter(block -> block.getType() == Material.IRON_BLOCK).forEach(floors::invalidate);
        blocks.forEach(controller::invalidate);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastInputs.remove(playerId);
        controller.quit(playerId);
    }

    private static void particles(Location location) {
        location.getWorld().spawnParticle(Particle.PORTAL, location.clone().add(0, 0.9, 0),
                40, 0.35, 0.65, 0.35, 0.1);
    }
}
