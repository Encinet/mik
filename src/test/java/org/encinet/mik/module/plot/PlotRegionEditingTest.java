package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotRegionEditingTest {
    @TempDir Path directory;
    private final UUID actor = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final UUID plotId = UUID.randomUUID();
    private final Set<Cell> original = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 1));
    private final PlotSelectionState state = new PlotSelectionState();

    private PlotSelectionState.Draft initialize() {
        state.bind(actor, plotId, world);
        state.fit(actor, world, original);
        return state.draft(actor, plotId);
    }

    @Test
    void liveAdjustmentOnlyReplacesTheTargetAndCachesItsCandidate() {
        var before = initialize();
        var target = PlotSelectionRegions.decompose(before.base().candidate()).get(1);
        state.beginRegionEdit(actor, world, target, before.revision());
        state.mark(actor, true, new PlotPosition(world, 0, 64, 8));
        state.mark(actor, false, new PlotPosition(world, 3, 67, 11));
        var editing = state.draft(actor, plotId);
        var proposed = editing.base().candidate();
        assertEquals(Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 2)), proposed.alignedCells());
        assertSame(proposed, editing.base().candidate());
        assertEquals(original, before.base().candidate().alignedCells());
        assertEquals(Message.PLOT_REGION_FINISH_FIRST, assertThrows(PlotProblem.class,
                () -> state.submission(actor, world)).message());
        state.finishRegionEdit(actor);
        assertEquals(proposed, state.submission(actor, world).shape());
        assertNull(state.draft(actor, plotId).base().edit());
        assertTrue(state.undo(actor));
        assertEquals(editing.base(), state.draft(actor, plotId).base());
        assertTrue(state.redo(actor));
        assertEquals(proposed, state.submission(actor, world).shape());
    }

    @Test
    void cancelRestoresTheExactOriginalDraftIncludingItsTwoPoints() {
        state.bind(actor, plotId, world);
        state.set(actor, new PlotSelection(new PlotPosition(world, 1, 65, 1), new PlotPosition(world, 7, 67, 3)));
        var before = state.draft(actor, plotId);
        var target = PlotSelectionRegions.decompose(before.base().candidate()).getFirst();
        state.beginRegionEdit(actor, world, target, before.revision());
        state.set(actor, new PlotSelection(new PlotPosition(world, 12, 64, 0), new PlotPosition(world, 15, 67, 3)));
        assertEquals(Set.of(new Cell(3, 16, 0)), state.draft(actor, plotId).base().candidate().alignedCells());
        state.cancelRegionEdit(actor);
        assertEquals(before.base(), state.draft(actor, plotId).base());
        assertTrue(state.undo(actor));
        assertTrue(state.draft(actor, plotId).base().edit() != null);
    }

    @Test
    void incompleteAndRepeatedMarksDoNotEraseTheOriginalOrConsumeFalseHistory() {
        var before = initialize();
        state.beginRegionEdit(actor, world, PlotSelectionRegions.decompose(before.base().candidate()).getFirst(), before.revision());
        state.discardBrush(actor);
        assertNull(state.draft(actor, plotId).base().candidate());
        assertThrows(PlotProblem.class, () -> state.finishRegionEdit(actor));
        assertEquals(original, state.draft(actor, plotId).base().shape().alignedCells());
        assertThrows(PlotProblem.class, () -> state.apply(actor, world, PlotSelectionShape.Operation.ADD));
        assertThrows(PlotProblem.class, () -> state.fit(actor, world, Set.of()));
        assertThrows(PlotProblem.class, () -> state.clear(actor));
        state.mark(actor, true, new PlotPosition(world, 0, 64, 0));
        var once = state.draft(actor, plotId);
        state.mark(actor, true, new PlotPosition(world, 0, 64, 0));
        assertSame(once, state.draft(actor, plotId));
        state.cancelRegionEdit(actor);
        assertEquals(before.base(), state.draft(actor, plotId).base());
    }

    @Test
    void deletionIsUndoableAndStaleOrForeignTargetsCannotTouchTheDraft() {
        var before = initialize();
        var target = PlotSelectionRegions.decompose(before.base().candidate()).get(1);
        state.deleteRegion(actor, world, target, before.revision());
        assertEquals(2, state.submission(actor, world).shape().alignedCells().size());
        assertEquals(Message.PLOT_REGION_CHANGED, assertThrows(PlotProblem.class,
                () -> state.deleteRegion(actor, world, target, before.revision())).message());
        assertTrue(state.undo(actor));
        assertEquals(original, state.submission(actor, world).shape().alignedCells());
        var restored = state.draft(actor, plotId);
        assertEquals(Message.PLOT_REGION_CHANGED, assertThrows(PlotProblem.class,
                () -> state.requireRegion(actor, UUID.randomUUID(), target, restored.revision())).message());
        assertEquals(Message.PLOT_REGION_CHANGED, assertThrows(PlotProblem.class,
                () -> state.requireRegion(actor, world, new PlotSelectionRegions.Region(
                        new PlotSelection.Bounds(100, 64, 100, 103, 67, 103)), restored.revision())).message());
        UUID other = UUID.randomUUID();
        state.bind(actor, other, world);
        assertThrows(PlotProblem.class, () -> state.deleteRegion(actor, world, target, restored.revision()));
        state.bind(actor, plotId, world);
        assertEquals(original, state.submission(actor, world).shape().alignedCells());
    }

    @Test
    void deletingTheOnlyRectangleLeavesAnEmptyDraftThatCanBeUndone() {
        state.bind(actor, plotId, world);
        state.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        var before = state.draft(actor, plotId);
        state.deleteRegion(actor, world, PlotSelectionRegions.decompose(before.base().candidate()).getFirst(), before.revision());
        assertTrue(state.draft(actor, plotId).base().candidate().empty());
        assertThrows(PlotProblem.class, () -> state.submission(actor, world));
        assertTrue(state.undo(actor));
        assertEquals(before.base(), state.draft(actor, plotId).base());
    }

    @Test
    void deletingARectangleDoesNotChangeOwnershipUntilTheWholeDraftIsSaved() throws Exception {
        try (PlotRepository repository = new PlotRepository(directory.resolve("rectangles.db"))) {
            repository.open();
            PlotRegistry registry = new PlotRegistry(repository);
            Plot saved = new Plot(plotId, world, actor, "Room", false, 0, original, Map.of(), Map.of(), null);
            registry.put(saved);
            var before = initialize();
            state.deleteRegion(actor, world, PlotSelectionRegions.decompose(before.base().candidate()).get(1), before.revision());
            assertEquals(original, registry.byId(plotId).cells());
            new PlotEdits(registry).resize(saved, actor, false, state.submission(actor, world).shape());
            assertEquals(2, registry.byId(plotId).cells().size());
            assertFalse(registry.byId(plotId).cells().contains(new Cell(0, 16, 1)));
        }
        try (PlotRepository repository = new PlotRepository(directory.resolve("rectangles.db"))) {
            repository.open();
            PlotRegistry registry = new PlotRegistry(repository);
            registry.load();
            assertEquals(2, registry.byId(plotId).cells().size());
        }
    }
}
