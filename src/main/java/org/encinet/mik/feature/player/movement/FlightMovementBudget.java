package org.encinet.mik.feature.player.movement;

/** Per-player distance allowance while an exploration ceiling is active. */
final class FlightMovementBudget {
    private static final double CLIENT_TICK_NANOS = 50_000_000.0D;
    private static final double BURST_TICKS = 4.0D;
    // Paper can combine position changes of up to 1/16 block into the next move event.
    private static final double EVENT_SLACK = 1.0D / 16.0D;
    // Bukkit speed is twice the ability speed. Sprint doubles horizontal acceleration;
    // vertical input adds three times the ability speed. Allow the terminal speeds in air.
    private static final double HORIZONTAL_SCALE = 1.0D / (1.0D - 0.91D);
    private static final double VERTICAL_SCALE = 1.5D / (1.0D - 0.6D);

    private long lastNanos;
    private double speed;
    private double horizontalCredit;
    private double verticalCredit;

    FlightMovementBudget(float bukkitSpeed, long nowNanos) {
        lastNanos = nowNanos;
        speed = Math.abs(bukkitSpeed);
        horizontalCredit = capacity(HORIZONTAL_SCALE);
        verticalCredit = capacity(VERTICAL_SCALE);
    }

    void setSpeed(float bukkitSpeed, long nowNanos) {
        refill(nowNanos);
        speed = Math.abs(bukkitSpeed);
        horizontalCredit = Math.min(horizontalCredit, capacity(HORIZONTAL_SCALE));
        verticalCredit = Math.min(verticalCredit, capacity(VERTICAL_SCALE));
    }

    boolean tryMove(double horizontal, double vertical, long nowNanos) {
        refill(nowNanos);
        if (horizontal > horizontalCredit || vertical > verticalCredit) {
            return false;
        }
        horizontalCredit -= horizontal;
        verticalCredit -= vertical;
        return true;
    }

    private void refill(long nowNanos) {
        double earned = (nowNanos - lastNanos) / CLIENT_TICK_NANOS * speed;
        lastNanos = nowNanos;
        horizontalCredit = Math.min(capacity(HORIZONTAL_SCALE), horizontalCredit + earned * HORIZONTAL_SCALE);
        verticalCredit = Math.min(capacity(VERTICAL_SCALE), verticalCredit + earned * VERTICAL_SCALE);
    }

    private double capacity(double scale) {
        return speed * scale * BURST_TICKS + EVENT_SLACK;
    }
}
