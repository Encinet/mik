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

/** Reads LX-supported platform catalogs without resolving or downloading audio. */
final class LxOnlineSearchService implements AutoCloseable {

    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PLAYLIST_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_RESULTS_PER_SOURCE = 30;
    private static final int MAX_PLAYLIST_TRACKS = 1_000;
    private static final Set<String> SUPPORTED_SOURCES = Set.of("kw", "kg", "tx", "wy", "mg");
    private static final Pattern KUWO_QUALITY = Pattern.compile(
            "bitrate:(\\d+).*?size:([^;,]+)", Pattern.CASE_INSENSITIVE);
    private static final String MIGU_DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20";
    private static final String MIGU_SIGNATURE_MD5 = "6cdc72a439cef99a3418d2a78aa28c73";
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36";

    private final Duration timeout;
    private final RequestExecutor requestExecutor;
    private final TextRequestExecutor textRequestExecutor;
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
        this.textRequestExecutor = request -> executeText(client, request);
    }

    LxOnlineSearchService(Duration timeout, RequestExecutor requestExecutor) {
        this(timeout, requestExecutor, request -> requestExecutor.execute(request).toString());
    }

    LxOnlineSearchService(Duration timeout, RequestExecutor requestExecutor,
                          TextRequestExecutor textRequestExecutor) {
        this.timeout = timeout;
        this.httpClient = null;
        this.requestExecutor = requestExecutor;
        this.textRequestExecutor = textRequestExecutor;
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

    CompletableFuture<PlaylistResult> playlist(String source, String reference) {
        String normalizedSource = source == null ? "" : source.strip().toLowerCase(Locale.ROOT);
        if (!supports(normalizedSource)) {
            return CompletableFuture.failedFuture(
                    new IOException("Unsupported LX playlist source: " + normalizedSource));
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("LX online catalog is closed"));
        }
        final String id;
        try {
            id = parsePlaylistId(normalizedSource, reference);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (closed.get()) {
                    throw new CompletionException(new IOException("LX online catalog is closed"));
                }
                try {
                    return switch (normalizedSource) {
                        case "kw" -> playlistKuwo(id);
                        case "kg" -> playlistKugou(id);
                        case "tx" -> playlistTencent(id);
                        case "wy" -> playlistNetease(id);
                        case "mg" -> playlistMigu(id);
                        default -> throw new IOException(
                                "Unsupported LX playlist source: " + normalizedSource);
                    };
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new CompletionException(exception);
                }
            }, executor);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(new IOException("LX online catalog is closed", exception));
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

    private PlaylistResult playlistKuwo(String id)
            throws IOException, InterruptedException {
        String url = "https://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=" + id
                + "&pn=0&rn=" + MAX_PLAYLIST_TRACKS
                + "&encode=utf8&keyset=pl2012&identity=kuwo&pcmp4=1"
                + "&vipver=MUSIC_9.0.5.0_W1&newver=1";
        JsonObject response = object(request(get(url)));
        if (!"ok".equalsIgnoreCase(text(response, "result"))) {
            throw new IOException("Kuwo playlist request failed");
        }
        JsonArray tracks = new JsonArray();
        for (JsonElement value : array(response, "musiclist")) {
            JsonObject raw = object(value);
            String songId = text(raw, "id");
            String name = text(raw, "name");
            if (!hasText(songId) || !hasText(name)) {
                continue;
            }
            JsonObject item = baseTrack("kw", songId, name, text(raw, "artist"),
                    text(raw, "album"), seconds(raw, "duration"));
            copyText(raw, item, "albumid", "albumId");
            addKuwoQualities(item, firstText(raw, "N_MINFO", "MINFO"));
            tracks.add(item);
            if (tracks.size() >= MAX_PLAYLIST_TRACKS) {
                break;
            }
        }
        return new PlaylistResult("kw", id, playlistName(response, "title", id), tracks,
                nonNegativeInt(response, tracks.size(), "total"));
    }

    private PlaylistResult playlistKugou(String id)
            throws IOException, InterruptedException {
        String url = "https://www.kugou.com/yy/special/single/" + id + ".html";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.kugou.com/")
                .GET().build();
        String html = textRequestExecutor.execute(request);
        JsonElement data;
        try {
            data = embeddedJson(html, "var data", '[');
        } catch (IOException exception) {
            data = embeddedJson(html, "global.data", '[');
        }
        if (!data.isJsonArray()) {
            throw new IOException("Kugou playlist page contains no track list");
        }
        JsonObject info;
        try {
            info = object(embeddedJson(html, "var specialInfo", '{'));
        } catch (IOException exception) {
            info = new JsonObject();
        }
        JsonArray tracks = new JsonArray();
        for (JsonElement value : data.getAsJsonArray()) {
            JsonObject raw = object(value);
            String songId = firstText(raw, "audio_id", "album_audio_id");
            String hash = firstText(raw, "hash", "hash_128", "HASH");
            String name = firstText(raw, "songname", "audio_name");
            if (!hasText(songId) || !hasText(hash) || !hasText(name)) {
                continue;
            }
            JsonObject item = baseTrack("kg", songId, name,
                    firstText(raw, "singername", "author_name"), text(raw, "album_name"),
                    formatDuration(nonNegativeLong(raw, "duration") / 1000));
            item.addProperty("hash", hash);
            copyText(raw, item, "album_id", "albumId");
            copyText(raw, item, "album_audio_id", "albumAudioId");
            addKugouQuality(item, "128k", raw, "filesize", "hash");
            addKugouQuality(item, "320k", raw, "filesize_320", "hash_320");
            addKugouQuality(item, "flac", raw, "filesize_flac", "hash_flac");
            addKugouQuality(item, "flac24bit", raw, "filesize_high", "hash_high");
            tracks.add(item);
            if (tracks.size() >= MAX_PLAYLIST_TRACKS) {
                break;
            }
        }
        String name = playlistName(info, "name", id);
        return new PlaylistResult("kg", id, name, tracks, tracks.size());
    }

    private PlaylistResult playlistTencent(String id)
            throws IOException, InterruptedException {
        String url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
                + "?type=1&json=1&utf8=1&onlysong=0&disstid=" + id + "&format=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Origin", "https://y.qq.com")
                .header("Referer", "https://y.qq.com/n/ryqq/playlist/" + id)
                .GET().build();
        JsonObject response = object(request(request));
        if (longValue(response, "code", -1) != 0 || longValue(response, "subcode", -1) != 0
                || array(response, "cdlist").isEmpty()) {
            throw new IOException("Tencent playlist request failed");
        }
        JsonObject playlist = object(array(response, "cdlist").get(0));
        JsonArray tracks = new JsonArray();
        for (JsonElement value : array(playlist, "songlist")) {
            JsonObject raw = object(value);
            String songId = firstText(raw, "songmid", "mid");
            String name = firstText(raw, "songname", "title", "name");
            String mediaMid = firstText(raw, "strMediaMid", "media_mid");
            if (!hasText(songId) || !hasText(name) || !hasText(mediaMid)) {
                continue;
            }
            JsonObject item = baseTrack("tx", songId, name, joinNames(array(raw, "singer")),
                    firstText(raw, "albumname", "albumName"), seconds(raw, "interval"));
            copyText(raw, item, "songid", "songId");
            item.addProperty("strMediaMid", mediaMid);
            copyText(raw, item, "albummid", "albumMid");
            copyText(raw, item, "albummid", "albumId");
            addSizeQuality(item, "128k", raw, "size128");
            addSizeQuality(item, "320k", raw, "size320");
            addSizeQuality(item, "flac", raw, "sizeflac");
            addSizeQuality(item, "flac24bit", raw, "size_hires");
            tracks.add(item);
            if (tracks.size() >= MAX_PLAYLIST_TRACKS) {
                break;
            }
        }
        return new PlaylistResult("tx", id, playlistName(playlist, "dissname", id), tracks,
                nonNegativeInt(playlist, tracks.size(), "songnum"));
    }

    private PlaylistResult playlistNetease(String id)
            throws IOException, InterruptedException {
        String url = "https://music.163.com/api/playlist/detail?id=" + id
                + "&n=" + MAX_PLAYLIST_TRACKS + "&s=8";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .GET().build();
        JsonObject response = object(request(request));
        if (longValue(response, "code", 200) != 200) {
            throw new IOException("NetEase playlist request failed: " + firstText(response, "message"));
        }
        JsonObject playlist = object(response.has("result")
                ? response.get("result") : response.get("playlist"));
        JsonArray rawTracks = array(playlist, "tracks");
        JsonArray tracks = new JsonArray();
        for (JsonElement value : rawTracks) {
            JsonObject raw = object(value);
            String songId = text(raw, "id");
            String name = text(raw, "name");
            if (!hasText(songId) || !hasText(name)) {
                continue;
            }
            JsonObject album = object(raw.has("album") ? raw.get("album") : raw.get("al"));
            JsonArray artists = raw.has("artists") ? array(raw, "artists") : array(raw, "ar");
            JsonObject item = baseTrack("wy", songId, name, joinNames(artists),
                    text(album, "name"), milliseconds(raw,
                            raw.has("duration") ? "duration" : "dt"));
            copyText(album, item, "id", "albumId");
            addSizeQuality(item, "128k", object(raw.has("lMusic")
                    ? raw.get("lMusic") : raw.get("l")), "size");
            addSizeQuality(item, "320k", object(raw.has("hMusic")
                    ? raw.get("hMusic") : raw.get("h")), "size");
            addSizeQuality(item, "flac", object(raw.has("sqMusic")
                    ? raw.get("sqMusic") : raw.get("sq")), "size");
            addSizeQuality(item, "flac24bit", object(raw.has("hrMusic")
                    ? raw.get("hrMusic") : raw.get("hr")), "size");
            tracks.add(item);
            if (tracks.size() >= MAX_PLAYLIST_TRACKS) {
                break;
            }
        }
        int total = nonNegativeInt(playlist, tracks.size(), "trackCount");
        return new PlaylistResult("wy", id, playlistName(playlist, "name", id), tracks, total);
    }

    private PlaylistResult playlistMigu(String id)
            throws IOException, InterruptedException {
        int pageSize = 100;
        JsonObject first = miguPlaylistPage(id, 1, pageSize);
        JsonObject firstData = object(first.get("data"));
        int total = nonNegativeInt(firstData, array(firstData, "songList").size(), "totalCount");
        JsonArray tracks = new JsonArray();
        collectMiguTracks(firstData.get("songList"), tracks, MAX_PLAYLIST_TRACKS);
        int pages = Math.min((MAX_PLAYLIST_TRACKS + pageSize - 1) / pageSize,
                Math.max(1, (total + pageSize - 1) / pageSize));
        for (int page = 2; page <= pages && tracks.size() < MAX_PLAYLIST_TRACKS; page++) {
            JsonObject data = object(miguPlaylistPage(id, page, pageSize).get("data"));
            collectMiguTracks(data.get("songList"), tracks, MAX_PLAYLIST_TRACKS);
        }

        String name = "MG playlist " + id;
        try {
            String url = "https://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/v2.0?playlistId=" + id;
            JsonObject response = object(request(miguRequest(url)));
            if ("000000".equals(text(response, "code"))) {
                name = playlistName(object(response.get("data")), "title", id);
            }
        } catch (IOException exception) {
            // The track list remains useful when only optional playlist metadata is unavailable.
        }
        return new PlaylistResult("mg", id, name, tracks, total);
    }

    private JsonObject miguPlaylistPage(String id, int page, int pageSize)
            throws IOException, InterruptedException {
        String url = "https://app.c.nf.migu.cn/MIGUM3.0/resource/playlist/song/v2.0?pageNo="
                + page + "&pageSize=" + pageSize + "&playlistId=" + id;
        JsonObject response = object(request(miguRequest(url)));
        if (!"000000".equals(text(response, "code"))) {
            throw new IOException("Migu playlist request failed: " + firstText(response, "info"));
        }
        return response;
    }

    private HttpRequest miguRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://m.music.migu.cn/")
                .GET().build();
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
        String name = firstText(raw, "name", "songName");
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
            String formatType = firstText(format, "formatType");
            String quality = switch (formatType == null ? "" : formatType) {
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

    static String parsePlaylistId(String source, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Playlist ID or URL must not be blank");
        }
        String value = reference.strip();
        if (value.length() > 4096 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Playlist reference is invalid");
        }
        if (value.matches("\\d{1,32}")) {
            return value;
        }

        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Playlist reference is not a valid URL or ID", exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || host == null || uri.getUserInfo() != null || !allowedPlaylistHost(source, host)) {
            throw new IllegalArgumentException("Playlist URL does not belong to " + source);
        }
        String candidate = value;
        List<Pattern> patterns = switch (source) {
            case "kw" -> List.of(
                    Pattern.compile("/playlist(?:_detail)?/(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:[?&#]|^)playlistId=(\\d+)", Pattern.CASE_INSENSITIVE));
            case "kg" -> List.of(
                    Pattern.compile("/special/single/(\\d+)\\.html", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("/plist/list/(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:[?&#]|^)specialid=(\\d+)", Pattern.CASE_INSENSITIVE));
            case "tx" -> List.of(
                    Pattern.compile("/(?:playlist|playsquare)/(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:[?&#]|^)id=(\\d+)", Pattern.CASE_INSENSITIVE));
            case "wy" -> List.of(
                    Pattern.compile("/playlist/(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:[?&#]|^)id=(\\d+)", Pattern.CASE_INSENSITIVE));
            case "mg" -> List.of(
                    Pattern.compile("/playlist/(\\d+)", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("(?:[?&#]|^)(?:playlistId|id)=(\\d+)", Pattern.CASE_INSENSITIVE));
            default -> throw new IllegalArgumentException("Unsupported LX playlist source: " + source);
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(candidate);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalArgumentException("Could not find a playlist ID in the " + source + " URL");
    }

    private static boolean allowedPlaylistHost(String source, String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        List<String> suffixes = switch (source) {
            case "kw" -> List.of("kuwo.cn");
            case "kg" -> List.of("kugou.com");
            case "tx" -> List.of("qq.com");
            case "wy" -> List.of("music.163.com");
            case "mg" -> List.of("migu.cn");
            default -> List.of();
        };
        return suffixes.stream().anyMatch(suffix -> normalized.equals(suffix)
                || normalized.endsWith("." + suffix));
    }

    private static JsonElement embeddedJson(String text, String marker, char opening)
            throws IOException {
        int markerIndex = text == null ? -1 : text.indexOf(marker);
        int start = markerIndex < 0 ? -1 : text.indexOf(opening, markerIndex + marker.length());
        if (start < 0) {
            throw new IOException("Kugou playlist page is missing " + marker);
        }
        char closing = opening == '[' ? ']' : '}';
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                try {
                    return JsonParser.parseString(text.substring(start, index + 1));
                } catch (RuntimeException exception) {
                    throw new IOException("Kugou playlist page contains invalid JSON", exception);
                }
            }
        }
        throw new IOException("Kugou playlist page contains incomplete " + marker);
    }

    private static String playlistName(JsonObject object, String key, String id) {
        String name = decodeHtml(text(object, key));
        return hasText(name) ? name : "Playlist " + id;
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

    private static String executeText(HttpClient client, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("LX online playlist returned HTTP " + response.statusCode());
            }
            return new String(readLimited(body, MAX_PLAYLIST_RESPONSE_BYTES), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        return readLimited(input, MAX_RESPONSE_BYTES);
    }

    private static byte[] readLimited(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximumBytes) {
                throw new IOException("LX online catalog response exceeds "
                        + maximumBytes + " bytes");
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

    record PlaylistResult(String source, String id, String name, JsonArray tracks, int total) {
        PlaylistResult {
            source = source == null ? "" : source;
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? "Playlist " + id : name.strip();
            if (name.length() > 256) {
                name = name.substring(0, 256);
            }
            tracks = tracks == null ? new JsonArray() : tracks.deepCopy();
            total = Math.max(tracks.size(), total);
        }
    }

    @FunctionalInterface
    interface RequestExecutor {
        JsonElement execute(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface TextRequestExecutor {
        String execute(HttpRequest request) throws IOException, InterruptedException;
    }
}
