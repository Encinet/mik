package org.encinet.mik.module.world.regen;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RegenTaskSnapshot(
        UUID owner,
        RegenTaskKind kind,
        RegenUpgradeScope upgradeScope,
        List<String> blockFilterIds,
        RegenTaskState state,
        int plannedChunks,
        int appliedChunks,
        int totalChunks,
        long discoveredChanges,
        long appliedChanges,
        long discoveredBiomeChanges,
        long appliedBiomeChanges,
        long discoveredStructureStarts,
        long appliedStructureStarts,
        long discoveredStructureReferences,
        long appliedStructureReferences,
        long skippedBoundaryBiomeCells,
        int skippedUngeneratedChunks,
        long atRiskBlockEntities,
        int structures,
        int boundaryStructures,
        Set<String> metadataEligibleStructures,
        RegenPreviewSamples previewSamples,
        Duration elapsed
) {

    public RegenTaskSnapshot {
        blockFilterIds = List.copyOf(blockFilterIds);
        metadataEligibleStructures = Set.copyOf(metadataEligibleStructures);
        previewSamples = new RegenPreviewSamples(
                previewSamples.changedBlocks(), previewSamples.atRiskBlockEntities());
    }
}
