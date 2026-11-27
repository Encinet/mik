package org.encinet.mik.feature.world.heads.catalog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Publishes verified snapshots without blocking the server or menu queries. */
public final class HeadCatalogs implements AutoCloseable {
    public record State(HeadCatalog remote, HeadCatalog local, boolean loading, boolean stale) {
        public HeadEntry find(String id) {
            var catalog = catalog(id);
            return catalog == null ? null : catalog.find(id);
        }
        public HeadCatalog catalog(String id) {
            if (id.startsWith("player:")) return null;
            return id.startsWith("custom:") ? local : remote;
        }
    }

    private final HeadFeed feed;
    private final Path localFile;
    private final Logger logger;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("mik-head-catalog").factory());
    private volatile State state = new State(null, null, true, false);
    private volatile boolean closed;
    private HeadFeed.Release loaded;
    private boolean refreshFailed;

    public HeadCatalogs(Path folder, Logger logger) {
        this.feed = new HeadFeed(folder.resolve("catalog.zip"));
        this.localFile = folder.resolve("custom.json");
        this.logger = logger;
    }

    public State state() { return state; }
    public void start() { worker.execute(this::initialize); }

    private void initialize() {
        HeadCatalog local = null;
        try { local = HeadFeed.local(localFile); }
        catch (IOException | RuntimeException error) { warn("Cannot load local decorative heads", error); }
        try { loaded = feed.load(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
        catch (IOException | RuntimeException | LinkageError error) { warn("Cannot load cached HeadsDB catalog", error); }
        if (closed) return;
        state = new State(loaded == null ? null : loaded.catalog(), local, true, false);
        refresh();
    }

    private void refresh() {
        if (closed) return;
        try {
            var result = feed.refresh(loaded);
            if (closed) return;
            if (result.release() != loaded || refreshFailed) {
                logger.info("HeadsDB revision " + result.release().revision() + " loaded with "
                        + result.release().catalog().size() + " decorative heads.");
            }
            loaded = result.release();
            state = new State(loaded.catalog(), state.local(), false, false);
            if (result.cacheFailure() != null) warn("Cannot save verified HeadsDB catalog", result.cacheFailure());
            refreshFailed = false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | RuntimeException | LinkageError error) {
            if (!refreshFailed) warn("Cannot refresh HeadsDB catalog", error);
            refreshFailed = true;
            if (!closed) state = new State(state.remote(), state.local(), false, state.remote() != null);
        }
        if (!closed) worker.schedule(this::refresh, refreshFailed ? 2 : 360, TimeUnit.MINUTES);
    }

    private void warn(String message, Throwable error) {
        if (!closed) logger.warning(message + ": " + error);
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        feed.close();
    }
}
