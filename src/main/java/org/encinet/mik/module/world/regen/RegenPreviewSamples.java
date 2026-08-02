package org.encinet.mik.module.world.regen;

import java.util.List;

/** Bounded spatial samples from an upgrade preview; these are hints, not the complete change set. */
public record RegenPreviewSamples(
        List<RegenPreviewPoint> changedBlocks,
        List<RegenPreviewPoint> atRiskBlockEntities
) {

    public static final RegenPreviewSamples EMPTY = new RegenPreviewSamples(List.of(), List.of());

    public RegenPreviewSamples {
        changedBlocks = List.copyOf(changedBlocks);
        atRiskBlockEntities = List.copyOf(atRiskBlockEntities);
    }
}
