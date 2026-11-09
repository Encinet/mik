package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element;
import static org.junit.jupiter.api.Assertions.*;

class MenuPresentationTest {
    // Bukkit locations hold weak world references; the test owns the world for its entire run.
    private final FloatingMenuTestWorld testWorld = new FloatingMenuTestWorld();

    @Test void switchingBetweenWideAndNarrowPagesKeepsTheCurrentReaderCentered() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        for (var spatial : new FloatingMenuSpatialFrame[]{FloatingMenuSpatialFrame.IN_FRONT, FloatingMenuSpatialFrame.FRONT_ARC})
            for (int fov : new int[]{30, 70, 110}) for (var text : FloatingMenuTextScale.values())
            for (double openingWidth : new double[]{160, 360}) {
                var view = new MenuPresentation();
                var opening = scene(6, openingWidth).toBuilder();
                if (spatial == FloatingMenuSpatialFrame.FRONT_ARC) opening.frontArc();
                var before = render(view, opening.build(), eye, text.factor(), fov, 1);
                for (double width : new double[]{360, 160, 240, openingWidth}) {
                    var page = scene(width == 360 ? 6 : 1, width).toBuilder();
                    if (spatial == FloatingMenuSpatialFrame.FRONT_ARC) page.frontArc();
                    var current = render(view, page.build(), eye, text.factor(), fov, 1);
                    var main = surfacePose(current, "/main/body");
                    String detail = spatial + " " + fov + " @ " + text + " " + openingWidth + " -> " + width;
                    var panels = current.geometry().frame().panels();
                    double right = panels.stream().mapToDouble(panel -> panel.x() + panel.size().width()).max().orElseThrow();
                    assertEquals(right,current.geometry().frame().size().width(),1e-6,
                            "Unused body budget must not displace the visible workspace: " + detail);
                    assertEquals(0, main.yawDegrees(), 1e-6, detail);
                    assertEquals(0, main.pitchDegrees(), 1e-6, detail);
                    assertEquals(before.anchor().origin(), current.anchor().origin(), detail);
                    assertEquals(before.anchor().distance(), current.anchor().distance(), 1e-6, detail);
                    assertEquals(before.anchor().forward(), current.anchor().forward(), detail);
                    if (spatial == FloatingMenuSpatialFrame.FRONT_ARC) assertEquals(before.anchor(), current.anchor(), detail);
                    var previousRail = panel(before, "/sidebar").size();
                    var currentRail = panel(current, "/sidebar").size();
                    assertEquals(previousRail.width(), currentRail.width(), 1e-6, detail);
                    assertEquals(previousRail.height(), currentRail.height(), 1e-6, detail);
                    assertTrue(pose(current, "category").right() < main.right(), detail);
                    assertTrue(pose(current, "save").up() < main.up(), detail);
                    if (spatial == FloatingMenuSpatialFrame.IN_FRONT) {
                        var body = panel(current,"/main/body");
                        assertTrue(main.right() + body.size().width() * UiSceneContent.WORLD_PER_UI / 2 > 0,
                                "The reading panel must not lie entirely to the viewer's left: " + detail);
                    }
                }
            }
    }

    @Test void anEmptyReadingBodyKeepsItsNavigationAvailable() {
        var scene = MenuScene.screen("empty").content(UiTemplates.sheet(
                UiTemplates.window("reader", UiTemplates.information("rows")),
                UiTemplates.toolbar("tools", Ui.button(Component.text("Back")).key("back")
                        .role(UiRole.NAVIGATION).primary((_, _) -> {}).build()))).build();
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var frame = render(new MenuPresentation(), scene, eye, 1, 70, 1);
        assertEquals(1, scene.entries().size());
        assertEquals(1, frame.geometry().frame().panels().size());
        assertNotNull(pose(frame, "back"));
    }

    @Test void contentResizesItsPanelsWhileTheReadingFrameRemainsStable() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        for (int fov : new int[]{30, 70, 110}) for (var text : FloatingMenuTextScale.values()) {
            var view = new MenuPresentation();
            var before = render(view, scene(0), eye, text.factor(), fov, 1);
            var after = render(view, scene(14), eye, text.factor(), fov, 1);
            String detail = fov + " @ " + text;
            assertEquals(before.anchor().origin(), after.anchor().origin(), detail);
            assertEquals(before.anchor().distance(), after.anchor().distance(), 1e-6, detail);
            for (String key : new String[]{"category"}) {
                var first = before.geometry().sizes().get(element(before.geometry().frame(), key).path());
                var second = after.geometry().sizes().get(element(after.geometry().frame(), key).path());
                assertEquals(first.width(), second.width(), 1e-6, detail);
                assertEquals(first.height(), second.height(), 1e-6, detail);
            }
            var firstHeading = before.geometry().measurements().get(element(before.geometry().frame(), "heading").path()).text();
            var secondHeading = after.geometry().measurements().get(element(after.geometry().frame(), "heading").path()).text();
            assertEquals(firstHeading.contentWidth(), secondHeading.contentWidth(), 1e-6, detail);
            assertEquals(firstHeading.renderedHeight(), secondHeading.renderedHeight(), 1e-6, detail);
            var oldRail = panel(before, "/sidebar");
            var newRail = panel(after, "/sidebar");
            assertEquals(oldRail.size().width(), newRail.size().width(), 1e-6, detail);
            assertEquals(oldRail.size().height(), newRail.size().height(), 1e-6, detail);
            assertTrue(panel(after, "/main/body").size().height() > panel(before, "/main/body").size().height(), detail);
            assertEquals(0, surfacePose(after, "/main/body").yawDegrees(), 1e-6, detail);
            assertEquals(0, surfacePose(after, "/main/body").pitchDegrees(), 1e-6, detail);
            assertEquals(0, pose(after, "heading").yawDegrees(), 1e-6, detail);
            assertEquals(0, pose(after, "heading").pitchDegrees(), 1e-6, detail);
            assertEquals(0, pose(after, "category").pitchDegrees(), 1e-6, detail);
            var restored = render(view, scene(0), eye, text.factor(), fov, 1);
            assertEquals(before.anchor(), restored.anchor(), detail);
            assertEquals(before.geometry().frame().panels(), restored.geometry().frame().panels(), detail);
            assertVector(position(before, "category", eye), position(restored, "category", eye), detail);
        }
    }

    @Test void contentWidthChangesDoNotTiltTheMainReadingSurface() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var narrow = render(view, reader("On"), eye, 1, 70, 1);
        var wide = render(view, reader("A substantially longer status value"), eye, 1, 70, 1);
        assertTrue(panel(wide, "/body").size().width() > panel(narrow, "/body").size().width());
        assertEquals(0, surfacePose(wide, "/body").yawDegrees(), 1e-6);
        assertEquals(0, surfacePose(wide, "/body").pitchDegrees(), 1e-6);
        assertEquals(0, pose(wide, "heading").yawDegrees(), 1e-6);
        assertEquals(0, pose(wide, "heading").pitchDegrees(), 1e-6);
        assertEquals(narrow.anchor().origin(), wide.anchor().origin());
        assertEquals(narrow.anchor().distance(), wide.anchor().distance(), 1e-6);
    }

    @Test void aRootReadingWindowStaysFlatWhenItsContentShrinks() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var items = java.util.stream.IntStream.range(0,14).mapToObj(row -> Ui.text(Component.text("Detail")).key("detail:" + row).build()).toList();
        var content = Ui.pagedList().header(Ui.text(Component.text("Settings")).key("heading").build()).items(items).build();
        var expanded = MenuScene.screen("window").content(UiTemplates.window("reader", content)).build();
        var collapsed = MenuScene.screen("window").content(UiTemplates.window("reader",
                Ui.text(Component.text("Settings")).key("heading").build())).build();
        var before = render(view, expanded, eye, 1, 70, 1);
        var after = render(view, collapsed, eye, 1, 70, 1);
        assertEquals(0, pose(after, "heading").yawDegrees(), 1e-6);
        assertEquals(0, pose(after, "heading").pitchDegrees(), 1e-6);
        assertEquals(before.anchor(), after.anchor());
        assertTrue(panel(after, "root").size().height() < panel(before, "root").size().height());
    }

    @Test void referenceSurvivesObstructionAndRecoversWhenSpaceClears() {
        var world = testWorld;
        var eye = new Location(world.world(), .5, 64.5, 0);
        var view = new MenuPresentation();
        var clear = render(view, reader("On"), eye, 1, 70, 1);
        for (int x = -3; x <= 3; x++) for (int y = 62; y <= 67; y++)
            world.block(x, y, 2, new BoundingBox(0, 0, 0, 1, 1, 1));
        var obstructed = render(view, reader("Off"), eye, 1, 70, 1);
        assertTrue(obstructed.anchor().distance() < clear.anchor().distance());
        assertEquals(clear.anchor().spatialScale() / clear.anchor().distance(),
                obstructed.anchor().spatialScale() / obstructed.anchor().distance(), 1e-8);
        Vector first = position(clear, "heading", eye), second = position(obstructed, "heading", eye);
        assertVector(first.multiply(1 / first.getZ()), second.multiply(1 / second.getZ()), "same apparent position");
        for (int x = -3; x <= 3; x++) for (int y = 62; y <= 67; y++) world.block(x, y, 2);
        var recovered = render(view, reader("On"), eye, 1, 70, 1);
        assertEquals(clear.anchor(), recovered.anchor());
    }

    @Test void readingPreferencesReevaluateTheFrameWithoutFeatureCode() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var before = render(view, reader("On"), eye, 1, 70, 1);
        var after = render(view, reader("On"), eye, 1, 70, .7);
        assertEquals(before.anchor().spatialScale() * .7, after.anchor().spatialScale(), 1e-8);
        var typographyView = new MenuPresentation();
        var smallText = render(typographyView, scene(14), eye, .7, 70, 1);
        var largeText = render(typographyView, scene(14), eye, 1.8, 70, 1);
        assertEquals(smallText.anchor().spatialScale(), largeText.anchor().spatialScale(), 1e-6,
                "Larger typography repaginates instead of shrinking the reading frame");
        var reduced = render(typographyView, scene(14).toBuilder().animation(FloatingMenuAnimation.DEFAULT.reduced()).build(),
                eye, 1.8, 70, 1);
        assertEquals(largeText.anchor(), reduced.anchor());
    }

    @Test void aSmallLoadingPlaceholderDoesNotDetermineTheLoadedPagesReadingScale() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var loading = MenuScene.screen("settings").loading(true)
                .content(UiTemplates.window("status", Ui.text(Component.text("Loading")).build())).build();
        assertTrue(loading.toBuilder().build().loading());
        assertFalse(loading.toBuilder().loading(false).build().loading());
        for (int fov : new int[]{30, 70, 110}) for (var scale : FloatingMenuTextScale.values()) {
            var view = new MenuPresentation();
            render(view, loading, eye, scale.factor(), fov, 1);
            var ready = render(view, scene(14), eye, scale.factor(), fov, 1);
            var direct = render(new MenuPresentation(), scene(14), eye, scale.factor(), fov, 1);
            assertEquals(direct.anchor(), ready.anchor(), fov + " @ " + scale);
            assertFalse(ready.geometry().definition().loading());
            var refresh = render(view, loading, eye, scale.factor(), fov, 1);
            assertEquals(ready.anchor(), refresh.anchor());
            assertEquals(ready.geometry().frame(), refresh.geometry().frame());
        }
    }

    @Test void failedPreferenceRemeasurePreservesThePreviousCompleteViewAndAllowsTheNextPreferenceChange() {
        var eye = new Location(testWorld.world(),0,64,0);
        var scene = reader("On");
        var presentation = new MenuPresentation();
        var before = render(presentation,scene,eye,1,70,1);
        var invalid = scene.toBuilder().content(Ui.stack().size(new UiSize(2000,2000)).build()).build();
        assertThrows(UiOverflowException.class,() -> render(presentation,invalid,eye,1.8,30,1.3));
        var retained = render(presentation,scene.toBuilder().loading(true).build(),eye,1,70,1);
        assertSame(before.geometry(),retained.geometry());
        assertEquals(before.anchor(),retained.anchor());
        var changed = render(presentation,scene,eye,1.8,110,1.3);
        var reopened = render(new MenuPresentation(),scene,eye,1.8,110,1.3);
        assertEquals(reopened.anchor(),changed.anchor());
        assertEquals(reopened.geometry().frame().surfaces(),changed.geometry().frame().surfaces());
        assertEquals(reopened.geometry().generatedDecorations(),changed.geometry().generatedDecorations());
    }

    @Test void anAdaptiveSceneAlsoKeepsReadableTypographyWhenItsContentNeedsPages() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var before = render(view, scene(0).toBuilder().anchorMode(FloatingMenuAnchorMode.ADAPTIVE).build(), eye, 1, 70, 1);
        var after = render(view, scene(100).toBuilder().anchorMode(FloatingMenuAnchorMode.ADAPTIVE).build(), eye, 1, 70, 1);
        assertEquals(before.anchor().spatialScale(), after.anchor().spatialScale(), 1e-6);
        assertTrue(after.geometry().frame().pagination().getFirst().page().count() > 1);
    }

    @Test void ordinaryUpdatesWithinTheReadingConeKeepTheWholeReadingFrame() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var before = render(view, reader("On"), eye, 1, 70, 1);
        for (String value : new String[]{"Off", "Ready", "On"})
            assertEquals(before.anchor(), render(view, reader(value), eye, 1, 70, 1).anchor());
    }

    @Test void growingContentStaysInsideTheReadingConeWithoutMovingCloserOrChangingTheReference() {
        var eye = new Location(testWorld.world(), 0, 64, 0);
        for (int fov : new int[]{30, 70, 110}) for (var text : FloatingMenuTextScale.values()) {
            var view = new MenuPresentation();
            var before = render(view, scene(0), eye, text.factor(), fov, 1);
            var expanded = render(view, scene(14), eye, text.factor(), fov, 1);
            String detail = fov + " @ " + text;
            assertEquals(before.anchor().origin(), expanded.anchor().origin(), detail);
            assertEquals(before.anchor().distance(), expanded.anchor().distance(), 1e-6, detail);
            assertEquals(before.anchor().forward(), expanded.anchor().forward(), detail);
            SceneGeometryTestSupport.assertReadingCone(expanded.geometry(), expanded.anchor(), eye, new FloatingMenuFieldOfView(fov), detail);
            assertEquals(before.anchor(), render(view, scene(0), eye, text.factor(), fov, 1).anchor(),
                    "A temporary fit must not shrink the stored reading reference: " + detail);
        }
    }

    private static MenuScene scene(int rows) {
        return scene(rows, 0);
    }

    private static MenuScene scene(int rows, double width) {
        var items = java.util.stream.IntStream.range(0,rows).mapToObj(row -> Ui.text(Component.text("Detail " + row)).key("detail:" + row).build()).toList();
        UiNode content = Ui.pagedList().key("details").gap(UiLength.ui(4))
                .header(Ui.text(Component.text("Settings")).key("heading").build()).items(items).build();
        if (width > 0) content = Ui.layout(UiLayouts.preferredWidth(UiLength.ui(width), UiLayouts.column(UiLength.ZERO))).child(content).build();
        return MenuScene.screen("settings").content(UiTemplates.workspace(
                Ui.button(Component.text("Category")).key("category").primary((_, _) -> {}).build(),
                UiTemplates.sheet(UiTemplates.window("reader", content),
                        UiTemplates.toolbar("tools", Ui.button(Component.text("Save")).key("save").build())))).build();
    }

    private static MenuScene reader(String value) {
        var content = Ui.column().gap(UiLength.ui(4))
                .child(Ui.text(Component.text("Settings")).key("heading").build(), new LinearSlot(0, LinearSlot.Alignment.START))
                .child(Ui.text(Component.text(value)).key("value").build(), new LinearSlot(0, LinearSlot.Alignment.START)).build();
        return MenuScene.screen("reader").content(UiTemplates.sheet(UiTemplates.window("reader", content),
                UiTemplates.toolbar("tools", Ui.text(Component.text("Actions")).build()))).build();
    }

    private record Rendered(SceneGeometry geometry, FloatingMenuAnchorResolver.Anchor anchor) { }

    private static Rendered render(MenuPresentation view, MenuScene scene, Location eye, double typography, int fov, double scale) {
        var fieldOfView = new FloatingMenuFieldOfView(fov);
        var prepared = view.prepare(null, eye, new Vector(0, 0, 1), scene, typography, scale, fieldOfView, UiPalette.DEFAULT);
        var geometry = prepared.geometry();
        var anchor = prepared.anchor().orElseThrow(() -> new AssertionError(scene.spatialFrame()+" / "+fov+" @ "+typography+" / "+scene.viewport()));
        if (scene.spatialFrame() == FloatingMenuSpatialFrame.IN_FRONT)
            SceneGeometryTestSupport.assertReadingCone(geometry, anchor, eye, fieldOfView, scene.screenId() + " / " + fov + " @ " + typography);
        return new Rendered(geometry, anchor);
    }

    private static UiFrame.Panel panel(Rendered frame, String suffix) {
        return frame.geometry().frame().panels().stream().filter(panel -> panel.path().endsWith(suffix)).findFirst().orElseThrow();
    }

    private static FloatingMenuPose pose(Rendered frame, String key) {
        return frame.geometry().poses().get(element(frame.geometry().frame(), key).path());
    }

    private static FloatingMenuPose surfacePose(Rendered frame, String suffix) {
        var surface = frame.geometry().generatedDecorations().get("$surface/" + panel(frame, suffix).path());
        return ((FloatingMenuPlacement.Local) surface.placement()).pose();
    }

    private static Vector position(Rendered frame, String key, Location eye) {
        var pose = pose(frame, key);
        var anchor = frame.anchor();
        return anchor.origin().toVector().subtract(eye.toVector())
                .add(anchor.right().multiply(pose.right() * anchor.spatialScale()))
                .add(new Vector(0, pose.up() * anchor.spatialScale(), 0))
                .add(anchor.forward().multiply(-pose.forward() * anchor.spatialScale()));
    }

    private static void assertVector(Vector first, Vector second, String detail) {
        assertEquals(first.getX(), second.getX(), 1e-6, detail);
        assertEquals(first.getY(), second.getY(), 1e-6, detail);
        assertEquals(first.getZ(), second.getZ(), 1e-6, detail);
    }
}
