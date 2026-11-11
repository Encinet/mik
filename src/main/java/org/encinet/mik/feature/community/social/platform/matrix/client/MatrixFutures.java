package org.encinet.mik.feature.community.social.platform.matrix.client;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Transfers cancellation to the active request when a Matrix operation has several HTTP stages. */
public final class MatrixFutures {
    private MatrixFutures() { }

    public static <T, R> CompletableFuture<R> compose(
            CompletableFuture<T> source, Function<T, CompletableFuture<R>> next) {
        return continueWith(source, (value, error) -> error == null ? next.apply(value)
                : CompletableFuture.failedFuture(MatrixApiErrors.unwrap(error)));
    }

    public static <T> CompletableFuture<T> recover(
            CompletableFuture<T> source, Function<Throwable, CompletableFuture<T>> recovery) {
        return continueWith(source, (value, error) -> {
            if (error == null) return CompletableFuture.completedFuture(value);
            Throwable cause = MatrixApiErrors.unwrap(error);
            return cause instanceof CancellationException ? CompletableFuture.failedFuture(cause) : recovery.apply(cause);
        });
    }

    private static <T, R> CompletableFuture<R> continueWith(
            CompletableFuture<T> source, BiFunction<T, Throwable, CompletableFuture<R>> continuation) {
        var active = new AtomicReference<CompletableFuture<?>>(source);
        var result = new CompletableFuture<R>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) active.get().cancel(true);
        });
        source.whenComplete((value, error) -> {
            if (result.isDone()) return;
            final CompletableFuture<R> next;
            try {
                next = Objects.requireNonNull(continuation.apply(value, error), "Matrix continuation result");
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            active.set(next);
            if (result.isDone()) {
                next.cancel(true);
                return;
            }
            next.whenComplete((answer, failure) -> {
                if (failure == null) result.complete(answer);
                else result.completeExceptionally(MatrixApiErrors.unwrap(failure));
            });
        });
        return result;
    }
}
