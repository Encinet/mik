package org.encinet.mik.module.world.regen;

/** Identifies the operation family and whether a task builds a preview or mutates the world. */
public enum RegenTaskKind {
    REGENERATE_PREVIEW(false, true),
    REGENERATE_APPLY(false, false),
    UPGRADE_PREVIEW(true, true),
    UPGRADE_APPLY(true, false);

    private final boolean upgrade;
    private final boolean preview;

    RegenTaskKind(boolean upgrade, boolean preview) {
        this.upgrade = upgrade;
        this.preview = preview;
    }

    public boolean upgrade() {
        return upgrade;
    }

    public boolean preview() {
        return preview;
    }
}
