package org.encinet.mik.module.music.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.LxSourceService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Loads bounded lyrics from local tags, LX actions, or provider LRC URLs. */
public final class LyricsService implements AutoCloseable {

    private static final int CACHE_VERSION = 1;
    private static final int MAX_CACHE_BYTES = 3 * 1024 * 1024 + 4096;
    private static final long MAX_CACHE_TOTAL_BYTES = 256L * 1024 * 1024;
    private static final int MAX_URL_LENGTH = 4096;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final Path cacheDirectory;
    private final LxSourceService sourceService;
    private final Consumer<String> warningLogger;
    private final LrcParser parser = new LrcParser();
    private final LocalEmbeddedLyricsReader localReader = new LocalEmbeddedLyricsReader();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ConcurrentHashMap<String, CompletableFuture<Optional<Lyrics>>> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object cacheWriteLock = new Object();

    public LyricsService(Path cacheDirectory, LxSourceService sourceService,
                         Consumer<String> warningLogger) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory")
                .toAbsolutePath().normalize();
        this.sourceService = Objects.requireNonNull(sourceService, "sourceService");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
    }

    public CompletableFuture<Optional<Lyrics>> load(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (closed.get()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String requestKey = requestKey(track);
        CompletableFuture<Optional<Lyrics>> future = inFlight.computeIfAbsent(requestKey,
                ignored -> loadNew(track));
        future.whenComplete((value, error) -> inFlight.remove(requestKey, future));
        return future;
    }

    private CompletableFuture<Optional<Lyrics>> loadNew(MusicTrack track) {
        TrackTarget target = track.target();
        if (target instanceof TrackTarget.LocalFile local) {
            return CompletableFuture.supplyAsync(() -> parseLocal(track, local), executor)
                    .exceptionally(error -> Optional.empty());
        }
        if (target instanceof TrackTarget.Lx lx) {
            return CompletableFuture.supplyAsync(() -> readCached(cacheKey(lx)), executor)
                    .thenCompose(cached -> cached == null
                            ? loadOnline(track, lx)
                            : CompletableFuture.completedFuture(parsed(track, cached)));
        }
        return CompletableFuture.completedFuture(Optional.empty());
    }

    private Optional<Lyrics> parseLocal(MusicTrack track, TrackTarget.LocalFile target) {
        try {
            return parsed(track, localReader.read(target));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private CompletableFuture<Optional<Lyrics>> loadOnline(
            MusicTrack track, TrackTarget.Lx target) {
        return sourceService.lyrics(target)
                .handle((payload, error) -> error == null && payload != null
                        ? fromPayload(payload) : new LyricSourceText(null, null, null))
                .thenCompose(actionLyrics -> actionLyrics.original() == null
                        ? CompletableFuture.supplyAsync(() -> merge(
                                actionLyrics, loadFromUrls(target)), executor)
                        : supplementTranslation(target, actionLyrics))
                .thenApplyAsync(source -> {
                    if (source.isEmpty()) {
                        return Optional.<Lyrics>empty();
                    }
                    Optional<Lyrics> parsed = parsed(track, source);
                    if (parsed.isPresent()) {
                        writeCached(cacheKey(target), source);
                    }
                    return parsed;
                }, executor)
                .exceptionally(error -> Optional.empty());
    }

    private CompletableFuture<LyricSourceText> supplementTranslation(
            TrackTarget.Lx target, LyricSourceText source) {
        if (source.translation() != null) {
            return CompletableFuture.completedFuture(source);
        }
        URI translationUrl = lyricUrl(target, "trcUrl");
        if (translationUrl == null) {
            return CompletableFuture.completedFuture(source);
        }
        return CompletableFuture.supplyAsync(() -> {
            String translation = fetch(translationUrl);
            return new LyricSourceText(source.original(), translation, source.romanization());
        }, executor).exceptionally(error -> source);
    }

    private LyricSourceText loadFromUrls(TrackTarget.Lx target) {
        URI originalUrl = lyricUrl(target, "lrcUrl");
        URI translationUrl = lyricUrl(target, "trcUrl");
        String original = originalUrl == null ? null : fetch(originalUrl);
        String translation = translationUrl == null ? null : fetch(translationUrl);
        return new LyricSourceText(original, translation, null);
    }

    private String fetch(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/plain, application/json;q=0.5, */*;q=0.1")
                    .GET().build();
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            validateHttpUri(response.uri().toString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                close(response.body());
                return null;
            }
            byte[] body;
            try (InputStream input = response.body()) {
                body = input.readNBytes(LrcParser.MAX_LYRIC_BYTES + 1);
            }
            if (body.length > LrcParser.MAX_LYRIC_BYTES) {
                return null;
            }
            return lyricBody(body, response.headers().firstValue("Content-Type").orElse(null));
        } catch (IOException | RuntimeException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Optional<Lyrics> parsed(MusicTrack track, LyricSourceText source) {
        Lyrics lyrics = parser.parse(source, track.details().audio().duration());
        return Optional.ofNullable(lyrics);
    }

    private LyricSourceText readCached(String key) {
        Path path = cacheDirectory.resolve(key + ".json");
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) {
                bytes = input.readNBytes(MAX_CACHE_BYTES + 1);
            }
            if (bytes.length > MAX_CACHE_BYTES) {
                return null;
            }
            JsonObject root = JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != CACHE_VERSION) {
                return null;
            }
            LyricSourceText source = new LyricSourceText(
                    jsonText(root, "original"), jsonText(root, "translation"),
                    jsonText(root, "romanization"));
            if (source.isEmpty()) {
                return null;
            }
            try {
                Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException ignored) {
                // A read-only cache remains usable even when its access time cannot be refreshed.
            }
            return source;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void writeCached(String key, LyricSourceText source) {
        synchronized (cacheWriteLock) {
            try {
                Files.createDirectories(cacheDirectory);
                JsonObject root = new JsonObject();
                root.addProperty("version", CACHE_VERSION);
                add(root, "original", source.original());
                add(root, "translation", source.translation());
                add(root, "romanization", source.romanization());
                byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_CACHE_BYTES) {
                    return;
                }
                Path target = cacheDirectory.resolve(key + ".json");
                Path temporary = Files.createTempFile(cacheDirectory, key + "-", ".part");
                try {
                    Files.write(temporary, bytes);
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    trimCache(target);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                warningLogger.accept("Failed to cache lyrics: " + message(exception));
            }
        }
    }

    private void trimCache(Path retained) throws IOException {
        List<CacheFile> files = new ArrayList<>();
        long total = 0;
        try (var paths = Files.list(cacheDirectory)) {
            for (Path path : paths.filter(candidate -> candidate.getFileName().toString()
                    .endsWith(".json")).toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                long size = Files.size(path);
                long lastModified = Files.getLastModifiedTime(
                        path, LinkOption.NOFOLLOW_LINKS).toMillis();
                files.add(new CacheFile(path, size, lastModified));
                total = Math.addExact(total, size);
            }
        } catch (ArithmeticException exception) {
            total = Long.MAX_VALUE;
        }
        if (total <= MAX_CACHE_TOTAL_BYTES) {
            return;
        }
        files.sort(Comparator.comparingLong(CacheFile::lastModifiedMillis));
        for (CacheFile file : files) {
            if (total <= MAX_CACHE_TOTAL_BYTES) {
                break;
            }
            if (file.path().equals(retained)) {
                continue;
            }
            if (Files.deleteIfExists(file.path())) {
                total = Math.max(0, total - file.size());
            }
        }
    }

    private static LyricSourceText fromPayload(LxSourceService.LyricsPayload payload) {
        return new LyricSourceText(payload.original(), payload.translation(), payload.romanization());
    }

    private static LyricSourceText merge(LyricSourceText preferred, LyricSourceText fallback) {
        return new LyricSourceText(
                preferred.original() != null ? preferred.original() : fallback.original(),
                preferred.translation() != null ? preferred.translation() : fallback.translation(),
                preferred.romanization() != null ? preferred.romanization() : fallback.romanization());
    }

    private static URI lyricUrl(TrackTarget.Lx target, String key) {
        try {
            JsonElement root = JsonParser.parseString(target.musicInfoJson());
            String value = findText(root, key, 0);
            return value == null ? null : validateHttpUri(value);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static String findText(JsonElement value, String key, int depth) {
        if (value == null || !value.isJsonObject() || depth >= 4) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        JsonElement direct = object.get(key);
        if (direct != null && direct.isJsonPrimitive()
                && direct.getAsJsonPrimitive().isString()) {
            String text = direct.getAsString().strip();
            if (!text.isEmpty()) {
                return text;
            }
        }
        for (String nestedKey : List.of("meta", "musicInfo", "data")) {
            String nested = findText(object.get(nestedKey), key, depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static URI validateHttpUri(String value) throws IOException {
        if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
            throw new IOException("Invalid lyric URL");
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null || scheme == null
                    || !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                throw new IOException("Only HTTP(S) lyric URLs are allowed");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid lyric URL", exception);
        }
    }

    private static String decode(byte[] bytes, String contentType) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb
                && bytes[2] == (byte) 0xbf) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        Charset charset = charset(contentType);
        return new String(bytes, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    private static String lyricBody(byte[] bytes, String contentType) {
        String decoded = decode(bytes, contentType).strip();
        if (decoded.isEmpty()) {
            return null;
        }
        try {
            JsonElement json = JsonParser.parseString(decoded);
            return responseText(json, 0);
        } catch (RuntimeException ignored) {
            return decoded;
        }
    }

    private static String responseText(JsonElement value, int depth) {
        if (value == null || value.isJsonNull() || depth >= 5) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString().strip();
            return text.isEmpty() ? null : text;
        }
        if (!value.isJsonObject()) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        for (String key : List.of("lyric", "lrc", "text", "content", "data", "result")) {
            String nested = responseText(object.get(key), depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Charset charset(String contentType) {
        if (contentType == null) {
            return null;
        }
        for (String part : contentType.split(";")) {
            String value = part.strip();
            if (value.regionMatches(true, 0, "charset=", 0, 8)) {
                try {
                    return Charset.forName(value.substring(8).replace("\"", "").strip());
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String requestKey(MusicTrack track) {
        return track.target() instanceof TrackTarget.Lx lx
                ? "online:" + cacheKey(lx) : "track:" + track.id();
    }

    private static String cacheKey(TrackTarget.Lx target) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        update(digest, target.source());
        update(digest, target.songId());
        update(digest, target.providerId());
        update(digest, target.musicInfoJson());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String jsonText(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static void add(JsonObject object, String key, String value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private static void close(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    private static String message(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause() : throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    private record CacheFile(Path path, long size, long lastModifiedMillis) {
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        inFlight.values().forEach(future -> future.cancel(true));
        inFlight.clear();
        httpClient.shutdownNow();
        executor.shutdownNow();
    }
}
