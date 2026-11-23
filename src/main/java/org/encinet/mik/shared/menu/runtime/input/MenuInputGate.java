package org.encinet.mik.shared.menu.runtime.input;

import java.util.Objects;
import java.util.function.LongSupplier;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;

/** Coalesces repeated packets, consumes input across scope changes, and rate-limits sneak-swap shortcuts. */
public final class MenuInputGate {
    private static final long WINDOW_NANOS = 55_000_000L;
    private static final int SNEAK_SWAP_LIMIT = 4;
    private static final long SNEAK_SWAP_WINDOW_NANOS = 1_000_000_000L;
    private final LongSupplier clock;
    private final long[] sneakSwapTimes = new long[SNEAK_SWAP_LIMIT];
    private String lastTarget;
    private FloatingMenuInteraction lastInteraction;
    private long lastAccepted;
    private long consumedAt;
    private boolean consumed;
    private int sneakSwapCount;
    private int nextSneakSwap;
    private int dispatchDepth;

    public MenuInputGate(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }
    public boolean permits() { return !consumed || clock.getAsLong() - consumedAt >= WINDOW_NANOS; }

    public boolean accept(String target, FloatingMenuInteraction interaction) {
        Objects.requireNonNull(interaction);
        if (!permits()) return false;
        long now = clock.getAsLong();
        if (Objects.equals(target, lastTarget) && interaction == lastInteraction && now - lastAccepted < WINDOW_NANOS) return false;
        lastTarget = target;
        lastInteraction = interaction;
        lastAccepted = now;
        return true;
    }

    public void consume() { consumedAt = clock.getAsLong(); consumed = true; }

    public boolean acceptSneakSwap() {
        long now = clock.getAsLong();
        // Once full, the next slot holds the oldest accepted shortcut.
        if (sneakSwapCount == SNEAK_SWAP_LIMIT && now - sneakSwapTimes[nextSneakSwap] < SNEAK_SWAP_WINDOW_NANOS) {
            return false;
        }
        sneakSwapTimes[nextSneakSwap] = now;
        nextSneakSwap = (nextSneakSwap + 1) % SNEAK_SWAP_LIMIT;
        if (sneakSwapCount < SNEAK_SWAP_LIMIT) sneakSwapCount++;
        return true;
    }

    public void dispatch(Runnable action) {
        dispatchDepth++;
        try { action.run(); }
        finally { dispatchDepth--; }
    }

    /** A redraw from an input callback cannot expose a second action to that input's remaining packets. */
    public void contentChanged() { if (dispatchDepth > 0) consume(); }
}
