package org.encinet.mik.feature.world.plot.integration;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.fastasyncworldedit.core.util.NbtUtils;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.encinet.mik.feature.world.plot.PlotRegistry;
import org.encinet.mik.feature.world.plot.protection.PlotEntityEditActions;
import org.enginehub.linbus.tree.LinTagType;

import java.util.UUID;
import java.util.List;

/** Filters queued edits against the registry's immutable permission snapshots. */
public final class PlotFaweProcessor implements IBatchProcessor {
    static final String EXTENT_NAME = Guard.class.getName();
    private final PlotRegistry registry;
    private final UUID world;
    private final UUID actor;
    private final boolean member;
    private final boolean staff;

    public PlotFaweProcessor(PlotRegistry registry, UUID world, UUID actor, boolean member, boolean staff) {
        this.registry = registry;
        this.world = world;
        this.actor = actor;
        this.member = member;
        this.staff = staff;
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        int originX = chunk.getX() << 4;
        int originZ = chunk.getZ() << 4;
        for (int layer = set.getMinSectionPosition(); layer <= set.getMaxSectionPosition(); layer++) {
            char[] blocks = set.loadIfPresent(layer);
            if (blocks == null) continue;
            for (int index = 0; index < blocks.length; index++) {
                if (blocks[index] != 0 && !allowed(originX + (index & 15), (layer << 4) + (index >> 8),
                        originZ + ((index >> 4) & 15), "build")) blocks[index] = 0;
            }
        }
        BiomeType[][] biomes = set.getBiomes();
        if (biomes != null) {
            for (int layer = 0; layer < biomes.length; layer++) {
                if (biomes[layer] == null) continue;
                for (int index = 0; index < biomes[layer].length; index++) {
                    // Plot cells and Minecraft biome cells both cover aligned 4x4x4 blocks.
                    if (biomes[layer][index] != null && !allowed(originX + ((index & 3) << 2),
                            ((layer + set.getMinSectionPosition()) << 4) + ((index >> 4) << 2),
                            originZ + (((index >> 2) & 3) << 2), "build")) biomes[layer][index] = null;
                }
            }
        }
        set.tiles().keySet().removeIf(point -> !allowed(originX + point.x(), point.y(),
                originZ + point.z(), "build"));
        set.entities().removeIf(tag -> {
            BlockVector3 point = NbtUtils.entityPosition(tag).toBlockPoint();
            String type = tag.linTag().getTag("id", LinTagType.stringTag()).value();
            return !allowed(point.x(), point.y(), point.z(), PlotEntityEditActions.placement(type).key());
        });
        return set;
    }

    @Override
    public Extent construct(Extent child) {
        return new Guard(child);
    }

    @Override
    public ProcessorScope getScope() {
        return ProcessorScope.REMOVING_BLOCKS;
    }

    private boolean allowed(int x, int y, int z, String permission) {
        return registry.allowed(world, x, y, z, actor, member, staff, permission);
    }

    // Use Extent's bulk operations so they call the guarded setters, rather than
    // AbstractDelegateExtent's bulk methods which would pass straight to the parent.
    private final class Guard implements Extent {
        private final Extent parent;

        Guard(Extent parent) {
            this.parent = parent;
        }

        @Override
        public BlockVector3 getMinimumPoint() { return parent.getMinimumPoint(); }

        @Override
        public BlockVector3 getMaximumPoint() { return parent.getMaximumPoint(); }

        @Override
        public BlockState getBlock(BlockVector3 point) { return parent.getBlock(point); }

        @Override
        public BaseBlock getFullBlock(BlockVector3 point) { return parent.getFullBlock(point); }

        @Override
        public BiomeType getBiome(BlockVector3 point) { return parent.getBiome(point); }

        @Override
        public List<? extends Entity> getEntities() { return parent.getEntities(); }

        @Override
        public List<? extends Entity> getEntities(Region region) { return parent.getEntities(region); }

        @Override
        public Operation commit() { return parent.commit(); }

        @Override
        public boolean tile(int x, int y, int z, FaweCompoundTag tile) throws WorldEditException {
            return allowed(x, y, z, "build") && parent.tile(x, y, z, tile);
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
            return allowed(x, y, z, "build") && parent.setBlock(x, y, z, block);
        }

        @Override
        public boolean setBiome(BlockVector3 point, BiomeType biome) {
            return setBiome(point.x(), point.y(), point.z(), biome);
        }

        @Override
        public boolean setBiome(int x, int y, int z, BiomeType biome) {
            return allowed(x, y, z, "build") && parent.setBiome(x, y, z, biome);
        }

        @Override
        public Entity createEntity(Location location, BaseEntity entity) {
            return allowedEntity(location, entity) ? parent.createEntity(location, entity) : null;
        }

        @Override
        public Entity createEntity(Location location, BaseEntity entity, UUID uuid) {
            return allowedEntity(location, entity) ? parent.createEntity(location, entity, uuid) : null;
        }

        private boolean allowedEntity(Location location, BaseEntity entity) {
            return allowed(location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                    PlotEntityEditActions.placement(entity.getType().id()).key());
        }
    }
}
