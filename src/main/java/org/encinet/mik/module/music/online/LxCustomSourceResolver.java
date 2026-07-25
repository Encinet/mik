package org.encinet.mik.module.music.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class LxCustomSourceResolver implements LxTrackResolver, MusicSearchService, AutoCloseable {

    public static final String ACTION_MUSIC_SEARCH = "musicSearch";
    private static final int MAX_RESULTS = 200;
    private static final int MAX_KEYWORD_LENGTH = 256;
    private static final int MAX_AGGREGATE_CALLS = 128;
    private static final String PREFERRED_QUALITY = "320k";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(60);
    private static final java.util.regex.Pattern REMOTE_SCRIPT_FILE =
            java.util.regex.Pattern.compile("remote-[0-9a-f]{24}\\.js");

    private final Path sourceDirectory;
    private final Path localDirectory;
    private final Path remoteDirectory;
    private final Supplier<Set<String>> managedRemoteScripts;
    private final Consumer<String> infoLogger;
    private final Consumer<String> warningLogger;
    private final Duration requestTimeout;
    private final int failureThreshold;
    private final Duration retryDelay;
    private final LxOnlineSearchService onlineSearch;
    private final ExecutorService availabilityExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object reloadLock = new Object();
    private final Set<LxCustomSourceRuntime> stagingRuntimes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final LxMusicTrackMapper trackMapper = new LxMusicTrackMapper();
    private volatile ResolverState state = new ResolverState(false, "320k", Duration.ofSeconds(20),
            3, Duration.ofSeconds(60), "", List.of());

    LxCustomSourceResolver(Path sourceDirectory, Consumer<String> infoLogger,
                                  Consumer<String> warningLogger) {
        this(sourceDirectory, infoLogger, warningLogger, null,
                REQUEST_TIMEOUT, FAILURE_THRESHOLD, RETRY_DELAY, null);
    }

    LxCustomSourceResolver(Path sourceDirectory, Consumer<String> infoLogger,
                                  Consumer<String> warningLogger,
                                  Supplier<Set<String>> managedRemoteScripts) {
        this(sourceDirectory, infoLogger, warningLogger, managedRemoteScripts,
                REQUEST_TIMEOUT, FAILURE_THRESHOLD, RETRY_DELAY, null);
    }

    LxCustomSourceResolver(Path sourceDirectory, Consumer<String> infoLogger,
                           Consumer<String> warningLogger, Duration requestTimeout,
                           int failureThreshold, Duration retryDelay) {
        this(sourceDirectory, infoLogger, warningLogger, null,
                requestTimeout, failureThreshold, retryDelay, null);
    }

    LxCustomSourceResolver(Path sourceDirectory, Consumer<String> infoLogger,
                           Consumer<String> warningLogger, Duration requestTimeout,
                           int failureThreshold, Duration retryDelay,
                           LxOnlineSearchService onlineSearch) {
        this(sourceDirectory, infoLogger, warningLogger, null,
                requestTimeout, failureThreshold, retryDelay, onlineSearch);
    }

    private LxCustomSourceResolver(Path sourceDirectory, Consumer<String> infoLogger,
                           Consumer<String> warningLogger,
                           Supplier<Set<String>> managedRemoteScripts,
                           Duration requestTimeout, int failureThreshold, Duration retryDelay,
                           LxOnlineSearchService onlineSearch) {
        this.sourceDirectory = sourceDirectory.toAbsolutePath().normalize();
        this.localDirectory = this.sourceDirectory.resolve("local");
        this.remoteDirectory = this.sourceDirectory.resolve("remote");
        this.managedRemoteScripts = managedRemoteScripts;
        this.infoLogger = infoLogger;
        this.warningLogger = warningLogger;
        this.requestTimeout = requestTimeout;
        this.failureThreshold = failureThreshold;
        this.retryDelay = retryDelay;
        this.onlineSearch = onlineSearch == null
                ? new LxOnlineSearchService(requestTimeout) : onlineSearch;
    }

    public void reload() {
        ResolverState previous;
        List<Channel> channels;
        synchronized (reloadLock) {
            if (closed.get()) {
                throw new IllegalStateException("LX custom-source resolver is closed");
            }
            ScriptSet scripts = scanScripts();
            if (state.enabled() && state.scriptFingerprint().equals(scripts.fingerprint())) {
                return;
            }
            channels = loadChannels(scripts.paths());
            synchronized (this) {
                if (closed.get()) {
                    closeChannels(channels);
                    throw new IllegalStateException("LX custom-source resolver is closed");
                }
                previous = state;
                state = new ResolverState(true, PREFERRED_QUALITY, requestTimeout,
                        failureThreshold, retryDelay, scripts.fingerprint(), channels);
            }
        }
        closeChannels(previous.channels());
        Instant now = Instant.now();
        long available = channels.stream().filter(channel -> channel.available(now)).count();
        infoLogger.accept("Loaded " + available + " available of " + channels.size()
                + " LX custom music sources from " + sourceDirectory);
    }

    @Override
    public CompletableFuture<String> resolve(TrackTarget.Lx target) {
        ResolverState current = state;
        if (!current.enabled()) {
            return CompletableFuture.failedFuture(new IOException("LX custom sources are disabled"));
        }

        List<Attempt> attempts = buildAttempts(current, target);
        if (attempts.isEmpty()) {
            return CompletableFuture.failedFuture(new IOException(
                    "No healthy LX custom source supports " + target.source()));
        }
        return attempt(current, target, attempts, 0, new ArrayList<>());
    }

    public List<SourceStatus> statuses() {
        Instant now = Instant.now();
        ResolverState current = state;
        return current.channels().stream().map(channel -> {
            channel.refreshIfNeeded(now, current.retryDelay());
            return channel.status(now);
        }).toList();
    }

    @Override
    public CompletableFuture<MusicSearchResult<MusicTrack>> searchMusic(
            String keyword, int page, int limit) {
        String normalizedKeyword = normalizeKeyword(keyword);
        JsonObject info = pagingInfo(normalizedKeyword, page, limit);
        return searchMusicActions(info, normalizedKeyword, page, limit).thenApply(results -> {
            Map<String, MusicTrack> tracks = new LinkedHashMap<>();
            int total = 0;
            List<String> failures = new ArrayList<>();
            for (ActionResult result : results) {
                if (result.error() != null) {
                    failures.add(result.label() + ": " + result.error());
                    continue;
                }
                JsonArray list = resultList(result.value());
                total = addTotal(total, result.value(), list.size());
                for (JsonElement element : list) {
                    if (tracks.size() >= MAX_RESULTS) {
                        break;
                    }
                    MusicTrack track = trackMapper.parse(element, result.source(), result.providerId());
                    if (track != null) {
                        tracks.putIfAbsent(track.id(), track);
                    }
                }
            }
            return new MusicSearchResult<>(List.copyOf(tracks.values()), total, failures);
        });
    }

    private CompletableFuture<List<ActionResult>> searchMusicActions(
            JsonObject info, String keyword, int page, int limit) {
        ResolverState current = state;
        if (!current.enabled()) {
            return CompletableFuture.failedFuture(
                    new IOException("LX custom sources are disabled"));
        }
        Instant now = Instant.now();
        Map<String, String> providersBySource = new LinkedHashMap<>();
        Map<String, List<CompletableFuture<ActionResult>>> customCalls = new LinkedHashMap<>();
        int customCallCount = 0;

        for (Channel channel : current.channels()) {
            channel.refreshIfNeeded(now, current.retryDelay());
            if (!channel.available(now)) {
                continue;
            }
            for (String source : channel.info().sourceQualities().keySet()) {
                providersBySource.putIfAbsent(source, channel.info().id());
                if (channel.info().supports(source, ACTION_MUSIC_SEARCH)
                        && customCallCount < MAX_AGGREGATE_CALLS) {
                    customCalls.computeIfAbsent(source, ignored -> new ArrayList<>())
                            .add(invokeAction(current, channel, source,
                                    ACTION_MUSIC_SEARCH, info.deepCopy()));
                    customCallCount++;
                }
            }
        }

        List<CompletableFuture<List<ActionResult>>> sourceCalls = new ArrayList<>();
        for (Map.Entry<String, String> entry : providersBySource.entrySet()) {
            String source = entry.getKey();
            List<CompletableFuture<ActionResult>> custom = customCalls.getOrDefault(source, List.of());
            if (custom.isEmpty()) {
                if (onlineSearch.supports(source)) {
                    sourceCalls.add(searchPlatform(source, entry.getValue(), keyword, page, limit)
                            .thenApply(List::of));
                }
                continue;
            }
            CompletableFuture<List<ActionResult>> customResults = CompletableFuture
                    .allOf(custom.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> custom.stream().map(CompletableFuture::join).toList());
            if (onlineSearch.supports(source)) {
                customResults = customResults.thenCompose(results -> {
                    if (results.stream().anyMatch(this::hasUsableSearchTrack)) {
                        return CompletableFuture.completedFuture(results);
                    }
                    return searchPlatform(source, entry.getValue(), keyword, page, limit)
                            .thenApply(fallback -> {
                                List<ActionResult> combined = new ArrayList<>(results);
                                combined.add(fallback);
                                return List.copyOf(combined);
                            });
                });
            }
            sourceCalls.add(customResults);
        }

        if (sourceCalls.isEmpty()) {
            return CompletableFuture.failedFuture(new IOException(
                    "No healthy LX custom source supports online music search"));
        }
        return CompletableFuture.allOf(sourceCalls.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> sourceCalls.stream()
                        .flatMap(call -> call.join().stream()).toList());
    }

    private boolean hasUsableSearchTrack(ActionResult result) {
        if (result.error() != null) {
            return false;
        }
        for (JsonElement element : resultList(result.value())) {
            if (trackMapper.parse(element, result.source(), result.providerId()) != null) {
                return true;
            }
        }
        return false;
    }

    private CompletableFuture<ActionResult> searchPlatform(
            String source, String providerId, String keyword, int page, int limit) {
        return onlineSearch.search(source, keyword, page, limit)
                .handle((result, error) -> {
                    if (error != null) {
                        return new ActionResult(providerId, "LX catalog", source,
                                null, rootMessage(error));
                    }
                    JsonObject value = new JsonObject();
                    value.add("list", result.tracks());
                    value.addProperty("total", result.total());
                    return new ActionResult(providerId, "LX catalog", source, value, null);
                });
    }

    private CompletableFuture<ActionResult> invokeAction(
            ResolverState state, Channel channel, String source, String action, JsonObject info) {
        LxCustomSourceRuntime runtime = channel.runtime();
        if (runtime == null) {
            return CompletableFuture.completedFuture(new ActionResult(
                    channel.info().id(), channel.info().name(), source, null, "source is reloading"));
        }
        return runtime.requestAction(action, source, info)
                .orTimeout(state.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handle((value, error) -> {
                    if (error == null) {
                        channel.succeeded();
                        return new ActionResult(channel.info().id(), channel.info().name(),
                                source, value, null);
                    }
                    String message = rootMessage(error);
                    if (isTimeout(error)) {
                        channel.timedOut(state.retryDelay(), message);
                    } else {
                        channel.failed(state.failureThreshold(), state.retryDelay(), message);
                    }
                    return new ActionResult(channel.info().id(), channel.info().name(),
                            source, null, message);
                });
    }

    private static JsonObject pagingInfo(String keyword, int page, int limit) {
        JsonObject info = new JsonObject();
        if (keyword != null) {
            info.addProperty("keyword", keyword);
        }
        info.addProperty("page", Math.max(1, page));
        info.addProperty("limit", Math.max(1, Math.min(MAX_RESULTS, limit)));
        return info;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String normalized = keyword.strip();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("Search keyword exceeds " + MAX_KEYWORD_LENGTH + " characters");
        }
        return normalized;
    }

    private static JsonArray resultList(JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return new JsonArray();
        }
        if (result.isJsonArray()) {
            return result.getAsJsonArray();
        }
        if (!result.isJsonObject()) {
            return new JsonArray();
        }
        JsonObject object = result.getAsJsonObject();
        for (String key : List.of("list", "tracks", "data")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) {
                return value.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private static int addTotal(int current, JsonElement result, int fallback) {
        if (result != null && result.isJsonObject()) {
            JsonElement total = result.getAsJsonObject().get("total");
            if (total != null && total.isJsonPrimitive() && total.getAsJsonPrimitive().isNumber()) {
                try {
                    return Math.addExact(current, Math.max(0, total.getAsInt()));
                } catch (ArithmeticException | NumberFormatException ignored) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        try {
            return Math.addExact(current, fallback);
        } catch (ArithmeticException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private CompletableFuture<String> attempt(ResolverState state, TrackTarget.Lx target,
                                              List<Attempt> attempts, int index,
                                              List<String> errors) {
        if (index >= attempts.size()) {
            return CompletableFuture.failedFuture(new IOException(
                    "All LX custom sources failed: " + String.join("; ", errors)));
        }
        Attempt attempt = attempts.get(index);
        LxCustomSourceRuntime runtime = attempt.channel().runtime();
        if (runtime == null) {
            errors.add(attempt.channel().info().name() + ": source is reloading");
            return attempt(state, target, attempts, index + 1, errors);
        }
        return runtime.resolve(target, attempt.quality())
                .orTimeout(state.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handle((url, error) -> {
                    if (error == null) {
                        attempt.channel().succeeded();
                        return CompletableFuture.completedFuture(url);
                    }
                    String message = rootMessage(error);
                    if (isTimeout(error)) {
                        attempt.channel().timedOut(state.retryDelay(), message);
                    } else {
                        attempt.channel().failed(state.failureThreshold(), state.retryDelay(), message);
                    }
                    errors.add(attempt.channel().info().name() + ": " + message);
                    return attempt(state, target, attempts, index + 1, errors);
                })
                .thenCompose(future -> future);
    }

    private static boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != current) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<Attempt> buildAttempts(ResolverState state, TrackTarget.Lx target) {
        Instant now = Instant.now();
        List<Attempt> attempts = new ArrayList<>();
        appendAttempts(attempts, state, target, now, true);
        appendAttempts(attempts, state, target, now, false);
        return attempts;
    }

    private static void appendAttempts(List<Attempt> attempts, ResolverState state,
                                       TrackTarget.Lx target, Instant now,
                                       boolean originatingProvider) {
        for (Channel channel : state.channels()) {
            boolean matchesProvider = target.providerId() != null
                    && target.providerId().equals(channel.info().id());
            if (matchesProvider != originatingProvider
                    || target.providerId() == null && originatingProvider) {
                continue;
            }
            channel.refreshIfNeeded(now, state.retryDelay());
            if (!channel.available(now)) {
                continue;
            }
            String quality = selectQuality(state.quality(), target.qualities(),
                    channel.info().qualities(target.source()));
            if (quality != null) {
                attempts.add(new Attempt(channel, quality));
            }
        }
    }

    static String selectQuality(String configured, List<String> trackQualities,
                                List<String> sourceQualities) {
        if (sourceQualities.isEmpty()) {
            return null;
        }
        Set<String> candidates = new LinkedHashSet<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(normalizeQuality(configured));
        }
        candidates.addAll(List.of("flac24bit", "flac", "ape", "wav", "320k", "192k", "128k"));
        candidates.addAll(trackQualities.stream().map(LxCustomSourceResolver::normalizeQuality).toList());
        Set<String> track = new LinkedHashSet<>(trackQualities.stream()
                .map(LxCustomSourceResolver::normalizeQuality).toList());
        Set<String> source = new LinkedHashSet<>(sourceQualities.stream()
                .map(LxCustomSourceResolver::normalizeQuality).toList());
        for (String candidate : candidates) {
            if (source.contains(candidate) && (track.isEmpty() || track.contains(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizeQuality(String quality) {
        String normalized = quality == null ? "" : quality.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("hires") ? "flac24bit" : normalized;
    }

    private ScriptSet scanScripts() {
        try {
            Files.createDirectories(localDirectory);
            Files.createDirectories(remoteDirectory);
        } catch (IOException exception) {
            throw new CompletionException(new IOException(
                    "Failed to create LX source directories", exception));
        }

        List<Path> scripts = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(localDirectory)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".js"))
                    .forEach(scripts::add);
        } catch (IOException exception) {
            throw new CompletionException(new IOException(
                    "Failed to scan local LX custom sources", exception));
        }
        Set<String> remoteScriptNames = managedRemoteScripts == null
                ? null : Set.copyOf(managedRemoteScripts.get());
        try (Stream<Path> paths = Files.list(remoteDirectory)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> REMOTE_SCRIPT_FILE.matcher(
                            path.getFileName().toString()).matches())
                    .filter(path -> remoteScriptNames == null
                            || remoteScriptNames.contains(path.getFileName().toString()))
                    .forEach(scripts::add);
        } catch (IOException exception) {
            throw new CompletionException(new IOException(
                    "Failed to scan imported LX custom sources", exception));
        }
        scripts.sort(Comparator.comparing(this::relativeId));
        return new ScriptSet(List.copyOf(scripts), fingerprint(scripts));
    }

    private String fingerprint(List<Path> scripts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] buffer = new byte[8192];
        for (Path script : scripts) {
            byte[] id = relativeId(script).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            digest.update((byte) (id.length >>> 24));
            digest.update((byte) (id.length >>> 16));
            digest.update((byte) (id.length >>> 8));
            digest.update((byte) id.length);
            digest.update(id);
            MessageDigest scriptDigest;
            try {
                scriptDigest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
            try (InputStream input = Files.newInputStream(script)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    scriptDigest.update(buffer, 0, read);
                }
            } catch (IOException exception) {
                throw new CompletionException(new IOException(
                        "Failed to read LX custom source " + relativeId(script), exception));
            }
            digest.update(scriptDigest.digest());
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private List<Channel> loadChannels(List<Path> scripts) {
        List<PendingChannel> pendingChannels = new ArrayList<>();
        List<Channel> channels = new ArrayList<>();
        boolean completed = false;
        try {
            for (Path script : scripts) {
                ensureOpenForReload();
                String id = relativeId(script);
                LxCustomSourceRuntime runtime = null;
                try {
                    runtime = newRuntime(id, script, requestTimeout);
                    stagingRuntimes.add(runtime);
                    ensureOpenForReload();
                    runtime.initialized().orTimeout(
                            requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    pendingChannels.add(new PendingChannel(id, script, runtime, null));
                    runtime = null;
                } catch (Exception exception) {
                    if (runtime != null) {
                        runtime.close();
                        stagingRuntimes.remove(runtime);
                    }
                    pendingChannels.add(new PendingChannel(id, script, null, rootMessage(exception)));
                }
            }

            for (PendingChannel pending : pendingChannels) {
                ensureOpenForReload();
                if (pending.runtime() == null) {
                    channels.add(new Channel(pending.script(), pending.id(), pending.id(),
                            requestTimeout, retryDelay, pending.initialError()));
                    warningLogger.accept("LX source unavailable: " + pending.id() + ": "
                            + pending.initialError());
                    continue;
                }
                try {
                    LxCustomSourceRuntime.SourceInfo info = pending.runtime().initialized()
                            .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    channels.add(new Channel(pending.script(), requestTimeout, pending.runtime(), info));
                    infoLogger.accept("LX source available: " + info.name() + " [" + pending.id() + "] "
                            + info.sourceActions());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new IOException(
                            "LX custom-source reload was interrupted", exception));
                } catch (Exception exception) {
                    pending.runtime().close();
                    stagingRuntimes.remove(pending.runtime());
                    String error = rootMessage(exception);
                    channels.add(new Channel(pending.script(), pending.id(),
                            pending.runtime().displayName(), requestTimeout, retryDelay, error));
                    warningLogger.accept("LX source unavailable: " + pending.id() + ": " + error);
                }
            }
            completed = true;
            return List.copyOf(channels);
        } finally {
            if (!completed) {
                pendingChannels.stream().map(PendingChannel::runtime)
                        .filter(java.util.Objects::nonNull)
                        .forEach(LxCustomSourceRuntime::close);
                closeChannels(channels);
            }
            pendingChannels.stream().map(PendingChannel::runtime)
                    .filter(java.util.Objects::nonNull)
                    .forEach(stagingRuntimes::remove);
        }
    }

    private void ensureOpenForReload() {
        if (closed.get()) {
            throw new IllegalStateException("LX custom-source resolver is closed");
        }
    }

    private LxCustomSourceRuntime newRuntime(String id, Path script, Duration requestTimeout)
            throws IOException {
        return new LxCustomSourceRuntime(id, script, requestTimeout, warningLogger);
    }

    private String relativeId(Path path) {
        return sourceDirectory.relativize(path.toAbsolutePath().normalize())
                .toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void closeChannels(List<Channel> channels) {
        channels.forEach(Channel::close);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<Channel> channels;
        synchronized (this) {
            channels = state.channels();
            state = new ResolverState(false, "320k", Duration.ofSeconds(20),
                    3, Duration.ofSeconds(60), "", List.of());
        }
        stagingRuntimes.forEach(LxCustomSourceRuntime::close);
        stagingRuntimes.clear();
        closeChannels(channels);
        availabilityExecutor.shutdownNow();
        onlineSearch.close();
    }

    public record SourceStatus(String id, String name, boolean available,
                               int consecutiveFailures, Instant retryAt, String lastError,
                               Map<String, List<String>> capabilities,
                               Map<String, List<String>> actions) {
    }

    private record ResolverState(boolean enabled, String quality, Duration requestTimeout,
                                 int failureThreshold, Duration retryDelay,
                                 String scriptFingerprint, List<Channel> channels) {
    }

    private record ScriptSet(List<Path> paths, String fingerprint) {
    }

    private record Attempt(Channel channel, String quality) {
    }

    private record ActionResult(String providerId, String providerName, String source,
                                JsonElement value, String error) {
        String label() {
            return providerName + "/" + source;
        }
    }

    private record PendingChannel(String id, Path script, LxCustomSourceRuntime runtime,
                                  String initialError) {
    }

    private final class Channel {
        private final Path script;
        private final Duration requestTimeout;
        private LxCustomSourceRuntime runtime;
        private LxCustomSourceRuntime.SourceInfo info;
        private int consecutiveFailures;
        private Instant retryAt;
        private String lastError;
        private boolean refreshing;
        private boolean closed;

        private Channel(Path script, Duration requestTimeout, LxCustomSourceRuntime runtime,
                        LxCustomSourceRuntime.SourceInfo info) {
            this.script = script;
            this.requestTimeout = requestTimeout;
            this.runtime = runtime;
            this.info = info;
        }

        private Channel(Path script, String id, String name, Duration requestTimeout,
                        Duration retryDelay, String error) {
            this(script, requestTimeout, null,
                    new LxCustomSourceRuntime.SourceInfo(id, name, Map.of(), Map.of()));
            consecutiveFailures = 1;
            retryAt = Instant.now().plus(retryDelay);
            lastError = error;
        }

        synchronized boolean available(Instant now) {
            return !closed && runtime != null && (retryAt == null || !now.isBefore(retryAt));
        }

        synchronized void refreshIfNeeded(Instant now, Duration retryDelay) {
            if (closed || runtime != null || refreshing || retryAt != null && now.isBefore(retryAt)) {
                return;
            }
            refreshing = true;
            availabilityExecutor.execute(() -> refresh(retryDelay));
        }

        private void refresh(Duration retryDelay) {
            LxCustomSourceRuntime replacement = null;
            try {
                LxCustomSourceRuntime.SourceInfo currentInfo;
                synchronized (this) {
                    currentInfo = info;
                }
                replacement = newRuntime(currentInfo.id(), script, requestTimeout);
                LxCustomSourceRuntime.SourceInfo replacementInfo = replacement.initialized()
                        .get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                synchronized (this) {
                    if (closed) {
                        replacement.close();
                        return;
                    }
                    runtime = replacement;
                    info = replacementInfo;
                    consecutiveFailures = 0;
                    retryAt = null;
                    lastError = null;
                    replacement = null;
                }
                infoLogger.accept("LX source recovered: " + replacementInfo.name()
                        + " [" + replacementInfo.id() + "]");
            } catch (Exception exception) {
                String error = rootMessage(exception);
                synchronized (this) {
                    lastError = error;
                    retryAt = Instant.now().plus(retryDelay);
                }
                warningLogger.accept("LX source still unavailable: " + info().id() + ": " + error);
            } finally {
                if (replacement != null) {
                    replacement.close();
                }
                synchronized (this) {
                    refreshing = false;
                }
            }
        }

        synchronized void succeeded() {
            consecutiveFailures = 0;
            retryAt = null;
            lastError = null;
        }

        synchronized void failed(int threshold, java.time.Duration retryDelay, String error) {
            consecutiveFailures++;
            lastError = error;
            if (consecutiveFailures >= threshold) {
                retryAt = Instant.now().plus(retryDelay);
            }
        }

        synchronized void timedOut(Duration retryDelay, String error) {
            consecutiveFailures++;
            lastError = error;
            retryAt = Instant.now().plus(retryDelay);
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
        }

        synchronized SourceStatus status(Instant now) {
            return new SourceStatus(info.id(), info.name(), available(now), consecutiveFailures,
                    retryAt, lastError, info.sourceQualities(), effectiveActions());
        }

        private Map<String, List<String>> effectiveActions() {
            Map<String, List<String>> actions = new LinkedHashMap<>();
            for (String source : info.sourceQualities().keySet()) {
                List<String> declared = new ArrayList<>(
                        info.sourceActions().getOrDefault(source, List.of()));
                if (onlineSearch.supports(source)
                        && declared.stream().noneMatch(ACTION_MUSIC_SEARCH::equalsIgnoreCase)) {
                    declared.add("musicsearch(catalog)");
                }
                actions.put(source, List.copyOf(declared));
            }
            return Map.copyOf(actions);
        }

        LxCustomSourceRuntime runtime() {
            synchronized (this) {
                return runtime;
            }
        }

        LxCustomSourceRuntime.SourceInfo info() {
            synchronized (this) {
                return info;
            }
        }

        synchronized void close() {
            closed = true;
            if (runtime != null) {
                runtime.close();
                runtime = null;
            }
        }
    }
}
