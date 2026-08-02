package org.encinet.mik.module.world.regen;

/** Packs a block position local to a chunk while retaining the world's minimum height. */
final class PackedBlockPosition {

    private static final int X_MASK = 0xF;
    private static final int Z_MASK = 0xF;

    private PackedBlockPosition() {
    }

    static int pack(int localX, int y, int localZ, int minHeight) {
        if ((localX & ~X_MASK) != 0 || (localZ & ~Z_MASK) != 0 || y < minHeight) {
            throw new IllegalArgumentException("Position is outside the chunk snapshot");
        }
        return ((y - minHeight) << 8) | (localZ << 4) | localX;
    }

    static int localX(int packed) {
        return packed & X_MASK;
    }

    static int localZ(int packed) {
        return (packed >>> 4) & Z_MASK;
    }

    static int y(int packed, int minHeight) {
        return (packed >>> 8) + minHeight;
    }
}
