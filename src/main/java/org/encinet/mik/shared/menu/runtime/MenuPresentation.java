package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Optional;
import java.util.function.BiFunction;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiThemeColors;
import org.encinet.mik.shared.menu.ui.UiFrame;

/** Owns a view's page layout and reading frame, including while the view is suspended. */
final class MenuPresentation {
    private final MenuLayoutState layout = new MenuLayoutState();
    private record Reference(FloatingMenuAnchorResolver.ReadingFrame reading, FloatingMenuSpatialFrame spatialFrame,
                             FloatingMenuAnchorMode anchorMode, double interfaceScale, double typography,
                             FloatingMenuFieldOfView fieldOfView) { }
    private Reference reference;
    private FloatingMenuWorldSpace placementSpace;
    private long worldEpoch;

    void reset() { reference = null; discardPlacementSpace(); }
    long worldEpoch() { return worldEpoch; }
    void discardPlacementSpace() { placementSpace = null; }

    /** Layout motion and companions share the parent's terrain reads; the presentation pass explicitly discards them. */
    FloatingMenuWorldSpace motionSpace(Player viewer, Location eye, double range) {
        var entities = MenuEntityObstacles.capture(viewer, eye, Math.min(range, FloatingMenuWorldSpace.MAX_TRACE_DISTANCE));
        if (placementSpace == null) placementSpace = new FloatingMenuWorldSpace(eye, entities);
        return placementSpace.forCompanion(eye, entities);
    }

    FloatingMenuWorldSpace companionSpace(Player viewer, Location eye, double range) {
        var space = motionSpace(viewer, eye, range);
        discardPlacementSpace();
        return space;
    }

    MenuTextMeasurementCache.Stats measurementStats() {
        return layout.measurements().stats();
    }

    void clearMeasurements() { layout.measurements().clear(); discardPlacementSpace(); }

    SceneGeometry worldLayout(MenuScene scene, org.encinet.mik.shared.menu.ui.UiSize viewport, double typography, UiThemeColors theme, int entityCapacity) {
        return layout.measureWithin(scene,viewport,typography,FloatingMenuFieldOfView.DEFAULT,1,theme,entityCapacity,
                content -> SceneGeometry.measureBase(content,typography,FloatingMenuFieldOfView.DEFAULT,1,theme,layout.measurements(),viewport));
    }

    record Frame(SceneGeometry geometry, Optional<FloatingMenuAnchorResolver.Anchor> anchor) { }

    /** Measure the parent, resolve its real reading frame, then measure the companion without a constraint cycle. */
    Frame prepare(Player viewer, Location eye, Vector forward, MenuScene scene, double typography,
                  double scale, FloatingMenuFieldOfView fov, UiThemeColors theme) {
        return prepare(viewer, eye, forward, scene, typography, scale, fov, theme, (content, _) -> content);
    }

    Frame prepare(Player viewer, Location eye, Vector forward, MenuScene scene, double typography,
                  double scale, FloatingMenuFieldOfView fov, UiThemeColors theme,
                  BiFunction<MenuScene, UiFrame, MenuScene> companion) {
        final class Pass {
            private Optional<FloatingMenuAnchorResolver.Anchor> anchor;
            private Optional<FloatingMenuAnchorResolver.Anchor> place(MenuScene content, SceneGeometry geometry) {
                try { return Optional.of(resolve(viewer, eye, forward, content, geometry, typography, scale, fov)); }
                catch (MenuSpaceUnavailableException unavailable) { return Optional.empty(); }
            }
            private SceneGeometry compile(MenuScene content) {
                SceneGeometry base = SceneGeometry.measureBase(content, typography, fov, scale, theme, layout.measurements());
                content = companion.apply(content, base.frame());
                anchor = place(content, base);
                MenuReadingParameters reading = anchor.map(value -> MenuReadingParameters.from(eye, value, fov, scale))
                        .orElseGet(() -> MenuReadingParameters.estimated(base.bounds(), fov, scale));
                return base.withPopover(content, typography, fov, scale, theme, reading, layout.measurements());
            }
        }
        var pass = new Pass();
        Reference previous = reference;
        try {
            SceneGeometry geometry = layout.measure(scene, typography, fov, scale, theme, pass::compile);
            return new Frame(geometry, pass.anchor == null ? pass.place(geometry.definition(), geometry) : pass.anchor);
        } catch (RuntimeException error) {
            reference = previous;
            discardPlacementSpace();
            throw error;
        }
    }

