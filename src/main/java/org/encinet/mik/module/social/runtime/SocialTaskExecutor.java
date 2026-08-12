package org.encinet.mik.module.social.runtime;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Virtual-thread-per-task execution with explicit bounded admission. */
public final class SocialTaskExecutor implements AutoCloseable {
    private final ExecutorService executor;
    private final Semaphore permits;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SocialTaskExecutor(int maximumConcurrentTasks, String threadNamePrefix) {
        if (maximumConcurrentTasks < 1) {
            throw new IllegalArgumentException("maximumConcurrentTasks must be positive");
        }
        permits = new Semaphore(maximumConcurrentTasks);
        executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(threadNamePrefix, 0).factory());
    }

    public boolean submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (closed.get() || !permits.tryAcquire()) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    permits.release();
                }
            });
            return true;
        } catch (RejectedExecutionException error) {
            permits.release();
            return false;
        } catch (RuntimeException error) {
            permits.release();
            throw error;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
