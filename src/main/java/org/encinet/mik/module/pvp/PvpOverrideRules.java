package org.encinet.mik.module.pvp;

/** Shared input rules for temporary and permanent PVP overrides. */
public final class PvpOverrideRules {
    public static final long PERMANENT = -1L;
    private static final int MAX_ID_LENGTH = 80;

    private PvpOverrideRules() { }

    public static String normalizeId(String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("PVP override id cannot be empty");
        }
        if (id.codePointCount(0, id.length()) > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("PVP override id cannot exceed "
                    + MAX_ID_LENGTH + " characters");
        }
        return id;
    }

    static void requireDuration(long durationMillis) {
        if (durationMillis < PERMANENT) {
            throw new IllegalArgumentException("PVP override duration cannot be less than -1");
        }
    }
}
