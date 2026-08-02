package org.encinet.mik.module.world.regen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/** Keeps a deterministic, spatially distributed sample without retaining every changed block. */
final class RegenPreviewPointSampler {

    private static final Comparator<Candidate> WORST_FIRST = (left, right) -> {
        int priority = Long.compareUnsigned(right.priority(), left.priority());
        return priority != 0 ? priority : comparePoint(left.point(), right.point());
    };

    private final int capacity;
    private final PriorityQueue<Candidate> candidates = new PriorityQueue<>(WORST_FIRST);
    private final Set<RegenPreviewPoint> retained = new HashSet<>();

    RegenPreviewPointSampler(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Preview sample capacity must be positive");
        }
        this.capacity = capacity;
    }

    void add(RegenPreviewPoint point) {
        if (retained.contains(point)) {
            return;
        }
        Candidate candidate = new Candidate(point, priority(point));
        if (candidates.size() < capacity) {
            candidates.add(candidate);
            retained.add(point);
            return;
        }
        Candidate worst = candidates.peek();
        if (worst == null || Long.compareUnsigned(candidate.priority(), worst.priority()) >= 0) {
            return;
        }
        candidates.remove();
        retained.remove(worst.point());
        candidates.add(candidate);
        retained.add(point);
    }

    void addAll(Iterable<RegenPreviewPoint> points) {
        for (RegenPreviewPoint point : points) {
            add(point);
        }
    }

    List<RegenPreviewPoint> snapshot() {
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort((left, right) -> {
            int priority = Long.compareUnsigned(left.priority(), right.priority());
            return priority != 0 ? priority : comparePoint(left.point(), right.point());
        });
        return ordered.stream().map(Candidate::point).toList();
    }

    private static long priority(RegenPreviewPoint point) {
        long value = (long) point.x() * 0x9E3779B97F4A7C15L;
        value ^= (long) point.y() * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) point.z() * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int comparePoint(RegenPreviewPoint left, RegenPreviewPoint right) {
        int x = Integer.compare(left.x(), right.x());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(left.y(), right.y());
        return y != 0 ? y : Integer.compare(left.z(), right.z());
    }

    private record Candidate(RegenPreviewPoint point, long priority) {
    }
}
