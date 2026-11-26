package org.encinet.mik.shared.menu.runtime.layout;

import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.encinet.mik.shared.menu.ui.UiLength;

import java.util.Objects;

/** Shared visual, label and animation envelope in node-local units, before scene scaling and rotation. */
public final class FloatingMenuNodeGeometry {
    private static final double LABEL_FORWARD = 0.018;
    private static final double LABEL_GAP = 0.12;
    private static final double EDGE_PADDING = 0.08;
    private static final double MODEL_PADDING = 0.04;
    private static final double MAX_VISUAL_MULTIPLIER = 1.22;

    private FloatingMenuNodeGeometry() { }

    /** The renderer and its stable layout envelope share these state sizes. */
    public static double visualScale(FloatingMenuElementStyle style, FloatingMenuElementState state) {
        return visualScale(style, state, UiLength.ZERO);
    }

    public static double visualScale(FloatingMenuElementStyle style, FloatingMenuElementState state, UiLength modelSize) {
        if (modelSize.value() > 0) return modelSize.value() * UiSceneContent.WORLD_PER_UI;
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

    private static double visualExtent(FloatingMenuElementStyle style, UiLength modelSize) {
        return visualScale(style, FloatingMenuElementState.HOVERED, modelSize) + MODEL_PADDING * 2;
    }

    public static Placement placement(FloatingMenuElementStyle style,
                               double textHeight, double spatialScale, double idleOffset) {
        return placement(style, textHeight, spatialScale, idleOffset, UiLength.ZERO);
    }

    public static Placement placement(FloatingMenuElementStyle style,
                                      double textHeight, double spatialScale, double idleOffset, UiLength modelSize) {
        if (style == null || !Double.isFinite(spatialScale) || spatialScale <= 0
                || !Double.isFinite(idleOffset) || !Double.isFinite(textHeight) || textHeight < 0)
            throw new IllegalArgumentException("Invalid node geometry");
        if (style == FloatingMenuElementStyle.TEXT) return new Placement(0, 0, 0, LABEL_FORWARD * spatialScale);
        double visual = visualExtent(style, modelSize);
        double gap = textHeight > 0 ? LABEL_GAP : 0;
        double forward = modelSize.value() > 0 ? visual / 2 : 0;
        // Center the whole icon/gap/label stack. Growing a caption moves the icon up,
        // rather than letting new lines grow through an icon at a fixed offset.
        // Icon thickness belongs to the model envelope, never to the caption's reading plane.
        return new Placement(((textHeight + gap) / 2 + idleOffset) * spatialScale, forward * spatialScale,
                (-(visual + gap) / 2 + idleOffset) * spatialScale,
                LABEL_FORWARD * spatialScale);
    }

    static FloatingMenuSize footprint(FloatingMenuElementStyle style, FloatingMenuSize text, double idleAmplitude) {
        return footprint(style, text, idleAmplitude, UiLength.ZERO, true);
    }

    static FloatingMenuSize footprint(FloatingMenuElementStyle style, FloatingMenuSize text, double idleAmplitude,
                                      UiLength modelSize, boolean hasCaption) {
        Objects.requireNonNull(text, "text");
        if (style == FloatingMenuElementStyle.TEXT) return text;
        double width = visualExtent(style, modelSize) + EDGE_PADDING * 2;
        return new FloatingMenuSize(hasCaption ? Math.max(text.width(), width) : width,
                (hasCaption ? text.height() : 0) + modelHeight(style, idleAmplitude, modelSize, hasCaption));
    }

    static double modelHeight(FloatingMenuElementStyle style, double idleAmplitude, UiLength modelSize, boolean hasCaption) {
        Objects.requireNonNull(style, "style");
        if (!Double.isFinite(idleAmplitude) || idleAmplitude < 0) throw new IllegalArgumentException("Invalid idle amplitude");
        if (style == FloatingMenuElementStyle.TEXT) return 0;
        return visualExtent(style, modelSize) + (hasCaption ? LABEL_GAP : 0) + EDGE_PADDING * 2 + idleAmplitude * 2;
    }

    public static double surfaceDepth(FloatingMenuElementStyle style) {
        return surfaceDepth(style, UiLength.ZERO);
    }

    public static double surfaceDepth(FloatingMenuElementStyle style, UiLength modelSize) {
        // A raised model occupies the space entirely in front of the card. The shared
        // collision surface is symmetric, so reserve its full forward reach on each side.
        return style == FloatingMenuElementStyle.TEXT ? 0
                : visualExtent(style, modelSize) * (modelSize.value() > 0 ? 2 : 1) + LABEL_FORWARD * 2;
    }

    public static double scaledDistance(double distance, double spatialScale) {
        if (!Double.isFinite(distance) || !Double.isFinite(spatialScale) || spatialScale <= 0)
            throw new IllegalArgumentException("Invalid scaled distance");
        return distance * spatialScale;
    }

    /** The client anchors text at its bottom; move it down to retain the measured center. */
    public static double textAnchorDown(double renderedHeight, double spatialScale) {
        if (!Double.isFinite(renderedHeight) || renderedHeight < 0
                || !Double.isFinite(spatialScale) || spatialScale <= 0)
            throw new IllegalArgumentException("Invalid text anchor geometry");
        return renderedHeight * spatialScale * .5;
    }

    public record Placement(double visualUp, double visualForward, double textUp, double textForward) { }
}
