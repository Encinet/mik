package org.encinet.mik.feature.entertainment.music.listener;

import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackController;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscKeys;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackOrigin;
import org.encinet.mik.feature.entertainment.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace;

/** Physical world events never masquerade as a nearby player's request. */
public final class MusicDeviceListener implements Listener {
    private final org.encinet.mik.feature.entertainment.music.application.MusicRooms rooms;
    private final JavaPlugin plugin;
    private final MusicPlaybackController devices;
    private final JukeboxPlaybackService backend;
    private final MusicWorkspace workspace;
    private final VanillaRecordSilencer silencer;
    private final BiPredicate<Player, Location> permission;
    public MusicDeviceListener(JavaPlugin plugin, MusicPlaybackController devices,
            JukeboxPlaybackService backend, MusicWorkspace workspace, VanillaRecordSilencer silencer,
            BiPredicate<Player, Location> permission,
            org.encinet.mik.feature.entertainment.music.application.MusicRooms rooms) {
        this.rooms = rooms;
        this.plugin = plugin;
        this.devices = devices;
        this.backend = backend;
        this.workspace = workspace;
        this.silencer = silencer;
        this.permission = permission;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()
                || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Jukebox box))
            return;
        Player player = event.getPlayer();
        if (!permission.test(player, box.getLocation())) {
            event.setCancelled(true);
            return;
        }
        ItemStack item = event.getItem();
        boolean empty = item == null || item.getType().isAir();
        if (player.isSneaking() && empty) {
            event.setCancelled(true);
            workspace.at(player, box);
            return;
        }
        if (backend.activeExperienceMode(box.getBlock()).orElse(JukeboxExperienceMode.MUSIC)
                == JukeboxExperienceMode.RHYTHM) {
            event.setCancelled(true);
            return;
        }
        boolean managed = MusicDiscKeys.isCustomDisc(item) || devices.grouped(box) && item != null
                && item.hasData(io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE);
        if (managed && !box.hasRecord()) {
            event.setCancelled(true);
            silencer.suppress(box.getBlock());
            boolean accepted = false;
            try {
                box.setRecord(item.asOne());
                box.stopPlaying();
                box.update(true, false);
                if (devices.playInsertedDisc(player, box, PlaybackOrigin.PLAYER)) {
                    accepted = true;
                    if (player.getGameMode() != GameMode.CREATIVE)
                        player.getInventory().setItemInMainHand(item.getAmount() == 1
                                        ? new ItemStack(Material.AIR)
                                        : item.asQuantity(item.getAmount() - 1));
                }
            } catch (Exception error) {
                workspace.error(player,error);
            } finally {
                if (!accepted) {
                    box.setRecord(new ItemStack(Material.AIR));
                    box.update(true, false);
                    devices.recordRemoved(box);
                }
                silencer.release(box.getBlock());
            }
            return;
        }
        try {
            if (devices.ejectInsertedDisc(player,box)) { event.setCancelled(true); return; }
        } catch (Exception error) {
            event.setCancelled(true); workspace.error(player,error); return;
        }
        if (MusicDiscKeys.isCustomDisc(box.getRecord())) {
            event.setCancelled(true);
            backend.stopAndEject(box.getBlock());
            return;
        }
        // Vanilla owns its inventory transaction; suspend only when a playable item can be inserted.
        if (!box.hasRecord() && item != null
                && item.hasData(io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE))
            devices.unavailable(box.getLocation(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        remove(e.getBlock(), true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void burn(BlockBurnEvent e) {
        remove(e.getBlock(), true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(BlockExplodeEvent e) {
        e.blockList().forEach(b -> remove(b, true));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        e.blockList().forEach(b -> remove(b, true));
    }
    @EventHandler
    public void load(ChunkLoadEvent e) {
        for (BlockState b : e.getChunk().getTileEntities(false))
            if (b instanceof Jukebox box)
                devices.loaded(box);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent e) {
        for (BlockState b : e.getChunk().getTileEntities(false))
            if (b instanceof Jukebox box)
                remove(box.getBlock(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void world(WorldUnloadEvent e) {
        for (Chunk c : e.getWorld().getLoadedChunks())
            for (BlockState b : c.getTileEntities(false))
                if (b instanceof Jukebox box)
                    remove(box.getBlock(), false);
    }
    private void remove(Block b, boolean destroyed) {
        if (b.getType() == Material.JUKEBOX) {
            rooms.unavailable(b.getLocation());
            devices.unavailable(b.getLocation(), destroyed);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(InventoryMoveItemEvent e) {
        Location destination = e.getDestination().getLocation();
        if (destination != null && destination.isChunkLoaded()
                && destination.getBlock().getState() instanceof Jukebox box) {
            if (backend.activeExperienceMode(box.getBlock()).orElse(JukeboxExperienceMode.MUSIC)
                    == JukeboxExperienceMode.RHYTHM) {
                e.setCancelled(true);
                return;
            }
            boolean managed = MusicDiscKeys.isCustomDisc(e.getItem()) || devices.grouped(box)
                    && e.getItem().hasData(io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE);
            if (managed) {
                e.setCancelled(true);
                if (!box.hasRecord())
                    automated(e.getSource(), box, e.getItem(), true);
                return;
            }
            if (!box.hasRecord()
                    && e.getItem().hasData(
                            io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE))
                devices.unavailable(destination, false);
        }
        Location source = e.getSource().getLocation();
        if (source != null && source.isChunkLoaded() && source.getBlock().getType() == Material.JUKEBOX)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (source.isChunkLoaded() && source.getBlock().getState() instanceof Jukebox box
                        && !box.hasRecord())
                    devices.recordRemoved(box);
            });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dispense(BlockDispenseEvent event) {
        if (!(event.getBlock().getState(false) instanceof org.bukkit.block.Container source)
                || !(event.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional facing))
            return;
        var block = event.getBlock().getRelative(facing.getFacing());
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                || !(block.getState() instanceof Jukebox box))
            return;
        boolean custom = MusicDiscKeys.isCustomDisc(event.getItem());
        if (!custom
                && !event.getItem().hasData(
                        io.papermc.paper.datacomponent.DataComponentTypes.JUKEBOX_PLAYABLE))
            return;
        if (backend.activeExperienceMode(block).orElse(JukeboxExperienceMode.MUSIC)
                == JukeboxExperienceMode.RHYTHM) {
            event.setCancelled(true);
            return;
        }
        if (box.hasRecord())
            return;
        event.setCancelled(true);
        automated(source.getInventory(), box, event.getItem(), custom || devices.grouped(box));
    }
    private void automated(
            org.bukkit.inventory.Inventory source, Jukebox box, ItemStack requested, boolean custom) {
        int slot = -1;
        for (int i = 0; i < source.getSize(); i++) {
            ItemStack item = source.getItem(i);
            if (item != null && item.getAmount() > 0 && item.isSimilar(requested)) {
                slot = i;
                break;
            }
        }
        if (slot < 0 || box.hasRecord())
            return;
        ItemStack original = source.getItem(slot).clone();
        if (!custom) devices.unavailable(box.getLocation(), false);
        if (custom)
            silencer.suppress(box.getBlock());
        boolean committed = false;
        try {
            box.setRecord(requested.asOne());
            if (custom) {
                box.stopPlaying();
                box.update(true, false);
                if (!devices.playInsertedDisc(null, box, PlaybackOrigin.AUTOMATIC))
                    return;
            }
            ItemStack remaining = source.getItem(slot);
            if (remaining == null || !remaining.isSimilar(original)
                    || remaining.getAmount() != original.getAmount())
                return;
            source.setItem(
                    slot, original.getAmount() == 1 ? null : original.asQuantity(original.getAmount() - 1));
            committed = true;
            if (source instanceof org.bukkit.inventory.JukeboxInventory jukebox
                    && jukebox.getLocation().getBlock().getState() instanceof Jukebox sourceBox && !sourceBox.hasRecord())
                devices.recordRemoved(sourceBox);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Music record insertion failed: " + error.getMessage());
        } finally {
            if (!committed) {
                box.setRecord(new ItemStack(Material.AIR));
                box.update(true, false);
                devices.recordRemoved(box);
            }
            if (custom)
                silencer.release(box.getBlock());
        }
    }
    @EventHandler
    public void join(PlayerJoinEvent e) {
        silencer.updatePlayerWorld(e.getPlayer());
    }
    @EventHandler
    public void changed(PlayerChangedWorldEvent e) {
        silencer.updatePlayerWorld(e.getPlayer());
    }
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        rooms.leave(e.getPlayer());
        workspace.quit(e.getPlayer().getUniqueId());
        silencer.removePlayer(e.getPlayer().getUniqueId());
    }
}
