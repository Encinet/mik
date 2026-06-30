package org.encinet.mik.module.presentation;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnBeaconColorModule {

    private static final int TICKS_PER_SECOND = 20;
    private static final String BEACON_WORLD_NAME = "world";
    private static final int BEACON_X = 33;
    private static final int BEACON_Y = 73;
    private static final int BEACON_Z = 82;
    private static final int BEACON_CHUNK_X = BEACON_X >> 4;
    private static final int BEACON_CHUNK_Z = BEACON_Z >> 4;
    private static final String COMMAND_NAME = "spawnbeaconcolor";
    private static final long INITIAL_DELAY_TICKS = TICKS_PER_SECOND * 30L;
    private static final long UPDATE_INTERVAL_TICKS = TICKS_PER_SECOND * 30L;
    private static final Material[] BEACON_GLASS_COLORS = {
            Material.WHITE_STAINED_GLASS,
            Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS
    };
    private static final BlockData[] BEACON_GLASS_BLOCK_DATA = createBeaconGlassBlockData();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private BukkitTask task;
    private boolean cyclingEnabled = true;

    public SpawnBeaconColorModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        startTask();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal(COMMAND_NAME)
                .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                .executes(ctx -> {
                    cyclingEnabled = !cyclingEnabled;
                    if (cyclingEnabled) {
                        startTask();
                        tick();
                    } else {
                        resetClientBlock();
                        stopTask();
                    }
                    CommandSender sender = ctx.getSource().getSender();
                    Message message = cyclingEnabled ? Message.SPAWN_BEACON_COLOR_ENABLED : Message.SPAWN_BEACON_COLOR_DISABLED;
                    NamedTextColor color = cyclingEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY;
                    if (sender instanceof Player player) {
                        sender.sendMessage(languageService.text(player, message, color));
                    } else {
                        sender.sendMessage(languageService.text(Language.DEFAULT, message, color));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build(), languageService.t(Language.DEFAULT, Message.SPAWN_BEACON_COLOR_COMMAND_DESCRIPTION)));
    }

    public void disable() {
        resetClientBlock();
        stopTask();
    }

    private void tick() {
        if (!cyclingEnabled) {
            return;
        }

        World world = Bukkit.getWorld(BEACON_WORLD_NAME);
        if (world == null || !world.isChunkLoaded(BEACON_CHUNK_X, BEACON_CHUNK_Z)) {
            return;
        }
        List<Player> viewers = world.getPlayers();
        if (viewers.isEmpty()) {
            return;
        }

        Block block = world.getBlockAt(BEACON_X, BEACON_Y, BEACON_Z);
        Material current = block.getType();
        int currentIndex = beaconGlassIndex(current);
        if (currentIndex < 0) {
            pauseCycling(world, current);
            return;
        }

        sendRandomFakeBlocks(viewers, world, currentIndex);
    }

    private void resetClientBlock() {
        World world = Bukkit.getWorld(BEACON_WORLD_NAME);
        if (world == null || !world.isChunkLoaded(BEACON_CHUNK_X, BEACON_CHUNK_Z)) {
            return;
        }
        Block block = world.getBlockAt(BEACON_X, BEACON_Y, BEACON_Z);
        resetClientBlock(world, block.getType());
    }

    private void resetClientBlock(World world, Material realMaterial) {
        BlockData realBlockData = realMaterial.createBlockData();
        Location location = beaconLocation(world);
        for (Player player : world.getPlayers()) {
            player.sendBlockChange(location, realBlockData);
        }
    }

    private void sendRandomFakeBlocks(List<Player> viewers, World world, int realColorIndex) {
        Location location = beaconLocation(world);
        int viewDistance = plugin.getServer().getViewDistance();
        for (Player player : viewers) {
            if (isBeaconChunkVisibleTo(player, viewDistance)) {
                player.sendBlockChange(location, randomBeaconGlassBlockData(realColorIndex));
            }
        }
    }

    private void pauseCycling(World world, Material realMaterial) {
        cyclingEnabled = false;
        resetClientBlock(world, realMaterial);
        stopTask();
    }

    private void startTask() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean isBeaconChunkVisibleTo(Player player, int viewDistance) {
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        return Math.abs(playerChunkX - BEACON_CHUNK_X) <= viewDistance
                && Math.abs(playerChunkZ - BEACON_CHUNK_Z) <= viewDistance;
    }

    private Location beaconLocation(World world) {
        return new Location(world, BEACON_X, BEACON_Y, BEACON_Z);
    }

    private int beaconGlassIndex(Material material) {
        for (int i = 0; i < BEACON_GLASS_COLORS.length; i++) {
            if (BEACON_GLASS_COLORS[i] == material) {
                return i;
            }
        }
        return -1;
    }

    private BlockData randomBeaconGlassBlockData(int currentIndex) {
        return BEACON_GLASS_BLOCK_DATA[randomBeaconGlassIndex(currentIndex)];
    }

    private int randomBeaconGlassIndex(int currentIndex) {
        int nextIndex = ThreadLocalRandom.current().nextInt(BEACON_GLASS_COLORS.length - 1);
        if (nextIndex >= currentIndex) {
            nextIndex++;
        }
        return nextIndex;
    }

    private static BlockData[] createBeaconGlassBlockData() {
        BlockData[] blockData = new BlockData[BEACON_GLASS_COLORS.length];
        for (int i = 0; i < BEACON_GLASS_COLORS.length; i++) {
            blockData[i] = BEACON_GLASS_COLORS[i].createBlockData();
        }
        return blockData;
    }
}
