package org.encinet.mik.module.world.regen;

import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RegenChunkChanges {

    private final RegenChunkPos chunk;
    private final int minHeight;
    private final int[] positions;
    private final int[] paletteIndexes;
    private final List<BlockData> palette;
    private final Map<Integer, BlockState> blockEntities;
    private final int[] biomePositions;
    private final int[] biomePaletteIndexes;
    private final List<Biome> biomePalette;
    private final Set<RegenStructureDescriptor> sourceStructures;
    private final Set<RegenStructureDescriptor> targetStructures;
    private final RegenStructureChanges structureChanges;
    private final RegenPreviewSamples previewSamples;
    private final int skippedBoundaryBiomeCells;
    private final int atRiskBlockEntities;
    private final boolean skippedUngeneratedChunk;

    RegenChunkChanges(
            RegenChunkPos chunk,
            int minHeight,
            int[] positions,
            int[] paletteIndexes,
            List<BlockData> palette,
            Map<Integer, BlockState> blockEntities,
            int[] biomePositions,
            int[] biomePaletteIndexes,
            List<Biome> biomePalette,
            Set<RegenStructureDescriptor> sourceStructures,
            Set<RegenStructureDescriptor> targetStructures,
            RegenStructureChanges structureChanges,
            RegenPreviewSamples previewSamples,
            int skippedBoundaryBiomeCells,
            int atRiskBlockEntities,
            boolean skippedUngeneratedChunk
    ) {
        if (positions.length != paletteIndexes.length) {
            throw new IllegalArgumentException("Every changed position requires a palette index");
        }
        if (biomePositions.length != biomePaletteIndexes.length) {
            throw new IllegalArgumentException("Every changed biome cell requires a palette index");
        }
        this.chunk = chunk;
        this.minHeight = minHeight;
        this.positions = Arrays.copyOf(positions, positions.length);
        this.paletteIndexes = Arrays.copyOf(paletteIndexes, paletteIndexes.length);
        this.palette = List.copyOf(palette);
        this.blockEntities = Map.copyOf(blockEntities);
        this.biomePositions = Arrays.copyOf(biomePositions, biomePositions.length);
        this.biomePaletteIndexes = Arrays.copyOf(biomePaletteIndexes, biomePaletteIndexes.length);
        this.biomePalette = List.copyOf(biomePalette);
        this.sourceStructures = Set.copyOf(sourceStructures);
        this.targetStructures = Set.copyOf(targetStructures);
        this.structureChanges = structureChanges;
        this.previewSamples = previewSamples;
        this.skippedBoundaryBiomeCells = skippedBoundaryBiomeCells;
        this.atRiskBlockEntities = atRiskBlockEntities;
        this.skippedUngeneratedChunk = skippedUngeneratedChunk;
    }

    static RegenChunkChanges ungenerated(RegenChunkPos chunk, int minHeight) {
        return new RegenChunkChanges(chunk, minHeight,
                new int[0], new int[0], List.of(), Map.of(),
                new int[0], new int[0], List.of(), Set.of(), Set.of(), RegenStructureChanges.EMPTY,
                RegenPreviewSamples.EMPTY, 0, 0, true);
    }

    RegenChunkPos chunk() {
        return chunk;
    }

    int blockSize() {
        return positions.length;
    }

    int biomeSize() {
        return biomePositions.length;
    }

    int totalUnits() {
        return blockSize() + biomeSize() + structureChanges.size();
    }

    int worldX(int index) {
        return (chunk.x() << 4) + PackedBlockPosition.localX(positions[index]);
    }

    int worldY(int index) {
        return PackedBlockPosition.y(positions[index], minHeight);
    }

    int worldZ(int index) {
        return (chunk.z() << 4) + PackedBlockPosition.localZ(positions[index]);
    }

    BlockData blockData(int index) {
        return palette.get(paletteIndexes[index]);
    }

    BlockState blockEntity(int index) {
        return blockEntities.get(index);
    }

    int biomeWorldX(int index) {
        return (chunk.x() << 4) + PackedBlockPosition.localX(biomePositions[index]);
    }

    int biomeWorldY(int index) {
        return PackedBlockPosition.y(biomePositions[index], minHeight);
    }

    int biomeWorldZ(int index) {
        return (chunk.z() << 4) + PackedBlockPosition.localZ(biomePositions[index]);
    }

    Biome biome(int index) {
        return biomePalette.get(biomePaletteIndexes[index]);
    }

    Set<RegenStructureDescriptor> structures() {
        return sourceStructures;
    }

    Set<RegenStructureDescriptor> targetStructures() {
        return targetStructures;
    }

    int structureStartSize() {
        return structureChanges.starts().size();
    }

    RegenStructureStartData structureStart(int index) {
        return structureChanges.starts().get(index);
    }

    int structureReferenceSize() {
        return structureChanges.references().size();
    }

    RegenStructureReference structureReference(int index) {
        return structureChanges.references().get(index);
    }

    RegenPreviewSamples previewSamples() {
        return previewSamples;
    }

    int skippedBoundaryBiomeCells() {
        return skippedBoundaryBiomeCells;
    }

    int atRiskBlockEntities() {
        return atRiskBlockEntities;
    }

    boolean skippedUngeneratedChunk() {
        return skippedUngeneratedChunk;
    }
}
