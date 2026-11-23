package org.encinet.mik.shared.menu.runtime.motion;

import java.util.Objects;

/** Session-owned press feedback, advanced by the session clock rather than presentation count. */
public final class MenuPressFeedback {
    private String elementId;
    private long startedAt;
    private long observedAt;
    private int duration;

    public void select(String id, long tick, int ticks) {
        Objects.requireNonNull(id, "id");
        if (ticks < 1) throw new IllegalArgumentException("Press duration must be positive");
        observe(tick);
        elementId = id;
        startedAt = tick;
        duration = ticks;
    }

    /** Returns whether expiry changed the semantic state; publication remains the caller's responsibility. */
    public boolean advance(long tick) {
        observe(tick);
        if (elementId == null || tick - startedAt < duration) return false;
        clear();
        return true;
    }

    public String elementId() { return elementId; }

    public boolean pressed(String id) { return elementId != null && elementId.equals(id); }

    public void clear() {
        elementId = null;
        duration = 0;
    }

    private void observe(long tick) {
        if (tick < observedAt) throw new IllegalArgumentException("Press clock cannot move backwards");
        observedAt = tick;
    }
}
