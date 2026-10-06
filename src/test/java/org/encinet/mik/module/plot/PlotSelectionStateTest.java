package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotSelectionStateTest {
    @Test
    void editorIntentAndTargetMustMatch() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlotEditorContext(null, PlotEditorContext.Intent.CREATE_SUBPLOT));
        assertThrows(IllegalArgumentException.class,
                () -> new PlotEditorContext(UUID.randomUUID(), PlotEditorContext.Intent.CREATE_PLOT));
        assertEquals(PlotPermission.MANAGE_AREA, PlotEditorContext.area(UUID.randomUUID()).permission());
        assertEquals(PlotPermission.CREATE_SUBPLOT, PlotEditorContext.subPlot(UUID.randomUUID()).permission());
    }

    @Test
    void childCreationAndParentEditingHaveIndependentDraftsAndHistory() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        PlotSelectionState.Submission parent = selections.submission(playerId, world);
        PlotEditorContext childContext = PlotEditorContext.subPlot(firstPlot);
        selections.bindContext(playerId, childContext, world);
        selections.initialize(playerId, world, java.util.Set.of(new PlotGeometry.Cell(0, 16, 0)));
        assertNull(selections.first(playerId));
        assertNull(selections.draft(playerId, firstPlot).base().candidate());
        assertTrue(selections.draft(playerId, firstPlot).history().isEmpty());
        selections.set(playerId, area(20));
        selections.resume(playerId, firstPlot, world);
        assertEquals(childContext, selections.context(playerId));
        assertEquals(area(20), selections.selection(playerId, world));
        assertTrue(selections.undo(playerId));
        assertNull(selections.first(playerId));
        assertTrue(selections.redo(playerId));
        selections.bind(playerId, firstPlot, world);
        assertEquals(parent, selections.submission(playerId, world));
        selections.bindContext(playerId, childContext, world);
        assertEquals(area(20), selections.selection(playerId, world));
    }

    @Test
    void childCreationDoesNotConsumeUnboundCommandPoints() {
        selections.set(playerId, area(20));
        selections.bindContext(playerId, PlotEditorContext.subPlot(firstPlot), world);
        assertNull(selections.first(playerId));
        selections.bind(playerId, firstPlot, world);
        assertEquals(area(20), selections.selection(playerId, world));
    }

    @Test
    void staleSubmissionCannotCrossIntentWorldOrDraftRevision() {
        PlotEditorContext childContext = PlotEditorContext.subPlot(firstPlot);
        selections.bindContext(playerId, childContext, world);
        selections.set(playerId, area(0));
        PlotSelectionState.Submission child = selections.submission(playerId, world);
        selections.requireSubmission(playerId, world, child);
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        assertEquals(Message.PLOT_ERROR_SELECTION_CHANGED, assertThrows(PlotProblem.class,
                () -> selections.requireSubmission(playerId, world, child)).message());
        selections.bindContext(playerId, childContext, world);
        selections.requireSubmission(playerId, world, child);
        assertThrows(PlotProblem.class, () -> selections.requireSubmission(playerId, UUID.randomUUID(), child));
        selections.set(playerId, area(20));
        assertEquals(Message.PLOT_ERROR_SELECTION_CHANGED, assertThrows(PlotProblem.class,
                () -> selections.requireSubmission(playerId, world, child)).message());
    }

    @Test
    void childCompletionClearsOnlyItsCapturedDraftEvenWhenParentEditorIsActive() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        PlotSelectionState.Submission parent = selections.submission(playerId, world);
        PlotEditorContext childContext = PlotEditorContext.subPlot(firstPlot);
        selections.bindContext(playerId, childContext, world);
        selections.set(playerId, area(20));
        PlotSelectionState.Submission child = selections.submission(playerId, world);
        selections.bind(playerId, firstPlot, world);
        assertTrue(selections.clearIfMatches(playerId, firstPlot, child));
        assertEquals(parent, selections.submission(playerId, world));
        assertNull(selections.draftFor(playerId, childContext).base().candidate());
    }

    @Test
    void deletingParentRemovesBothEditorIntents() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        PlotEditorContext childContext = PlotEditorContext.subPlot(firstPlot);
        selections.bindContext(playerId, childContext, world);
        selections.set(playerId, area(20));
        selections.removePlot(firstPlot);
        assertNull(selections.currentPlot(playerId));
        assertNull(selections.draftFor(playerId, PlotEditorContext.area(firstPlot)).base().candidate());
        assertNull(selections.draftFor(playerId, childContext).base().candidate());
    }

    @Test
    void initialSavedRangeDoesNotCreateAnEmptyUndoStepOrReplaceAnExistingDraft() {
        var state = new PlotSelectionState();
        UUID player = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID level = UUID.randomUUID();
        state.bind(player, project, level);
        state.initialize(player, level, java.util.Set.of(new PlotGeometry.Cell(0, 0, 0)));
        assertTrue(state.draft(player, project).history().isEmpty());
        state.mark(player, true, new PlotPosition(level, 10, 10, 10));
        state.initialize(player, level, java.util.Set.of(new PlotGeometry.Cell(1, 1, 1)));
        assertEquals(10, state.first(player).x());
        assertTrue(state.undo(player));
        assertEquals(java.util.Set.of(new PlotGeometry.Cell(0, 0, 0)), state.submission(player, level).shape().alignedCells());
    }
    private final PlotSelectionState selections = new PlotSelectionState();
    private final UUID playerId = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final UUID firstPlot = UUID.randomUUID();
    private final UUID secondPlot = UUID.randomUUID();

    @Test
    void keepsSeparateDraftsWhenSwitchingBetweenPlots() {
        selections.bind(playerId, firstPlot, world);
        PlotSelection first = area(0);
        selections.set(playerId, first);
        selections.bind(playerId, secondPlot, world);
        assertNull(selections.first(playerId));
        assertNull(selections.second(playerId));
        PlotSelection second = area(20);
        selections.set(playerId, second);
        selections.bind(playerId, firstPlot, world);
        assertEquals(first, selections.selection(playerId, world));
        selections.bind(playerId, secondPlot, world);
        assertEquals(second, selections.selection(playerId, world));
    }

    @Test
    void adoptsUnboundCommandPointsOnlyForTheFirstTarget() {
        selections.set(playerId, area(0));
        selections.bind(playerId, firstPlot, world);
        assertEquals(area(0), selections.selection(playerId, world));
        selections.bind(playerId, secondPlot, world);
        assertThrows(PlotProblem.class, () -> selections.selection(playerId, world));
    }

    @Test
    void retainsNewPointsWhenAnOlderConfirmationFinishes() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        PlotSelection submitted = selections.selection(playerId, world);
        selections.set(playerId, area(20));
        selections.clearIfMatches(playerId, firstPlot, submitted);
        assertEquals(area(20), selections.selection(playerId, world));
        assertEquals(area(0), submitted);
    }

    @Test
    void clearsOnlyTheSubmittedProjectEvenAfterSwitchingTargets() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        selections.bind(playerId, secondPlot, world);
        selections.set(playerId, area(20));
        selections.clearIfMatches(playerId, firstPlot, area(0));
        assertEquals(area(20), selections.selection(playerId, world));
        selections.bind(playerId, firstPlot, world);
        assertNull(selections.first(playerId));
    }

    @Test
    void rejectsCrossWorldPointsAndDiscardUnboundPointsFromOtherWorlds() {
        selections.mark(playerId, true, area(0).first());
        UUID otherWorld = UUID.randomUUID();
        PlotPosition second = new PlotPosition(otherWorld, 1, 2, 3);
        selections.mark(playerId, false, second);
        assertNull(selections.first(playerId));
        assertEquals(second, selections.second(playerId));
        selections.bind(playerId, firstPlot, world);
        assertNull(selections.second(playerId));
        assertThrows(PlotProblem.class, () -> selections.selection(playerId, world));
    }

    @Test
    void clearingOrDeletingAProjectDoesNotEraseOtherDrafts() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        selections.bind(playerId, secondPlot, world);
        selections.set(playerId, area(20));
        selections.clear(playerId);
        assertNull(selections.first(playerId));
        selections.set(playerId, area(20));
        selections.removePlot(firstPlot);
        assertEquals(area(20), selections.selection(playerId, world));
        assertNull(selections.points(playerId, firstPlot).first());
    }

    @Test
    void boundsTheDraftCacheAndKeepsRecentlyUsedProjects() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        for (int index = 0; index < 7; index++) selections.bind(playerId, UUID.randomUUID(), world);
        selections.bind(playerId, firstPlot, world);
        selections.bind(playerId, UUID.randomUUID(), world);
        assertEquals(area(0).first(), selections.points(playerId, firstPlot).first());
        for (int index = 0; index < 8; index++) selections.bind(playerId, UUID.randomUUID(), world);
        assertNull(selections.points(playerId, firstPlot).first());
    }

    @Test
    void isolatesPlayersAndReleasesAllStateOnQuitAndShutdown() {
        UUID otherPlayer = UUID.randomUUID();
        selections.bind(playerId, firstPlot, world);
        selections.bind(otherPlayer, firstPlot, world);
        selections.set(playerId, area(0));
        selections.set(otherPlayer, area(20));
        selections.forget(playerId);
        assertNull(selections.first(playerId));
        assertEquals(area(20), selections.selection(otherPlayer, world));
        selections.clearAll();
        assertNull(selections.first(otherPlayer));
    }

    @Test
    void unboundCommandsInAnotherWorldDoNotOverwriteThePreviousProjectDraft() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        selections.unbind(playerId);
        assertNull(selections.currentPlot(playerId));
        UUID otherWorld = UUID.randomUUID();
        PlotPosition point = new PlotPosition(otherWorld, 8, 70, 4);
        selections.mark(playerId, true, point);
        assertEquals(area(0).first(), selections.points(playerId, firstPlot).first());
        selections.bind(playerId, secondPlot, otherWorld);
        assertEquals(point, selections.first(playerId));
        selections.bind(playerId, firstPlot, world);
        assertEquals(area(0), selections.selection(playerId, world));
    }

    @Test
    void unboundDraftsAlsoRespectTheCacheLimit() {
        selections.bind(playerId, firstPlot, world);
        selections.set(playerId, area(0));
        for (int index = 0; index < 7; index++) selections.bind(playerId, UUID.randomUUID(), world);
        selections.unbind(playerId);
        selections.set(playerId, area(20));
        assertNull(selections.points(playerId, firstPlot).first());
        assertEquals(area(20), selections.selection(playerId, world));
    }

    private PlotSelection area(int offset) {
        return new PlotSelection(new PlotPosition(world, offset, 64, 0),
                new PlotPosition(world, offset + 3, 67, 3));
    }
}
