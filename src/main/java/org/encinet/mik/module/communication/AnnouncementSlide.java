package org.encinet.mik.module.communication;

import org.encinet.mik.module.menu.FloatingMenuEasing;

record AnnouncementSlide(double origin, int target, long startedAt) {
    static final long DURATION_NANOS = 260_000_000L;

    AnnouncementSlide {
        if (!Double.isFinite(origin) || origin < 0 || target < 0)
            throw new IllegalArgumentException("Invalid announcement slide");
    }

    static AnnouncementSlide rest(int page) { return new AnnouncementSlide(page, page, 0); }

    double position(long now) {
        if (startedAt == 0) return target;
        double progress = Math.clamp((now - startedAt) / (double) DURATION_NANOS, 0, 1);
        return origin + (target - origin) * FloatingMenuEasing.CUBIC_OUT.apply(progress);
    }

    boolean moving(long now) { return startedAt != 0 && now - startedAt < DURATION_NANOS; }

    AnnouncementSlide move(int page, long now) {
        return page == target ? this : new AnnouncementSlide(position(now), page, now);
    }
}
