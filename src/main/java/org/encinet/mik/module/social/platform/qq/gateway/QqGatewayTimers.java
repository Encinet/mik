package org.encinet.mik.module.social.platform.qq.gateway;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Owns the timer thread and cancellable Gateway handshake/heartbeat tasks. */
final class QqGatewayTimers implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> handshake;
    private ScheduledFuture<?> heartbeat;

    QqGatewayTimers() {
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mik-qq-gateway");
            thread.setDaemon(true);
            return thread;
        });
    }

    synchronized void scheduleHandshake(Runnable task, long timeoutMillis) {
        Objects.requireNonNull(task, "task");
        cancelHandshake();
        handshake = scheduler.schedule(task, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    synchronized void scheduleHeartbeat(Runnable task, long intervalMillis) {
        Objects.requireNonNull(task, "task");
        cancelHeartbeat();
        heartbeat = scheduler.scheduleAtFixedRate(
                task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    boolean scheduleReconnect(Runnable task, long delayMillis) {
        Objects.requireNonNull(task, "task");
        try {
            scheduler.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    synchronized void cancelHandshake() {
        if (handshake != null) {
            handshake.cancel(false);
            handshake = null;
        }
    }

    synchronized void cancelHeartbeat() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeat = null;
        }
    }

    @Override
    public synchronized void close() {
        cancelHandshake();
        cancelHeartbeat();
        scheduler.shutdownNow();
    }
}
