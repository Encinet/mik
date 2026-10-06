package org.encinet.mik.module.music.listener;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
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
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.module.music.ui.JukeboxControlGui;

/** Maintains playback state as jukebox blocks and records move through the world. */
public final class MusicJukeboxListener implements Listener {

    private final JavaPlugin plugin;
    private final MusicLibrary musicLibrary;
    private final JukeboxPlaybackService playbackService;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxQueueService queueService;
    private final JukeboxAutoPlayService autoPlayService;
    private final JukeboxControlGui controlGui;

    public MusicJukeboxListener(JavaPlugin plugin, MusicLibrary musicLibrary, JukeboxPlaybackService playbackService,
                                VanillaRecordSilencer recordSilencer,
                                JukeboxQueueService queueService,
                                JukeboxAutoPlayService autoPlayService,
                                JukeboxControlGui controlGui) {
        this.plugin = plugin;
        this.musicLibrary = musicLibrary;
        this.playbackService = playbackService;
        this.recordSilencer = recordSilencer;
        this.queueService = queueService;
        this.autoPlayService = autoPlayService;
        this.controlGui = controlGui;
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
        if (block == null || block.getType() != Material.JUKEBOX
                || !(block.getState() instanceof Jukebox jukebox)
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
        if (block == null || block.getType() != Material.JUKEBOX
                || !(block.getState() instanceof Jukebox jukebox)) {
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
                playbackService.stopAndClear(block);
            }
        } finally {
            recordSilencer.release(block);
        }
    }

