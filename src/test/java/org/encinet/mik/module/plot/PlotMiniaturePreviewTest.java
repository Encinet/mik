package org.encinet.mik.module.plot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMiniaturePreviewTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final UUID plotId = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[] {World.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUID" -> worldId;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                default -> throw new AssertionError(method.getName());
            });
    private Location position = new Location(world, 1.5, 65, 1.5);
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[] {Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "getWorld" -> world;
                case "getLocation" -> position.clone();
                default -> throw new AssertionError(method.getName());
            });
    private final PlotMiniaturePreview preview = new PlotMiniaturePreview(new PlotPreviewCache(Runnable::run));
    private final PlotSelectionState state = new PlotSelectionState();

    @Test
    void savedAndProposedIrregularShapesRetainTheActualHoleEdges() {
        Set<PlotGeometry.Cell> ring = Set.of(new PlotGeometry.Cell(0, 16, 0), new PlotGeometry.Cell(1, 16, 0),
                new PlotGeometry.Cell(2, 16, 0), new PlotGeometry.Cell(0, 16, 1), new PlotGeometry.Cell(2, 16, 1),
                new PlotGeometry.Cell(0, 16, 2), new PlotGeometry.Cell(1, 16, 2), new PlotGeometry.Cell(2, 16, 2));
        Plot plot = plot(ring);
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, ring);
        var scene = scene(plot);
        var expected = PlotPreviewGeometry.core(ring);
        assertTrue(expected.size() > 12);
        assertEquals(expected, outline(scene, "saved").edges());
        assertEquals(1, scene.outlines().size());
        assertFalse(scene.limited());
    }

    @Test
    void twoCornersShowOneAlignedBoundaryWithoutASecondRawBox() {
        state.bind(playerId, null, worldId);
        state.set(playerId, new PlotSelection(new PlotPosition(worldId, 3, 65, 3),
                new PlotPosition(worldId, 5, 66, 5)));
        var scene = scene(null);
        var candidate = outline(scene, "candidate").edges();
        assertEquals(1, scene.outlines().size());
        assertEquals(new PlotPosition(worldId, 3, 65, 3), scene.points().first());
        assertEquals(new PlotPosition(worldId, 5, 66, 5), scene.points().second());
        assertEquals(PlotPreviewGeometry.box(new PlotSelection.Bounds(0, 64, 0, 7, 67, 7)), candidate);
    }

    @Test
    void twoNewCornersBecomeTheSaveCandidateWhileOverviewStillShowsTheSavedPlot() {
        Set<PlotGeometry.Cell> saved = Set.of(new PlotGeometry.Cell(0, 16, 0));
        Plot plot = plot(saved);
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, saved);
        state.mark(playerId, true, new PlotPosition(worldId, 100, 65, 100));
        state.mark(playerId, false, new PlotPosition(worldId, 103, 67, 103));
        var scene = scene(plot);
        assertEquals(PlotPreviewGeometry.box(state.submission(playerId, plotId, worldId)
                .shape().alignedBounds()), outline(scene, "candidate").edges());
        assertEquals(PlotPreviewGeometry.core(saved), outline(scene, "saved").edges());
        assertTrue(scene.projection().contains(0, 64, 0));
        assertTrue(scene.projection().contains(104, 68, 104));
    }

    @Test
    void incompleteNewPointsDoNotDisplayTheOldShapeAsASaveCandidate() {
        Plot plot = plot(Set.of(new PlotGeometry.Cell(0, 16, 0)));
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, plot.cells());
        state.mark(playerId, true, new PlotPosition(worldId, 7, 65, 7));
        var scene = scene(plot);
        assertTrue(scene.outlines().stream().anyMatch(outline -> outline.id().equals("saved")));
        assertTrue(scene.outlines().stream().noneMatch(outline -> outline.id().equals("candidate")));
        assertEquals(new PlotPosition(worldId, 7, 65, 7), scene.points().first());
    }

    @Test
    void rotationRetainsSamplingAndSelectionHistoryWhileSliceAndFocusOnlyChangeTheView() {
        Plot plot = plot(Set.of(new PlotGeometry.Cell(0, 16, 0), new PlotGeometry.Cell(0, 30, 0)));
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, plot.cells());
        var before = scene(plot);
        long selectionRevision = state.draft(playerId, plotId).revision();
        preview.rotate(player, plotId, 45);
        var rotated = scene(plot);
        assertSame(before.snapshot(), rotated.snapshot());
        assertEquals(before.projection().bounds(), rotated.projection().bounds());
        assertEquals(45, rotated.projection().rotation());
        preview.slice(player, plotId);
        assertEquals(67, scene(plot).projection().bounds().maximumY());
        preview.focus(player, plotId);
        assertTrue(scene(plot).playerFocus());
        assertEquals(selectionRevision, state.draft(playerId, plotId).revision());
        assertTrue(state.draft(playerId, plotId).history().isEmpty());
    }

    @Test
    void playerMarkerUpdatesDoNotRestartTerrainSamplingAndNativeFormsDoNotRequestTerrain() {
        var before = scene(null);
        position = new Location(world, 2, 65, 2, 90, 0);
        var moved = scene(null);
        assertSame(before.snapshot(), moved.snapshot());
        assertFalse(before.stamp().equals(moved.stamp()));
        var nativeScene = preview.scene(player, null, state.draft(playerId, null), false);
        assertTrue(nativeScene.snapshot().ready());
        assertTrue(nativeScene.outlines().isEmpty());
    }

    @Test
    void topViewChangesOnlyProjectionAndNeverChangesTheSelectionOrResamplesTerrain() {
        var before = scene(null);
        long revision = state.draft(playerId, null).revision();
        preview.topDown(player, null);
        var top = scene(null);
        assertTrue(top.topDown());
        assertEquals(90, top.projection().tilt());
        assertEquals(before.projection().bounds(), top.projection().bounds());
        assertSame(before.snapshot(), top.snapshot());
        assertEquals(revision, state.draft(playerId, null).revision());
        preview.topDown(player, null);
        assertEquals(PlotMiniatureGeometry.TILT, scene(null).projection().tilt());
    }

    @Test
    void wireframeKeepsRealOutlinesAndStopsRequestingTerrain() {
        Plot plot = plot(Set.of(new PlotGeometry.Cell(0, 16, 0)));
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, plot.cells());
        var before = scene(plot);
        preview.terrain(player, plotId);
        var wireframe = scene(plot);
        assertFalse(wireframe.terrain());
        assertTrue(wireframe.snapshot().ready());
        assertTrue(wireframe.snapshot().terrain().isEmpty());
        assertEquals(before.outlines(), wireframe.outlines());
        preview.terrain(player, plotId);
        assertTrue(scene(plot).terrain());
        assertFalse(scene(plot).snapshot().ready());
    }

    @Test
    void layerControlsStartAtThePlayerAndStepTheActualCutPlaneWithoutChangingTheDraft() {
        long revision = state.draft(playerId, null).revision();
        preview.layer(player, null, -1);
        assertTrue(scene(null).sliced());
        assertEquals(66, scene(null).projection().bounds().maximumY());
        preview.layer(player, null, 1);
        assertEquals(67, scene(null).projection().bounds().maximumY());
        for (int count = 0; count < 40; count++) {
            preview.layer(player, null, -1);
            scene(null);
        }
        assertEquals(58, scene(null).projection().bounds().maximumY());
        preview.layer(player, null, 1);
        assertEquals(59, scene(null).projection().bounds().maximumY());
        assertEquals(revision, state.draft(playerId, null).revision());
        preview.slice(player, null);
        assertFalse(scene(null).sliced());
        assertEquals(73, scene(null).projection().bounds().maximumY());
    }

    @Test
    void emptyRectangleManagementHasNoSelectionAndClampsItsPage() {
        state.bind(playerId, null, worldId);
        preview.openRegions(player, null, true);
        preview.regionPage(player, null, 99);
        var empty = scene(null);
        assertTrue(empty.regions().open());
        assertTrue(empty.regions().items().isEmpty());
        assertNull(empty.regions().selected());
        assertEquals(0, empty.regions().selectedNumber());
        assertEquals(0, empty.regions().page().index());
        assertThrows(PlotProblem.class, () -> preview.selectRegion(player, null, null));
    }

    @Test
    void rectangleDecompositionIsReusedAcrossRotationAndPlayerMovement() {
        initializeRegions(3);
        preview.openRegions(player, null, true);
        var initial = scene(null);
        preview.rotate(player, null, 45);
        var rotated = scene(null);
        position = new Location(world, 3, 65, 3, 90, 0);
        var moved = scene(null);
        assertSame(initial.regions().items(), rotated.regions().items());
        assertSame(initial.regions().items(), moved.regions().items());
        assertSame(initial.snapshot(), moved.snapshot());
    }

    @Test
    void selectingARectangleOnlyChangesItsHighlightAndFocus() {
        initializeRegions(3);
        preview.openRegions(player, null, true);
        var initial = scene(null);
        var draft = state.draft(playerId, null);
        var selected = initial.regions().items().get(1);
        preview.selectRegion(player, null, selected);
        var focused = scene(null);
        assertEquals(selected, focused.regions().selected());
        assertEquals(2, focused.regions().selectedNumber());
        assertTrue(focused.playerFocus());
        assertEquals(selected.bounds().minimumX() - 2, focused.projection().bounds().minimumX());
        assertEquals(selected.bounds().maximumX() + 2, focused.projection().bounds().maximumX());
        assertSame(draft, state.draft(playerId, null));
        preview.focus(player, null);
        var overview = scene(null);
        assertFalse(overview.playerFocus());
        assertEquals(initial.projection().bounds(), overview.projection().bounds());
        assertEquals(selected, overview.regions().selected());
        assertTrue(overview.outlines().stream().allMatch(outline -> outline.budget() <= 96));
    }

    @Test
    void changingPagesReturnsToOverviewAndDoesNotLeaveAnOffPageSelection() {
        initializeRegions(14);
        preview.openRegions(player, null, true);
        var initial = scene(null);
        preview.selectRegion(player, null, initial.regions().items().getFirst());
        preview.regionPage(player, null, 99);
        var last = scene(null);
        assertEquals(2, last.regions().page().index());
        assertEquals(2, last.regions().page().slice(last.regions().items()).size());
        assertNull(last.regions().selected());
        assertFalse(last.playerFocus());
        assertEquals(initial.projection().bounds(), last.projection().bounds());
        preview.regionPage(player, null, -1);
        assertEquals(0, scene(null).regions().page().index());
        preview.regionPage(player, null, 99);
        state.clear(playerId);
        assertEquals(0, scene(null).regions().page().index());
    }

    @Test
    void deletingASelectedRectangleInvalidatesItsHighlight() {
        initializeRegions(3);
        preview.openRegions(player, null, true);
        var initial = scene(null);
        var target = initial.regions().items().get(1);
        preview.selectRegion(player, null, target);
        state.deleteRegion(playerId, worldId, target, state.draft(playerId, null).revision());
        var deleted = scene(null);
        assertEquals(2, deleted.regions().items().size());
        assertNull(deleted.regions().selected());
        assertEquals(0, deleted.regions().selectedNumber());
        assertFalse(deleted.playerFocus());
        assertThrows(PlotProblem.class, () -> preview.selectRegion(player, null, target));
    }

    @Test
    void rectangleAdjustmentTracksNewCornersWithoutLosingTheOriginalNumber() {
        initializeRegions(3);
        var initial = scene(null);
        var target = initial.regions().items().get(1);
        state.beginRegionEdit(playerId, worldId, target, state.draft(playerId, null).revision());
        state.set(playerId, new PlotSelection(new PlotPosition(worldId, 100, 64, 0),
                new PlotPosition(worldId, 107, 71, 7)));
        preview.forget(playerId);
        var recovered = scene(null);
        assertTrue(recovered.regions().open());
        assertTrue(recovered.regions().editing());
        assertEquals(2, recovered.regions().selectedNumber());
        assertEquals(new PlotSelection.Bounds(100, 64, 0, 107, 71, 7), recovered.regions().selected().bounds());
        assertEquals(98, recovered.projection().bounds().minimumX());
        assertEquals(109, recovered.projection().bounds().maximumX());
        state.cancelRegionEdit(playerId);
        var cancelled = scene(null);
        assertTrue(cancelled.regions().open());
        assertFalse(cancelled.regions().editing());
        assertEquals(target, cancelled.regions().selected());
        assertEquals(2, cancelled.regions().selectedNumber());
    }

    @Test
    void incompleteRectangleAdjustmentRetainsItsTargetHighlightUntilCancelled() {
        initializeRegions(3);
        var target = scene(null).regions().items().get(1);
        state.beginRegionEdit(playerId, worldId, target, state.draft(playerId, null).revision());
        state.discardBrush(playerId);
        var incomplete = scene(null);
        assertTrue(incomplete.regions().items().isEmpty());
        assertEquals(target, incomplete.regions().selected());
        assertEquals(2, incomplete.regions().selectedNumber());
        assertTrue(incomplete.regions().editing());
    }

    @Test
    void nativeRectangleManagementUsesTheSameNumbersWithoutSpatialDecorations() {
        initializeRegions(3);
        preview.openRegions(player, null, true);
        var nativeScene = preview.scene(player, null, state.draft(playerId, null), false);
        preview.selectRegion(player, null, nativeScene.regions().items().get(1));
        nativeScene = preview.scene(player, null, state.draft(playerId, null), false);
        assertEquals(2, nativeScene.regions().selectedNumber());
        assertTrue(nativeScene.snapshot().terrain().isEmpty());
        assertTrue(nativeScene.snapshot().unknownBoxes().isEmpty());
        assertEquals(0, nativeScene.snapshot().revision());
        var builder = org.encinet.mik.module.menu.FloatingMenuDefinition.builder();
        preview.decorate(builder, player, nativeScene);
        assertTrue(builder.build().decorations().isEmpty());
    }

    @Test
    void overviewIncludesEveryAlignedCandidateEdgeRatherThanCroppingToRawCorners() {
        for (int coordinate : new int[] {-5, -1, 3, 7}) {
            state.bind(playerId, null, worldId);
            state.set(playerId, new PlotSelection(new PlotPosition(worldId, coordinate, 65, coordinate),
                    new PlotPosition(worldId, coordinate + 2, 66, coordinate + 2)));
            var scene = scene(null);
            for (var edge : outline(scene, "candidate").edges()) {
                for (var point : new PlotPreviewGeometry.Point[] {edge.first(), edge.second()}) {
                    assertTrue(scene.projection().contains(point.horizontal(), point.vertical(), point.forward()),
                            "Candidate edge outside overview: " + point);
                }
            }
        }
    }

    @Test
    void cuttingAtTheCurrentHeightDoesNotMoveThePreviouslyChosenFocusWindow() {
        preview.focus(player, null);
        var before = scene(null);
        position = new Location(world, 80.5, 70, 90.5);
        preview.slice(player, null);
        var after = scene(null);
        assertEquals(before.projection().bounds().minimumX(), after.projection().bounds().minimumX());
        assertEquals(before.projection().bounds().maximumX(), after.projection().bounds().maximumX());
        assertEquals(before.projection().bounds().minimumZ(), after.projection().bounds().minimumZ());
        assertEquals(72, after.projection().bounds().maximumY());
        assertTrue(after.playerFocus());
    }

    @Test
    void focusingAHighRegionDoesNotOverwriteTheCutHeightWhenReturningToOverview() {
        Plot plot = plot(Set.of(new PlotGeometry.Cell(0, 16, 0), new PlotGeometry.Cell(0, 40, 0)));
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, plot.cells());
        preview.openRegions(player, plotId, true);
        var initial = scene(plot);
        preview.slice(player, plotId);
        assertEquals(67, scene(plot).projection().bounds().maximumY());
        var upper = initial.regions().items().stream().max(java.util.Comparator.comparingInt(
                region -> region.bounds().minimumY())).orElseThrow();
        preview.selectRegion(player, plotId, upper);
        assertTrue(scene(plot).projection().bounds().maximumY() > 67);
        preview.focus(player, plotId);
        assertEquals(67, scene(plot).projection().bounds().maximumY());
    }

    @Test
    void overviewPaddingDoesNotForceSkippingLayersWithinTheActualWorldHeight() {
        Set<PlotGeometry.Cell> cells = new HashSet<>();
        for (int row = -16; row < 80; row++) cells.add(new PlotGeometry.Cell(0, row, 0));
        Plot plot = plot(cells);
        state.bind(playerId, plotId, worldId);
        state.initialize(playerId, worldId, plot.cells());
        var scene = scene(plot);
        assertTrue(scene.projection().bounds().minimumY() < world.getMinHeight());
        assertTrue(scene.projection().bounds().maximumY() >= world.getMaxHeight());
        assertEquals(1, scene.grid().stepY());
        assertEquals(384, scene.grid().rows());
        assertEquals(-64, scene.grid().bounds().minimumY());
        assertEquals(319, scene.grid().bounds().maximumY());
        assertTrue(scene.grid().count() <= PlotMiniatureGeometry.MAX_SAMPLES);
    }

    private void initializeRegions(int count) {
        Set<PlotGeometry.Cell> cells = new HashSet<>();
        for (int index = 0; index < count; index++) cells.add(new PlotGeometry.Cell(index * 20, 16, 0));
        state.bind(playerId, null, worldId);
        state.initialize(playerId, worldId, cells);
    }

    private PlotMiniaturePreview.Scene scene(Plot plot) {
        return preview.scene(player, plot, state.draft(playerId, plot == null ? null : plot.id()), true);
    }

    private PlotMiniaturePreview.Outline outline(PlotMiniaturePreview.Scene scene, String id) {
        return scene.outlines().stream().filter(outline -> outline.id().equals(id)).findFirst().orElseThrow();
    }

    private Plot plot(Set<PlotGeometry.Cell> cells) {
        return new Plot(plotId, worldId, playerId, "Plot", false, 0, cells, Map.of(), Map.of(), null);
    }
}
