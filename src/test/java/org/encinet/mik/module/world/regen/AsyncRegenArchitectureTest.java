package org.encinet.mik.module.world.regen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncRegenArchitectureTest {

    private static final Path ROOT = Path.of(
            "src/main/java/org/encinet/mik/module/world/regen");

    @Test
    void worldEditOnlyProvidesTheSelection() throws IOException {
        String provider = source("WorldEditSelectionProvider.java");
        String service = source("AsyncRegenService.java");

        assertTrue(provider.contains("getSelection(selectionWorld)"));
        assertFalse(provider.contains("regenerate("));
        assertFalse(service.contains("com.sk89q.worldedit"));
    }

    @Test
    void filterAcceptsOnlyCurrentBlockMaterials() throws IOException {
        String filter = source("RegenBlockFilter.java");
        String planner = source("RegenChunkPlanner.java");
        String service = source("AsyncRegenService.java");

        assertTrue(filter.contains("!material.isBlock()"));
        assertTrue(planner.contains("filter.includes(currentSnapshot.getBlockType"));
        assertTrue(service.contains("task.plan.blockFilter().includes(block.getType())"));
    }

    @Test
    void generationAndComparisonNeverUseSynchronousChunkLoading() throws IOException {
        String service = source("AsyncRegenService.java");
        String planner = source("RegenChunkPlanner.java");

        assertTrue(service.contains("getChunkAtAsync("));
        assertFalse(service.contains("getChunkAt("));
        assertTrue(service.contains("getChunkAtAsync(position.x(), position.z(), source, false)"));
        assertFalse(service.contains("isChunkGenerated("));
        assertTrue(service.contains("RegenChunkChanges.ungenerated("));
        assertTrue(service.contains("APPLY_BUDGET_NANOS"));
        assertTrue(service.contains("thenApplyAsync("));
        assertFalse(planner.contains("getBlockAt("));
        assertFalse(planner.contains("setBlockData("));
    }

    @Test
    void liveWorldMutationIsIsolatedFromTheComparisonExecutor() throws IOException {
        String service = source("AsyncRegenService.java");

        int asynchronousComparison = service.indexOf("thenApplyAsync(");
        int liveMutation = service.indexOf("private void applyBlock(");
        assertTrue(asynchronousComparison >= 0);
        assertTrue(liveMutation > asynchronousComparison);
        assertTrue(service.contains("block.setBlockData(generatedData, false)"));
        assertTrue(service.contains("runTaskTimer(plugin, this::tick"));
    }

    @Test
    void everyPreviewIsSeparatedFromApplicationAndCannotEnterTheMutationQueue() throws IOException {
        String service = source("AsyncRegenService.java");
        String module = source("AsyncRegenModule.java");

        int previewGuard = service.indexOf("if (task.plan.preview())");
        int mutationQueue = service.indexOf("task.pendingChanges.addLast(changes)");
        assertTrue(previewGuard >= 0 && previewGuard < mutationQueue);
        assertTrue(module.contains("new PreviewRequest(selection, operation)"));
        assertTrue(module.contains("service.previewRegeneration("));
        assertTrue(module.contains("service.applyRegeneration("));
        assertTrue(module.contains("Commands.literal(\"preview\")"));
        assertTrue(module.contains("Commands.literal(\"apply\")"));
        assertTrue(module.contains(".executes(context -> usage(context.getSource()))"));
        assertFalse(module.contains("Commands.literal(\"confirm\")"));
        assertFalse(module.contains("Commands.literal(\"visualize\")"));
        assertTrue(module.contains("PLAN_CONFIRMATION_TTL"));
        assertFalse(module.contains("service.submit("));
        assertTrue(service.contains("source || task.plan.preview() || task.plan.kind().upgrade()"));
    }

    @Test
    void biomeUpgradeUsesSnapshotsAndMainThreadApplication() throws IOException {
        String service = source("AsyncRegenService.java");
        String planner = source("RegenChunkPlanner.java");

        assertTrue(service.contains("getChunkSnapshot(false, task.plan.biomes()"));
        assertTrue(planner.contains("coverage(selection, cellX, cellY, cellZ)"));
        assertTrue(planner.contains("!coverage.fullyInsideSelection()"));
        assertTrue(service.contains("task.target.setBiome(x, y, z, generatedBiome)"));
        assertTrue(service.contains("task.target.refreshChunk("));
    }

    @Test
    void structureReportingNeverRelaxesTheSelectionBoundary() throws IOException {
        String descriptor = source("RegenStructureDescriptor.java");
        String provider = source("WorldEditSelectionProvider.java");
        String service = source("AsyncRegenService.java");
        String module = source("AsyncRegenModule.java");

        assertTrue(descriptor.contains("selection.fullyContainsBox("));
        assertTrue(provider.contains("region instanceof CuboidRegion"));
        assertTrue(provider.contains("return cuboid"));
        assertFalse(service.contains("chunk.getStructures()"));
        assertTrue(service.contains("handle.getAllReferences()"));
        assertTrue(service.contains("handle.getAllStarts()"));
        assertTrue(service.contains("setStartForStructure("));
        assertTrue(service.contains("addReferenceForStructure("));
        assertTrue(service.contains("onStructureStartsAvailable("));
        assertTrue(module.contains("pending.preview().metadataEligibleStructures()"));
    }

    @Test
    void previewVisualizationIsPrivateBoundedAndBoundToTheStoredSelection() throws IOException {
        String visualizer = source("RegenSelectionVisualizer.java");
        String planner = source("RegenChunkPlanner.java");
        String module = source("AsyncRegenModule.java");

        assertTrue(visualizer.contains("player.spawnParticle("));
        assertFalse(visualizer.contains("world.spawnParticle("));
        assertFalse(visualizer.contains("spawnEntity("));
        assertTrue(visualizer.contains("MAX_DISTANCE_SQUARED"));
        assertTrue(planner.contains("CHANGED_BLOCK_SAMPLES_PER_CHUNK"));
        assertTrue(module.contains("pending.selection(), pending.preview().previewSamples()"));
        assertTrue(module.contains("visualizer.hide(snapshot.owner())"));
    }

    private static String source(String name) throws IOException {
        return Files.readString(ROOT.resolve(name));
    }
}
