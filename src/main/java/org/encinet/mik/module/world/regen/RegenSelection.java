package org.encinet.mik.module.world.regen;

import org.bukkit.World;

import java.util.List;

/** Immutable view of a WorldEdit selection used by the regeneration pipeline. */
public interface RegenSelection {

    World world();

    long volume();

    int minX();

    int minY();

    int minZ();

    int maxX();

    int maxY();

    int maxZ();

    int worldMinHeight();

    int worldMaxHeight();

    List<RegenChunkPos> chunks();

    boolean contains(int x, int y, int z);

    /**
     * Returns true only when the selection can prove that an entire cuboid is inside it.
     * Arbitrary region shapes should conservatively return false.
     */
    default boolean fullyContainsBox(
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ
    ) {
        return false;
    }
}
