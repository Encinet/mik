package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** Bounded local artwork. Frames contain only rectangles, never callbacks or live feature state. */
public record UiIcon(List<Frame> frames, int ticksPerFrame, int reducedFrame) {
    public static final int CANVAS = 24, MAX_PARTS = 16, MAX_FRAMES = 32;

    public UiIcon {
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > MAX_FRAMES || ticksPerFrame < 2 || ticksPerFrame > 20
                || reducedFrame < 0 || reducedFrame >= frames.size())
            throw new IllegalArgumentException("Invalid icon frame sequence");
        int count = frames.getFirst().parts().size();
        if (frames.stream().anyMatch(frame -> frame.parts().size() != count))
            throw new IllegalArgumentException("Icon frames must retain their part identities");
    }

    public static UiIcon of(Part... parts) { return new UiIcon(List.of(new Frame(List.of(parts))), 2, 0); }
    public boolean animated() { return frames.size() > 1; }
    public int partCount() { return frames.getFirst().parts().size(); }
    public Frame sample(long ticks, boolean reducedMotion) {
        int index = reducedMotion ? reducedFrame : Math.floorMod(Math.floorDiv(ticks, ticksPerFrame), frames.size());
        return frames.get(index);
    }

    public record Frame(List<Part> parts) {
        public Frame {
            parts = List.copyOf(parts);
            if (parts.isEmpty() || parts.size() > MAX_PARTS) throw new IllegalArgumentException("Icon part budget exceeded");
        }
    }

    /** Canvas coordinates are right/down; angle is clockwise. Every rotated corner stays inside the canvas. */
    public record Part(double x, double y, double width, double height, double angle, double alpha) {
        public Part {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                    || !Double.isFinite(angle) || !Double.isFinite(alpha) || width <= 0 || height <= 0 || alpha < 0 || alpha > 1)
                throw new IllegalArgumentException("Invalid icon rectangle");
            double radians = Math.toRadians(angle);
            double halfWidth = (Math.abs(Math.cos(radians)) * width + Math.abs(Math.sin(radians)) * height) / 2;
            double halfHeight = (Math.abs(Math.sin(radians)) * width + Math.abs(Math.cos(radians)) * height) / 2;
            if (x - halfWidth < -1e-6 || y - halfHeight < -1e-6
                    || x + halfWidth > CANVAS + 1e-6 || y + halfHeight > CANVAS + 1e-6)
                throw new IllegalArgumentException("Icon rectangle exceeds the drawing canvas");
        }

        public static Part rectangle(double x, double y, double width, double height) {
            return new Part(x + width / 2, y + height / 2, width, height, 0, 1);
        }
        public static Part line(double x1, double y1, double x2, double y2) {
            return new Part((x1 + x2) / 2, (y1 + y2) / 2, Math.hypot(x2 - x1, y2 - y1), 2,
                    Math.toDegrees(Math.atan2(y2 - y1, x2 - x1)), 1);
        }
    }
}
