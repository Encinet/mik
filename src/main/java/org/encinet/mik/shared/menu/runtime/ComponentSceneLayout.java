package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/** Adapts pure component geometry to the existing world, packet and collision presenters. */
final class ComponentSceneLayout {
    private ComponentSceneLayout() { }

    record Result(Map<String, FloatingMenuPose> poses, Map<String, FloatingMenuSize> sizes,
                  Map<String, FloatingMenuNodeSizing.Measurement> measurements,
                  Map<String, FloatingMenuDecoration> generatedDecorations,
                  Map<String, FloatingMenuDecoration.RangePart> ranges,
                  Map<String, FloatingMenuDecoration.IconPart> icons,
                  List<MenuPaginationInput.Region> pagination,
                  List<FloatingMenuAnchorResolver.Surface> surfaceBounds, UiFrame frame) { }

    static Result measure(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale) {
        return measure(scene, typography, fov, scale, UiPalette.DEFAULT);
    }

    static Result measure(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale,
                          UiThemeColors theme) {
        return measure(scene, typography, fov, scale, theme, null);
    }

    static Result measure(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale,
                          UiThemeColors theme, MenuTextMeasurementCache cache) {
        return measure(scene, typography, fov, scale, theme, cache, scene.viewport());
    }

    static Result measure(MenuScene scene, double typography, FloatingMenuFieldOfView fov, double scale,
                          UiThemeColors theme, MenuTextMeasurementCache cache, UiSize viewport) {
        var constraints = UiConstraints.bounded(viewport.width(), viewport.height());
        var documentMeasurements = cache == null ? new MenuTextMeasurementCache() : cache;
        var measurer = new UiCompiler.ElementMeasurer() {
                    @Override public boolean heightIndependent(UiElement node) {
                        return node.content() instanceof UiContent.Text text && (text.singleLine() || !text.wrapping());
                    }
                    @Override public Optional<List<Integer>> documentPages(UiElement node, UiConstraints constraints) {
                        double textScale = typography * node.style().textScale();
                        int width = Math.min(node.textWidthPixels(), FloatingMenuNodeSizing.textWidthPixels(
                                constraints.maxWidth() * UiSceneContent.WORLD_PER_UI, textScale));
                        int lines = FloatingMenuNodeSizing.textLines(constraints.maxHeight() * UiSceneContent.WORLD_PER_UI, textScale);
                        if (width < 1 || lines < 1) throw new UiOverflowException("Document paragraph needs one readable line");
                        var source = node.content().label();
                        return Optional.of(documentMeasurements.documentLines(source, width,
                                () -> UiTextMetrics.readingLines(source, width)).pageEnds(lines));
                    }
                    @Override public UiSize minimum(UiElement node) {
                        if (node.content() instanceof UiContent.Icon) return ComponentIconLayout.minimum(node, typography);
                        if (node.content() instanceof UiContent.Text text && !text.wrapping())
                            return unwrappedText(node, typography).size();
                        return UiSize.ZERO;
                    }

                    @Override public UiCompiler.ElementMeasurement measure(UiElement node, UiConstraints constraints) {
                        return cache == null ? measureElement(scene, node, constraints, typography)
                                : cache.measure(node, constraints, typography, scene.animation().idleAmplitude(),
                                        () -> measureElement(scene, node, constraints, typography));
                    }
                };
        UiFrame frame = UiCompiler.compile(scene.content(), constraints, measurer);
        var projector = new ComponentSceneProjector(scene, frame, fov, scale);
        var pagination = new ArrayList<MenuPaginationInput.Region>();
        for (var page : frame.pagination()) if (page.size().width() > 0 && page.size().height() > 0) {
            pagination.add(new MenuPaginationInput.Region(page, projector.project(page.path(), page.x(), page.y(), page.size()),
                    new FloatingMenuSize(page.size().width() * UiSceneContent.WORLD_PER_UI,
                            page.size().height() * UiSceneContent.WORLD_PER_UI)));
        }
        Map<String, FloatingMenuPose> poses = new LinkedHashMap<>();
        Map<String, FloatingMenuSize> sizes = new LinkedHashMap<>();
        Map<String, FloatingMenuNodeSizing.Measurement> measurements = new LinkedHashMap<>();
        List<FloatingMenuAnchorResolver.Surface> surfaceBounds = new ArrayList<>();
        for (UiFrame.Panel panel : frame.panels()) addSurfaceBounds(projector, panel.path(), panel.x(), panel.y(), panel.size(), surfaceBounds);
        for (UiFrame.Surface surface : frame.surfaces()) addSurfaceBounds(projector, surface.path(), surface.x(), surface.y(), surface.size(), surfaceBounds);
        for (UiFrame.Element element : frame.elements()) {
            if (element.node().content() instanceof UiContent.Image) {
                addSurfaceBounds(projector, element.path(), element.x(), element.y(), element.size(), surfaceBounds);
                continue;
            }
            double unit = UiSceneContent.WORLD_PER_UI;
            FloatingMenuPose local = projector.project(element.path(), element.x(), element.y(), element.size());
            poses.put(element.path(), local);
            sizes.put(element.path(), new FloatingMenuSize(element.size().width() * unit, element.size().height() * unit));
            measurements.put(element.path(), element.node().content() instanceof UiContent.Range || element.node().content() instanceof UiContent.Icon
                    ? new FloatingMenuNodeSizing.Measurement(ComponentRangeLayout.text(Component.empty()), sizes.get(element.path()))
                    : FloatingMenuNodeSizing.measure(
                    UiSceneContent.entry(element.path(), element.node()), typography * element.node().style().textScale(),
                    scene.animation().idleAmplitude(), element.node().textWidthPixels()));
        }
        Map<String, FloatingMenuDecoration> backgrounds = new LinkedHashMap<>();
        for (UiFrame.Surface surface : frame.surfaces()) {
            if (surface.style().backgroundAlpha(theme) == 0 || surface.size().width() == 0 || surface.size().height() == 0) continue;
            FloatingMenuPose pose = projector.project(surface.path(), surface.x(), surface.y(), surface.size());
            double depth = -0.04 + Math.min(20, surface.path().split("/").length) * 0.001;
            pose = offsetNormal(pose, depth);
            String id = "$surface/" + surface.path();
            var decoration = new FloatingMenuDecoration(id, pose,
                    new FloatingMenuDecoration.Surface((float) (surface.size().width() * UiSceneContent.WORLD_PER_UI),
                            (float) (surface.size().height() * UiSceneContent.WORLD_PER_UI), surface.style().backgroundArgb(theme)),
                    FloatingMenuDecoration.Motion.NONE);
            backgrounds.put(id, projector.transformed(surface.path()) ? decoration.tracking() : decoration);
        }
        ComponentImageLayout.append(frame, projector, backgrounds, theme);
        var ranges = ComponentRangeLayout.append(frame, projector, backgrounds, typography);
        var icons = ComponentIconLayout.append(frame, projector, backgrounds, typography);
        return new Result(Map.copyOf(poses), Map.copyOf(sizes), Map.copyOf(measurements), Map.copyOf(backgrounds), ranges, icons, List.copyOf(pagination),
                surfaceBounds.stream().distinct().toList(), frame);
    }

