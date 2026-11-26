package org.encinet.mik.feature.world.heads;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serializes favorites changes and bounds queueing for expensive catalog queries. */
final class HeadWork implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), Thread.ofPlatform().daemon().name("mik-head-queries").factory());

    <T> CompletableFuture<T> submit(Callable<T> task) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return task.call(); }
                catch (Exception error) { throw new CompletionException(error); }
            }, worker);
        } catch (RejectedExecutionException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Override public void close() { worker.shutdownNow(); }
}
