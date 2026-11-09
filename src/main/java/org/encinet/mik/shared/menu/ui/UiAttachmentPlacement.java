package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Places an overlay next to its pointer anchor. Underlying content does not reserve overlay space. */
public final class UiAttachmentPlacement {
    private static final int EDGE_OBSTACLES = 16;
    private UiAttachmentPlacement() { }
    public record Rect(double x, double y, double width, double height) {
        public Rect {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                    || !Double.isFinite(height) || width < 0 || height < 0)
                throw new IllegalArgumentException("Invalid attachment rectangle");
        }
        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }
        public boolean contains(UiLayout.Position point) {
            return point.x() >= x && point.x() <= x + width && point.y() >= y && point.y() <= y + height;
        }
        public double intersectionArea(Rect other) {
            return Math.max(0, Math.min(x + width, other.x + other.width) - Math.max(x, other.x))
                    * Math.max(0, Math.min(y + height, other.y + other.height) - Math.max(y, other.y));
        }
        private double distanceSquared(UiLayout.Position point) {
            double dx = Math.max(0, Math.max(x - point.x(), point.x() - x - width));
            double dy = Math.max(0, Math.max(y - point.y(), point.y() - y - height));
            return dx * dx + dy * dy;
        }
        boolean covers(UiLayout.Position point, double gap) {
            return point.x() > x - gap && point.x() < x + width + gap
                    && point.y() > y - gap && point.y() < y + height + gap;
        }
    }
    public static Optional<Rect> place(UiLayout.Position pointer, UiSize size, UiSize viewport, double gap) {
        return place(pointer, size, viewport, gap, List.of());
    }

    /** Prefer nearby free space; overlapping content remains a valid fallback in a dense workspace. */
    public static Optional<Rect> place(UiLayout.Position pointer, UiSize size, UiSize viewport, double gap, List<Rect> obstacles) {
        Objects.requireNonNull(pointer);
        Objects.requireNonNull(size);
        Objects.requireNonNull(viewport);
        obstacles = List.copyOf(obstacles);
        if (!Double.isFinite(gap) || gap < 0) throw new IllegalArgumentException("Invalid attachment gap");
        if (!Double.isFinite(pointer.x()) || !Double.isFinite(pointer.y())
                || pointer.x() < 0 || pointer.y() < 0 || pointer.x() > viewport.width() || pointer.y() > viewport.height())
            throw new IllegalArgumentException("Pointer is outside the viewport");
        if (size.width() > viewport.width() || size.height() > viewport.height()) return Optional.empty();
        List<Candidate> candidates = new ArrayList<>();
        for (boolean clamp : new boolean[]{false, true}) for (int vertical : new int[]{1, -1}) for (int horizontal : new int[]{1, -1}) {
            add(candidates, pointer, size, viewport, gap, horizontal > 0 ? pointer.x() + gap : pointer.x() - gap - size.width(),
                    vertical > 0 ? pointer.y() + gap : pointer.y() - gap - size.height(), clamp ? 2 : 0, clamp);
        }
        // Only nearby edges generate candidates, bounding the search independently of scene size.
        for (Rect obstacle : obstacles.stream().sorted(Comparator.<Rect>comparingDouble(rect -> rect.distanceSquared(pointer))
                        .thenComparingDouble(Rect::x).thenComparingDouble(Rect::y).thenComparingDouble(Rect::width).thenComparingDouble(Rect::height))
                .limit(EDGE_OBSTACLES).toList()) {
            for (double y : new double[]{pointer.y() + gap, pointer.y() - gap - size.height(), pointer.y() - size.height() / 2}) {
                add(candidates, pointer, size, viewport, gap, obstacle.x() - gap - size.width(), y, 1, true);
                add(candidates, pointer, size, viewport, gap, obstacle.x() + obstacle.width() + gap, y, 1, true);
            }
            for (double x : new double[]{pointer.x() + gap, pointer.x() - gap - size.width(), pointer.x() - size.width() / 2}) {
                add(candidates, pointer, size, viewport, gap, x, obstacle.y() - gap - size.height(), 1, true);
                add(candidates, pointer, size, viewport, gap, x, obstacle.y() + obstacle.height() + gap, 1, true);
            }
        }
        Candidate best = null;
        double bestOverlap = Double.POSITIVE_INFINITY, bestDistance = Double.POSITIVE_INFINITY;
        var padded = obstacles.stream().map(obstacle -> new Rect(obstacle.x() - gap, obstacle.y() - gap,
                obstacle.width() + gap * 2, obstacle.height() + gap * 2)).toList();
        for (Candidate candidate : candidates) {
            double overlap = 0;
            for (Rect obstacle : padded) overlap += candidate.rect().intersectionArea(obstacle);
            if (size.width() * size.height() > 0) overlap /= size.width() * size.height();
            double distance = Math.pow(candidate.rect().centerX() - pointer.x(), 2) + Math.pow(candidate.rect().centerY() - pointer.y(), 2);
            if (best == null || overlap < bestOverlap - 1e-9 || Math.abs(overlap - bestOverlap) <= 1e-9
                    && (candidate.rank() < best.rank() || candidate.rank() == best.rank() && distance < bestDistance - 1e-9)) {
                best = candidate; bestOverlap = overlap; bestDistance = distance;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.rect());
    }

    private static void add(List<Candidate> candidates, UiLayout.Position pointer, UiSize size, UiSize viewport,
                            double gap, double x, double y, int rank, boolean clamp) {
        if (!clamp && (x < 0 || y < 0 || x + size.width() > viewport.width() || y + size.height() > viewport.height())) return;
        var rect = new Rect(Math.clamp(x, 0, viewport.width() - size.width()), Math.clamp(y, 0, viewport.height() - size.height()),
                size.width(), size.height());
        double reach = Math.hypot(size.width(), size.height()) + gap * 2;
        if (!rect.covers(pointer, gap) && rect.distanceSquared(pointer) <= reach * reach) candidates.add(new Candidate(rect, rank));
    }

    private record Candidate(Rect rect, int rank) { }
}
