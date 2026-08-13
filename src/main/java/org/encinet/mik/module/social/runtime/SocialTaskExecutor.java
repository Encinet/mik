package org.encinet.mik.module.social.runtime;

import java.util.Objects;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Virtual-thread-per-task execution with explicit bounded admission. */
public final class SocialTaskExecutor implements AutoCloseable {
    private final ExecutorService executor;
    private final Semaphore permits;
    private final Semaphore orderedQueueSlots;
    private final Object orderedLock = new Object();
    private final Map<String, ArrayDeque<Runnable>> orderedTasks = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SocialTaskExecutor(int maximumConcurrentTasks, String threadNamePrefix) {
        if (maximumConcurrentTasks < 1) {
            throw new IllegalArgumentException("maximumConcurrentTasks must be positive");
        }
        permits = new Semaphore(maximumConcurrentTasks);
        orderedQueueSlots = new Semaphore(Math.max(
                256, maximumConcurrentTasks * 64));
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

    /**
     * Admits a bounded task which runs after all earlier tasks with the same
     * key. Different keys can still execute concurrently.
     */
    public boolean submitOrdered(String key, Runnable task) {
        String checkedKey = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(task, "task");
        if (checkedKey.isBlank() || closed.get()
                || !orderedQueueSlots.tryAcquire()) {
            return false;
        }
        boolean start;
        synchronized (orderedLock) {
            if (closed.get()) {
                orderedQueueSlots.release();
                return false;
            }
            ArrayDeque<Runnable> queue = orderedTasks.computeIfAbsent(
                    checkedKey, ignored -> new ArrayDeque<>());
            start = queue.isEmpty();
            queue.addLast(task);
        }
        if (start) {
            if (!scheduleOrderedHead(checkedKey)) {
                return false;
            }
        }
        return true;
    }

    private boolean scheduleOrderedHead(String key) {
        if (closed.get()) {
            discardOrdered(key);
            return false;
        }
        try {
            executor.execute(() -> runOrderedHead(key));
            return true;
        } catch (RejectedExecutionException ignored) {
            discardOrdered(key);
            return false;
        } catch (RuntimeException error) {
            discardOrdered(key);
            throw error;
        }
    }

    private void runOrderedHead(String key) {
        Runnable task;
        synchronized (orderedLock) {
            ArrayDeque<Runnable> queue = orderedTasks.get(key);
            task = queue == null ? null : queue.peekFirst();
        }
        if (task == null) {
            return;
        }
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            if (!closed.get()) {
                task.run();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired) {
                permits.release();
            }
            orderedQueueSlots.release();
            boolean next;
            synchronized (orderedLock) {
                ArrayDeque<Runnable> queue = orderedTasks.get(key);
                if (queue == null) {
                    next = false;
                } else {
                    queue.pollFirst();
                    next = !queue.isEmpty();
                    if (!next) {
                        orderedTasks.remove(key);
                    }
                }
            }
            if (next) {
                scheduleOrderedHead(key);
            }
        }
    }

    private void discardOrdered(String key) {
        int discarded;
        synchronized (orderedLock) {
            ArrayDeque<Runnable> queue = orderedTasks.remove(key);
            discarded = queue == null ? 0 : queue.size();
        }
        if (discarded > 0) {
            orderedQueueSlots.release(discarded);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            int discarded;
            synchronized (orderedLock) {
                discarded = orderedTasks.values().stream()
                        .mapToInt(ArrayDeque::size).sum();
                orderedTasks.clear();
            }
            if (discarded > 0) {
                orderedQueueSlots.release(discarded);
            }
            executor.shutdownNow();
        }
    }
}
