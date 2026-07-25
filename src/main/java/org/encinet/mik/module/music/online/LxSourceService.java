package org.encinet.mik.module.music.online;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Single lifecycle and capability boundary for LX custom sources.
 *
 * <p>The script runtime, search aggregation, remote subscriptions, and source health are
 * coordinated here. Callers only use search, URL resolution, source administration, and status
 * snapshots; they do not manipulate individual runtimes or managed source files.</p>
 */
public final class LxSourceService
        implements MusicSearchService, LxTrackResolver, AutoCloseable {

    private final LxSubscriptionManager subscriptions;
    private final LxCustomSourceResolver resolver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofVirtual().name("mik-lx-source-service").unstarted(runnable));
    private final AtomicBoolean closed = new AtomicBoolean();

    public LxSourceService(Path directory, Consumer<String> infoLogger,
                           Consumer<String> warningLogger) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(infoLogger, "infoLogger");
        Objects.requireNonNull(warningLogger, "warningLogger");
        this.subscriptions = new LxSubscriptionManager(directory, warningLogger);
        this.resolver = new LxCustomSourceResolver(directory, infoLogger, warningLogger,
                subscriptions::managedScriptNames);
    }

    /** Refreshes subscriptions and runtimes independently, retaining each previous state on failure. */
    public CompletableFuture<ReloadResult> reloadAsync() {
        return submit(() -> {
            SubscriptionReload subscriptionsResult;
            try {
                LxSubscriptionManager.RefreshResult refreshed = subscriptions.refreshAll();
                subscriptionsResult = new SubscriptionReload(true, refreshed.total(),
                        refreshed.changed(), refreshed.failed(), null);
            } catch (RuntimeException exception) {
                subscriptionsResult = new SubscriptionReload(false,
                        subscriptions.statuses().size(), 0, 0, rootMessage(exception));
            }

            RuntimeReload runtimesResult;
            try {
                resolver.reload();
                List<SourceStatus> statuses = statuses();
                int available = (int) statuses.stream().filter(SourceStatus::available).count();
                runtimesResult = new RuntimeReload(true, available, statuses.size(), null);
            } catch (RuntimeException exception) {
                List<SourceStatus> retained = statuses();
                int available = (int) retained.stream().filter(SourceStatus::available).count();
                runtimesResult = new RuntimeReload(false, available, retained.size(),
                        rootMessage(exception));
            }
            return new ReloadResult(subscriptionsResult, runtimesResult);
        });
    }

    public CompletableFuture<ImportResult> importSourceAsync(String url) {
        return submit(() -> {
            LxSubscriptionManager.ImportResult result = subscriptions.importSource(url);
            if (result.added() || result.changed()) {
                resolver.reload();
            }
            return new ImportResult(result.id(), result.added(), result.changed());
        });
    }

    public CompletableFuture<Boolean> removeSourceAsync(String id) {
        return submit(() -> {
            boolean removed = subscriptions.remove(id);
            if (removed) {
                resolver.reload();
            }
            return removed;
        });
    }

    /** Checks subscriptions and only rebuilds runtimes when a managed script actually changed. */
    public CompletableFuture<RefreshResult> refreshSubscriptionsAsync() {
        return submit(() -> {
            LxSubscriptionManager.RefreshResult result = subscriptions.refreshAll();
            if (result.changed() > 0) {
                resolver.reload();
            }
            return new RefreshResult(result.total(), result.changed(), result.failed());
        });
    }

    public List<SourceStatus> statuses() {
        return resolver.statuses().stream().map(status -> new SourceStatus(status.id(), status.name(),
                status.available(), status.consecutiveFailures(), status.retryAt(), status.lastError(),
                status.capabilities(), status.actions())).toList();
    }

    public List<SubscriptionStatus> subscriptionStatuses() {
        return subscriptions.statuses().stream().map(status -> new SubscriptionStatus(status.id(),
                status.url().toString(), status.updatedAt(), status.lastCheckedAt(), status.lastError())).toList();
    }

    @Override
    public CompletableFuture<MusicSearchResult<MusicTrack>> searchMusic(String keyword, int page, int limit) {
        return resolver.searchMusic(keyword, page, limit);
    }

    @Override
    public CompletableFuture<String> resolve(TrackTarget.Lx target) {
        return resolver.resolve(target);
    }

    /** Loads and normalizes optional lyrics returned by an LX custom source. */
    public CompletableFuture<LyricsPayload> lyrics(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("LX source service is closed"));
        }
        return resolver.lyrics(target).thenApply(LxSourceService::lyricsPayload);
    }

    static LyricsPayload lyricsPayload(JsonElement result) {
        JsonElement value = unwrap(result, 0);
        if (value == null || value.isJsonNull()) {
            return LyricsPayload.EMPTY;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new LyricsPayload(value.getAsString(), null, null);
        }
        if (!value.isJsonObject()) {
            return LyricsPayload.EMPTY;
        }
        JsonObject object = value.getAsJsonObject();
        return new LyricsPayload(
                text(object, "lyric", "lrc", "lxlyric", "original"),
                text(object, "tlyric", "tlrc", "translation", "trans"),
                text(object, "rlyric", "rlrc", "romanization", "roma"));
    }

    private static JsonElement unwrap(JsonElement value, int depth) {
        if (value == null || depth >= 4 || !value.isJsonObject()) {
            return value;
        }
        JsonObject object = value.getAsJsonObject();
        for (String key : List.of("lyric", "lrc", "lxlyric", "original",
                "tlyric", "tlrc", "translation", "trans",
                "rlyric", "rlrc", "romanization", "roma")) {
            if (object.has(key)) {
                return value;
            }
        }
        for (String key : List.of("data", "result", "body")) {
            JsonElement nested = object.get(key);
            if (nested != null && !nested.isJsonNull()) {
                return unwrap(nested, depth + 1);
            }
        }
        return value;
    }

    private static String text(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = textValue(object.get(key), 0);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String textValue(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth >= 3) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString().strip();
            return text.isEmpty() ? null : text;
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String key : List.of("lyric", "lrc", "text", "content")) {
                String text = textValue(object.get(key), depth + 1);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private <T> CompletableFuture<T> submit(CheckedSupplier<T> operation) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("LX source service is closed"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (closed.get()) {
                    throw new java.util.concurrent.CompletionException(
                            new IllegalStateException("LX source service is closed"));
                }
                try {
                    return operation.get();
                } catch (Exception exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            }, executor);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        resolver.close();
        subscriptions.close();
        executor.shutdownNow();
    }

    public record SourceStatus(String id, String name, boolean available,
                               int consecutiveFailures, Instant retryAt, String lastError,
                               Map<String, List<String>> capabilities,
                               Map<String, List<String>> actions) {
    }

    public record SubscriptionStatus(String id, String url, Instant updatedAt,
                                     Instant lastCheckedAt, String lastError) {
    }

    public record ImportResult(String id, boolean added, boolean changed) {
    }

    public record RefreshResult(int total, int changed, int failed) {
    }

    public record ReloadResult(SubscriptionReload subscriptions, RuntimeReload runtimes) {
        public boolean successful() {
            return subscriptions.successful() && subscriptions.failed() == 0
                    && runtimes.successful();
        }
    }

    public record SubscriptionReload(boolean successful, int total, int changed,
                                     int failed, String error) {
    }

    public record RuntimeReload(boolean successful, int available, int total, String error) {
    }

    public record LyricsPayload(String original, String translation, String romanization) {
        public static final LyricsPayload EMPTY = new LyricsPayload(null, null, null);

        public LyricsPayload {
            original = normalize(original);
            translation = normalize(translation);
            romanization = normalize(romanization);
        }

        public boolean isEmpty() {
            return original == null && translation == null && romanization == null;
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.replace("\r\n", "\n")
                    .replace('\r', '\n').replace('\0', ' ').strip();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }
}
