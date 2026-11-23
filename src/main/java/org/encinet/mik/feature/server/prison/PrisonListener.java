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
import org.bukkit.event.world.ChunkUnloadEvent;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

final class PrisonListener implements Listener {
    private final PrisonService service;
    private final LanguageService language;
    private final Consumer<Block> blocksChanged;
    private final Consumer<ChunkUnloadEvent> chunkUnloaded;
    private final Consumer<java.util.UUID> quit;

    PrisonListener(PrisonService service, LanguageService language, Consumer<Block> blocksChanged,
                   Consumer<ChunkUnloadEvent> chunkUnloaded, Consumer<java.util.UUID> quit) {
        this.service = service;
        this.language = language;
        this.blocksChanged = blocksChanged;
        this.chunkUnloaded = chunkUnloaded;
        this.quit = quit;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (service.isTeleporting(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) return;
        if (!service.canMoveTo(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(language.text(event.getPlayer(), Message.PRISON_MOVE_BLOCKED,
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (service.prisoner(event.getPlayer().getUniqueId()).isPresent()
                && !service.allowedCommand(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(language.text(event.getPlayer(), Message.PRISON_COMMAND_BLOCKED,
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (service.prisoner(event.getPlayer().getUniqueId()).isPresent()
                || !service.mayBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (service.prisoner(event.getPlayer().getUniqueId()).isPresent()
                || !service.mayBuild(event.getPlayer(), event.getBlock().getLocation())) event.setCancelled(true);
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
        service.prisoner(event.getPlayer().getUniqueId()).flatMap(service::location)
                .ifPresent(event::setRespawnLocation);
    }

    // World-change forwarding: invalidates cached wall-panel mounts near prison cells.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelPlace(BlockPlaceEvent event) { blocksChanged.accept(event.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelBreak(BlockBreakEvent event) { blocksChanged.accept(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) blocksChanged.accept(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) blocksChanged.accept(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Block> changed = new LinkedHashSet<>(event.getBlocks());
        event.getBlocks().forEach(block -> changed.add(block.getRelative(event.getDirection())));
        for (Block block : changed) blocksChanged.accept(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Block> changed = new LinkedHashSet<>(event.getBlocks());
        event.getBlocks().forEach(block -> changed.add(block.getRelative(event.getDirection())));
        for (Block block : changed) blocksChanged.accept(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPanelChunkUnload(ChunkUnloadEvent event) {
        if (chunkUnloaded != null) chunkUnloaded.accept(event);
    }

    @EventHandler
    public void onPanelQuit(PlayerQuitEvent event) {
        if (quit != null) quit.accept(event.getPlayer().getUniqueId());
    }
}
