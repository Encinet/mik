package org.encinet.mik.module;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Module for handling music chest interaction
 */
public class MusicChestModule implements Listener {

    private final JavaPlugin plugin;
    private final MusicDiscModule musicDiscModule;
    private Set<Location> musicChestLocations;

    public MusicChestModule(JavaPlugin plugin, MusicDiscModule musicDiscModule) {
        this.plugin = plugin;
        this.musicDiscModule = musicDiscModule;
    }

    /**
     * Enable the music chest module
     */
    public void enable() {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null) {
            plugin.getLogger().severe("World 'world' not found!");
            return;
        }

        // Music chest locations
        Location musicChest1 = new Location(mainWorld, 49, 55, 120).getBlock().getLocation();
        Location musicChest2 = new Location(mainWorld, 49, 55, 121).getBlock().getLocation();

        musicChestLocations = Set.of(musicChest1, musicChest2);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getLogger().info("MusicChestModule enabled with " + musicChestLocations.size() + " chests");
    }

    /**
     * Handle player interact event
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Check if clicked block is one of the music chests
        Location clickedLocation = clickedBlock.getLocation();
        if (!musicChestLocations.contains(clickedLocation)) return;

        // Cancel the event to prevent opening the chest
        event.setCancelled(true);

        // Open music GUI
        Player player = event.getPlayer();
        musicDiscModule.openMusicInventory(player);
    }
}
