package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.ui.UiFrame;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.encinet.mik.shared.menu.ui.UiPalette;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.menu.FloatingMenuElementState;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Production text-footprint model shared by feature composition tests; not a client renderer. */
public final class SceneGeometryTestSupport {
    private SceneGeometryTestSupport() { }

    public record ViewingFrame(UiFrame frame, double distance, double scale, double worldBottom, UiSize worldSize) { }

    /** Actual opening placement above a flat floor, without native Paper entities or a client. */
    public static ViewingFrame viewingFrame(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale) {
        var world = new FloatingMenuTestWorld();
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++)
            world.block(x,63,z,new org.bukkit.util.BoundingBox(0,0,0,1,1,1));
        var eye = new org.bukkit.Location(world.world(),0,65.62,0);
        var result = new MenuPresentation().prepare(null,eye,new org.bukkit.util.Vector(0,0,1),scene,typography,scale,fov,UiPalette.DEFAULT);
        var anchor = result.anchor().orElseThrow(() -> new AssertionError("No comfortable opening placement"));
        var horizontal = result.geometry().bounds().probes().stream().mapToDouble(point -> point.right()).summaryStatistics();
        var vertical = result.geometry().bounds().probes().stream().mapToDouble(point -> point.up()).summaryStatistics();
        var worldSize = new UiSize((horizontal.getMax() - horizontal.getMin()) * anchor.spatialScale(),
                (vertical.getMax() - vertical.getMin()) * anchor.spatialScale());
        double bottom = anchor.origin().getY() + vertical.getMin() * anchor.spatialScale();
        return new ViewingFrame(result.geometry().frame(),anchor.distance(),anchor.spatialScale(),bottom,worldSize);
    }

    /** Real preview footprint without starting Paper's block/item registries. */
    public static UiSize visualSize(FloatingMenuElementStyle style, Component caption, double typography) {
        var text = FloatingMenuNodeSizing.measureText(caption, false, 160);
        var size = FloatingMenuNodeSizing.footprint(style, text, typography, 0);
        return new UiSize(size.width() / UiSceneContent.WORLD_PER_UI, size.height() / UiSceneContent.WORLD_PER_UI);
    }

    public static UiFrame measure(MenuScene scene, double typography) {
        return SceneGeometry.measureBase(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1,
                org.encinet.mik.shared.menu.ui.UiPalette.DEFAULT).frame();
    }

    /** Intrinsic auxiliary content, measured before its host assigns a reading region. */
    public static UiFrame naturalFrame(MenuScene scene, double typography) {
        return ComponentSceneLayout.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1).frame();
    }

    /** Fixed geometry fixtures exercise projection before a host assigns its primary reading budget. */
    static SceneGeometry naturalGeometry(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale) {
        var base = SceneGeometry.measureBase(scene, typography, fov, scale, UiPalette.DEFAULT, null, scene.viewport());
        return base.withPopover(scene, typography, fov, scale, UiPalette.DEFAULT,
                MenuReadingParameters.estimated(base.bounds(), fov, scale));
    }

    public static int naturalTextLines(MenuScene scene, double typography, String key) {
        var geometry = ComponentSceneLayout.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
        String path = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element(geometry.frame(), key).path();
        return geometry.measurements().get(path).text().lines();
    }

    public static UiFrame worldFrame(MenuScene scene, org.encinet.mik.shared.menu.WorldMenuSurface surface, double typography) {
        return MenuWorldPresentation.prepare(new MenuPresentation(),surface,scene,typography,UiPalette.DEFAULT,MenuEntityBudget.LIMIT).geometry().frame();
    }

    public static org.bukkit.Location worldOrigin(MenuScene scene, org.encinet.mik.shared.menu.WorldMenuSurface surface, double typography) {
        return MenuWorldPresentation.prepare(new MenuPresentation(),surface,scene,typography,UiPalette.DEFAULT,MenuEntityBudget.LIMIT).anchor().orElseThrow().origin();
    }

    /** The production pre-layout reading constraints, before any world obstacle adaptation. */
    public static UiFrame readableFrame(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale) {
        return SceneGeometry.measureBase(scene, typography, fov, scale, org.encinet.mik.shared.menu.ui.UiPalette.DEFAULT, null,
                MenuViewportLayout.viewport(scene.viewport(), fov, scale)).frame();
    }

    /** Check the actual eye-relative footprint, including panel depth and an off-center anchor. */
    static void assertReadingCone(SceneGeometry geometry, FloatingMenuAnchorResolver.Anchor anchor,
                                  org.bukkit.Location eye, FloatingMenuFieldOfView fov, String detail) {
        assertReadingCone(geometry.bounds(), anchor, eye, fov, detail);
    }

    static void assertReadingCone(FloatingMenuAnchorResolver.SceneBounds bounds, FloatingMenuAnchorResolver.Anchor anchor,
                                  org.bukkit.Location eye, FloatingMenuFieldOfView fov, String detail) {
        double verticalOffset = anchor.origin().getY() - eye.getY();
        for (var point : bounds.probes()) {
            double toward = anchor.distance() - point.forward() * anchor.spatialScale();
            assertTrue(toward > 0, detail + " has content behind the viewer");
            assertTrue(Math.abs(point.right()) * anchor.spatialScale()
                    <= toward * fov.readingFraming().horizontalTangent() + 1e-6, detail + " exceeds the horizontal reading cone");
            assertTrue(Math.abs(point.up() * anchor.spatialScale() + verticalOffset)
                    <= toward * fov.readingFraming().verticalTangent() + 1e-6, detail + " exceeds the vertical reading cone");
        }
    }

    /** Dispatch wheel input over a named element using production page ownership and boundary handling. */
    public static void scroll(MenuScene scene, double typography, String key,
                              org.encinet.mik.shared.menu.FloatingMenuInteraction interaction) {
        var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
        String target = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element(geometry.frame(), key).path();
        var entry = geometry.definition().entries().get(target);
        var action = entry.enabled() ? entry.trigger(interaction) : null;
        if (action == null) action = MenuPaginationInput.action(geometry.pagination(), target, "", interaction,
                _ -> java.util.Optional.empty());
        if (action == null) action = geometry.definition().triggers().get(interaction);
        org.junit.jupiter.api.Assertions.assertNotNull(action, "Wheel input must remain captured by " + key);
        action.execute(null, null, interaction);
    }

    /** Measures the same independent companion that the runtime publishes. */
    public static UiFrame popoverFrame(MenuScene scene, double typography, FloatingMenuFieldOfView fov) {
        var geometry = SceneGeometry.measure(scene, typography, fov, 1);
        return geometry.popover() == null ? null : geometry.popover().content().frame();
    }

    /** The production view lifetime, including loading refreshes and content resizing on the same page. */
    public static final class LayoutSession {
        private final MenuLayoutState state = new MenuLayoutState();

        public UiFrame measure(MenuScene scene, double typography) {
            return state.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1).frame();
        }

        /** Intrinsic recipe fixtures may place arbitrary prose into headings and tool ports. */
        public UiFrame naturalFrame(MenuScene scene, double typography) {
            return state.measure(scene,typography,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT,
                    content -> naturalGeometry(content,typography,FloatingMenuFieldOfView.DEFAULT,1)).frame();
        }
    }

    public static org.encinet.mik.shared.menu.FloatingMenuPose pose(MenuScene scene, double typography, String localKey) {
        var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
        return geometry.poses().get(org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element(geometry.frame(), localKey).path());
    }

    public static org.encinet.mik.shared.menu.FloatingMenuPose readingPose(MenuScene scene, double typography) {
        var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
        var region = geometry.frame().readingRegion();
        return new ComponentSceneProjector(scene, geometry.frame(), FloatingMenuFieldOfView.DEFAULT, 1)
                .project(region.path(), region.x(), region.y(), region.size());
    }

    public static int textLines(MenuScene scene, double typography, String localKey) {
        var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
        String path = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element(geometry.frame(), localKey).path();
        return geometry.measurements().get(path).text().lines();
    }

    /** Real item content and geometry; avoids Paper registries that are irrelevant to layout. */
    public static ItemStack item() { return new LayoutItem(); }

    /** Check rendered icon and caption bounds, not merely that their enclosing cards are disjoint. */
    public static void assertVisualContentsFit(MenuScene scene, double typography) {
        assertVisualContentsFit(scene, typography, SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1));
    }

    public static void assertNaturalVisualContentsFit(MenuScene scene, double typography) {
        assertVisualContentsFit(scene, typography, naturalGeometry(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1));
    }

    private static void assertVisualContentsFit(MenuScene scene, double typography, SceneGeometry geometry) {
        for (var element : geometry.frame().elements()) {
            var entry = geometry.definition().entries().get(element.path());
            if (entry == null || entry.style() == FloatingMenuElementStyle.TEXT) continue;
            var size = geometry.sizes().get(element.path());
            var measurement = geometry.measurements().get(element.path());
            double factor = typography * element.node().style().textScale();
            double captionHeight = measurement.text().size().height() * factor;
            double renderedHeight = measurement.text().renderedHeight() * factor;
            for (double idle : new double[]{-scene.animation().idleAmplitude(), scene.animation().idleAmplitude()}) {
                var placement = FloatingMenuNodeGeometry.placement(entry.style(), captionHeight, 1, idle);
                double labelTop = placement.textUp() + renderedHeight / 2;
                String detail = element.path() + " textScale=" + factor + " idle=" + idle;
                assertTrue(Math.abs(placement.textUp()) + captionHeight / 2 <= size.height() / 2 + 1e-6, detail + ": caption outside card");
                assertTrue(measurement.text().contentWidth() * factor <= size.width() + 1e-6, detail + ": caption too wide");
                for (var state : FloatingMenuElementState.values()) {
                    double half = FloatingMenuNodeGeometry.visualScale(entry.style(), state) / 2;
                    assertTrue(placement.visualUp() - half - labelTop >= .08 - 1e-6, detail + " " + state + ": icon overlaps caption");
                    assertTrue(Math.abs(placement.visualUp()) + half <= size.height() / 2 + 1e-6, detail + " " + state + ": icon outside card");
                    assertTrue(half <= size.width() / 2 + 1e-6, detail + " " + state + ": icon too wide");
                }
                double readingPlane = FloatingMenuNodeGeometry.placement(FloatingMenuElementStyle.TEXT, captionHeight, 1, idle).textForward();
                assertEquals(readingPlane, placement.textForward(), 1e-6, detail + ": caption floats away from the reading plane");
                assertTrue(placement.textForward() <= FloatingMenuNodeGeometry.surfaceDepth(entry.style()) / 2 + 1e-6,
                        detail + ": caption missing from spatial bounds");
            }
        }
    }

    private static final class LayoutItem extends ItemStack {
        private LayoutItem() { super(); }
        @Override public LayoutItem clone() { return this; }
    }
}
