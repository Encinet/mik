package org.encinet.mik.module.music.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.JukeboxInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.VanillaRecordSilencer;

/** Maintains playback state as jukebox blocks and records move through the world. */
public final class MusicJukeboxListener implements Listener {

    private final JavaPlugin plugin;
    private final MusicLibrary musicLibrary;
    private final JukeboxPlaybackService playbackService;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxQueueService queueService;
    private final JukeboxAutoPlayService autoPlayService;

    public MusicJukeboxListener(JavaPlugin plugin, MusicLibrary musicLibrary, JukeboxPlaybackService playbackService,
                                VanillaRecordSilencer recordSilencer,
                                JukeboxQueueService queueService,
                                JukeboxAutoPlayService autoPlayService) {
        this.plugin = plugin;
        this.musicLibrary = musicLibrary;
        this.playbackService = playbackService;
        this.recordSilencer = recordSilencer;
        this.queueService = queueService;
        this.autoPlayService = autoPlayService;
    }

    public void restorePlaybackInLoadedChunks() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                restorePlayback(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInternalDiscInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Jukebox jukebox)
                || !MusicDiscKeys.isInternal(jukebox.getRecord())) {
            return;
        }
        event.setCancelled(true);
        playbackService.stopAndClear(block);
        autoPlayService.cancelScheduledTask(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCustomJukeboxInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Jukebox jukebox)) {
            return;
        }

        ItemStack interactionItem = event.getItem();
        if (event.getPlayer().isSneaking()
                && (interactionItem == null || interactionItem.getType().isAir())) {
            return;
        }

        if (MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
            String trackId = MusicDiscKeys.trackId(jukebox.getRecord());
            JukeboxPlaybackService.PlaybackHandle playback =
                    playbackService.activePlayback(block);
            Bukkit.getScheduler().runTask(plugin,
                    () -> stopAfterConfirmedRemoval(block.getLocation(), trackId, playback));
            return;
        }
        if (!MusicDiscKeys.isCustomDisc(interactionItem)) {
            return;
        }

        // MIK discs intentionally have no JUKEBOX_PLAYABLE component, so insertion is owned here.
        if (jukebox.hasRecord()) {
            return;
        }

        event.setCancelled(true);
        recordSilencer.suppress(block);
        try {
            ItemStack inserted = interactionItem.asOne();
            jukebox.setRecord(inserted);
            jukebox.stopPlaying();
            jukebox.update(true, false);
            if (playbackService.playInsertedDisc(event.getPlayer(), jukebox)) {
                consumeInsertedDisc(event.getPlayer(), interactionItem);
            } else {
                jukebox.setRecord(new ItemStack(Material.AIR));
                jukebox.update(true, false);
            }
        } finally {
            recordSilencer.release(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == org.bukkit.Material.JUKEBOX) {
            removeJukebox(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxExplode(EntityExplodeEvent event) {
        event.blockList().stream()
                .filter(block -> block.getType() == org.bukkit.Material.JUKEBOX)
                .forEach(this::removeJukebox);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxExplode(BlockExplodeEvent event) {
        event.blockList().stream()
                .filter(block -> block.getType() == org.bukkit.Material.JUKEBOX)
                .forEach(this::removeJukebox);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxBurn(BlockBurnEvent event) {
        if (event.getBlock().getType() == org.bukkit.Material.JUKEBOX) {
            removeJukebox(event.getBlock());
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (org.bukkit.block.BlockState state : event.getChunk().getTileEntities(false)) {
            if (state instanceof Jukebox jukebox && MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
                stopJukebox(jukebox.getBlock());
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        restorePlayback(event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        playbackService.removeWorld(event.getWorld());
        recordSilencer.removeWorld(event.getWorld());
        autoPlayService.removeWorld(event.getWorld());
        queueService.removeWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource() instanceof JukeboxInventory source
                && MusicDiscKeys.isCustomDisc(event.getItem())) {
            Location sourceLocation = source.getHolder().getLocation();
            String movedTrackId = MusicDiscKeys.trackId(event.getItem());
            JukeboxPlaybackService.PlaybackHandle playback =
                    playbackService.activePlayback(sourceLocation.getBlock());
            Bukkit.getScheduler().runTask(plugin,
                    () -> stopAfterConfirmedRemoval(sourceLocation, movedTrackId, playback));
        }
        if (!(event.getDestination() instanceof JukeboxInventory destination)
                || !MusicDiscKeys.isCustomDisc(event.getItem())) {
            return;
        }

        Location location = destination.getHolder().getLocation();
        recordSilencer.suppress(location.getBlock());
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (location.getBlock().getState() instanceof Jukebox jukebox
                        && MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
                    jukebox.stopPlaying();
                    jukebox.update(true, false);
                    playbackService.playInsertedDisc(null, jukebox);
                }
            } finally {
                recordSilencer.release(location.getBlock());
            }
        });
    }

    private void stopAfterConfirmedRemoval(
            Location location, String movedTrackId,
            JukeboxPlaybackService.PlaybackHandle movedPlayback) {
        if (location.getWorld() == null || !location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Block block = location.getBlock();
        String currentTrackId = block.getState() instanceof Jukebox jukebox
                ? MusicDiscKeys.trackId(jukebox.getRecord()) : null;
        if (movedTrackId.equals(currentTrackId)) {
            return;
        }
        boolean stoppedMovedTrack = playbackService.stopIfCurrent(block, movedPlayback);
        if (currentTrackId == null || stoppedMovedTrack) {
            autoPlayService.cancelScheduledTask(block.getLocation());
        }
    }

    private static void consumeInsertedDisc(org.bukkit.entity.Player player, ItemStack inserted) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        int remaining = inserted.getAmount() - 1;
        player.getInventory().setItemInMainHand(remaining <= 0
                ? new ItemStack(Material.AIR) : inserted.asQuantity(remaining));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInternalDiscMove(InventoryMoveItemEvent event) {
        if (MusicDiscKeys.isInternal(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInternalDiscSpawn(ItemSpawnEvent event) {
        if (MusicDiscKeys.isInternal(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recordSilencer.updatePlayerWorld(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        recordSilencer.updatePlayerWorld(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        recordSilencer.removePlayer(event.getPlayer().getUniqueId());
    }

    private void restorePlayback(org.bukkit.Chunk chunk) {
        if (!musicLibrary.isLoaded()) {
            return;
        }
        for (org.bukkit.block.BlockState state : chunk.getTileEntities(false)) {
            if (state instanceof Jukebox jukebox && MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
                jukebox.stopPlaying();
                jukebox.update(true, false);
                if (playbackService.shouldRestore(jukebox.getBlock(), jukebox.getRecord())) {
                    playbackService.playInsertedDisc(null, jukebox);
                }
            }
        }
    }

    private void stopJukebox(Block block) {
        playbackService.stop(block);
        autoPlayService.cancelScheduledTask(block.getLocation());
    }

    private void removeJukebox(Block block) {
        if (block.getState() instanceof Jukebox jukebox
                && MusicDiscKeys.isInternal(jukebox.getRecord())) {
            playbackService.stopAndClear(block);
            autoPlayService.cancelScheduledTask(block.getLocation());
        } else {
            stopJukebox(block);
        }
        queueService.removeState(block.getLocation());
    }
}
