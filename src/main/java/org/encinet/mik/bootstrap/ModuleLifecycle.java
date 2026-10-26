package org.encinet.mik.bootstrap;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns startup rollback and reverse-order cleanup for one plugin instance. */
final class ModuleLifecycle {
    private record ShutdownAction(String name, Runnable action) { }

    private final Logger logger;
    private final Deque<ShutdownAction> shutdownActions = new ArrayDeque<>();

    ModuleLifecycle(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void startManaged(String name, Runnable start, Runnable stop) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(stop, "stop");
        try {
            start.run();
        } catch (RuntimeException | LinkageError error) {
            try {
                stop.run();
            } catch (RuntimeException | LinkageError cleanupError) {
                if (cleanupError != error) error.addSuppressed(cleanupError);
            }
            throw error;
        }
        shutdownActions.push(new ShutdownAction(name, stop));
    }

    void startListener(String name, Listener listener, Runnable start) {
        Objects.requireNonNull(listener, "listener");
        startManaged(name, start, () -> HandlerList.unregisterAll(listener));
    }

    <T extends AutoCloseable> T own(String name, T resource) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resource, "resource");
        shutdownActions.push(new ShutdownAction(name, () -> {
            try {
                resource.close();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException("Could not close " + name, error);
            }
        }));
        return resource;
    }

    boolean stopAll(Throwable startupError) {
        boolean cleanShutdown = true;
        while (!shutdownActions.isEmpty()) {
            ShutdownAction shutdown = shutdownActions.pop();
            try {
                shutdown.action().run();
            } catch (RuntimeException | LinkageError error) {
                cleanShutdown = false;
                logger.log(Level.SEVERE, "Could not stop " + shutdown.name(), error);
                if (startupError != null && startupError != error) startupError.addSuppressed(error);
            }
        }
        return cleanShutdown;
    }
}
