package org.encinet.mik.module.plot;

import java.util.Objects;

/** Player-chosen arrival point and optional MiniMessage text, keyed by plot UUID. */
record PlotArrival(double x, double y, double z, float yaw, float pitch,
                   String title, String subtitle) {
    PlotArrival {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Arrival coordinates must be finite");
        }
        title = Objects.requireNonNull(title, "title");
        subtitle = Objects.requireNonNull(subtitle, "subtitle");
    }

    int blockX() { return (int) Math.floor(x); }
    int blockY() { return (int) Math.floor(y); }
    int blockZ() { return (int) Math.floor(z); }
}
