package org.encinet.mik.module.world.regen;

import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Performs snapshot-only comparison and is therefore safe to run off the server thread. */
final class RegenChunkPlanner {

    static final int BIOME_CELL_SIZE = 4;
    private static final int CHANGED_BLOCK_SAMPLES_PER_CHUNK = 16;
    private static final int RISK_SAMPLES_PER_CHUNK = 8;

    private RegenChunkPlanner() {
    }

    static RegenChunkChanges plan(
            RegenSelection selection,
            RegenChunkCapture generated,
            RegenChunkCapture current,
            RegenPlan plan
    ) {
        ChunkSnapshot generatedSnapshot = generated.snapshot();
        ChunkSnapshot currentSnapshot = current.snapshot();
        if (generatedSnapshot.getX() != currentSnapshot.getX()
                || generatedSnapshot.getZ() != currentSnapshot.getZ()) {
            throw new IllegalArgumentException("Snapshots must describe the same chunk coordinates");
        }

        int chunkX = currentSnapshot.getX();
        int chunkZ = currentSnapshot.getZ();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minHeight = selection.worldMinHeight();
        int minimumX = Math.max(selection.minX(), baseX);
        int maximumX = Math.min(selection.maxX(), baseX + 15);
        int minimumZ = Math.max(selection.minZ(), baseZ);
        int maximumZ = Math.min(selection.maxZ(), baseZ + 15);
        int minimumY = Math.max(selection.minY(), minHeight);
        int maximumY = Math.min(selection.maxY(), selection.worldMaxHeight() - 1);

        RegenStructureChanges structureChanges = RegenStructureChanges.select(
                generated.structures(), plan.structureMetadataIdentities());
        Builder changes = new Builder(
                new RegenChunkPos(chunkX, chunkZ),
                minHeight,
                generated.structures().descriptors(),
                current.structures().descriptors(),
                structureChanges,
                plan.preview());
        if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
            return changes.build();
        }

