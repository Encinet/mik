package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.runtime.layout.MenuLayoutState;
import org.encinet.mik.shared.menu.runtime.layout.MenuReadingParameters;
import org.encinet.mik.shared.menu.runtime.layout.MenuViewportLayout;
import org.encinet.mik.shared.menu.runtime.layout.SceneGeometry;
import org.encinet.mik.shared.menu.runtime.space.FloatingMenuAnchorResolver;
import org.encinet.mik.shared.menu.runtime.space.FloatingMenuWorldSpace;
import org.encinet.mik.shared.menu.runtime.space.MenuCollisionSpace;
import org.encinet.mik.shared.menu.runtime.space.MenuEntityObstacles;
import org.encinet.mik.shared.menu.runtime.space.MenuSpaceRefits;
import org.encinet.mik.shared.menu.runtime.space.MenuSpaceUnavailableException;
import org.encinet.mik.shared.menu.runtime.diagnostics.MenuTiming;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import java.util.Optional;
import java.util.function.BiFunction;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;
import org.encinet.mik.shared.menu.FloatingMenuAnchorMode;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuPreferences;
import org.encinet.mik.shared.menu.FloatingMenuSpatialFrame;
import org.encinet.mik.shared.menu.FloatingMenuViewpoint;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiThemeColors;
import org.encinet.mik.shared.menu.ui.UiFrame;

/** Owns a view's page layout and reading frame, including while the view is suspended. */
public final class MenuPresentation {
    private final MenuLayoutState layout = new MenuLayoutState();
    private record Reference(FloatingMenuAnchorResolver.ReadingFrame reading, FloatingMenuSpatialFrame spatialFrame,
                             FloatingMenuAnchorMode anchorMode, double interfaceScale, double typography,
                             FloatingMenuFieldOfView fieldOfView) {
        boolean matches(MenuScene scene, double scale, double textScale, FloatingMenuFieldOfView fov) {
            return spatialFrame == scene.spatialFrame() && anchorMode == scene.anchorMode()
                    && interfaceScale == scale && typography == textScale && fieldOfView.equals(fov);
        }
    }
    private Reference reference;
    private FloatingMenuAnchorResolver.ReadingFrame placedReading;
    void retainWindowPlacement(FloatingMenuAnchorResolver.Anchor anchor, Location eye) { placedReading = anchor.relativeTo(eye); }

    void reset() { reference = null; placedReading = null; }

    MenuTextMeasurementCache.Stats measurementStats() {
        return layout.measurements().stats();
    }

    void clearMeasurements() { layout.measurements().clear(); }

    public SceneGeometry worldLayout(MenuScene scene, org.encinet.mik.shared.menu.ui.UiSize viewport, double typography, UiThemeColors theme, int entityCapacity) {
        var timing = MenuTiming.start("world_layout");
        try {
            return layout.measureWithin(scene,viewport,typography,FloatingMenuFieldOfView.DEFAULT,1,theme,entityCapacity,
                    content -> SceneGeometry.measureBase(content,typography,FloatingMenuFieldOfView.DEFAULT,1,theme,layout.measurements(),viewport));
        } finally { MenuTiming.finish(timing); }
    }

    public record Frame(SceneGeometry geometry, Optional<FloatingMenuAnchorResolver.Anchor> anchor) { }

    SceneGeometry measure(MenuScene scene, FloatingMenuPreferences preferences,
                          BiFunction<MenuScene, UiFrame, MenuScene> companion) {
        double typography = preferences.typographyFactor(), scale = preferences.layout().factor();
        var fov = preferences.fieldOfView();
        var theme = preferences.theme();
        var timing = MenuTiming.start("layout");
        try {
            return layout.measureCandidate(scene, typography, fov, scale, theme, content -> {
                var base = SceneGeometry.measureBase(content, typography, fov, scale, theme, layout.measurements());
                var requested = companion.apply(content, base.frame());
                var reading = reference != null && reference.matches(content, scale, typography, fov)
                        ? MenuReadingParameters.from(new Location(null, 0, 0, 0),
                        reference.reading().at(new Location(null, 0, 0, 0), new Vector(0, 0, 1)), fov, scale)
                        : MenuReadingParameters.estimated(base.bounds(), fov, scale, content.readingFraming(fov));
                return base.withPopover(requested, typography, fov, scale, theme, reading, layout.measurements());
            });
        } finally { MenuTiming.finish(timing); }
    }

    void publish(SceneGeometry geometry, FloatingMenuPreferences preferences) {
        layout.publish(geometry, preferences.typographyFactor(), preferences.fieldOfView(), preferences.layout().factor(), preferences.theme());
    }

