package org.encinet.mik.shared.menu.runtime;

/** Delayed hints survive reading and a short source-to-hint crossing, but never activate an action. */
final class MenuHintLifetime {
    enum Phase { WAITING, VISIBLE, SUPPRESSED, CLOSED }
    private static final int CROSSING_TICKS = 4;
    private final long since;
    private final int delay;
    private long lastEngaged;
    private boolean shown;
    private boolean dismissed;
    private boolean closed;

    MenuHintLifetime(long tick, int delay) {
        if (delay < 0) throw new IllegalArgumentException("Negative hint delay");
        since = lastEngaged = tick;
        this.delay = delay;
    }

    Phase update(long tick, boolean source, boolean reading) {
        if (closed) return Phase.CLOSED;
        if (dismissed) {
            if (!source && !reading) closed = true;
            return closed ? Phase.CLOSED : Phase.SUPPRESSED;
        }
        if (source || shown && reading) lastEngaged = tick;
        else if (!shown || tick - lastEngaged > CROSSING_TICKS) {
            closed = true;
            return Phase.CLOSED;
        }
        if (!shown && tick - since < delay) return Phase.WAITING;
        shown = true;
        return Phase.VISIBLE;
    }

    boolean holds(long tick) { return shown && !dismissed && !closed && tick - lastEngaged <= CROSSING_TICKS; }
    void reveal() { if (!closed) { shown = true; dismissed = false; } }
    void dismiss() { dismissed = true; }
}
