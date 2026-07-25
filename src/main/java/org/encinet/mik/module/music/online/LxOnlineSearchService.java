package org.encinet.mik.module.music.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Searches LX-supported platform catalogs without resolving or downloading audio. */
final class LxOnlineSearchService implements AutoCloseable {

    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_RESULTS_PER_SOURCE = 30;
    private static final Set<String> SUPPORTED_SOURCES = Set.of("kw", "kg", "tx", "wy", "mg");
    private static final Pattern KUWO_QUALITY = Pattern.compile(
            "bitrate:(\\d+).*?size:([^;,]+)", Pattern.CASE_INSENSITIVE);
    private static final String MIGU_DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20";
    private static final String MIGU_SIGNATURE_MD5 = "6cdc72a439cef99a3418d2a78aa28c73";
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36";

    private final Duration timeout;
    private final RequestExecutor requestExecutor;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();

    LxOnlineSearchService(Duration timeout) {
        this.timeout = timeout;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.httpClient = client;
        this.requestExecutor = request -> execute(client, request);
    }

    LxOnlineSearchService(Duration timeout, RequestExecutor requestExecutor) {
        this.timeout = timeout;
        this.httpClient = null;
        this.requestExecutor = requestExecutor;
    }

    boolean supports(String source) {
        return source != null && SUPPORTED_SOURCES.contains(source.toLowerCase(Locale.ROOT));
    }