    /** Measure the parent, resolve its real reading frame, then measure the companion without a constraint cycle. */
    public Frame prepare(Player viewer, Location eye, Vector forward, MenuScene scene, double typography,
                  double scale, FloatingMenuFieldOfView fov, UiThemeColors theme) {
        return prepare(viewer, eye, forward, scene, typography, scale, fov, theme, (content, _) -> content);
    }

    public Frame prepare(Player viewer, Location eye, Vector forward, MenuScene scene, double typography,
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
                var readingFraming = content.readingFraming(fov);
                MenuReadingParameters reading = anchor.map(value -> MenuReadingParameters.from(eye, value, fov, scale))
                        .orElseGet(() -> MenuReadingParameters.estimated(base.bounds(), fov, scale, readingFraming));
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
        var placement = placement(scene, geometry, typography, interfaceScale, fieldOfView, false);
        var fit = placement.solve(eye, forward, space);
        adoptReference(placement, fit);
        return fit.anchor().orElseThrow(MenuSpaceUnavailableException::new);
    }

    Placement placement(SceneGeometry geometry, FloatingMenuPreferences preferences, boolean free) {
        return placement(geometry.definition(), geometry, preferences.typographyFactor(), preferences.layout().factor(),
                preferences.fieldOfView(), free);
    }

    private Placement placement(MenuScene scene, SceneGeometry geometry, double typography, double scale,
                                FloatingMenuFieldOfView fov, boolean free) {
        var frame = scene.spatialFrame();
        var bounds = frame == FloatingMenuSpatialFrame.AROUND_VIEWER
                ? FloatingMenuAnchorResolver.orbitalBounds(scene, geometry.poses(), geometry.sizes(), geometry.bounds().titlePose())
                : geometry.bounds();
        var retained = reference != null && reference.matches(scene, scale, typography, fov)
                && scene.anchorMode() != FloatingMenuAnchorMode.ADAPTIVE ? reference.reading() : null;
        return new Placement(bounds, frame, scene.anchorMode(), scene.viewpoint(), scale, typography, fov, scene.readingFraming(fov), scene.loading(),
                retained, free, free ? placedReading : null);
    }

    Placement refit(Placement placement) {
        if (reference == null || reference.reading().equals(placement.retained())) return placement;
        return new Placement(placement.bounds(), placement.frame(), placement.mode(), placement.viewpoint(), placement.scale(), placement.typography(),
                placement.fov(), placement.readingFraming(), placement.loading(), reference.reading(), placement.free(), placement.placed());
    }

    Optional<FloatingMenuAnchorResolver.Anchor> commitRefit(Placement placement, Fit fit, Location eye) {
        adoptReference(placement, fit);
        return fit.anchor().map(anchor -> new FloatingMenuAnchorResolver.Anchor(eye.clone().add(anchor.origin().toVector()),
                anchor.forward(), anchor.right(), anchor.spatialScale(), anchor.distance(), anchor.yawOffsetDegrees()));
    }

    private void adoptReference(Placement placement, Fit fit) {
        if (placement.frame() == FloatingMenuSpatialFrame.AROUND_VIEWER) reference = null;
        else if (fit.anchor().isPresent() && fit.reading() != null) reference = new Reference(fit.reading(), placement.frame(), placement.mode(),
                placement.scale(), placement.typography(), placement.fov());
    }

    record Fit(Optional<FloatingMenuAnchorResolver.Anchor> anchor, FloatingMenuAnchorResolver.ReadingFrame reading) {
        static Fit unavailable() { return new Fit(Optional.empty(), null); }
    }

