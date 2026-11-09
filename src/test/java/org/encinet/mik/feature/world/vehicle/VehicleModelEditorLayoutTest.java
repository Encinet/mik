package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(4, definition.entries().values().stream().filter(entry -> entry.id().contains("/navigation/")).count());
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

    @Test void anUnassignedDismissReproducesTheOriginalErrorWithoutSilentlyFallingBack() {
        var menu = VehicleModelEditor.createMenu(Component.text("Models"), new Library(0));
        menu.dismiss(Component.text("Close"));
        assertThrows(IllegalArgumentException.class, menu::build);
    }

    private static void assertLayout(MenuScene definition) {
        var frame = UiSceneTestSupport.frame(definition);
        assertEquals(definition.entries().size(), frame.elements().size());
        UiSceneTestSupport.assertDisjoint(frame);
    }

}
