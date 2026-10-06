package org.encinet.mik.module.ai.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Virtual-thread-per-task worker with separate concurrency and admission bounds.
 * Blocking work parks virtual threads, while semaphores provide real backpressure.
 */
public final class AiWorkerPool implements AutoCloseable {
    private final ExecutorService executor;
    private final Semaphore runningSlots;
    private final Semaphore admissionSlots;
    private final Set<CompletableFuture<?>> pending = java.util.concurrent.ConcurrentHashMap
            .newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    public AiWorkerPool(
            String threadNamePrefix,
            int maximumConcurrentTasks,
            int maximumQueuedTasks
    ) {
        if (maximumConcurrentTasks < 1) {
            throw new IllegalArgumentException("maximumConcurrentTasks must be positive");
        }
        if (maximumQueuedTasks < 0) {
            throw new IllegalArgumentException("maximumQueuedTasks must not be negative");
        }
        String prefix = Objects.requireNonNull(threadNamePrefix, "threadNamePrefix").strip();
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("threadNamePrefix must not be blank");
        }
        runningSlots = new Semaphore(maximumConcurrentTasks, true);
        admissionSlots = new Semaphore(maximumConcurrentTasks + maximumQueuedTasks, true);
        executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(prefix, 0).factory());
    }

    /** Returns empty immediately when the bounded worker cannot admit another task. */
    public <T> Optional<CompletableFuture<T>> trySubmit(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        if (closed.get() || !admissionSlots.tryAcquire()) {
            dropped.incrementAndGet();
            return Optional.empty();
        }
        if (closed.get()) {
            admissionSlots.release();
            dropped.incrementAndGet();
            return Optional.empty();
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        queued.incrementAndGet();
        try {
            executor.execute(() -> run(task, result));
            return Optional.of(result);
        } catch (RejectedExecutionException ignored) {
            reject(result);
            return Optional.empty();
        } catch (RuntimeException error) {
            reject(result);
            throw error;
        }
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return trySubmit(task).orElseGet(() -> CompletableFuture.failedFuture(
                new RejectedExecutionException("AI worker is closed or saturated")));
    }

    public boolean tryExecute(Runnable task) {
        Objects.requireNonNull(task, "task");
        return trySubmit(() -> {
            task.run();
            return null;
        }).isPresent();
    }

    private <T> void run(Supplier<T> task, CompletableFuture<T> result) {
        boolean acquired = false;
        try {
            runningSlots.acquire();
            acquired = true;
            queued.decrementAndGet();
            running.incrementAndGet();
            if (closed.get()) {
                throw new CancellationException("AI worker was closed");
            }
            result.complete(task.get());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(new CancellationException(
                    "AI worker task was interrupted"));
        } catch (Throwable error) {
            result.completeExceptionally(error);
            if (error instanceof Error fatal) {
                throw fatal;
            }
        } finally {
            if (acquired) {
                running.decrementAndGet();
                runningSlots.release();
            } else {
                queued.decrementAndGet();
            }
            pending.remove(result);
            admissionSlots.release();
        }
    }

    private void reject(CompletableFuture<?> result) {
        queued.decrementAndGet();
        pending.remove(result);
        admissionSlots.release();
        dropped.incrementAndGet();
        result.completeExceptionally(new RejectedExecutionException(
                "AI worker rejected the task"));
    }

    public Status status() {
        return new Status(queued.get(), running.get(), dropped.get(), closed.get());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pending.forEach(future -> future.completeExceptionally(
                new CancellationException("AI worker was closed")));
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    public record Status(int queued, int running, int dropped, boolean closed) {
    }
}
