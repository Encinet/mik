package org.encinet.mik.module.world.regen;

import java.util.Objects;
import java.util.Set;

/** Immutable structure-start information captured without synchronously loading another chunk. */
record RegenStructureDescriptor(
        String type,
        int startChunkX,
        int startChunkZ,
        boolean boundsKnown,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    RegenStructureDescriptor {
        Objects.requireNonNull(type, "type");
    }

    static RegenStructureDescriptor reference(String type, int startChunkX, int startChunkZ) {
        return new RegenStructureDescriptor(type, startChunkX, startChunkZ,
                false, 0, 0, 0, 0, 0, 0);
    }

    static RegenStructureDescriptor start(
            String type,
            int startChunkX,
            int startChunkZ,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return new RegenStructureDescriptor(type, startChunkX, startChunkZ,
                true, minX, minY, minZ, maxX, maxY, maxZ);
    }

    String identity() {
        return type + '@' + startChunkX + ',' + startChunkZ;
    }

    boolean fullyInside(RegenSelection selection) {
        return boundsKnown
                && selection.fullyContainsBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    boolean metadataEligible(RegenSelection selection, Set<RegenChunkPos> ungeneratedChunks) {
        if (!fullyInside(selection)) {
            return false;
        }
        int minimumChunkX = minX >> 4;
        int maximumChunkX = maxX >> 4;
        int minimumChunkZ = minZ >> 4;
        int maximumChunkZ = maxZ >> 4;
        for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                if (ungeneratedChunks.contains(new RegenChunkPos(chunkX, chunkZ))) {
                    return false;
                }
            }
        }
        return true;
    }
}
