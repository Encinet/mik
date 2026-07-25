package org.encinet.mik.module.music.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Converts LX online-catalog and custom-source song objects into playable tracks. */
final class LxMusicTrackMapper {

    public static final int MAX_MUSIC_INFO_BYTES = 24 * 1024;
    private static final int MAX_FIELD_LENGTH = 4096;
    private static final int MAX_ID_LENGTH = 512;

    public MusicTrack parse(JsonElement element) {
        return parse(element, null);
    }

    public MusicTrack parse(JsonElement element, String requestedSource) {
        return parse(element, requestedSource, null);
    }

    /** Maps a script result while retaining the script that can resolve its playback URL. */
    public MusicTrack parse(JsonElement element, String requestedSource, String providerId) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject item = element.getAsJsonObject();
        if (item.toString().getBytes(StandardCharsets.UTF_8).length > MAX_MUSIC_INFO_BYTES) {
            return null;
        }
        String source = requestedSource == null ? string(item, "source") : requestedSource;
        source = normalizeSource(source);
        String name = string(item, "name");
        String singer = first(string(item, "singer"), string(item, "artist"));
        JsonObject meta = object(item, "meta");
        if (!valid(name) || !validSource(source) || source.equalsIgnoreCase("local")) {
            return null;
        }

        String canonicalSongId = firstIdentifier(MAX_ID_LENGTH,
                meta == null ? null : string(meta, "songId"),
                string(item, "songmid"), string(item, "songId"));
        String songId = canonicalSongId;
        if (source.equalsIgnoreCase("kg")) {
            String hash = firstIdentifier(MAX_ID_LENGTH, string(item, "hash"),
                    meta == null ? null : string(meta, "hash"));
            if (!valid(hash) && meta != null) {
                hash = firstKugouHash(meta);
            }
            if (valid(hash)) {
                songId = hash;
            }
        }

        String lxId = identifier(string(item, "id"), MAX_ID_LENGTH);
        if (!valid(songId)) {
            songId = lxId;
        }
        if (!valid(songId)) {
            return null;
        }
        if (lxId == null) {
            lxId = source.toLowerCase(Locale.ROOT) + "_" + songId;
        }
        lxId = identifier(lxId, MAX_ID_LENGTH);
        if (lxId == null) {
            return null;
        }

        String normalizedSource = source.toLowerCase(Locale.ROOT);
        if (!valid(canonicalSongId)) {
            canonicalSongId = songId;
        }
        JsonObject musicInfo = toScriptMusicInfo(
                item, meta, normalizedSource, canonicalSongId, lxId);
        String musicInfoJson = musicInfo.toString();
        if (musicInfoJson.getBytes(StandardCharsets.UTF_8).length > MAX_MUSIC_INFO_BYTES) {
            return null;
        }

