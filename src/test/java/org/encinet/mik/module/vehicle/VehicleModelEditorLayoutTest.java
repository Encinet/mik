package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleModelEditorLayoutTest {
    @Test void rotationScalingAndColliderSizeScreensKeepTheirControlsLayoutable() {
        for (var mode : VehicleEditorTransform.Mode.values()) {
            var menu = VehicleModelEditor.createMenu(Component.text(mode.name()));
            menu.information("summary", Component.text("Relative transform / full dimensions")).region("summary");
            for (String axis : List.of("x", "y", "z")) for (String direction : List.of("minus", "plus"))
                menu.item(axis + ":" + direction, new TestItemStack(), Component.text(axis + " " + direction)).region("items");
            for (String action : mode == VehicleEditorTransform.Mode.SCALE ? List.of("step", "manual", "grow", "shrink") : List.of("step", "manual"))
                menu.item(action, new TestItemStack(), Component.text(action)).region("actions");
            for (String action : List.of("undo", "redo", "back")) menu.navigation(action, Component.text(action)).region("navigation");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertEquals(6, definition.entries().values().stream().filter(entry -> entry.region().equals("items")).count());
            assertLayout(definition);
        }
    }

    @Test void positionEditorPlacesAxisSamplingGridAndHistoryControlsInSeparateRegions() {
        var menu = VehicleModelEditor.createMenu(Component.text("Position"));
        menu.information("summary", Component.text("Fixed origin · local coordinates")).region("summary");
        for (String axis : List.of("x", "y", "z")) for (String direction : List.of("minus", "plus"))
            menu.item(axis + ":" + direction, new TestItemStack(), Component.text(axis + " " + direction)).region("items");
        for (String action : List.of("here", "look", "snap", "step", "manual"))
            menu.item(action, new TestItemStack(), Component.text(action)).region("actions");
        menu.navigation("undo", Component.text("Undo")).region("navigation");
        menu.navigation("redo", Component.text("Redo")).region("navigation");
        menu.navigation("back", Component.text("Back")).region("navigation");
        var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
        assertEquals(6, definition.entries().values().stream().filter(entry -> entry.region().equals("items")).count());
        assertEquals(5, definition.entries().values().stream().filter(entry -> entry.region().equals("actions")).count());
        assertEquals(4, definition.entries().values().stream().filter(entry -> entry.region().equals("navigation")).count());
        assertLayout(definition);
    }

    @Test void emptyAndPaginatedMenusPlaceAllNodesIncludingDismissInComposedRegions() {
        for (int total : List.of(0, 1, 6, 13)) {
            var page = new FloatingMenuPage(0, total, 6);
            var menu = VehicleModelEditor.createMenu(Component.text("Models"));
            menu.information("summary", Component.text("Saved models")).region("summary");
            for (int index = 0; index < Math.min(total, 6); index++)
                menu.item("model:" + index, new TestItemStack(), Component.text("Model " + index)).region("items");
            menu.pagination("pages", page, next -> { });
            menu.item("create", new TestItemStack(), Component.text("Create")).region("actions");
            menu.navigation("back", Component.text("Back")).region("navigation");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertEquals("navigation", definition.entries().get("dismiss").region());
            assertTrue(definition.entries().values().stream().noneMatch(entry -> entry.region().equals(FloatingMenuDefinition.DEFAULT_REGION)));
            assertLayout(definition);
        }
    }

    @Test void deniedAndComponentScreensKeepTheSharedDismissControlLayoutable() {
        for (String summary : List.of("Permission required", "Component removed", "Part 0", "Unsaved draft")) {
            var menu = VehicleModelEditor.createMenu(Component.text("Model editor"));
            menu.information("summary", Component.text(summary)).region("summary");
            var definition = VehicleModelEditor.finishMenu(menu, Component.text("Close"));
            assertEquals("navigation", definition.entries().get("dismiss").region());
            assertLayout(definition);
        }
    }

    @Test void anUnassignedDismissReproducesTheOriginalErrorWithoutSilentlyFallingBack() {
        var menu = VehicleModelEditor.createMenu(Component.text("Models"));
        menu.dismiss(Component.text("Close"));
        var definition = menu.build();
        var nodes = nodes(definition);
        var exception = assertThrows(IllegalArgumentException.class,
                () -> definition.layout().pose(new FloatingMenuLayout.Context(0, nodes)));
        assertEquals("No composed layout for region 'default'", exception.getMessage());
    }

    private static void assertLayout(FloatingMenuDefinition definition) {
        var nodes = nodes(definition);
        for (int index = 0; index < nodes.size(); index++) {
            var pose = definition.layout().pose(new FloatingMenuLayout.Context(index, nodes));
            assertTrue(Double.isFinite(pose.right()));
            assertTrue(Double.isFinite(pose.up()));
        }
    }

    private static List<FloatingMenuLayout.Node> nodes(FloatingMenuDefinition definition) {
        return definition.entries().values().stream().map(entry -> new FloatingMenuLayout.Node(
                entry.id(), entry.style(), entry.role(), entry.region(), new FloatingMenuSize(1, 0.4))).toList();
    }

    private static final class TestItemStack extends ItemStack {
        private TestItemStack() { super(); }
        @Override public TestItemStack clone() { return this; }
    }
}