        if (plan.blocks()) {
            planBlocks(selection, generated, current, plan.blockFilter(), changes,
                    baseX, baseZ, minHeight,
                    minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ);
        }
        if (plan.biomes()) {
            planBiomes(selection, generatedSnapshot, currentSnapshot, changes,
                    baseX, baseZ, minHeight,
                    minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ);
        }
        return changes.build();
    }

    private static void planBlocks(
            RegenSelection selection,
            RegenChunkCapture generated,
            RegenChunkCapture current,
            RegenBlockFilter filter,
            Builder changes,
            int baseX,
            int baseZ,
            int minHeight,
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ
    ) {
        ChunkSnapshot generatedSnapshot = generated.snapshot();
        ChunkSnapshot currentSnapshot = current.snapshot();
        for (int y = minimumY; y <= maximumY; y++) {
            checkInterrupted();
            for (int z = minimumZ; z <= maximumZ; z++) {
                int localZ = z - baseZ;
                for (int x = minimumX; x <= maximumX; x++) {
                    if (!selection.contains(x, y, z)) {
                        continue;
                    }
                    int localX = x - baseX;
                    if (!filter.includes(currentSnapshot.getBlockType(localX, y, localZ))) {
                        continue;
                    }

                    int packed = PackedBlockPosition.pack(localX, y, localZ, minHeight);
                    BlockData generatedData = generatedSnapshot.getBlockData(localX, y, localZ);
                    BlockData currentData = currentSnapshot.getBlockData(localX, y, localZ);
                    BlockState generatedBlockEntity = generated.blockEntities().get(packed);
                    if (generatedData.equals(currentData) && generatedBlockEntity == null) {
                        continue;
                    }
                    changes.addBlock(packed, generatedData, generatedBlockEntity,
                            current.blockEntities().containsKey(packed));
                }
            }
        }
    }

    private static void planBiomes(
            RegenSelection selection,
            ChunkSnapshot generated,
            ChunkSnapshot current,
            Builder changes,
            int baseX,
            int baseZ,
            int minHeight,
            int minimumX,
            int maximumX,
            int minimumY,
            int maximumY,
            int minimumZ,
            int maximumZ
    ) {
        int firstCellX = cellOrigin(minimumX);
        int firstCellY = cellOrigin(minimumY);
        int firstCellZ = cellOrigin(minimumZ);
        for (int cellY = firstCellY; cellY <= maximumY; cellY += BIOME_CELL_SIZE) {
            checkInterrupted();
            int sampleY = Math.max(cellY, minHeight);
            for (int cellZ = firstCellZ; cellZ <= maximumZ; cellZ += BIOME_CELL_SIZE) {
                for (int cellX = firstCellX; cellX <= maximumX; cellX += BIOME_CELL_SIZE) {
                    CellCoverage coverage = coverage(selection, cellX, cellY, cellZ);
                    if (!coverage.intersectsSelection()) {
                        continue;
                    }
                    if (!coverage.fullyInsideSelection()) {
                        changes.skipBoundaryBiomeCell();
                        continue;
                    }

                    int localX = cellX - baseX;
                    int localZ = cellZ - baseZ;
                    Biome generatedBiome = generated.getBiome(localX, sampleY, localZ);
                    Biome currentBiome = current.getBiome(localX, sampleY, localZ);
                    if (!generatedBiome.equals(currentBiome)) {
                        changes.addBiome(PackedBlockPosition.pack(localX, sampleY, localZ, minHeight),
                                generatedBiome);
                    }
                }
            }
        }
    }

    static int cellOrigin(int coordinate) {
        return Math.floorDiv(coordinate, BIOME_CELL_SIZE) * BIOME_CELL_SIZE;
    }

    static CellCoverage coverage(RegenSelection selection, int cellX, int cellY, int cellZ) {
        boolean intersects = false;
        boolean contained = true;
        int minimumY = Math.max(cellY, selection.worldMinHeight());
        int maximumY = Math.min(cellY + BIOME_CELL_SIZE - 1, selection.worldMaxHeight() - 1);
        if (minimumY > maximumY) {
            return new CellCoverage(false, false);
        }
        for (int y = minimumY; y <= maximumY; y++) {
            for (int z = cellZ; z < cellZ + BIOME_CELL_SIZE; z++) {
                for (int x = cellX; x < cellX + BIOME_CELL_SIZE; x++) {
                    boolean selected = selection.contains(x, y, z);
                    intersects |= selected;
                    contained &= selected;
                }
            }
        }
        return new CellCoverage(intersects, intersects && contained);
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Regeneration comparison was interrupted");
        }
    }

    record CellCoverage(boolean intersectsSelection, boolean fullyInsideSelection) {
    }

    private static final class Builder {

        private final RegenChunkPos chunk;
        private final int minHeight;
        private final List<BlockData> blockPalette = new ArrayList<>();
        private final Map<BlockData, Integer> blockPaletteIndexes = new HashMap<>();
        private final Map<Integer, BlockState> blockEntities = new HashMap<>();
        private final List<Biome> biomePalette = new ArrayList<>();
        private final Map<Biome, Integer> biomePaletteIndexes = new HashMap<>();
        private final Set<RegenStructureDescriptor> sourceStructures;
        private final Set<RegenStructureDescriptor> targetStructures;
        private final RegenStructureChanges structureChanges;
        private final RegenPreviewPointSampler changedBlockSamples;
        private final RegenPreviewPointSampler riskSamples;
        private int[] blockPositions = new int[256];
        private int[] blockPaletteByPosition = new int[256];
        private int[] biomePositions = new int[64];
        private int[] biomePaletteByPosition = new int[64];
        private int blockSize;
        private int biomeSize;
        private int skippedBoundaryBiomeCells;
        private int atRiskBlockEntities;

        private Builder(
                RegenChunkPos chunk,
                int minHeight,
                Set<RegenStructureDescriptor> sourceStructures,
                Set<RegenStructureDescriptor> targetStructures,
                RegenStructureChanges structureChanges,
                boolean collectPreviewSamples
        ) {
            this.chunk = chunk;
            this.minHeight = minHeight;
            this.sourceStructures = sourceStructures;
            this.targetStructures = targetStructures;
            this.structureChanges = structureChanges;
            this.changedBlockSamples = collectPreviewSamples
                    ? new RegenPreviewPointSampler(CHANGED_BLOCK_SAMPLES_PER_CHUNK)
                    : null;
            this.riskSamples = collectPreviewSamples
                    ? new RegenPreviewPointSampler(RISK_SAMPLES_PER_CHUNK)
                    : null;
        }

        private void addBlock(int packed, BlockData data, BlockState blockEntity, boolean atRisk) {
            ensureBlockCapacity(blockSize + 1);
            blockPositions[blockSize] = packed;
            Integer paletteIndex = blockPaletteIndexes.get(data);
            if (paletteIndex == null) {
                BlockData paletteData = data.clone();
                paletteIndex = blockPalette.size();
                blockPalette.add(paletteData);
                blockPaletteIndexes.put(paletteData, paletteIndex);
            }
            blockPaletteByPosition[blockSize] = paletteIndex;
            if (blockEntity != null) {
                blockEntities.put(blockSize, blockEntity);
            }
            if (atRisk) {
                atRiskBlockEntities++;
            }
            if (changedBlockSamples != null) {
                RegenPreviewPoint point = new RegenPreviewPoint(
                        (chunk.x() << 4) + PackedBlockPosition.localX(packed),
                        PackedBlockPosition.y(packed, minHeight),
                        (chunk.z() << 4) + PackedBlockPosition.localZ(packed));
                changedBlockSamples.add(point);
                if (atRisk) {
                    riskSamples.add(point);
                }
            }
            blockSize++;
        }

        private void addBiome(int packed, Biome biome) {
            ensureBiomeCapacity(biomeSize + 1);
            biomePositions[biomeSize] = packed;
            Integer paletteIndex = biomePaletteIndexes.get(biome);
            if (paletteIndex == null) {
                paletteIndex = biomePalette.size();
                biomePalette.add(biome);
                biomePaletteIndexes.put(biome, paletteIndex);
            }
            biomePaletteByPosition[biomeSize] = paletteIndex;
            biomeSize++;
        }

        private void skipBoundaryBiomeCell() {
            skippedBoundaryBiomeCells++;
        }

        private RegenChunkChanges build() {
            return new RegenChunkChanges(
                    chunk,
                    minHeight,
                    Arrays.copyOf(blockPositions, blockSize),
                    Arrays.copyOf(blockPaletteByPosition, blockSize),
                    blockPalette,
                    blockEntities,
                    Arrays.copyOf(biomePositions, biomeSize),
                    Arrays.copyOf(biomePaletteByPosition, biomeSize),
                    biomePalette,
                    sourceStructures,
                    targetStructures,
                    structureChanges,
                    changedBlockSamples == null
                            ? RegenPreviewSamples.EMPTY
                            : new RegenPreviewSamples(
                                    changedBlockSamples.snapshot(), riskSamples.snapshot()),
                    skippedBoundaryBiomeCells,
                    atRiskBlockEntities,
                    false
            );
        }

        private void ensureBlockCapacity(int required) {
            if (required <= blockPositions.length) {
                return;
            }
            int capacity = Math.max(required, blockPositions.length * 2);
            blockPositions = Arrays.copyOf(blockPositions, capacity);
            blockPaletteByPosition = Arrays.copyOf(blockPaletteByPosition, capacity);
        }

        private void ensureBiomeCapacity(int required) {
            if (required <= biomePositions.length) {
                return;
            }
            int capacity = Math.max(required, biomePositions.length * 2);
            biomePositions = Arrays.copyOf(biomePositions, capacity);
            biomePaletteByPosition = Arrays.copyOf(biomePaletteByPosition, capacity);
        }
    }
}
