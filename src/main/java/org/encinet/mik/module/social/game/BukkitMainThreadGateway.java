package org.encinet.mik.module.social.game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Bukkit implementation of the narrow main-thread snapshot boundary. */
public final class BukkitMainThreadGateway implements SocialMainThreadGateway {
    private final JavaPlugin plugin;

    public BukkitMainThreadGateway(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public <T> T call(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) {
            return invoke(task);
        }
        FutureTask<T> future = new FutureTask<>(task);
        Bukkit.getScheduler().runTask(plugin, future);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Social game snapshot was interrupted", error);
        } catch (java.util.concurrent.TimeoutException error) {
            future.cancel(false);
            throw new IllegalStateException("Social game snapshot timed out", error);
        } catch (java.util.concurrent.ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Social game snapshot failed", cause);
        }
    }

    private static <T> T invoke(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Social game snapshot failed", error);
        }
    }
}
