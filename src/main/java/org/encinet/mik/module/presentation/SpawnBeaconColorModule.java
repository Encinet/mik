package org.encinet.mik.module.presentation;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

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
            Material.CYAN_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS
    };

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private BukkitTask task;
    private boolean enabled = true;

    public SpawnBeaconColorModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, INITIAL_DELAY_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal(COMMAND_NAME)
                .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                .executes(ctx -> {
                    enabled = !enabled;
                    ctx.getSource().getSender().sendMessage(languageService.text(Language.DEFAULT,
                            enabled ? Message.SPAWN_BEACON_COLOR_ENABLED : Message.SPAWN_BEACON_COLOR_DISABLED,
                            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                    return Command.SINGLE_SUCCESS;
                })
                .build(), languageService.t(Language.DEFAULT, Message.SPAWN_BEACON_COLOR_COMMAND_DESCRIPTION)));
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!enabled) {
            return;
        }

        World world = Bukkit.getWorld(BEACON_WORLD_NAME);
        if (world == null || !world.isChunkLoaded(BEACON_CHUNK_X, BEACON_CHUNK_Z)) {
            return;
        }

        Block block = world.getBlockAt(BEACON_X, BEACON_Y, BEACON_Z);
        Material current = block.getType();
        int currentIndex = beaconGlassIndex(current);
        if (currentIndex < 0) {
            return;
        }

        Material next = randomBeaconGlass(currentIndex);
        block.setType(next, false);
    }

    private int beaconGlassIndex(Material material) {
        for (int i = 0; i < BEACON_GLASS_COLORS.length; i++) {
            if (BEACON_GLASS_COLORS[i] == material) {
                return i;
            }
        }
        return -1;
    }

    private Material randomBeaconGlass(int currentIndex) {
        int nextIndex = ThreadLocalRandom.current().nextInt(BEACON_GLASS_COLORS.length - 1);
        if (nextIndex >= currentIndex) {
            nextIndex++;
        }
        return BEACON_GLASS_COLORS[nextIndex];
    }
}
