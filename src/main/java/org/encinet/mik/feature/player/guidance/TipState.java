package org.encinet.mik.feature.player.guidance;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** Finite reminder history. No observation can permanently disable a scenario. */
final class TipState {
    static final long RETENTION = Duration.ofDays(365).toMillis();
    static final int MAX_PRESENTATIONS = 6;
    static final double MAX_PRESSURE = 32;
    static final int PRESSURE_PRECISION = 256;
    private static final long HALF_LIFE = Duration.ofMinutes(30).toMillis();
    private static final long CHAT_RECOVERY = Duration.ofHours(1).toMillis();
    private static final long MAX_INTERVAL = Duration.ofDays(30).toMillis();
    Long lastSentAt;
    final Map<String, Entry> history;

    TipState() {
        this(new Snapshot(Map.of()));
    }

    TipState(Snapshot snapshot) {
        history = new HashMap<>(snapshot.history());
        lastSentAt = latestSentAt();
    }

    boolean discardHistory(String id) {
        Entry removed = history.remove(id);
        if (removed == null) return false;
        if (lastSentAt != null && lastSentAt == removed.sentAt()) lastSentAt = latestSentAt();
        return true;
    }

    private Long latestSentAt() {
        return history.values().stream().filter(entry -> entry.presentations() > 0)
                .map(Entry::sentAt).max(Long::compare).orElse(null);
    }

    Snapshot snapshot() {
        return new Snapshot(history);
    }

    record Entry(String situation, long sentAt, int presentations, double pressure, long matchedAt) {
        Entry {
            if (situation == null || situation.length() > 256 || sentAt < 0 || matchedAt < 0
                    || presentations < 0 || presentations > MAX_PRESENTATIONS
                    || presentations == 0 && (sentAt != 0 || !situation.isEmpty())
                    || presentations > 0 && situation.isBlank()
                    || !Double.isFinite(pressure) || pressure < 0 || pressure > MAX_PRESSURE) {
                throw new IllegalArgumentException("Invalid tip history");
            }
        }

        static Entry empty() { return new Entry("", 0, 0, 0, 0); }

        double pressureAt(long now) {
            if (now < matchedAt) return 0;
            double decayed = pressure * Math.pow(0.5, (double) (now - matchedAt) / HALF_LIFE);
            return decayed < 1.0 / PRESSURE_PRECISION ? 0 : decayed;
        }

        double probability(long now, int occurrences) {
            double recovery = presentations == 0 || now < sentAt ? 1
                    : Math.min(1, (double) (now - sentAt) / CHAT_RECOVERY);
            double recent = pressureAt(now);
            double density = 1 + (recent == 0 ? 0 : Math.min(MAX_PRESSURE, recent + occurrences - 1));
            return recovery / (density * density);
        }

        Entry matched(long now, int occurrences) {
            return new Entry(situation, sentAt, presentations,
                    Math.min(MAX_PRESSURE, pressureAt(now) + occurrences), now);
        }

        boolean due(String currentSituation, long now, Duration base) {
            return presentations == 0 || !situation.equals(currentSituation) || now < sentAt
                    || now - sentAt >= interval(base);
        }

        long interval(Duration base) {
            long minimum = base.toMillis();
            return Math.min(minimum * (1L << Math.max(0, presentations - 1)), Math.max(minimum, MAX_INTERVAL));
        }

        Entry delivered(String currentSituation, long now) {
            boolean changed = presentations > 0 && !situation.equals(currentSituation);
            boolean reset = presentations > 0 && (changed || now < sentAt || now - sentAt >= RETENTION);
            return new Entry(currentSituation, now, reset ? 1 : Math.min(MAX_PRESENTATIONS, presentations + 1),
                    changed ? 0 : pressure, changed ? 0 : matchedAt);
        }

        boolean expired(long now) {
            long latest = Math.max(sentAt, matchedAt);
            return now >= latest && now - latest >= RETENTION;
        }
    }

    record Snapshot(Map<String, Entry> history) {
        Snapshot { history = Map.copyOf(history); }
    }
}
