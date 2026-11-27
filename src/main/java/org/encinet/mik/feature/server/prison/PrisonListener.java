package org.encinet.mik.feature.server.prison;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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

final class PrisonListener implements Listener {
    private final PrisonService service;
    private final LanguageService language;
    private final PrisonPanelView panels;

    PrisonListener(PrisonService service, LanguageService language, PrisonPanelView panels) {
        this.service = service;
        this.language = language;
        this.panels = panels;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        service.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) return;
        restrictMovement(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        restrictMovement(event);
    }

    private void restrictMovement(PlayerMoveEvent event) {
        if (!service.canMoveTo(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(language.text(event.getPlayer(), Message.PRISON_MOVE_BLOCKED,
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (service.prisoner(event.getPlayer().getUniqueId()).isPresent()
                && !PrisonService.allowedCommand(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(language.text(event.getPlayer(), Message.PRISON_COMMAND_BLOCKED,
                    NamedTextColor.RED));
        }
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
        if (service.prisoner(event.getEntity().getUniqueId()).isEmpty()) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        service.prisoner(event.getPlayer().getUniqueId()).map(service::entrance)
                .ifPresent(event::setRespawnLocation);
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
    public void onPanelQuit(PlayerQuitEvent event) {
        panels.clear(event.getPlayer().getUniqueId());
    }

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
