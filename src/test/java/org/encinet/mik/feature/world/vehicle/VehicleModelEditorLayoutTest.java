package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleModelEditorLayoutTest {
    @Test void rotationScalingAndColliderSizeScreensKeepTheirControlsLayoutable() {
        for (var mode : VehicleEditorTransform.Mode.values()) {
            var menu = VehicleModelEditor.createMenu(Component.text(mode.name()), new Transform(new ComponentRef(mode == VehicleEditorTransform.Mode.SIZE ? Section.COLLIDERS : Section.PARTS, 0), mode));
            menu.information("summary", Component.text("Relative transform / full dimensions")).region("summary");
            for (String axis : List.of("x", "y", "z")) for (String direction : List.of("minus", "plus"))
                menu.control(axis + ":" + direction, Component.text(axis + " " + direction)).region("items");
            for (String action : mode == VehicleEditorTransform.Mode.SCALE ? List.of("step", "manual", "grow", "shrink") : List.of("step", "manual"))
                menu.control(action, Component.text(action)).region("actions");
            for (String action : List.of("undo", "redo", "back")) menu.navigation(action, Component.text(action)).region("navigation");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertEquals(6, definition.entries().values().stream().filter(entry -> entry.id().contains("/items/")).count());
            assertLayout(definition);
        }
    }

    @Test void positionEditorPlacesAxisSamplingGridAndHistoryControlsInSeparateRegions() {
        var menu = VehicleModelEditor.createMenu(Component.text("Position"), new Position(new ComponentRef(Section.PARTS, 0), false));
        menu.information("summary", Component.text("Fixed origin · local coordinates")).region("summary");
        for (String axis : List.of("x", "y", "z")) for (String direction : List.of("minus", "plus"))
            menu.control(axis + ":" + direction, Component.text(axis + " " + direction)).region("items");
        for (String action : List.of("here", "look", "snap", "step", "manual"))
            menu.control(action, Component.text(action)).region("actions");
        menu.navigation("undo", Component.text("Undo")).region("navigation");
        menu.navigation("redo", Component.text("Redo")).region("navigation");
        menu.navigation("back", Component.text("Back")).region("navigation");
        var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
        assertEquals(6, definition.entries().values().stream().filter(entry -> entry.id().contains("/items/")).count());
        assertEquals(5, definition.entries().values().stream().filter(entry -> entry.id().contains("/actions/")).count());
        for (String key : List.of("undo", "redo", "back", "dismiss")) assertNotNull(UiSceneTestSupport.entry(definition, key));
        assertLayout(definition);
    }

    @Test void emptyAndPaginatedMenusPlaceAllNodesIncludingDismissInComposedRegions() {
        for (int total : List.of(0, 1, 6, 13)) {
            var page = new FloatingMenuPage(0, total, 6);
            var menu = VehicleModelEditor.createMenu(Component.text("Models"), new Library(0));
            menu.information("summary", Component.text("Saved models")).region("summary");
            for (int index = 0; index < Math.min(total, 6); index++)
                menu.control("model:" + index, Component.text("Model " + index)).region("items");
            menu.pagination("pages", page, _ -> { });
            menu.control("create", Component.text("Create")).region("actions");
            menu.navigation("back", Component.text("Back")).region("navigation");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertNotNull(UiSceneTestSupport.entry(definition, "dismiss"));
            assertTrue(definition.entries().values().stream().noneMatch(entry -> entry.region().equals(MenuScene.DEFAULT_REGION)));
            assertLayout(definition);
        }
    }

    @Test void deniedAndComponentScreensKeepTheSharedDismissControlLayoutable() {
        for (String summary : List.of("Permission required", "Component removed", "Part 0", "Unsaved draft")) {
            var menu = VehicleModelEditor.createMenu(Component.text("Model editor"), Workspace.INSTANCE);
            menu.information("summary", Component.text(summary)).region("summary");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertNotNull(UiSceneTestSupport.entry(definition, "dismiss"));
            assertLayout(definition);
        }
    }

    @Test void libraryMakesCaptureAndResumeDiscoverableWithoutDuplicatingCreationActions() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.editor.library(h.player);
            assertLayout(h.scene.measured(org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport.measure(h.scene, 1)));
            assertNotNull(UiSceneTestSupport.entry(h.scene, "create"));
            assertNotNull(UiSceneTestSupport.entry(h.scene, "import"));
            assertNull(UiSceneTestSupport.entry(h.scene, "capture"));
            h.click("selection");
            org.mockito.Mockito.verify(h.module).openSelection(h.player);
            h.edit();
            h.editor.library(h.player);
            assertNotNull(UiSceneTestSupport.entry(h.scene, "resume"));
            assertNull(UiSceneTestSupport.entry(h.scene, "create"));
            assertNull(UiSceneTestSupport.entry(h.scene, "import"));
            h.click("model:" + h.definition.id());
            assertFalse(UiSceneTestSupport.entry(h.scene, "clone").enabled());
            assertTrue(UiSceneTestSupport.entry(h.scene, "spawn").enabled());
            h.click("back");
            h.click("resume");
            assertNotNull(UiSceneTestSupport.entry(h.scene, "apply"));
        }
    }

    @Test void anUnassignedDismissReproducesTheOriginalErrorWithoutSilentlyFallingBack() {
        var menu = VehicleModelEditor.createMenu(Component.text("Models"), new Library(0));
        menu.dismiss(Component.text("Close"));
        assertThrows(IllegalArgumentException.class, menu::build);
    }

    @Test void modelDetailsKeepEditSpawnCopyAndDeleteReachableAtExtremeSettings() throws Exception {
        for (var language : Language.values()) try (var h = new VehicleEditorHarness(language)) {
            h.editor.library(h.player);
            h.click("model:" + h.definition.id());
            for (int fov : new int[]{30, 110}) {
                var frame = SceneGeometryTestSupport.readableFrame(h.scene, 1.8, new FloatingMenuFieldOfView(fov), 1.3);
                UiSceneTestSupport.assertDisjoint(frame);
                for (String action : List.of("edit", "spawn", "clone", "delete", "back"))
                    assertNotNull(UiSceneTestSupport.entry(h.scene, frame, action), language + " · " + fov + " · " + action);
            }
        }
    }

    @Test void spawnReportsFailureWithoutCommittingTheFormAndOpensTheExactCreatedVehicleOnRetry() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.editor.library(h.player); h.click("model:" + h.definition.id()); h.click("spawn");
            var form = h.form.get();
            var values = new MenuInputForm.Values(Map.of("x", "10", "y", "64", "z", "20"));
            var vehicle = UUID.randomUUID();
            when(h.module.spawn(h.player, h.definition.id(), new Location(h.world, 10, 64, 20)))
                    .thenThrow(new IllegalArgumentException("Spawn intersects terrain")).thenReturn(vehicle);
            assertNull(form.validation().apply(h.player, values));
            var rejected = mock(MenuInputForm.Completion.class);
            form.asynchronousSubmit().submit(h.player, values, rejected);
            verify(rejected).failure(any());
            verifyNoMoreInteractions(rejected);
            verify(h.module, never()).openCreatedVehicle(any(), any());
            var accepted = mock(MenuInputForm.Completion.class);
            form.asynchronousSubmit().submit(h.player, values, accepted);
            var order = inOrder(accepted, h.module);
            order.verify(accepted).success();
            order.verify(h.module).openCreatedVehicle(h.player, vehicle);
        }
    }

    @Test void spawnFormCannotSubmitForAClosedOrReplacedModelPage() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.editor.library(h.player); h.click("model:" + h.definition.id()); h.click("spawn");
            var form = h.form.get();
            var values = new MenuInputForm.Values(Map.of("x", "10", "y", "64", "z", "20"));
            assertNull(form.validation().apply(h.player, values));
            h.click("back");
            assertNotNull(form.validation().apply(h.player, values));
            h.click("model:" + h.definition.id()); h.click("spawn");
            form = h.form.get();
            FloatingMenus.current(h.player).orElseThrow().close();
            var completion = mock(MenuInputForm.Completion.class);
            form.asynchronousSubmit().submit(h.player, values, completion);
            verify(completion).failure(any());
            verify(h.module, never()).spawn(any(), anyString(), any());
        }
    }

    private static void assertLayout(MenuScene definition) {
        var frame = UiSceneTestSupport.frame(definition);
        assertEquals(definition.entries().size(), frame.elements().size());
        UiSceneTestSupport.assertDisjoint(frame);
    }

}
