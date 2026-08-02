package org.encinet.mik.module.world.regen;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenStructureMetadataTest {

    @Test
    void metadataRequiresTheWholeStructureAndEveryTargetChunk() {
        RegenSelection selection = new BoxSelection(0, -64, 0, 47, 200, 47, true);
        RegenStructureDescriptor complete = RegenStructureDescriptor.start(
                "minecraft:trial_chambers", 1, 1,
                2, -20, 3, 45, 60, 44);
        RegenStructureDescriptor clipped = RegenStructureDescriptor.start(
                "minecraft:mineshaft", 1, 1,
                -1, -20, 3, 45, 60, 44);

        assertTrue(complete.metadataEligible(selection, Set.of()));
        assertFalse(complete.metadataEligible(selection, Set.of(new RegenChunkPos(2, 1))));
        assertFalse(clipped.metadataEligible(selection, Set.of()));
    }

    @Test
    void arbitrarySelectionCannotClaimCuboidStructureContainment() {
        RegenSelection selection = new BoxSelection(0, -64, 0, 47, 200, 47, false);
        RegenStructureDescriptor structure = RegenStructureDescriptor.start(
                "minecraft:ancient_city", 1, 1,
                2, -20, 3, 45, 60, 44);

        assertFalse(structure.metadataEligible(selection, Set.of()));
    }

    @Test
    void confirmedManifestFiltersEveryChunkReference() {
        RegenStructureReference selected = new RegenStructureReference(
                "minecraft:swamp_hut@2,3", "minecraft:swamp_hut", 42L);
        RegenStructureReference rejected = new RegenStructureReference(
                "minecraft:ocean_monument@8,9", "minecraft:ocean_monument", 99L);
        RegenStructureCapture capture = new RegenStructureCapture(
                Set.of(), Map.of(), Set.of(selected, rejected));

        RegenStructureChanges changes = RegenStructureChanges.select(
                capture, Set.of(selected.identity()));

        assertEquals(List.of(selected), changes.references());
        assertTrue(changes.starts().isEmpty());
    }

    private record BoxSelection(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            boolean provesCuboids
    ) implements RegenSelection {

        @Override
        public World world() {
            return null;
        }

        @Override
        public long volume() {
            return (long) (maxX - minX + 1)
                    * (maxY - minY + 1)
                    * (maxZ - minZ + 1);
        }

        @Override
        public int worldMinHeight() {
            return -64;
        }

        @Override
        public int worldMaxHeight() {
            return 320;
        }

        @Override
        public List<RegenChunkPos> chunks() {
            return List.of();
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        @Override
        public boolean fullyContainsBox(
                int minimumX,
                int minimumY,
                int minimumZ,
                int maximumX,
                int maximumY,
                int maximumZ
        ) {
            return provesCuboids
                    && contains(minimumX, minimumY, minimumZ)
                    && contains(maximumX, maximumY, maximumZ);
        }
    }
}