    CompletableFuture<SearchResult> search(String source, String keyword, int page, int limit) {
        String normalizedSource = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (!supports(normalizedSource)) {
            return CompletableFuture.failedFuture(
                    new IOException("Unsupported LX online search source: " + normalizedSource));
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("LX online search is closed"));
        }
        int boundedPage = Math.max(1, page);
        int boundedLimit = Math.max(1, Math.min(MAX_RESULTS_PER_SOURCE, limit));
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (closed.get()) {
                    throw new CompletionException(new IOException("LX online search is closed"));
                }
                try {
                    return switch (normalizedSource) {
                        case "kw" -> searchKuwo(keyword, boundedPage, boundedLimit);
                        case "kg" -> searchKugou(keyword, boundedPage, boundedLimit);
                        case "tx" -> searchTencent(keyword, boundedPage, boundedLimit);
                        case "wy" -> searchNetease(keyword, boundedPage, boundedLimit);
                        case "mg" -> searchMigu(keyword, boundedPage, boundedLimit);
                        default -> throw new IOException(
                                "Unsupported LX online search source: " + normalizedSource);
                    };
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new CompletionException(exception);
                }
            }, executor);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(new IOException("LX online search is closed", exception));
        }
    }

    private SearchResult searchKuwo(String keyword, int page, int limit)
            throws IOException, InterruptedException {
        String url = "https://search.kuwo.cn/r.s?client=kt&all=" + encode(keyword)
                + "&pn=" + (page - 1) + "&rn=" + limit
                + "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1"
                + "&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012"
                + "&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1";
        JsonObject response = object(request(get(url)));
        JsonArray values = array(response, "abslist");
        if (values.isEmpty() && nonNegativeLong(response, "TOTAL") > 0) {
            throw new IOException("Kuwo search returned no result data");
        }
        JsonArray tracks = new JsonArray();
        for (JsonElement value : values) {
            JsonObject raw = object(value);
            String songId = stripPrefix(text(raw, "MUSICRID"), "MUSIC_");
            String name = firstText(raw, "SONGNAME", "NAME");
            if (!hasText(songId) || !hasText(name)) {
                continue;
            }
            JsonObject item = baseTrack("kw", songId, name,
                    text(raw, "ARTIST"), text(raw, "ALBUM"), seconds(raw, "DURATION"));
            copyText(raw, item, "ALBUMID", "albumId");
            addKuwoQualities(item, firstText(raw, "N_MINFO", "MINFO"));
            tracks.add(item);
            if (tracks.size() >= limit) {
                break;
            }
        }
        return new SearchResult(tracks, nonNegativeInt(response, tracks.size(), "TOTAL"));
    }

    private SearchResult searchKugou(String keyword, int page, int limit)
            throws IOException, InterruptedException {
        String url = "https://songsearch.kugou.com/song_search_v2?keyword=" + encode(keyword)
                + "&page=" + page + "&pagesize=" + limit
                + "&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1"
                + "&privilege_filter=0&area_code=1";
        JsonObject response = object(request(get(url)));
        if (longValue(response, "error_code", 0) != 0) {
            throw new IOException("Kugou search failed: " + firstText(response, "error_msg"));
        }
        JsonObject data = object(response.get("data"));
        JsonArray tracks = new JsonArray();
        for (JsonElement value : array(data, "lists")) {
            JsonObject raw = object(value);
            String songId = firstText(raw, "Audioid", "MixSongID", "ID");
            String hash = text(raw, "FileHash");
            String name = text(raw, "SongName");
            if (!hasText(songId) || !hasText(hash) || !hasText(name)) {
                continue;
            }
            JsonObject item = baseTrack("kg", songId, name,
                    firstText(raw, "SingerName", "FileName"), text(raw, "AlbumName"),
                    seconds(raw, "Duration"));
            item.addProperty("hash", hash);
            copyText(raw, item, "AlbumID", "albumId");
            addKugouQuality(item, "128k", raw, "FileSize", "FileHash");
            addKugouQuality(item, "320k", raw, "HQFileSize", "HQFileHash");
            addKugouQuality(item, "flac", raw, "SQFileSize", "SQFileHash");
            addKugouQuality(item, "flac24bit", raw, "ResFileSize", "ResFileHash");
            tracks.add(item);
            if (tracks.size() >= limit) {
                break;
            }
        }
        return new SearchResult(tracks, nonNegativeInt(data, tracks.size(), "total"));
    }

    private SearchResult searchTencent(String keyword, int page, int limit)
            throws IOException, InterruptedException {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=" + page
                + "&n=" + limit + "&w=" + encode(keyword) + "&cr=1&new_json=1";
        JsonObject response = object(request(HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y.qq.com/")
                .GET().build()));
        if (longValue(response, "code", 0) != 0) {
            throw new IOException("Tencent search failed: " + firstText(response, "message"));
        }
        JsonObject song = object(object(response.get("data")).get("song"));
        JsonArray tracks = new JsonArray();
        for (JsonElement value : array(song, "list")) {
            JsonObject raw = object(value);
            JsonObject file = object(raw.get("file"));
            String songId = text(raw, "mid");
            String name = firstText(raw, "title", "name");
            if (!hasText(songId) || !hasText(name) || !hasText(text(file, "media_mid"))) {
                continue;
            }
            JsonObject album = object(raw.get("album"));
            JsonObject item = baseTrack("tx", songId, name,
                    joinNames(array(raw, "singer")), text(album, "name"),
                    seconds(raw, "interval"));
            copyText(raw, item, "id", "songId");
            copyText(file, item, "media_mid", "strMediaMid");
            copyText(album, item, "mid", "albumMid");
            copyText(album, item, "mid", "albumId");
            addSizeQuality(item, "128k", file, "size_128mp3");
            addSizeQuality(item, "320k", file, "size_320mp3");
            addSizeQuality(item, "flac", file, "size_flac");
            addSizeQuality(item, "flac24bit", file, "size_hires");
            tracks.add(item);
            if (tracks.size() >= limit) {
                break;
            }
        }
        return new SearchResult(tracks, nonNegativeInt(song, tracks.size(), "totalnum"));
    }

    private SearchResult searchNetease(String keyword, int page, int limit)
            throws IOException, InterruptedException {
        String body = "s=" + encode(keyword) + "&type=1&limit=" + limit
                + "&offset=" + ((page - 1) * limit);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://music.163.com/api/search/get/web"))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonObject response = object(request(request));
        if (response.has("code") && longValue(response, "code", 0) != 200) {
            throw new IOException("NetEase search failed: " + firstText(response, "message"));
        }
        JsonObject result = object(response.get("result"));
        JsonArray tracks = new JsonArray();
        for (JsonElement value : array(result, "songs")) {
            JsonObject raw = object(value);
            String songId = text(raw, "id");
            String name = text(raw, "name");
            if (!hasText(songId) || !hasText(name)) {
                continue;
            }
            JsonObject album = object(raw.get("album"));
            JsonObject item = baseTrack("wy", songId, name,
                    joinNames(array(raw, "artists")), text(album, "name"),
                    milliseconds(raw, "duration"));
            copyText(album, item, "id", "albumId");
            tracks.add(item);
            if (tracks.size() >= limit) {
                break;
            }
        }
        return new SearchResult(tracks, nonNegativeInt(result, tracks.size(), "songCount"));
    }

    private SearchResult searchMigu(String keyword, int page, int limit)
            throws IOException, InterruptedException {
        String timestamp = Long.toString(System.currentTimeMillis());
        String sign = md5(keyword + MIGU_SIGNATURE_MD5
                + "yyapp2d16148780a1dcc7408e06336b98cfd50" + MIGU_DEVICE_ID + timestamp);
        String url = "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0"
                + "&isCopyright=1&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C"
                + "%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C"
                + "%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D"
                + "&pageSize=" + limit + "&text=" + encode(keyword) + "&pageNo=" + page
                + "&sort=0&sid=USS";
        JsonObject response = object(request(HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("uiVersion", "A_music_3.6.1")
                .header("deviceId", MIGU_DEVICE_ID)
                .header("timestamp", timestamp)
                .header("sign", sign)
                .header("channel", "0146921")
                .GET().build()));
        String code = text(response, "code");
        if (code != null && !code.equals("000000")) {
            throw new IOException("Migu search failed: " + firstText(response, "info"));
        }
        JsonObject data = object(response.get("songResultData"));
        JsonArray tracks = new JsonArray();
        collectMiguTracks(data.get("resultList"), tracks, limit);
        return new SearchResult(tracks, nonNegativeInt(data, tracks.size(), "totalCount"));
    }

    private void collectMiguTracks(JsonElement value, JsonArray tracks, int limit) {
        if (value == null || value.isJsonNull() || tracks.size() >= limit) {
            return;
        }
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                collectMiguTracks(child, tracks, limit);
                if (tracks.size() >= limit) {
                    break;
                }
            }
            return;
        }
        JsonObject raw = object(value);
        String songId = text(raw, "songId");
        String copyrightId = text(raw, "copyrightId");
        String name = text(raw, "name");
        if (!hasText(songId) || !hasText(copyrightId) || !hasText(name)) {
            return;
        }
        JsonObject item = baseTrack("mg", songId, name,
                joinNames(array(raw, "singerList")), text(raw, "album"),
                seconds(raw, "duration"));
        item.addProperty("copyrightId", copyrightId);
        copyText(raw, item, "albumId", "albumId");
        copyText(raw, item, "lrcUrl", "lrcUrl");
        copyText(raw, item, "mrcurl", "mrcUrl");
        copyText(raw, item, "trcUrl", "trcUrl");
        for (JsonElement formatValue : array(raw, "audioFormats")) {
            JsonObject format = object(formatValue);
            String quality = switch (firstText(format, "formatType")) {
                case "PQ" -> "128k";
                case "HQ" -> "320k";
                case "SQ" -> "flac";
                case "ZQ24" -> "flac24bit";
                default -> null;
            };
            if (quality != null) {
                addQuality(item, quality, firstPositiveLong(format, "asize", "isize"),
                        null, null);
            }
        }
        tracks.add(item);
    }

    private HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .GET().build();
    }

    private JsonElement request(HttpRequest request) throws IOException, InterruptedException {
        return requestExecutor.execute(request);
    }

    private static JsonElement execute(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("LX online search returned HTTP " + response.statusCode());
            }
            return JsonParser.parseString(new String(readLimited(body), StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("LX online search returned invalid JSON", exception);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("LX online search response exceeds "
                        + MAX_RESPONSE_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static JsonObject baseTrack(String source, String songId, String name,
                                        String singer, String album, String interval) {
        JsonObject item = new JsonObject();
        item.addProperty("id", source + "_" + songId);
        item.addProperty("source", source);
        item.addProperty("songmid", songId);
        item.addProperty("name", decodeHtml(name));
        addText(item, "singer", decodeHtml(singer));
        addText(item, "albumName", decodeHtml(album));
        addText(item, "interval", interval);
        item.add("types", new JsonArray());
        item.add("_types", new JsonObject());
        item.add("typeUrl", new JsonObject());
        return item;
    }

    private static void addKuwoQualities(JsonObject item, String raw) {
        if (!hasText(raw)) {
            return;
        }
        for (String entry : raw.split(";")) {
            Matcher matcher = KUWO_QUALITY.matcher(entry);
            if (!matcher.find()) {
                continue;
            }
            String quality = switch (matcher.group(1)) {
                case "4000" -> "flac24bit";
                case "2000" -> "flac";
                case "320" -> "320k";
                case "128" -> "128k";
                default -> null;
            };
            if (quality != null) {
                addQuality(item, quality, 0, matcher.group(2), null);
            }
        }
    }

    private static void addKugouQuality(JsonObject item, String quality, JsonObject raw,
                                        String sizeKey, String hashKey) {
        long size = positiveLong(raw, sizeKey);
        String hash = text(raw, hashKey);
        if (size > 0 && hasText(hash)) {
            addQuality(item, quality, size, null, hash);
        }
    }

    private static void addSizeQuality(JsonObject item, String quality,
                                       JsonObject raw, String sizeKey) {
        long size = positiveLong(raw, sizeKey);
        if (size > 0) {
            addQuality(item, quality, size, null, null);
        }
    }

    private static void addQuality(JsonObject item, String quality, long size,
                                   String sizeText, String hash) {
        JsonArray types = item.getAsJsonArray("types");
        for (JsonElement existing : types) {
            if (quality.equals(text(object(existing), "type"))) {
                return;
            }
        }
        JsonObject type = new JsonObject();
        type.addProperty("type", quality);
        if (size > 0) {
            type.addProperty("size", size);
        } else {
            addText(type, "size", sizeText);
        }
        addText(type, "hash", hash);
        types.add(type);
        JsonObject details = new JsonObject();
        if (size > 0) {
            details.addProperty("size", size);
        } else {
            addText(details, "size", sizeText);
        }
        addText(details, "hash", hash);
        item.getAsJsonObject("_types").add(quality, details);
    }

    private static String joinNames(JsonArray values) {
        List<String> names = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = object(value);
            String name = firstText(object, "name", "singerName");
            if (hasText(name)) {
                names.add(decodeHtml(name));
            }
        }
        return String.join("、", names);
    }

    private static String seconds(JsonObject object, String key) {
        return formatDuration(nonNegativeLong(object, key));
    }

    private static String milliseconds(JsonObject object, String key) {
        return formatDuration(nonNegativeLong(object, key) / 1000);
    }

    private static String formatDuration(long seconds) {
        return seconds <= 0 ? null : "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String md5(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 is unavailable", exception);
        }
    }

    private static String decodeHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstText(JsonObject object, String... keys) {
        for (String key : keys) {
            String value = text(object, key);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static long positiveLong(JsonObject object, String key) {
        return Math.max(0, nonNegativeLong(object, key));
    }

    private static long firstPositiveLong(JsonObject object, String... keys) {
        for (String key : keys) {
            long value = positiveLong(object, key);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private static long nonNegativeLong(JsonObject object, String key) {
        return Math.max(0, longValue(object, key, 0));
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int nonNegativeInt(JsonObject object, int fallback, String key) {
        long value = nonNegativeLong(object, key);
        return value <= 0 ? fallback : (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static String stripPrefix(String value, String prefix) {
        return value != null && value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static void copyText(JsonObject from, JsonObject to,
                                 String sourceKey, String targetKey) {
        addText(to, targetKey, text(from, sourceKey));
    }

    private static void addText(JsonObject object, String key, String value) {
        if (hasText(value)) {
            object.addProperty(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (httpClient != null) {
                httpClient.shutdownNow();
            }
            executor.shutdownNow();
        }
    }

    record SearchResult(JsonArray tracks, int total) {
        SearchResult {
            tracks = tracks == null ? new JsonArray() : tracks.deepCopy();
            total = Math.max(tracks.size(), total);
        }
    }

    @FunctionalInterface
    interface RequestExecutor {
        JsonElement execute(HttpRequest request) throws IOException, InterruptedException;
    }
}
