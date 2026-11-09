package org.encinet.mik.feature.server.performance;

/** Monotonic byte-counter deltas. A missing/reset counter always starts a new baseline. */
final class NetworkEgressRateMeter {
    private long lastBytes = -1L;
    private long lastNanos;

    Reading sample(long bytes, long nowNanos) {
        if (bytes < 0L) {
            reset();
            return new Reading(-1.0D, 0.0D);
        }
        Reading reading = new Reading(-1.0D, 0.0D);
        if (lastBytes >= 0L && bytes >= lastBytes && nowNanos > lastNanos) {
            double seconds = (nowNanos - lastNanos) / 1_000_000_000.0D;
            reading = new Reading((bytes - lastBytes) / seconds, seconds);
        }
        lastBytes = bytes;
        lastNanos = nowNanos;
        return reading;
    }

    void reset() {
        lastBytes = -1L;
        lastNanos = 0L;
    }

    record Reading(double bytesPerSecond, double seconds) {
        boolean measured() {
            return bytesPerSecond >= 0.0D;
        }
    }
}