    private static UiCompiler.ElementMeasurement measureElement(MenuScene scene, UiElement node,
                                                                 UiConstraints constraints, double typography) {
        if (node.content() instanceof UiContent.Icon)
            return new UiCompiler.ElementMeasurement(ComponentIconLayout.measure(node, typography, constraints), node.textWidthPixels());
        if (node.content() instanceof UiContent.Range range) {
            return new UiCompiler.ElementMeasurement(ComponentRangeLayout.measure(range.value(),
                    typography * node.style().textScale(), constraints), node.textWidthPixels());
        }
        if (node.content() instanceof UiContent.RangeValue reading) {
            double textScale = typography * node.style().textScale();
            double width = 0, height = 0;
            for (Component label : List.of(reading.value().text(reading.value().minimum()),
                    reading.value().text(reading.value().maximum()), reading.label())) {
                var size = FloatingMenuNodeSizing.measureText(label, node.input().interactive(), node.textWidthPixels()).size();
                width = Math.max(width, size.width() * textScale / UiSceneContent.WORLD_PER_UI);
                height = Math.max(height, size.height() * textScale / UiSceneContent.WORLD_PER_UI);
            }
            if (width > constraints.maxWidth() + 1e-6 || height > constraints.maxHeight() + 1e-6)
                throw new UiOverflowException("Numeric reading must fit in full within " + constraints);
            UiSize size = constraints.constrain(new UiSize(width, height));
            var text = FloatingMenuNodeSizing.measureText(reading.label(), node.input().interactive(), node.textWidthPixels());
            return new UiCompiler.ElementMeasurement(size, node.textWidthPixels())
                    .withBaseline(FloatingMenuNodeSizing.baseline(text, textScale, size.height()));
        }
        if (node.content() instanceof UiContent.Text text && !text.wrapping()) {
            var natural = unwrappedText(node, typography);
            if (natural.size().width() > constraints.maxWidth() + 1e-6 || natural.size().height() > constraints.maxHeight() + 1e-6)
                throw new UiOverflowException("Complete unwrapped text needs " + natural.size() + " within " + constraints);
            return natural;
        }
        if (node.content() instanceof UiContent.Image image) {
            double width = image.source().pixelWidth(), height = image.source().pixelHeight();
            double shrink = Math.min(1, Math.min(constraints.maxWidth() / width, constraints.maxHeight() / height));
            return new UiCompiler.ElementMeasurement(new UiSize(width * shrink, height * shrink), node.textWidthPixels());
        }
        MenuScene.Entry entry = UiSceneContent.entry("measuring", node);
        double textScale = typography * node.style().textScale();
        int pixels = node.textWidthPixels();
        double available = constraints.maxWidth() * UiSceneContent.WORLD_PER_UI;
        Optional<Component> fittedLabel = Optional.empty();
        // Resolve the final wrapping width before scanning the source. Trying successively
        // narrower widths rescans every off-screen document fragment during footer fitting.
        if (node.content() instanceof UiContent.Text text && text.wrapping())
            pixels = Math.max(1, Math.min(pixels, FloatingMenuNodeSizing.textWidthPixels(available, textScale)));
        if (node.content() instanceof UiContent.Text text && text.maximumLines() < Integer.MAX_VALUE) {
            pixels = Math.max(1, Math.min(pixels, FloatingMenuNodeSizing.textWidthPixels(available, textScale)));
            int lines = Math.max(1, Math.min(text.maximumLines(), FloatingMenuNodeSizing.textLines(
                    constraints.maxHeight() * UiSceneContent.WORLD_PER_UI, textScale)));
            var label = text.singleLine() ? UiTextMetrics.fitSingleLine(text.label(), pixels)
                    : UiTextMetrics.fitLines(text.label(), pixels, lines);
            fittedLabel = Optional.of(label);
            entry = UiSceneContent.entry("measuring", node.withText(label));
        }
        var measurement = FloatingMenuNodeSizing.measure(entry, textScale, scene.animation().idleAmplitude(), pixels);
        double widthTolerance = 1e-6 * UiSceneContent.WORLD_PER_UI;
        if (measurement.footprint().width() > available + widthTolerance) {
            int low = 1, high = pixels;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                var candidate = FloatingMenuNodeSizing.measure(entry, textScale, scene.animation().idleAmplitude(), middle);
                if (candidate.footprint().width() <= available + widthTolerance) low = middle;
                else high = middle - 1;
            }
            pixels = low;
            measurement = FloatingMenuNodeSizing.measure(entry, textScale, scene.animation().idleAmplitude(), pixels);
        }
        UiSize size = new UiSize(measurement.footprint().width() / UiSceneContent.WORLD_PER_UI,
                measurement.footprint().height() / UiSceneContent.WORLD_PER_UI);
        if (size.width() > constraints.maxWidth() + 1e-6 || size.height() > constraints.maxHeight() + 1e-6) {
            throw new UiOverflowException("Content needs " + size + " within " + constraints);
        }
        var result = new UiCompiler.ElementMeasurement(size, pixels, fittedLabel);
        return node.content() instanceof UiContent.Text
                ? result.withBaseline(FloatingMenuNodeSizing.baseline(measurement.text(), textScale, size.height())) : result;
    }

    private static UiCompiler.ElementMeasurement unwrappedText(UiElement node, double typography) {
        int pixels = UiTextMetrics.measure(node.content().label(), Integer.MAX_VALUE).widthPixels();
        var text = FloatingMenuNodeSizing.measureText(node.content().label(), node.input().interactive(), pixels);
        double scale = typography * node.style().textScale() / UiSceneContent.WORLD_PER_UI;
        UiSize size = new UiSize(text.size().width() * scale, text.size().height() * scale);
        return new UiCompiler.ElementMeasurement(size, pixels)
                .withBaseline(FloatingMenuNodeSizing.baseline(text, typography * node.style().textScale(), size.height()));
    }

    private static void addSurfaceBounds(ComponentSceneProjector projector, String path, double x, double y, UiSize size,
                                         List<FloatingMenuAnchorResolver.Surface> output) {
        if (size.width() == 0 || size.height() == 0) return;
        output.add(new FloatingMenuAnchorResolver.Surface(projector.project(path, x, y, size),
                new FloatingMenuSize(size.width() * UiSceneContent.WORLD_PER_UI, size.height() * UiSceneContent.WORLD_PER_UI), .1));
    }

    static FloatingMenuPose offsetNormal(FloatingMenuPose pose, double distance) {
        var normal = FloatingMenuSurfaceGeometry.facing((float) pose.yawDegrees(), (float) pose.pitchDegrees());
        return pose.offset(normal.getX() * distance * pose.scale(), normal.getY() * distance * pose.scale(), normal.getZ() * distance * pose.scale());
    }
}
