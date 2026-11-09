package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** A range has one hit surface and a few planar paint parts; live values never participate in layout. */
final class ComponentRangeLayout {
    private static final double UNIT = UiSceneContent.WORLD_PER_UI;
    private static final double TRACK_WIDTH = 144;
    private static final double MIN_TRACK_WIDTH = 64;
    private ComponentRangeLayout() { }

    static UiSize measure(UiRange range, double textScale, UiConstraints constraints) {
        double label = labelWidth(range, textScale);
        double gap = range.labels() ? 6 * textScale : 0;
        double minimumWidth = MIN_TRACK_WIDTH + 2 * (label / UNIT + gap);
        double height = Math.max(24, labelHeight(range, textScale) / UNIT);
        if (constraints.maxWidth() + 1e-6 < minimumWidth || constraints.maxHeight() + 1e-6 < height)
            throw new UiOverflowException("Range needs a usable track and hit surface within " + constraints);
        return constraints.constrain(new UiSize(Math.min(constraints.maxWidth(), TRACK_WIDTH + 2 * (label / UNIT + gap)), height));
    }

    private static double labelWidth(UiRange range, double textScale) {
        if (!range.labels()) return 0;
        // Formatters must reserve the same digit envelope throughout the declared range.
        return Math.max(text(range.text(range.minimum())).size().width(), text(range.text(range.maximum())).size().width()) * textScale;
    }

    private static double labelHeight(UiRange range, double textScale) {
        if (!range.labels()) return 0;
        return Math.max(text(range.text(range.minimum())).size().height(), text(range.text(range.maximum())).size().height()) * textScale;
    }

    static FloatingMenuNodeSizing.TextLayout text(Component text) {
        return FloatingMenuNodeSizing.measureText(text, false, 320);
    }

    static Map<String, FloatingMenuDecoration.RangePart> append(UiFrame frame, ComponentSceneProjector projector,
                                                                Map<String, FloatingMenuDecoration> output, double typography) {
        var ranges = new LinkedHashMap<String, FloatingMenuDecoration.RangePart>();
        for (var element : frame.elements()) {
            if (!(element.node().content() instanceof UiContent.Range content)) continue;
            var range = content.value();
            double textScale = typography * element.node().style().textScale();
            double label = labelWidth(range, textScale);
            var geometry = new UiRange.Geometry(element.size().width() * UNIT, element.size().height() * UNIT,
                    label, label, range.labels() ? 6 * textScale * UNIT : 0, textScale);
            var pose = projector.project(element.path(), element.x(), element.y(), element.size());
            for (var part : FloatingMenuDecoration.RangePart.Part.values()) {
                if (!range.labels() && (part == FloatingMenuDecoration.RangePart.Part.VALUE || part == FloatingMenuDecoration.RangePart.Part.MAXIMUM)) continue;
                String id = "$range/" + element.path() + "/" + part.name().toLowerCase(java.util.Locale.ROOT);
                var paint = new FloatingMenuDecoration.RangePart(element.path(), range, geometry, part, element.node().style().contentAlpha());
                var decoration = new FloatingMenuDecoration(id, pose, paint, FloatingMenuDecoration.Motion.NONE);
                output.put(id, projector.transformed(element.path()) ? decoration.tracking() : decoration);
                if (part == FloatingMenuDecoration.RangePart.Part.TRACK) ranges.put(element.path(), paint);
            }
        }
        return Map.copyOf(ranges);
    }
}
