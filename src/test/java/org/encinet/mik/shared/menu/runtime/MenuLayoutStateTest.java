package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuLayoutStateTest {
    private static final FloatingMenuFieldOfView FOV = FloatingMenuFieldOfView.DEFAULT;

    @Test void samePagePanelsGrowAndShrinkWithTheirCurrentContent() {
        for (var scale : FloatingMenuTextScale.values()) {
            var state = new MenuLayoutState();
            var full = state.measure(page("settings", 12, true, true), scale.factor(), FOV, 1);
            var small = page("settings", 0, false, true);
            var shortPage = state.measure(small, scale.factor(), FOV, 1);
            var restored = state.measure(page("settings", 12, true, true), scale.factor(), FOV, 1);
            assertNatural(small, shortPage, scale.factor());
            assertTrue(panel(shortPage, "/main/body").size().height() < panel(full, "/main/body").size().height());
            assertTrue(panel(shortPage, "/main/body").size().width() < panel(full, "/main/body").size().width());
            assertEquals(full.frame().panels(), restored.frame().panels(), scale.toString());
            for (String key : List.of("heading", "category", "save")) {
                var before = element(full.frame(), key);
                var after = element(restored.frame(), key);
                assertEquals(before.x(), after.x(), 1e-6, key + " @ " + scale);
                assertEquals(before.y(), after.y(), 1e-6, key + " @ " + scale);
            }
            assertDisjoint(shortPage.frame());
        }
    }

    @Test void anotherScreenAndAnotherViewStartFromTheirOwnContent() {
        var parent = new MenuLayoutState();
        var full = parent.measure(page("settings", 12, true, true), 1, FOV, 1);
        var child = new MenuLayoutState();
        var small = child.measure(page("details", 0, false, true), 1, FOV, 1);
        assertTrue(panel(small, "/main/body").size().height() < panel(full, "/main/body").size().height());
        var resumed = parent.measure(page("settings", 0, false, true), 1, FOV, 1);
        assertNatural(page("settings", 0, false, true), resumed, 1);

        var switched = parent.measure(page("details", 0, false, true), 1, FOV, 1);
        assertGeometry(small, switched);
        var body = switched.generatedDecorations().get("$surface/" + panel(switched, "/main/body").path());
        var pose = ((FloatingMenuPlacement.Local) body.placement()).pose();
        assertEquals(0, pose.yawDegrees(), 1e-6);
        assertEquals(0, pose.pitchDegrees(), 1e-6);
    }

    @Test void aRefreshKeepsTheCompletePageEvenWhenThePlaceholderHasAnotherStructure() {
        var state = new MenuLayoutState();
        var full = state.measure(page("settings", 12, true, true), 1, FOV, 1);
        var placeholder = MenuScene.screen("settings").loading(true).viewport(new UiSize(1200, 2048))
                .content(UiTemplates.window("loading", Ui.text(Component.text("Loading")).build())).build();
        assertSame(full, state.measure(placeholder, 1, FOV, 1));
        var after = state.measure(page("settings", 0, false, true), 1, FOV, 1);
        assertNatural(page("settings", 0, false, true), after, 1);
        assertFalse(after.definition().loading());
    }

    @Test void aFirstPlaceholderDoesNotReserveSpaceForTheRealContent() {
        var state = new MenuLayoutState();
        var placeholder = MenuScene.screen("settings").loading(true).content(UiTemplates.window("loading",
                Ui.text(Component.text("Loading")).build()).withSizing(UiSizing.atLeast(new UiSize(400, 100)))).build();
        state.measure(placeholder, 1, FOV, 1);
        var scene = page("settings", 0, false, true);
        var ready = state.measure(scene, 1, FOV, 1);
        assertGeometry(SceneGeometry.measure(scene, 1, FOV, 1), ready);
    }

    @Test void textPreferencesAndViewportChangesRemeasureWithoutOldPanelMinima() {
        var scene = page("settings", 8, true, true);
        var state = new MenuLayoutState();
        state.measure(scene, 1.8, FOV, 1);
        var smallerText = state.measure(scene, .7, FOV, 1);
        assertGeometry(SceneGeometry.measure(scene, .7, FOV, 1), smallerText);
        var narrower = scene.toBuilder().viewport(new UiSize(520, 2048)).build();
        var resized = state.measure(narrower, .7, FOV, 1);
        assertGeometry(SceneGeometry.measure(narrower, .7, FOV, 1), resized);
    }

    @Test void removedPanelsDoNotReserveSpaceWhenTheyAppearAgain() {
        var state = new MenuLayoutState();
        var first = state.measure(page("settings", 0, true, true), 1, FOV, 1);
        state.measure(page("settings", 0, false, false), 1, FOV, 1);
        var returned = state.measure(page("settings", 0, false, true), 1, FOV, 1);
        assertTrue(panel(returned, "/sidebar").size().width() < panel(first, "/sidebar").size().width());
    }

    @Test void failedMeasurementsLeaveTheLastSuccessfulPageIntact() {
        var state = new MenuLayoutState();
        var first = state.measure(page("settings", 8, true, true), 1, FOV, 1);
        var invalid = MenuScene.screen("other").viewport(new UiSize(80, 100))
                .content(UiTemplates.window("too-wide", Ui.text(Component.text("No room")).build())
                        .withSizing(UiSizing.atLeast(new UiSize(200, 100)))).build();
        assertThrows(UiOverflowException.class, () -> state.measure(invalid, 1, FOV, 1));
        var placeholder = MenuScene.screen("settings").loading(true)
                .content(Ui.text(Component.text("Loading")).build()).build();
        assertSame(first, state.measure(placeholder, 1, FOV, 1));
        var recovered = state.measure(page("settings", 0, false, true), 1, FOV, 1);
        assertNatural(page("settings", 0, false, true), recovered, 1);
    }

    @Test void onlyDeclaredSizingReservesSpaceWithinTheParentLimits() {
        var surface = UiTemplates.window("reader", Ui.text(Component.text("Value")).key("value").build())
                .withSizing(UiSizing.atLeast(new UiSize(180, 120)));
        var frame = UiCompiler.compile(surface, UiConstraints.bounded(180, 120),
                (_, _) -> new UiCompiler.ElementMeasurement(new UiSize(60, 24), 160));
        assertEquals(new UiSize(180, 120), frame.size());
        assertEquals(12, element(frame, "value").y(), 1e-6);
        assertEquals(60, element(frame, "value").size().width(), 1e-6);
        assertDisjoint(frame);
    }

    @Test void explicitMinimumSizesStillAllowExpansionAndReturnToTheDeclaredMinimum() {
        var state = new MenuLayoutState();
        var small = MenuScene.screen("reserved").content(UiTemplates.window("reader",
                Ui.text(Component.text("Value")).build()).withSizing(UiSizing.atLeast(new UiSize(240, 100)))).build();
        var large = small.toBuilder().content(UiTemplates.window("reader", Ui.document()
                .blocks(List.of(Ui.text(Component.text("Heading")).key("heading").build(),
                        Ui.text(Component.text("Detail\n".repeat(20))).key("details").build()))
                .position(UiReadingPosition.START, _ -> {}).build())
                .withSizing(UiSizing.atLeast(new UiSize(240, 100)))).build();
        var before = state.measure(small, 1, FOV, 1);
        var expanded = state.measure(large, 1, FOV, 1);
        var restored = state.measure(small, 1, FOV, 1);
        assertEquals(new UiSize(240, 100), before.frame().size());
        assertTrue(expanded.frame().size().height() > 100);
        assertGeometry(before, restored);
        assertNatural(small, restored, 1);
    }

    private static void assertNatural(MenuScene scene, SceneGeometry actual, double typography) {
        var expected = SceneGeometry.measure(scene, typography, FOV, 1);
        assertGeometry(expected, actual);
    }

    /** Fresh document actions close over their new page snapshot; geometry equality must not compare callback identity. */
    private static void assertGeometry(SceneGeometry expected, SceneGeometry actual) {
        assertEquals(expected.poses(), actual.poses());
        assertEquals(expected.sizes(), actual.sizes());
        assertEquals(expected.labels(), actual.labels());
        assertEquals(expected.frame().size(), actual.frame().size());
        assertEquals(expected.frame().panels(), actual.frame().panels());
        assertEquals(expected.frame().surfaces(), actual.frame().surfaces());
        assertEquals(expected.frame().focus(), actual.frame().focus());
        assertEquals(expected.frame().readingRegion(), actual.frame().readingRegion());
        assertEquals(expected.frame().pagination().stream().map(UiFrame.Pagination::page).toList(),
                actual.frame().pagination().stream().map(UiFrame.Pagination::page).toList());
        assertEquals(expected.generatedDecorations(), actual.generatedDecorations());
        assertEquals(expected.bounds(), actual.bounds());
    }

    private static MenuScene page(String id, int rows, boolean longLabels, boolean rail) {
        var blocks = new java.util.ArrayList<UiNode>();
        blocks.add(Ui.text(Component.text("Settings")).key("heading").build());
        blocks.add(Ui.text(Component.text(longLabels ? "A substantially longer current value" : "On")).key("value").build());
        for (int i = 0; i < rows; i++) blocks.add(Ui.text(Component.text("Detail " + i)).key("row-" + i).build());
        var content = Ui.document().gap(UiLength.ui(4)).blocks(blocks).position(UiReadingPosition.START, _ -> {});
        UiNode navigation = rail ? Ui.column()
                .child(Ui.button(Component.text("Category")).key("category").build())
                .child(Ui.text(Component.text(longLabels ? "Additional navigation information" : "Info")).build()).build()
                : UiTemplates.information("absent");
        return MenuScene.screen(id).viewport(new UiSize(900, 2048))
                .content(UiTemplates.workspace(navigation, content.build(),
                        Ui.button(Component.text("Save")).key("save").build())).build();
    }

    private static UiFrame.Panel panel(SceneGeometry geometry, String suffix) {
        return geometry.frame().panels().stream().filter(panel -> panel.path().endsWith(suffix)).findFirst().orElseThrow();
    }
}
