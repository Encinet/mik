package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotCompositeSelectionStateTest {
    private final PlotSelectionState selections = new PlotSelectionState();
    private final UUID actor = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final UUID plotId = UUID.randomUUID();

    @Test
    void fitsExactCoreRatherThanFillingItsEnvelope() {
        selections.bind(actor, plotId, world);
        Set<Cell> core = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0), new Cell(0, 16, 1));
        selections.fit(actor, world, core);
        assertEquals(core, selections.submission(actor, world).shape().alignedCells());
        assertNull(selections.first(actor));
        assertNull(selections.second(actor));
    }

    @Test
    void twoNewCornersReplaceLoadedShapeWithoutMutatingTheDraftBeforeSaving() {
        selections.bind(actor, plotId, world);
        Set<Cell> original = Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0));
        selections.fit(actor, world, original);
        selections.mark(actor, true, new PlotPosition(world, 8, 64, 0));
        assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                () -> selections.submission(actor, plotId, world)).message());
        selections.mark(actor, false, new PlotPosition(world, 11, 67, 3));
        PlotSelectionState.Draft before = selections.draft(actor, plotId);
        PlotSelectionState.Submission submitted = selections.submission(actor, plotId, world);
        assertEquals(Set.of(new Cell(2, 16, 0)), submitted.shape().alignedCells());
        assertFalse(submitted.shape().composite());
        assertEquals(before, selections.draft(actor, plotId));
        assertEquals(original, selections.draft(actor, plotId).base().shape().alignedCells());
        assertEquals(submitted, selections.submission(actor, plotId, world));
        selections.mark(actor, true, new PlotPosition(world, 12, 64, 0));
        selections.clearIfMatches(actor, plotId, submitted);
        assertEquals(original, selections.draft(actor, plotId).base().shape().alignedCells());
    }

    @Test
    void incompletePointsCannotSubmitAndAddingMergesWithTheExistingShape() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        selections.mark(actor, true, point(4));
        assertEquals(Message.PLOT_ERROR_SELECTION, assertThrows(PlotProblem.class,
                () -> selections.submission(actor, world)).message());
        selections.mark(actor, false, point(7));
        selections.apply(actor, world, PlotSelectionShape.Operation.ADD);
        assertEquals(Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)), selections.submission(actor, world).shape().alignedCells());
        assertNull(selections.first(actor));
        selections.mark(actor, true, point(8));
        selections.discardBrush(actor);
        assertEquals(2, selections.submission(actor, world).shape().alignedCells().size());
    }

    @Test
    void undoRestoresBothBrushAndCompositeAndDoesNotMutateCapturedSubmission() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)));
        PlotSelectionState.Submission captured = selections.submission(actor, world);
        selections.mark(actor, true, point(4));
        selections.mark(actor, false, point(7));
        selections.apply(actor, world, PlotSelectionShape.Operation.SUBTRACT);
        assertEquals(1, selections.submission(actor, world).shape().alignedCells().size());
        assertTrue(selections.undo(actor));
        assertEquals(point(4), selections.first(actor));
        assertEquals(point(7), selections.second(actor));
        selections.discardBrush(actor);
        assertEquals(captured.shape(), selections.submission(actor, world).shape());
        selections.clearIfMatches(actor, plotId, captured);
        assertEquals(captured.shape(), selections.submission(actor, world).shape());
    }

    @Test
    void submissionsAndHistoryRemainScopedToTheirProject() {
        UUID otherPlot = UUID.randomUUID();
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        PlotSelectionState.Submission first = selections.submission(actor, world);
        selections.bind(actor, otherPlot, world);
        selections.fit(actor, world, Set.of(new Cell(2, 16, 0)));
        assertEquals(first, selections.submission(actor, plotId, world));
        selections.clearIfMatches(actor, plotId, first);
        assertEquals(Set.of(new Cell(2, 16, 0)), selections.submission(actor, world).shape().alignedCells());
        selections.bind(actor, plotId, world);
        assertThrows(PlotProblem.class, () -> selections.submission(actor, world));
        assertFalse(selections.undo(actor));
    }

    @Test
    void emptySubtractionCannotBeSubmittedButCanBeUndone() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        selections.mark(actor, true, point(0));
        selections.mark(actor, false, point(3));
        selections.apply(actor, world, PlotSelectionShape.Operation.SUBTRACT);
        assertThrows(PlotProblem.class, () -> selections.submission(actor, world));
        selections.undo(actor);
        selections.discardBrush(actor);
        assertEquals(1, selections.submission(actor, world).shape().alignedCells().size());
    }

    @Test
    void largeBrushCanBeAppliedUndoneAndRedoneWithoutASizeQuota() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        selections.mark(actor, true, new PlotPosition(world, 0, 64, 0));
        selections.mark(actor, false, new PlotPosition(world, 20000, 64, 0));
        PlotSelectionState.Draft before = selections.draft(actor, plotId);
        selections.apply(actor, world, PlotSelectionShape.Operation.ADD);
        assertEquals(5001, selections.submission(actor, world).shape().alignedCells().size());
        assertTrue(selections.undo(actor));
        assertEquals(before.base(), selections.draft(actor, plotId).base());
        assertEquals(before.history(), selections.draft(actor, plotId).history());
        assertTrue(selections.redo(actor));
        assertEquals(5001, selections.submission(actor, world).shape().alignedCells().size());
    }

    @Test
    void crossWorldResetCannotUndoBackIntoAnotherWorldAndClearCanBeUndone() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        selections.clear(actor);
        selections.undo(actor);
        assertEquals(1, selections.submission(actor, world).shape().alignedCells().size());
        selections.mark(actor, true, new PlotPosition(UUID.randomUUID(), 0, 64, 0));
        assertFalse(selections.undo(actor));
        assertNull(selections.draft(actor, plotId).base().shape());
    }

    @Test
    void undoHistoryIsBounded() {
        selections.bind(actor, plotId, world);
        for (int index = 0; index < 40; index++) selections.mark(actor, true, point(index));
        assertEquals(16, selections.draft(actor, plotId).history().size());
        for (int index = 0; index < 16; index++) assertTrue(selections.undo(actor));
        assertFalse(selections.undo(actor));
    }

    @Test
    void redoRestoresTheWholeShapeAndBrushWithoutClearingNewerMatchingDrafts() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        PlotSelectionState.Submission before = selections.submission(actor, world);
        selections.mark(actor, true, point(4));
        selections.mark(actor, false, point(7));
        selections.apply(actor, world, PlotSelectionShape.Operation.ADD);
        PlotSelectionState.Submission applied = selections.submission(actor, world);
        selections.undo(actor);
        assertEquals(point(4), selections.first(actor));
        assertTrue(selections.redo(actor));
        assertEquals(applied.shape(), selections.submission(actor, world).shape());
        assertNull(selections.first(actor));
        selections.clearIfMatches(actor, plotId, applied);
        assertEquals(2, selections.submission(actor, world).shape().alignedCells().size());
        assertEquals(1, before.shape().alignedCells().size());
        assertFalse(selections.redo(actor));
    }

    @Test
    void newEditsDiscardRedoButRepeatedMarksDoNotCreateFalseHistory() {
        selections.bind(actor, plotId, world);
        selections.mark(actor, true, point(0));
        PlotSelectionState.Draft original = selections.draft(actor, plotId);
        selections.mark(actor, true, point(0));
        assertEquals(original, selections.draft(actor, plotId));
        selections.mark(actor, false, point(3));
        selections.undo(actor);
        PlotSelectionState.Draft undone = selections.draft(actor, plotId);
        selections.mark(actor, true, point(0));
        assertEquals(undone, selections.draft(actor, plotId));
        assertFalse(undone.future().isEmpty());
        selections.mark(actor, false, point(7));
        assertFalse(selections.redo(actor));
        assertTrue(selections.draft(actor, plotId).future().isEmpty());
    }

    @Test
    void redoRemainsScopedAndClearCanBeRedone() {
        selections.bind(actor, plotId, world);
        selections.fit(actor, world, Set.of(new Cell(0, 16, 0)));
        selections.clear(actor);
        selections.undo(actor);
        UUID other = UUID.randomUUID();
        selections.bind(actor, other, world);
        assertFalse(selections.redo(actor));
        selections.bind(actor, plotId, world);
        assertTrue(selections.redo(actor));
        assertThrows(PlotProblem.class, () -> selections.submission(actor, world));
        selections.undo(actor);
        assertEquals(1, selections.submission(actor, world).shape().alignedCells().size());
    }

    @Test
    void undoAndRedoTogetherKeepAtMostSixteenVersions() {
        selections.bind(actor, plotId, world);
        for (int index = 0; index < 40; index++) selections.mark(actor, true, point(index));
        for (int index = 0; index < 16; index++) assertTrue(selections.undo(actor));
        assertEquals(16, selections.draft(actor, plotId).future().size());
        for (int index = 0; index < 16; index++) assertTrue(selections.redo(actor));
        assertEquals(16, selections.draft(actor, plotId).history().size());
        assertTrue(selections.draft(actor, plotId).future().isEmpty());
        assertEquals(point(39), selections.first(actor));
    }

    private PlotPosition point(int blockX) {
        return new PlotPosition(world, blockX, 64, 0);
    }
}
