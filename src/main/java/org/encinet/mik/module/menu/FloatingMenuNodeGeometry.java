package org.encinet.mik.module.menu;

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

    private FloatingMenuNodeGeometry() {
    }

    static Placement placement(FloatingMenuElementStyle style,
                               double spatialScale, double idleOffset) {
        if (style == null || !Double.isFinite(spatialScale) || spatialScale <= 0.0
                || !Double.isFinite(idleOffset)) {
            throw new IllegalArgumentException("Invalid node geometry");
        }
        boolean visual = style != FloatingMenuElementStyle.TEXT;
        return new Placement(
                visual ? (VISUAL_CENTER_UP + idleOffset) * spatialScale : 0.0,
                visual ? -LABEL_CENTER_DOWN * spatialScale : 0.0,
                LABEL_FORWARD * spatialScale);
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
    static double textAnchorDown(FloatingMenuNodeSizing.TextLayout text,
                                 double spatialScale) {
        if (text == null || !Double.isFinite(spatialScale) || spatialScale <= 0.0) {
            throw new IllegalArgumentException("Invalid text anchor geometry");
        }
        return text.renderedHeight() * spatialScale * 0.5;
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

    record Placement(double visualUp, double textUp, double textForward) {
    }
}
