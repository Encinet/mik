package org.encinet.mik.feature.entertainment.music.application;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** A lifecycle-scoped completion dispatcher. Shutdown completes pending callers exceptionally. */
public final class MusicMainThread implements AutoCloseable {
    private final java.util.function.Consumer<Runnable> dispatch;
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    public MusicMainThread(JavaPlugin plugin) {
        this(action -> {
            if (Bukkit.isPrimaryThread()) action.run();
            else Bukkit.getScheduler().runTask(plugin, action);
        });
    }
    MusicMainThread(java.util.function.Consumer<Runnable> dispatch) {
        this.dispatch = java.util.Objects.requireNonNull(dispatch);
    }
    public <T> CompletableFuture<T> after(CompletableFuture<T> future, Runnable apply) {
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((_, _) -> pending.remove(result));
        future.whenComplete((value, error) -> {
            if (closed) {
                result.completeExceptionally(new MusicProblem(MusicProblem.Code.UNAVAILABLE));
                return;
            }
            Runnable work = () -> {
                if (closed) {
                    result.completeExceptionally(new MusicProblem(MusicProblem.Code.UNAVAILABLE));
                    return;
                }
                if (error != null)
                    result.completeExceptionally(error);
                else
                    try {
                        apply.run();
                        result.complete(value);
                    } catch (Exception failed) {
                        result.completeExceptionally(failed);
                    }
            };
            try {
                dispatch.accept(work);
            } catch (RuntimeException failed) {
                result.completeExceptionally(failed);
            }
        });
        return result;
    }
    @Override
    public void close() {
        closed = true;
        for (CompletableFuture<?> future : pending)
            future.completeExceptionally(new MusicProblem(MusicProblem.Code.UNAVAILABLE));
        pending.clear();
    }
}
