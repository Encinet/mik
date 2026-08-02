package org.encinet.mik.module.world.regen;

import io.papermc.paper.math.Position;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Owns pristine, player-inaccessible worlds used as the source for regeneration snapshots.
 * All methods must be called from the server thread.
 */
final class ShadowWorldManager {

    private static final String WORLD_KEY_PREFIX = "regen_shadow_";

    private final JavaPlugin plugin;
    private final String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final Map<UUID, ShadowWorld> shadows = new HashMap<>();
    private final List<ShadowWorld> deferredCleanup = new ArrayList<>();
    private int sequence;

    ShadowWorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    World worldFor(World target) {
        requireServerThread();
        ShadowWorld existing = shadows.get(target.getUID());
        if (existing != null && Bukkit.getWorld(existing.world().getUID()) == existing.world()) {
            return existing.world();
        }

        if (existing != null) {
            shadows.remove(target.getUID());
            deleteTrackedWorld(existing.folder());
        }
        String keyValue = WORLD_KEY_PREFIX + sessionId + "_" + sequence++;
        WorldCreator creator = WorldCreator.ofKey(new NamespacedKey(plugin, keyValue))
                .copy(target)
                .bonusChest(false)
                .forcedSpawnPosition(Position.block(0, target.getMinHeight(), 0), 0.0F, 0.0F);
        World shadow = creator.createWorld();
        if (shadow == null) {
            throw new IllegalStateException("Paper could not create regeneration shadow world for "
                    + target.key().asString());
        }

        Path folder = shadow.getWorldFolder().toPath().toAbsolutePath().normalize();
        try {
            validateShape(target, shadow);
            configure(shadow);
        } catch (RuntimeException error) {
            if (Bukkit.unloadWorld(shadow, false)) {
                deleteTrackedWorld(folder);
            } else {
                deferredCleanup.add(new ShadowWorld(shadow, folder));
                plugin.getLogger().warning("Could not unload an invalid regeneration shadow world; "
                        + "cleanup will be retried when the plugin stops");
            }
            throw error;
        }
        shadows.put(target.getUID(), new ShadowWorld(shadow, folder));
        plugin.getLogger().info("Created asynchronous regeneration shadow world "
                + shadow.key().asString() + " for " + target.key().asString());
        return shadow;
    }

    void close() {
        requireServerThread();
        List<ShadowWorld> created = new ArrayList<>(shadows.values());
        created.addAll(deferredCleanup);
        shadows.clear();
        deferredCleanup.clear();
        for (ShadowWorld shadow : created) {
            boolean unloaded = Bukkit.getWorld(shadow.world().getUID()) == null
                    || Bukkit.unloadWorld(shadow.world(), false);
            if (!unloaded) {
                plugin.getLogger().warning("Could not unload regeneration shadow world "
                        + shadow.world().key().asString() + "; its files were left intact");
                continue;
            }
            deleteTrackedWorld(shadow.folder());
        }
    }

    private void configure(World world) {
        world.setAutoSave(false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
    }

    private static void validateShape(World target, World shadow) {
        if (shadow.getMinHeight() != target.getMinHeight()
                || shadow.getMaxHeight() != target.getMaxHeight()) {
            throw new IllegalStateException("Regeneration shadow world has incompatible build heights for "
                    + target.key().asString());
        }
        if (shadow.getSeed() != target.getSeed()) {
            throw new IllegalStateException("Regeneration shadow world has an incompatible seed for "
                    + target.key().asString());
        }
    }

    private void deleteTrackedWorld(Path folder) {
        Path worldContainer = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        String fileName = folder.getFileName() == null ? "" : folder.getFileName().toString();
        String expectedKeyPrefix = WORLD_KEY_PREFIX + sessionId + "_";
        String legacyFolderPrefix = plugin.getName().toLowerCase(Locale.ROOT) + "_" + expectedKeyPrefix;
        if (!folder.startsWith(worldContainer)
                || folder.equals(worldContainer)
                || (!fileName.startsWith(expectedKeyPrefix) && !fileName.startsWith(legacyFolderPrefix))) {
            plugin.getLogger().severe("Refusing to delete unrecognized regeneration world path: " + folder);
            return;
        }
        if (!Files.exists(folder)) {
            return;
        }
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    if (error != null) {
                        throw error;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not delete regeneration shadow world " + folder + ": "
                    + exception.getMessage());
        }
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Shadow worlds may only be managed from the server thread");
        }
    }

    private record ShadowWorld(World world, Path folder) {
    }
}
