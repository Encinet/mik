package org.encinet.mik.feature.server.ban.prison;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class PrisonListener implements Listener {
    private final PrisonService service;
    private final LanguageService language;
    private final PrisonPanelView panels;

    private final Consumer<Player> joined;
    private final Consumer<UUID> left;

    PrisonListener(PrisonService service, LanguageService language, PrisonPanelView panels,
                   Consumer<Player> joined, Consumer<UUID> left) {
        this.service = service;
        this.language = language;
        this.panels = panels;
        this.joined = joined;
        this.left = left;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) { joined.accept(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && event.hasExplicitlyChangedPosition()
                && !service.canMoveTo(event.getPlayer(), event.getTo())) blockMove(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!service.canTeleportTo(event.getPlayer(), event.getTo())) blockMove(event);
    }

    private void blockMove(PlayerMoveEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendActionBar(language.text(event.getPlayer(), Message.PRISON_MOVE_BLOCKED,
                NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!service.mayBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!service.mayBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!service.confined(event.getEntity().getUniqueId())) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location point = service.respawn(event.getPlayer());
        if (point != null) event.setRespawnLocation(point);
    }

    // World-change forwarding: invalidates cached wall-panel mounts near prison cells.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelPlace(BlockPlaceEvent event) { panels.invalidate(event.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelBreak(BlockBreakEvent event) { panels.invalidate(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) panels.invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) panels.invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Block> changed = new LinkedHashSet<>(event.getBlocks());
        event.getBlocks().forEach(block -> changed.add(block.getRelative(event.getDirection())));
        for (Block block : changed) panels.invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Block> changed = new LinkedHashSet<>(event.getBlocks());
        event.getBlocks().forEach(block -> changed.add(block.getRelative(event.getDirection())));
        for (Block block : changed) panels.invalidate(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelChunkUnload(ChunkUnloadEvent event) {
        panels.invalidateChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { left.accept(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        service.worldLoaded(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        service.worldUnloaded(event.getWorld().getUID());
        panels.invalidateWorld(event.getWorld().getUID());
    }
}
