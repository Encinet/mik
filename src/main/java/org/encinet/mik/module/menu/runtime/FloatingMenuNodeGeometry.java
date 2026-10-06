package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuSize;

/**
 * Converts node-local presentation distances into the scene's current scale.
 * Keeping these offsets together prevents icons, labels and motion from
 * separating when a large menu is reduced to fit the player's view.
 */
final class FloatingMenuNodeGeometry {
    private static final double VISUAL_CENTER_UP = 0.06;
    private static final double LABEL_CENTER_DOWN = 0.30;
    private static final double LABEL_FORWARD = 0.018;
    private static final double VISUAL_EDGE_PADDING = 0.08;
    private static final double BLOCK_VISUAL_SIZE = 0.46;
    private static final double BLOCK_LABEL_GAP = 0.08;

    private FloatingMenuNodeGeometry() {
    }

    static Placement placement(FloatingMenuElementStyle style,
                               double textHeight, double spatialScale, double idleOffset) {
        if (style == null || !Double.isFinite(spatialScale) || spatialScale <= 0.0
                || !Double.isFinite(idleOffset) || !Double.isFinite(textHeight)
                || textHeight < 0.0) {
            throw new IllegalArgumentException("Invalid node geometry");
        }
        boolean visual = style != FloatingMenuElementStyle.TEXT;
        boolean block = style == FloatingMenuElementStyle.BLOCK;
        double textUp = block ? blockLabelUp(textHeight) + idleOffset
                : visual ? -LABEL_CENTER_DOWN : 0.0;
        return new Placement(
                visual ? (VISUAL_CENTER_UP + idleOffset) * spatialScale : 0.0,
                textUp * spatialScale,
                (block ? BLOCK_VISUAL_SIZE * 0.5 + LABEL_FORWARD : LABEL_FORWARD)
                        * spatialScale);
    }

    private static double blockLabelUp(double textHeight) {
        return Math.min(-LABEL_CENTER_DOWN,
                VISUAL_CENTER_UP - BLOCK_VISUAL_SIZE * 0.5
                        - BLOCK_LABEL_GAP - textHeight * 0.5);
    }

    static double surfaceDepth(FloatingMenuElementStyle style) {
        return style == FloatingMenuElementStyle.BLOCK
                ? BLOCK_VISUAL_SIZE + LABEL_FORWARD * 2.0 : 0.0;
    }

    static double scaledDistance(double distance, double spatialScale) {
        if (!Double.isFinite(distance) || !Double.isFinite(spatialScale)
                || spatialScale <= 0.0) {
            throw new IllegalArgumentException("Invalid scaled distance");
        }
        return distance * spatialScale;
    }

    /**
     * Text displays are bottom-anchored by the client renderer. This offset moves
     * that anchor down so the measured text center remains on the layout pose.
     */
    static double textAnchorDown(double renderedHeight,
                                 double spatialScale) {
        if (!Double.isFinite(renderedHeight) || renderedHeight < 0.0
                || !Double.isFinite(spatialScale) || spatialScale <= 0.0) {
            throw new IllegalArgumentException("Invalid text anchor geometry");
        }
        return renderedHeight * spatialScale * 0.5;
    }

    /** Symmetric layout envelope containing both the raised visual and lower label. */
    static FloatingMenuSize visualFootprint(FloatingMenuSize text, double visualSize) {
        if (text == null || !Double.isFinite(visualSize) || visualSize <= 0.0) {
            throw new IllegalArgumentException("Invalid visual footprint");
        }
        double width = Math.max(visualSize + 0.12, text.width());
        double top = VISUAL_CENTER_UP + visualSize * 0.5 + VISUAL_EDGE_PADDING;
        double bottom = Math.max(visualSize * 0.5 - VISUAL_CENTER_UP,
                LABEL_CENTER_DOWN + text.height() * 0.5);
        return new FloatingMenuSize(width, Math.max(top, bottom) * 2.0);
    }

    static FloatingMenuSize blockFootprint(FloatingMenuSize text, double idleAmplitude) {
        if (text == null || !Double.isFinite(idleAmplitude) || idleAmplitude < 0.0) {
            throw new IllegalArgumentException("Invalid block footprint");
        }
        double width = Math.max(BLOCK_VISUAL_SIZE + 0.12, text.width());
        double top = VISUAL_CENTER_UP + BLOCK_VISUAL_SIZE * 0.5 + VISUAL_EDGE_PADDING;
        double bottom = -blockLabelUp(text.height()) + text.height() * 0.5;
        return new FloatingMenuSize(width, (Math.max(top, bottom) + idleAmplitude) * 2.0);
    }

    record Placement(double visualUp, double textUp, double textForward) {
    }
}
