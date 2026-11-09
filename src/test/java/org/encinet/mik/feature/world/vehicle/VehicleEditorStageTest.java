package org.encinet.mik.feature.world.vehicle;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class VehicleEditorStageTest {
    @Test void fittingCreatesAnElevatedMiniatureWithoutChangingAuthoredGeometry() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var before = draft.validate(); var viewer = new Location(mock(World.class), 20, 64, -10, 37, 89);
        var stage = VehicleEditorStage.fit(draft, viewer);
        assertTrue(stage.scale() < 1); assertTrue(stage.origin().getY() > viewer.getY());
        assertEquals(before, draft.validate()); assertFalse(draft.canUndo());
        var center = VehicleModel.bounds(draft.parts()).center(); var floating = stage.world(draft, center);
        assertEquals(65.4, floating.getY(), 1.0E-8);
        assertEquals(3.5, Math.hypot(floating.getX() - viewer.getX(), floating.getZ() - viewer.getZ()), 1.0E-8);
    }
    @Test void inverseCoordinatesAndFrameChangesRemainExactAtDifferentScalesAndHeadings() {
        var world = mock(World.class);
        for (double scale : new double[]{.02, .1, .6, 1, 2}) for (float yaw : new float[]{0, 37, 90, 180, -170}) {
            var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
            var stage = VehicleEditorStage.place(draft, new Location(world, 9, 72, 15, yaw, 0), scale);
            var point = new VehicleVector(.75, 1.2, -2.5);
            var roundtrip = stage.local(draft, stage.world(draft, point));
            assertTrue(point.subtract(roundtrip).length() < 1.0E-8);
            var old = stage.world(draft, draft.position("parts", 0, false));
            draft.front(1);
            var changed = stage.world(draft, draft.position("parts", 0, false));
            assertEquals(0, old.distance(changed), 1.0E-8);
        }
    }
    @Test void zoomKeepsTheModelCenterFixedAfterChoosingANewFront() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var stage = VehicleEditorStage.fit(draft, new Location(mock(World.class), 10, 64, 20, 37, 0));
        draft.front(1);
        var center = VehicleModel.bounds(draft.parts()).center();
        var before = stage.world(draft, center);
        var zoomed = stage.withScale(draft, .9);
        assertEquals(0, before.distance(zoomed.world(draft, center)), 1.0E-8);
        assertEquals(stage.oriented(draft).getYaw(), zoomed.oriented(draft).getYaw());
        assertEquals(.9, zoomed.scale());
    }
    @Test void miniaturePublishesClickableToolsAtWorldSizedDistances() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var route = new VehicleEditorRoute.Position(new VehicleEditorRoute.ComponentRef(VehicleEditorRoute.Section.SEATS, 0), false);
        for (double scale : new double[]{.02, .2, .6}) {
            var scene = VehicleEditorScene.stage(draft, route, .1, scale);
            var handle = scene.handles().stream().filter(value -> value.key().equals("adjust:0:1")).findFirst().orElseThrow();
            var center = draft.position("seats", 0, false);
            assertTrue(handle.point().subtract(center).length() * scale >= .65 - 1.0E-8);
            var eye = handle.point().add(new VehicleVector(0, 0, -3 / scale));
            assertEquals(handle, VehicleEditorScene.pick(java.util.List.of(handle), eye, new VehicleVector(0, 0, 1), 32 / scale, scale));
            assertTrue(scene.handles().stream().anyMatch(value -> value.action() == VehicleEditorScene.Tool.ZOOM_IN));
        }
    }
}