        return new MusicTrack(
                "lx:" + normalizedSource + ":" + songId,
                new TrackDetails(name, singer,
                        first(meta == null ? null : string(meta, "albumName"),
                                string(item, "albumName"), string(item, "album")),
                        "LX/" + normalizedSource.toUpperCase(Locale.ROOT),
                        new AudioProperties(null, null,
                                parseDuration(first(string(item, "interval"),
                                        string(item, "duration"))))),
                new TrackTarget.Lx(normalizedSource, songId, qualities(item, meta), musicInfoJson,
                        providerId)
        );
    }

    private static Duration parseDuration(String value) {
        if (!valid(value)) {
            return null;
        }
        try {
            String[] parts = value.strip().split(":");
            long seconds = switch (parts.length) {
                case 1 -> Long.parseLong(parts[0]);
                case 2 -> Math.addExact(Math.multiplyExact(Long.parseLong(parts[0]), 60),
                        Long.parseLong(parts[1]));
                case 3 -> Math.addExact(Math.multiplyExact(Long.parseLong(parts[0]), 3600),
                        Math.addExact(Math.multiplyExact(Long.parseLong(parts[1]), 60),
                                Long.parseLong(parts[2])));
                default -> -1;
            };
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private JsonObject toScriptMusicInfo(JsonObject item, JsonObject meta,
                                         String source, String songId, String lxId) {
        JsonObject info = item.deepCopy();
        info.addProperty("id", lxId);
        info.addProperty("source", source);
        if (!info.has("singer") && info.has("artist")) {
            copy(info, info, "artist", "singer");
        }

        JsonObject officialMeta = meta == null ? new JsonObject() : meta.deepCopy();
        officialMeta.addProperty("songId", songId);
        copyIfMissing(item, officialMeta, "albumName", "albumName");
        copyIfMissing(item, officialMeta, "album", "albumName");
        copyIfMissing(item, officialMeta, "img", "picUrl");
        copyIfMissing(item, officialMeta, "albumId", "albumId");
        copyIfMissing(item, officialMeta, "types", "qualitys");
        copyIfMissing(item, officialMeta, "qualitys", "qualitys");
        copyIfMissing(item, officialMeta, "_types", "_qualitys");
        copyIfMissing(item, officialMeta, "_qualitys", "_qualitys");
        if (!isArray(officialMeta.get("qualitys"))) {
            JsonElement fallback = firstArray(item.get("qualitys"), item.get("types"));
            officialMeta.add("qualitys", fallback == null ? new JsonArray() : fallback.deepCopy());
        }
        if (!isObject(officialMeta.get("_qualitys"))) {
            JsonElement fallback = firstObject(item.get("_qualitys"), item.get("_types"));
            officialMeta.add("_qualitys", fallback == null ? new JsonObject() : fallback.deepCopy());
        }

        if ("kg".equals(source)) {
            copyIfMissing(item, officialMeta, "hash", "hash");
        } else if ("tx".equals(source)) {
            copyIfMissing(item, officialMeta, "strMediaMid", "strMediaMid");
            copyIfMissing(item, officialMeta, "songId", "id");
            copyIfMissing(item, officialMeta, "albumMid", "albumMid");
        } else if ("mg".equals(source)) {
            copyIfMissing(item, officialMeta, "copyrightId", "copyrightId");
            copyIfMissing(item, officialMeta, "lrcUrl", "lrcUrl");
            copyIfMissing(item, officialMeta, "mrcUrl", "mrcUrl");
            copyIfMissing(item, officialMeta, "trcUrl", "trcUrl");
        }
        info.add("meta", officialMeta);

        // Keep the aliases used by LX Music 1.x custom sources alongside current MusicInfo.
        info.addProperty("songmid", songId);
        copy(officialMeta, info, "albumName", "albumName");
        copy(officialMeta, info, "picUrl", "img");
        copy(officialMeta, info, "albumId", "albumId");
        copy(officialMeta, info, "qualitys", "types");
        copy(officialMeta, info, "_qualitys", "_types");
        if ("kg".equals(source)) {
            copy(officialMeta, info, "hash", "hash");
        } else if ("tx".equals(source)) {
            copy(officialMeta, info, "strMediaMid", "strMediaMid");
            copy(officialMeta, info, "id", "songId");
            copy(officialMeta, info, "albumMid", "albumMid");
        } else if ("mg".equals(source)) {
            copy(officialMeta, info, "copyrightId", "copyrightId");
            copy(officialMeta, info, "lrcUrl", "lrcUrl");
            copy(officialMeta, info, "mrcUrl", "mrcUrl");
            copy(officialMeta, info, "trcUrl", "trcUrl");
        }
        if (!isObject(info.get("typeUrl"))) {
            info.add("typeUrl", new JsonObject());
        }
        return info;
    }

    private List<String> qualities(JsonObject item, JsonObject meta) {
        Set<String> values = new LinkedHashSet<>();
        collectQualities(values, item.get("types"));
        collectQualities(values, item.get("qualitys"));
        collectQualityKeys(values, object(item, "_types"));
        collectQualityKeys(values, object(item, "_qualitys"));
        if (meta != null) {
            collectQualities(values, meta.get("types"));
            collectQualities(values, meta.get("qualitys"));
            collectQualityKeys(values, object(meta, "_types"));
            collectQualityKeys(values, object(meta, "_qualitys"));
        }
        return List.copyOf(values);
    }

    private static void collectQualities(Set<String> values, JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return;
        }
        for (JsonElement element : value.getAsJsonArray()) {
            String type = element.isJsonObject()
                    ? string(element.getAsJsonObject(), "type")
                    : element.isJsonPrimitive() ? element.getAsString() : null;
            type = normalizeQuality(type);
            if (valid(type)) {
                values.add(type);
            }
        }
    }

    private static void collectQualityKeys(Set<String> values, JsonObject object) {
        if (object == null) {
            return;
        }
        object.keySet().stream().map(LxMusicTrackMapper::normalizeQuality)
                .filter(LxMusicTrackMapper::valid).forEach(values::add);
    }

    private static String firstKugouHash(JsonObject meta) {
        JsonObject qualityObject = object(meta, "_qualitys");
        if (qualityObject != null) {
            for (String quality : List.of("128k", "320k", "flac", "flac24bit")) {
                JsonObject value = object(qualityObject, quality);
                String hash = value == null ? null
                        : identifier(string(value, "hash"), MAX_ID_LENGTH);
                if (valid(hash)) {
                    return hash;
                }
            }
        }
        JsonElement qualitys = meta.get("qualitys");
        if (qualitys != null && qualitys.isJsonArray()) {
            for (JsonElement element : qualitys.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    String hash = identifier(string(element.getAsJsonObject(), "hash"),
                            MAX_ID_LENGTH);
                    if (valid(hash)) {
                        return hash;
                    }
                }
            }
        }
        return null;
    }

    private static void copy(JsonObject from, JsonObject to, String sourceKey, String targetKey) {
        JsonElement value = from.get(sourceKey);
        if (value != null && !value.isJsonNull()) {
            to.add(targetKey, value.deepCopy());
        }
    }

    private static void copyIfMissing(
            JsonObject from, JsonObject to, String sourceKey, String targetKey) {
        if (!to.has(targetKey) || to.get(targetKey).isJsonNull()) {
            copy(from, to, sourceKey, targetKey);
        }
    }

    private static JsonElement firstArray(JsonElement... values) {
        for (JsonElement value : values) {
            if (isArray(value)) {
                return value;
            }
        }
        return null;
    }

    private static JsonElement firstObject(JsonElement... values) {
        for (JsonElement value : values) {
            if (isObject(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isArray(JsonElement value) {
        return value != null && value.isJsonArray();
    }

    private static boolean isObject(JsonElement value) {
        return value != null && value.isJsonObject();
    }

    private static String normalizeQuality(String value) {
        if (!valid(value)) {
            return null;
        }
        String quality = value.strip().toLowerCase(Locale.ROOT);
        return quality.equals("hires") ? "flac24bit" : quality;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (valid(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstIdentifier(int maximumLength, String... values) {
        for (String value : values) {
            String identifier = identifier(value, maximumLength);
            if (identifier != null) {
                return identifier;
            }
        }
        return null;
    }

    private static String identifier(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        return normalized;
    }

    private static String normalizeSource(String value) {
        if (value == null) {
            return null;
        }
        String source = value.strip();
        return validSource(source) ? source : null;
    }

    private static boolean validSource(String value) {
        return valid(value) && value.length() <= 32 && value.matches("[A-Za-z0-9_-]+");
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_FIELD_LENGTH;
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