    record Placement(FloatingMenuAnchorResolver.SceneBounds bounds, FloatingMenuSpatialFrame frame,
                     FloatingMenuAnchorMode mode, FloatingMenuViewpoint viewpoint, double scale, double typography, FloatingMenuFieldOfView fov,
                     FloatingMenuFraming readingFraming, boolean loading, FloatingMenuAnchorResolver.ReadingFrame retained, boolean free,
                     FloatingMenuAnchorResolver.ReadingFrame placed) {
        boolean sameTarget(Placement next) {
            return next != null && frame == next.frame && mode == next.mode && viewpoint == next.viewpoint && scale == next.scale && typography == next.typography
                    && fov.equals(next.fov) && readingFraming.equals(next.readingFraming) && loading == next.loading && free == next.free
                    && java.util.Objects.equals(placed, next.placed) && bounds.equals(next.bounds);
        }

        MenuSpaceRefits.Plan<Fit> plan(Vector forward) {
            var eye = new Location(null, 0, 0, 0);
            var region = samplingBounds(forward);
            if (!searches()) return MenuSpaceRefits.Plan.bounded(region,
                    (space, factor) -> contract(eye, solve(eye, forward, space), factor), Fit::unavailable);
            boolean arc = frame == FloatingMenuSpatialFrame.FRONT_ARC;
            var center = FloatingMenuAnchorResolver.centeredReference(eye, forward, bounds, scale, fov, readingFraming, arc);
            var reading = loading ? null : initialReading(center, eye);
            var target = loading ? center : effectiveReading(reading).at(eye, forward);
            var first = FloatingMenuAnchorResolver.samplingBounds(center, bounds)
                    .union(FloatingMenuAnchorResolver.samplingBounds(target, bounds));
            return MenuSpaceRefits.Plan.bounded(region, (space, factor) -> contract(eye, solve(eye, forward, space), factor),
                    Fit::unavailable, first, (space, factor) -> {
                        double clear = FloatingMenuAnchorResolver.fitFactor(eye, center.origin(), center.forward(), center.right(),
                                center.spatialScale(), bounds, space);
                        if (!FloatingMenuAnchorResolver.centerDominates(center, clear, arc)) return null;
                        try {
                            var verified = center.equals(target) ? center.contract(eye, clear)
                                    : FloatingMenuAnchorResolver.fit(eye, target, bounds, space);
                            return contract(eye, new Fit(Optional.of(verified), reading), factor);
                        } catch (MenuSpaceUnavailableException unavailable) { return null; }
                    });
        }

        private static Fit contract(Location eye, Fit fit, double factor) {
            return factor == 1 ? fit : new Fit(fit.anchor().map(anchor -> anchor.contract(eye, factor)), fit.reading());
        }

        private boolean searches() {
            return frame != FloatingMenuSpatialFrame.AROUND_VIEWER && retained == null && !(free && placed != null);
        }

        BoundingBox samplingBounds(Vector forward) {
            var eye = new Location(null, 0, 0, 0);
            if (frame == FloatingMenuSpatialFrame.AROUND_VIEWER) {
                var reading = new FloatingMenuAnchorResolver.ReadingFrame(scale, 0, 0, 0);
                return FloatingMenuAnchorResolver.samplingBounds(reading.at(eye, forward), bounds);
            }
            if (searches())
                return FloatingMenuAnchorResolver.searchBounds(bounds, fov, scale, forward, frame == FloatingMenuSpatialFrame.FRONT_ARC);
            return FloatingMenuAnchorResolver.samplingBounds(effectiveReading(retained).at(eye, forward), bounds);
        }

        Fit solve(Location eye, Vector forward, MenuCollisionSpace space) {
            var reading = retained;
            try {
                if (frame == FloatingMenuSpatialFrame.AROUND_VIEWER)
                    return new Fit(Optional.of(FloatingMenuAnchorResolver.aroundViewer(eye, forward, scale, bounds, space)), null);
                if (reading == null && free && placed != null) reading = placed;
                if (reading == null) {
                    var anchor = frame == FloatingMenuSpatialFrame.FRONT_ARC
                            ? FloatingMenuAnchorResolver.frontArcReference(eye, forward, scale, bounds, fov, space)
                            : FloatingMenuAnchorResolver.reference(eye, forward, bounds, scale, fov, readingFraming, space);
                    if (loading) return new Fit(Optional.of(FloatingMenuAnchorResolver.fit(eye, anchor, bounds, space)), null);
                    reading = initialReading(anchor, eye);
                }
                var anchor = FloatingMenuAnchorResolver.fit(eye, effectiveReading(reading).at(eye, forward), bounds, space);
                return new Fit(Optional.of(anchor), reading);
            } catch (MenuSpaceUnavailableException unavailable) {
                return new Fit(Optional.empty(), reading);
            }
        }

        private FloatingMenuAnchorResolver.ReadingFrame initialReading(FloatingMenuAnchorResolver.Anchor anchor, Location eye) {
            var reading = anchor.relativeTo(eye);
            return frame != FloatingMenuSpatialFrame.IN_FRONT ? reading
                    : new FloatingMenuAnchorResolver.ReadingFrame(MenuViewportLayout.scale(scale), reading.distance(),
                    reading.verticalOffset(), reading.yawOffsetDegrees());
        }

        private FloatingMenuAnchorResolver.ReadingFrame effectiveReading(FloatingMenuAnchorResolver.ReadingFrame reading) {
            if (free && placed != null) return placed;
            return frame == FloatingMenuSpatialFrame.IN_FRONT && !free ? reading.fittedTo(bounds, readingFraming) : reading;
        }
    }
}
