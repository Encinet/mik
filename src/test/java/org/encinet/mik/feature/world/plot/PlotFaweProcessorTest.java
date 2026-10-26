package org.encinet.mik.feature.world.plot;

import com.fastasyncworldedit.core.configuration.Settings;
import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.implementation.blocks.CharSetBlocks;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.encinet.mik.feature.world.plot.integration.PlotFaweProcessor;
import org.encinet.mik.feature.world.plot.integration.PlotFaweHook;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinDoubleTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinTagType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PlotFaweProcessorTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();
    private final BiomeType biome = new BiomeType("minecraft:plains");

    @Test
    void hookCleansUpOnlyItsOwnFallbackAllowance() {
        var settings = Settings.settings().EXTENT;
        List<String> original = settings.ALLOWED_PLUGINS;
        PlotFaweHook hook = new PlotFaweHook(null);
        String extent = new PlotFaweProcessor(null, world, visitor, false, false)
                .construct(null).getClass().getName();
        try {
            settings.ALLOWED_PLUGINS = new java.util.ArrayList<>(List.of("other.plugin"));
            hook.enable();
            hook.enable();
            assertEquals(List.of("other.plugin", extent), settings.ALLOWED_PLUGINS);
            hook.disable();
            hook.disable();
            assertEquals(List.of("other.plugin"), settings.ALLOWED_PLUGINS);

            settings.ALLOWED_PLUGINS.add(extent);
            hook.enable();
            hook.disable();
            assertEquals(List.of("other.plugin", extent), settings.ALLOWED_PLUGINS);
        } finally {
            hook.disable();
            settings.ALLOWED_PLUGINS = original;
        }
    }

    @Test
    void asynchronousQueueFiltersBlocksBiomesTilesAndEntitiesAtNegativeCoordinates() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of(), Map.of());
            PlotFaweProcessor processor = new PlotFaweProcessor(registry, world, visitor, false, false);
            CharSetBlocks set = edits();

            assertSame(set, CompletableFuture.supplyAsync(() -> processor.processSet(chunk(), null, set)).get());

            assertEquals(0, set.load(-4)[blockIndex(3, 4, 5)]);
            assertEquals(1, set.load(-4)[blockIndex(4, 4, 5)]);
            assertNull(set.getBiomes()[0][biomeIndex(0, 4, 4)]);
            assertEquals(biome, set.getBiomes()[0][biomeIndex(4, 4, 4)]);
            assertFalse(set.tiles().containsKey(BlockVector3.at(3, -60, 5)));
            assertTrue(set.tiles().containsKey(BlockVector3.at(4, -60, 5)));
            assertEquals(Set.of(entity("minecraft:pig", -12, -60, 37)), Set.copyOf(set.entities()));
        }
    }

    @Test
    void ownerAndStaffRetainEditsAndExistingProcessorsObservePermissionChanges() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of(), Map.of());
            for (PlotFaweProcessor processor : List.of(
                    new PlotFaweProcessor(registry, world, owner, false, false),
                    new PlotFaweProcessor(registry, world, visitor, false, true))) {
                CharSetBlocks set = edits();
                processor.processSet(chunk(), null, set);
                assertEquals(1, set.load(-4)[blockIndex(3, 4, 5)]);
                assertEquals(2, set.tiles().size());
                assertEquals(2, set.entities().size());
                assertEquals(biome, set.getBiomes()[0][biomeIndex(0, 4, 4)]);
            }

            PlotFaweProcessor processor = new PlotFaweProcessor(registry, world, visitor, false, false);
            Plot plot = registry.all().getFirst();
            registry.put(new Plot(plot.id(), world, owner, plot.name(), false, 0, plot.cells(),
                    Map.of(visitor, Plot.Role.COLLABORATOR), Map.of(), null));
            CharSetBlocks set = edits();
            processor.processSet(chunk(), null, set);
            assertEquals(1, set.load(-4)[blockIndex(3, 4, 5)]);
        }
    }

    @Test
    void queuedEntityPlacementUsesItsOwnPermission() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of(visitor, Plot.Role.COLLABORATOR),
                    Map.of("collaborator.place", false, "collaborator.decoration", true,
                            "collaborator.animal", false));
            PlotFaweProcessor processor = new PlotFaweProcessor(registry, world, visitor, false, false);
            CharSetBlocks set = CharSetBlocks.newInstance(-1, 2);
            FaweCompoundTag decoration = entity("minecraft:armor_stand", -13, -60, 37);
            set.entity(decoration);
            set.entity(entity("minecraft:leash_knot", -13, -60, 37));
            set.entity(entity("minecraft:pig", -13, -60, 37));

            processor.processSet(chunk(), null, set);

            assertEquals(Set.of(decoration), Set.copyOf(set.entities()));
        }
    }

    @Test
    void fallbackGuardsVectorIntegerAndBulkWritesWithoutDelegatingAroundProtection() throws Exception {
        try (PlotRepository store = store()) {
            PlotRegistry registry = registry(store, Map.of(), Map.of());
            Set<BlockVector3> writes = new java.util.HashSet<>();
            Extent parent = (Extent) Proxy.newProxyInstance(Extent.class.getClassLoader(),
                    new Class<?>[]{Extent.class}, (proxy, method, args) -> {
                        if (Set.of("setBlock", "setBiome", "tile").contains(method.getName())) {
                            assertEquals(4, args.length);
                            writes.add(BlockVector3.at((int) args[0], (int) args[1], (int) args[2]));
                            return true;
                        }
                        throw new AssertionError("Unexpected unguarded call: " + method.getName());
                    });
            Extent guard = new PlotFaweProcessor(registry, world, visitor, false, false).construct(parent);
            BlockVector3 inside = BlockVector3.at(-13, -60, 37);
            BlockVector3 outside = BlockVector3.at(-12, -60, 37);
            assertFalse(guard.setBlock(inside, null));
            assertFalse(guard.setBlock(-13, -60, 37, null));
            assertFalse(guard.tile(-13, -60, 37, null));
            assertFalse(guard.setBiome(inside, biome));
            assertFalse(guard.setBiome(-13, -60, 37, biome));
            assertTrue(writes.isEmpty());
            assertTrue(guard.setBlock(outside, null));
            assertTrue(guard.setBiome(outside, biome));
            assertTrue(guard.tile(-12, -60, 37, null));
            writes.clear();
            BlockStateHolder<?> block = (BlockStateHolder<?>) Proxy.newProxyInstance(
                    BlockStateHolder.class.getClassLoader(), new Class<?>[]{BlockStateHolder.class},
                    (proxy, method, args) -> { throw new AssertionError(method.getName()); });
            assertEquals(1, guard.setBlocks((Region) new CuboidRegion(inside, outside), block));
            assertEquals(Set.of(outside), writes);
        }
    }

    private PlotRepository store() throws Exception {
        PlotRepository store = new PlotRepository(directory.resolve(UUID.randomUUID() + ".db"));
        store.open();
        return store;
    }

    private PlotRegistry registry(PlotRepository store, Map<UUID, Plot.Role> members, Map<String, Boolean> flags)
            throws Exception {
        PlotRegistry registry = new PlotRegistry(store);
        registry.put(new Plot(UUID.randomUUID(), world, owner, "Protected", false, 0,
                Set.of(PlotGeometry.Cell.at(-13, -60, 37)), members, flags, null));
        return registry;
    }

    private CharSetBlocks edits() {
        CharSetBlocks set = CharSetBlocks.newInstance(-1, 2);
        char[] blocks = new char[4096];
        blocks[blockIndex(3, 4, 5)] = 1;
        blocks[blockIndex(4, 4, 5)] = 1;
        set.setBlocks(-4, blocks);
        set.setBiome(0, -60, 4, biome);
        set.setBiome(4, -60, 4, biome);
        FaweCompoundTag tile = FaweCompoundTag.of(LinCompoundTag.builder().build());
        set.tile(3, -60, 5, tile);
        set.tile(4, -60, 5, tile);
        set.entity(entity("minecraft:pig", -13, -60, 37));
        set.entity(entity("minecraft:pig", -12, -60, 37));
        return set;
    }

    private static int blockIndex(int x, int y, int z) { return y << 8 | z << 4 | x; }
    private static int biomeIndex(int x, int y, int z) { return (y & 12) << 2 | (z & 12) | x >> 2; }

    private static FaweCompoundTag entity(String type, double x, double y, double z) {
        return FaweCompoundTag.of(LinCompoundTag.builder().putString("id", type)
                .put("Pos", LinListTag.of(LinTagType.doubleTag(),
                        List.of(LinDoubleTag.of(x), LinDoubleTag.of(y), LinDoubleTag.of(z)))).build());
    }

    private static IChunk chunk() {
        return (IChunk) Proxy.newProxyInstance(IChunk.class.getClassLoader(), new Class<?>[]{IChunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> -1;
                    case "getZ" -> 2;
                    default -> throw new AssertionError(method.getName());
                });
    }
}
