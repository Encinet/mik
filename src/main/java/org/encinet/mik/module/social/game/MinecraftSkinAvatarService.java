package org.encinet.mik.module.social.game;


import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Downloads official skin textures and caches locally rendered face avatars. */
final class MinecraftSkinAvatarService {
    private static final int TIMEOUT_MILLIS = 2_000;
    private static final int MAXIMUM_SKIN_BYTES = 1_048_576;
    private static final int MAXIMUM_CACHE_ENTRIES = 512;
    private static final long FAILED_DOWNLOAD_CACHE_NANOS = Duration.ofSeconds(30).toNanos();

    private final Map<URI, CacheEntry> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<URI, CacheEntry> eldest) {
            return size() > MAXIMUM_CACHE_ENTRIES;
        }
    };
    private final ConcurrentMap<URI, CompletableFuture<Optional<SocialPlayerAvatar>>> inFlight =
            new ConcurrentHashMap<>();
    private final Downloader downloader;
    private final LongSupplier nanoTime;

    MinecraftSkinAvatarService() {
        this(MinecraftSkinAvatarService::download, System::nanoTime);
    }

    MinecraftSkinAvatarService(Downloader downloader, LongSupplier nanoTime) {
        this.downloader = Objects.requireNonNull(downloader, "downloader");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    Optional<SocialPlayerAvatar> avatar(URI skinTextureUrl) {
        URI url = Objects.requireNonNull(skinTextureUrl, "skinTextureUrl");
        CacheEntry cached = cached(url);
        if (cached != null) {
            return cached.avatar();
        }

        CompletableFuture<Optional<SocialPlayerAvatar>> pending = new CompletableFuture<>();
        CompletableFuture<Optional<SocialPlayerAvatar>> existing =
                inFlight.putIfAbsent(url, pending);
        if (existing != null) {
            return existing.join();
        }

        Optional<SocialPlayerAvatar> rendered;
        try {
            rendered = downloader.download(url).flatMap(MinecraftSkinFaceRenderer::render);
        } catch (RuntimeException ignored) {
            rendered = Optional.empty();
        }
        synchronized (cache) {
            cache.put(url, new CacheEntry(rendered, nanoTime.getAsLong()));
        }
        pending.complete(rendered);
        inFlight.remove(url, pending);
        return rendered;
    }

    private CacheEntry cached(URI url) {
        synchronized (cache) {
            CacheEntry entry = cache.get(url);
            if (entry == null) {
                return null;
            }
            if (entry.avatar().isPresent()
                    || nanoTime.getAsLong() - entry.storedAtNanos()
                    < FAILED_DOWNLOAD_CACHE_NANOS) {
                return entry;
            }
            cache.remove(url);
            return null;
        }
    }

    private static Optional<byte[]> download(URI skinTextureUrl) {
        HttpURLConnection connection = null;
        try {
            URLConnection opened = skinTextureUrl.toURL().openConnection();
            if (!(opened instanceof HttpURLConnection http)) {
                return Optional.empty();
            }
            connection = http;
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/png");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }
            int declaredLength = connection.getContentLength();
            if (declaredLength > MAXIMUM_SKIN_BYTES) {
                return Optional.empty();
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] data = input.readNBytes(MAXIMUM_SKIN_BYTES + 1);
                return data.length > MAXIMUM_SKIN_BYTES
                        ? Optional.empty() : Optional.of(data);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            return Optional.empty();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @FunctionalInterface
    interface Downloader {
        Optional<byte[]> download(URI url);
    }

    private record CacheEntry(
            Optional<SocialPlayerAvatar> avatar,
            long storedAtNanos
    ) {
        private CacheEntry {
            avatar = Objects.requireNonNull(avatar, "avatar");
        }
    }
}
