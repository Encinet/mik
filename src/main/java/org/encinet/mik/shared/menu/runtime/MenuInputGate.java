package org.encinet.mik.shared.menu.runtime;

import java.util.Objects;
import java.util.function.LongSupplier;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;

/** Coalesces repeated packets and consumes the remainder of an input that closed a local scope. */
final class MenuInputGate {
    private static final long WINDOW_NANOS = 55_000_000L;
    private final LongSupplier clock;
    private String lastTarget;
    private FloatingMenuInteraction lastInteraction;
    private long lastAccepted;
    private long consumedAt;
    private boolean consumed;
    private int dispatchDepth;

    MenuInputGate(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    boolean permits() { return !consumed || clock.getAsLong() - consumedAt >= WINDOW_NANOS; }

    boolean accept(String target, FloatingMenuInteraction interaction) {
        Objects.requireNonNull(interaction);
        if (!permits()) return false;
        long now = clock.getAsLong();
        if (Objects.equals(target, lastTarget) && interaction == lastInteraction && now - lastAccepted < WINDOW_NANOS) return false;
        lastTarget = target;
        lastInteraction = interaction;
        lastAccepted = now;
        return true;
    }

    void consume() { consumedAt = clock.getAsLong(); consumed = true; }

    void dispatch(Runnable action) {
        dispatchDepth++;
        try { action.run(); }
        finally { dispatchDepth--; }
    }

    /** A redraw from an input callback cannot expose a second action to that input's remaining packets. */
    void contentChanged() { if (dispatchDepth > 0) consume(); }
}
