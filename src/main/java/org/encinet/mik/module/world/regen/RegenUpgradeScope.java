package org.encinet.mik.module.world.regen;

/** The data copied from the current-version generator during a world upgrade. */
public enum RegenUpgradeScope {
    TERRAIN_AND_BIOMES(true),
    BIOMES_ONLY(false);

    private final boolean blocks;

    RegenUpgradeScope(boolean blocks) {
        this.blocks = blocks;
    }

    public boolean blocks() {
        return blocks;
    }
}
