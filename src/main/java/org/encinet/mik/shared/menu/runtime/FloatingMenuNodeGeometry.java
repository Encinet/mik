package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import org.encinet.mik.shared.menu.FloatingMenuSize;

import java.util.Objects;

/** Shared visual, label and animation envelope in node-local units, before scene scaling and rotation. */
final class FloatingMenuNodeGeometry {
    private static final double LABEL_FORWARD = 0.018;
    private static final double LABEL_GAP = 0.12;
    private static final double EDGE_PADDING = 0.08;
    private static final double MODEL_PADDING = 0.04;
    private static final double MAX_VISUAL_MULTIPLIER = 1.22;

    private FloatingMenuNodeGeometry() { }

    /** The renderer and its stable layout envelope share these state sizes. */
    static double visualScale(FloatingMenuElementStyle style, FloatingMenuElementState state) {
        double base = switch (Objects.requireNonNull(style, "style")) {
            case TEXT -> 0;
            case ITEM -> .50;
            case BLOCK -> .32;
        };
        return base * switch (Objects.requireNonNull(state, "state")) {
            case NORMAL -> 1;
            case SELECTED -> 1.10;
            case HOVERED -> MAX_VISUAL_MULTIPLIER;
            case PRESSED -> .88;
            case DISABLED -> .92;
        };
    }

    private static double visualExtent(FloatingMenuElementStyle style) {
        return visualScale(style, FloatingMenuElementState.HOVERED) + MODEL_PADDING * 2;
    }

    static Placement placement(FloatingMenuElementStyle style,
                               double textHeight, double spatialScale, double idleOffset) {
        if (style == null || !Double.isFinite(spatialScale) || spatialScale <= 0
                || !Double.isFinite(idleOffset) || !Double.isFinite(textHeight) || textHeight < 0)
            throw new IllegalArgumentException("Invalid node geometry");
        if (style == FloatingMenuElementStyle.TEXT) return new Placement(0, 0, LABEL_FORWARD * spatialScale);
        double visual = visualExtent(style);
        // Center the whole icon/gap/label stack. Growing a caption moves the icon up,
        // rather than letting new lines grow through an icon at a fixed offset.
        // Icon thickness belongs to the model envelope, never to the caption's reading plane.
        return new Placement(((textHeight + LABEL_GAP) / 2 + idleOffset) * spatialScale,
                (-(visual + LABEL_GAP) / 2 + idleOffset) * spatialScale,
                LABEL_FORWARD * spatialScale);
    }

    static FloatingMenuSize footprint(FloatingMenuElementStyle style, FloatingMenuSize text, double idleAmplitude) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(text, "text");
        if (!Double.isFinite(idleAmplitude) || idleAmplitude < 0) throw new IllegalArgumentException("Invalid idle amplitude");
        if (style == FloatingMenuElementStyle.TEXT) return text;
        double visual = visualExtent(style);
        return new FloatingMenuSize(Math.max(text.width(), visual + EDGE_PADDING * 2),
                visual + LABEL_GAP + text.height() + EDGE_PADDING * 2 + idleAmplitude * 2);
    }

    static double surfaceDepth(FloatingMenuElementStyle style) {
        return style == FloatingMenuElementStyle.TEXT ? 0 : visualExtent(style) + LABEL_FORWARD * 2;
    }

    static double scaledDistance(double distance, double spatialScale) {
        if (!Double.isFinite(distance) || !Double.isFinite(spatialScale) || spatialScale <= 0)
            throw new IllegalArgumentException("Invalid scaled distance");
        return distance * spatialScale;
    }

    /** The client anchors text at its bottom; move it down to retain the measured center. */
    static double textAnchorDown(double renderedHeight, double spatialScale) {
        if (!Double.isFinite(renderedHeight) || renderedHeight < 0
                || !Double.isFinite(spatialScale) || spatialScale <= 0)
            throw new IllegalArgumentException("Invalid text anchor geometry");
        return renderedHeight * spatialScale * .5;
    }

    record Placement(double visualUp, double textUp, double textForward) { }
}