    /** Vanilla insertion and ejection mutate the block after the interaction event returns. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaJukeboxInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.JUKEBOX) return;
        Location location = block.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> controlGui.refreshViewers(location));
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
            if (state instanceof Jukebox jukebox) {
                controlGui.closeViewers(jukebox.getLocation());
                if (MusicDiscKeys.isCustomDisc(jukebox.getRecord())) {
                    stopJukebox(jukebox.getBlock());
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        restorePlayback(event.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        controlGui.closeWorld(event.getWorld());
        playbackService.removeWorld(event.getWorld());
        recordSilencer.removeWorld(event.getWorld());
        autoPlayService.removeWorld(event.getWorld());
        queueService.removeWorld(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukeboxInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource() instanceof JukeboxInventory source
                && MusicDiscKeys.isCustomDisc(event.getItem())) {
            Location sourceLocation = source.getLocation();
            String movedTrackId = MusicDiscKeys.trackId(event.getItem());
            JukeboxPlaybackService.PlaybackHandle playback =
                    playbackService.activePlayback(sourceLocation.getBlock());
            Bukkit.getScheduler().runTask(plugin,
                    () -> stopAfterConfirmedRemoval(sourceLocation, movedTrackId, playback));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedJukeboxMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination() instanceof JukeboxInventory destination)
                || !MusicDiscKeys.isCustomDisc(event.getItem())
                || MusicDiscKeys.isInternal(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        scheduleAutomatedInsertion(
                event.getSource(), destination.getLocation(), event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutomatedJukeboxDispense(BlockDispenseEvent event) {
        boolean customDisc = MusicDiscKeys.isCustomDisc(event.getItem());
        boolean vanillaRecord = event.getItem().hasData(DataComponentTypes.JUKEBOX_PLAYABLE);
        if ((!customDisc && !vanillaRecord)
                || MusicDiscKeys.isInternal(event.getItem())
                || !(event.getBlock().getState(false) instanceof Container source)
                || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        Block target = event.getBlock().getRelative(directional.getFacing());
        if (target.getType() != Material.JUKEBOX
                || !(target.getState() instanceof Jukebox jukebox) || jukebox.hasRecord()) {
            return;
        }

        event.setCancelled(true);
        scheduleAutomatedInsertion(
                source.getInventory(), target.getLocation(), event.getItem(), customDisc);
    }

    private void stopAfterConfirmedRemoval(
            Location location, String movedTrackId,
            JukeboxPlaybackService.PlaybackHandle movedPlayback) {
        if (location.getWorld() == null || !location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Block block = location.getBlock();
        String currentTrackId = block.getType() == Material.JUKEBOX
                && block.getState() instanceof Jukebox jukebox
                ? MusicDiscKeys.trackId(jukebox.getRecord()) : null;
        if (movedTrackId.equals(currentTrackId)) {
            return;
        }
        boolean stoppedMovedTrack = playbackService.stopIfCurrent(block, movedPlayback);
        if (currentTrackId == null || stoppedMovedTrack) {
            autoPlayService.cancelScheduledTask(block.getLocation());
        }
        controlGui.refreshViewers(location);
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
    public void onInternalDiscDispense(BlockDispenseEvent event) {
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
        controlGui.closeViewers(block.getLocation());
        if (block.getState() instanceof Jukebox jukebox
                && MusicDiscKeys.isInternal(jukebox.getRecord())) {
            playbackService.stopAndClear(block);
            autoPlayService.cancelScheduledTask(block.getLocation());
        } else {
            stopJukebox(block);
        }
        queueService.removeState(block.getLocation());
    }

    private void scheduleAutomatedInsertion(
            Inventory source, Location jukeboxLocation, ItemStack requestedDisc) {
        scheduleAutomatedInsertion(source, jukeboxLocation, requestedDisc, true);
    }

    private void scheduleAutomatedInsertion(
            Inventory source, Location jukeboxLocation, ItemStack requestedDisc,
            boolean customDisc) {
        ItemStack disc = requestedDisc.asOne();
        Location location = jukeboxLocation.clone();
        BlockInventoryHolder sourceHolder = source.getHolder(false) instanceof BlockInventoryHolder holder
                ? holder : null;
        Location sourceLocation = sourceHolder == null ? null : source.getLocation();
        Material sourceType = sourceHolder == null ? null : sourceHolder.getBlock().getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (location.getWorld() == null || !location.getWorld().isChunkLoaded(
                    location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return;
            }
            Block block = location.getBlock();
            if (block.getType() != Material.JUKEBOX
                    || !(block.getState() instanceof Jukebox jukebox) || jukebox.hasRecord()) {
                return;
            }
            Inventory currentSource = currentSourceInventory(source, sourceLocation, sourceType);
            if (currentSource == null) {
                return;
            }
            int sourceSlot = findSimilarItem(currentSource, disc);
            if (sourceSlot < 0) {
                return;
            }
            insertAutomatedDisc(jukebox, currentSource, sourceSlot, disc, customDisc);
        });
    }

    private static Inventory currentSourceInventory(
            Inventory original, Location sourceLocation, Material sourceType) {
        if (sourceLocation == null) {
            return original;
        }
        if (!sourceLocation.isChunkLoaded()) {
            return null;
        }
        return sourceLocation.getBlock().getType() == sourceType
                && sourceLocation.getBlock().getState(false) instanceof BlockInventoryHolder holder
                && holder.getInventory().getType() == original.getType()
                ? holder.getInventory() : null;
    }

    private void insertAutomatedDisc(
            Jukebox jukebox, Inventory source, int sourceSlot, ItemStack disc,
            boolean customDisc) {
        Block block = jukebox.getBlock();
        if (customDisc) {
            recordSilencer.suppress(block);
        }
        try {
            jukebox.setRecord(disc.asOne());
            if (customDisc) {
                jukebox.stopPlaying();
                jukebox.update(true, false);
                if (!playbackService.playInsertedDisc(null, jukebox)) {
                    playbackService.stopAndClear(block);
                    return;
                }
            }
            if (!consumeOne(source, sourceSlot, disc)) {
                if (customDisc) {
                    playbackService.stopAndClear(block);
                } else {
                    clearRecord(jukebox);
                }
                return;
            }
            if (source instanceof JukeboxInventory sourceJukebox) {
                stopJukebox(sourceJukebox.getLocation().getBlock());
            }
        } catch (RuntimeException exception) {
            if (customDisc) {
                playbackService.stopAndClear(block);
            } else {
                clearRecord(jukebox);
            }
            plugin.getLogger().warning("Failed to load an automated MIK disc at "
                    + formatLocation(jukebox.getLocation()) + ": " + exception.getMessage());
        } finally {
            if (customDisc) {
                recordSilencer.release(block);
            }
            controlGui.refreshViewers(jukebox.getLocation());
        }
    }

    private static void clearRecord(Jukebox jukebox) {
        jukebox.stopPlaying();
        jukebox.setRecord(new ItemStack(Material.AIR));
        jukebox.update(true, false);
    }

    private static int findSimilarItem(Inventory inventory, ItemStack expected) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack current = contents[slot];
            if (current != null && current.getAmount() > 0 && current.isSimilar(expected)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean consumeOne(Inventory inventory, int slot, ItemStack expected) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getAmount() < 1 || !current.isSimilar(expected)) {
            return false;
        }
        inventory.setItem(slot, current.getAmount() == 1
                ? new ItemStack(Material.AIR) : current.asQuantity(current.getAmount() - 1));
        return true;
    }

    private static String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ();
    }
}