    FloatingMenuAnchorResolver.Anchor resolve(Location eye, Vector forward, MenuScene scene, SceneGeometry geometry,
                                              double typography, double interfaceScale, FloatingMenuFieldOfView fieldOfView) {
        return resolve(null, eye, forward, scene, geometry, typography, interfaceScale, fieldOfView);
    }

    FloatingMenuAnchorResolver.Anchor resolve(Player viewer, Location eye, Vector forward, MenuScene scene, SceneGeometry geometry,
                                              double typography, double interfaceScale, FloatingMenuFieldOfView fieldOfView) {
        double searchScale = scene.spatialFrame() == FloatingMenuSpatialFrame.IN_FRONT
                ? MenuViewportLayout.scale(interfaceScale) : interfaceScale;
        double range = MenuViewportLayout.distance(fieldOfView) + geometry.bounds().probes().stream()
                .mapToDouble(point -> Math.sqrt(point.right() * point.right() + point.up() * point.up()
                        + point.forward() * point.forward()) * searchScale).max().orElse(0) + 1;
        var space = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer, eye,
                Math.min(range, FloatingMenuWorldSpace.MAX_TRACE_DISTANCE)));
        placementSpace = space;
        worldEpoch++;
        if (scene.spatialFrame() == FloatingMenuSpatialFrame.AROUND_VIEWER) {
            reference = null;
            return FloatingMenuAnchorResolver.aroundViewer(eye, forward, interfaceScale, scene,
                    geometry.poses(), geometry.sizes(), geometry.bounds().titlePose(), space);
        }
        if (reference == null || scene.anchorMode() == FloatingMenuAnchorMode.ADAPTIVE
                || reference.spatialFrame() != scene.spatialFrame() || reference.anchorMode() != scene.anchorMode()
                || reference.typography() != typography || reference.interfaceScale() != interfaceScale || !fieldOfView.equals(reference.fieldOfView())) {
            var anchor = scene.spatialFrame() == FloatingMenuSpatialFrame.FRONT_ARC
                    ? FloatingMenuAnchorResolver.frontArcReference(eye, forward, interfaceScale, geometry.bounds(), fieldOfView, space)
                    : FloatingMenuAnchorResolver.reference(eye, forward, geometry.bounds(), interfaceScale, fieldOfView, space);
            if (scene.loading()) return FloatingMenuAnchorResolver.fit(eye, anchor, geometry.bounds(), space);
            var reading = anchor.relativeTo(eye);
            // Keep the nominal page scale independently of contraction caused by the opening content.
            if (scene.spatialFrame() == FloatingMenuSpatialFrame.IN_FRONT)
                reading = new FloatingMenuAnchorResolver.ReadingFrame(MenuViewportLayout.scale(interfaceScale), reading.distance(),
                        reading.verticalOffset(), reading.yawOffsetDegrees());
            reference = new Reference(reading, scene.spatialFrame(), scene.anchorMode(), interfaceScale, typography, fieldOfView);
        }
        var reading = scene.spatialFrame() == FloatingMenuSpatialFrame.IN_FRONT
                ? reference.reading().fittedTo(geometry.bounds(), fieldOfView.readingFraming()) : reference.reading();
        return FloatingMenuAnchorResolver.fit(eye, reading.at(eye, forward), geometry.bounds(), space);
    }
}
