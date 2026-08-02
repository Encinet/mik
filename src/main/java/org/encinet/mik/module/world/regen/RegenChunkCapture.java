package org.encinet.mik.module.world.regen;

import org.bukkit.ChunkSnapshot;
import org.bukkit.block.BlockState;

import java.util.Map;
record RegenChunkCapture(
        ChunkSnapshot snapshot,
        Map<Integer, BlockState> blockEntities,
        RegenStructureCapture structures
) {

    RegenChunkCapture {
        blockEntities = Map.copyOf(blockEntities);
    }
}
