package org.encinet.mik.module.music.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validated, portable representation of an online music track. */
public final class OnlineTrackSnapshot {

    private static final int MAX_SNAPSHOT_BYTES = 30 * 1024;
    private static final int MAX_TITLE_LENGTH = 512;
    private static final int MAX_ARTIST_LENGTH = 512;
    private static final int MAX_ALBUM_LENGTH = 512;
    private static final int MAX_FORMAT_LENGTH = 128;
    private static final int MAX_TRACK_ID_LENGTH = 1024;
    private static final int MAX_SOURCE_LENGTH = 32;
    private static final int MAX_SONG_ID_LENGTH = 512;
    private static final int MAX_PROVIDER_ID_LENGTH = 2048;
    private static final int MAX_QUALITIES = 64;

    private OnlineTrackSnapshot() {
    }

    public static String serialize(MusicTrack track) {
        if (!(track.target() instanceof TrackTarget.Lx target)) {
            return null;
        }
        try {
            if (track.id().length() > MAX_TRACK_ID_LENGTH
                    || target.source().length() > MAX_SOURCE_LENGTH
                    || target.songId().length() > MAX_SONG_ID_LENGTH
                    || !validSource(target.source())
                    || target.songId().chars().anyMatch(Character::isISOControl)
                    || !track.id().equals("lx:" + target.source() + ":" + target.songId())
                    || target.providerId() != null
                    && invalidSnapshotText(target.providerId(), MAX_PROVIDER_ID_LENGTH)) {
                return null;
            }
            TrackDetails details = track.details();
            String title = snapshotText(details.title(), MAX_TITLE_LENGTH);
            String artist = optionalSnapshotText(details.artist(), MAX_ARTIST_LENGTH);
            String album = optionalSnapshotText(details.album(), MAX_ALBUM_LENGTH);
            String format = snapshotText(details.format(), MAX_FORMAT_LENGTH);
            Long durationSeconds = durationSeconds(details.audio().duration());
            if (title == null || format == null || artist == null && details.artist() != null
                    || album == null && details.album() != null) {
                return null;
            }
            JsonObject root = new JsonObject();
            root.addProperty("id", track.id());
            root.addProperty("title", title);
            if (artist != null) {
                root.addProperty("artist", artist);
            }
            if (album != null) {
                root.addProperty("album", album);
            }
            root.addProperty("format", format);
            if (durationSeconds != null) {
                root.addProperty("durationSeconds", durationSeconds);
            }
            root.addProperty("source", target.source());
            root.addProperty("songId", target.songId());
            if (target.providerId() != null) {
                root.addProperty("providerId", target.providerId());
            }
            JsonArray qualities = new JsonArray();
            int qualityCount = 0;
            for (String quality : target.qualities()) {
                if (quality != null && quality.length() <= 32) {
                    qualities.add(quality);
                    if (++qualityCount >= MAX_QUALITIES) {
                        break;
                    }
                }
            }
            root.add("qualities", qualities);
            JsonElement musicInfo = JsonParser.parseString(target.musicInfoJson());
            if (!validMusicInfo(musicInfo, target.source())) {
                return null;
            }
            root.add("musicInfo", musicInfo);
            String serialized = root.toString();
            return serialized.getBytes(StandardCharsets.UTF_8).length <= MAX_SNAPSHOT_BYTES
                    ? serialized : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static MusicTrack deserialize(String expectedTrackId, String serialized) {
        if (expectedTrackId == null || serialized == null
                || serialized.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(serialized);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject root = element.getAsJsonObject();
            String id = text(root, "id", MAX_TRACK_ID_LENGTH);
            if (!expectedTrackId.equals(id) || !id.startsWith("lx:")) {
                return null;
            }
            String title = text(root, "title", MAX_TITLE_LENGTH);
            String artist = optionalText(root, "artist", MAX_ARTIST_LENGTH);
            String album = optionalText(root, "album", MAX_ALBUM_LENGTH);
            String format = text(root, "format", MAX_FORMAT_LENGTH);
            String source = text(root, "source", MAX_SOURCE_LENGTH);
            String songId = text(root, "songId", MAX_SONG_ID_LENGTH);
            String providerId = optionalText(root, "providerId", MAX_PROVIDER_ID_LENGTH);
            Duration duration = duration(root);
            JsonElement musicInfo = root.get("musicInfo");
            if (title == null || format == null || source == null || songId == null
                    || hasValue(root, "artist") && artist == null
                    || hasValue(root, "album") && album == null
                    || hasValue(root, "durationSeconds") && duration == null
                    || root.has("providerId") && !root.get("providerId").isJsonNull()
                    && providerId == null
                    || !validSource(source) || !validMusicInfo(musicInfo, source)) {
                return null;
            }
            String musicInfoJson = musicInfo.toString();
            List<String> qualities = qualities(root.get("qualities"));
            if (!id.equals("lx:" + source.toLowerCase(java.util.Locale.ROOT) + ":" + songId)) {
                return null;
            }
            return new MusicTrack(id,
                    new TrackDetails(title, artist, album, format,
                            new AudioProperties(null, null, duration)),
                    new TrackTarget.Lx(source, songId, qualities, musicInfoJson, providerId));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static MusicTrack deserialize(String serialized) {
        if (serialized == null
                || serialized.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(serialized);
            if (!element.isJsonObject()) {
                return null;
            }
            String id = text(element.getAsJsonObject(), "id", MAX_TRACK_ID_LENGTH);
            return id == null ? null : deserialize(id, serialized);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<String> qualities(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String quality = value.getAsString();
                if (!quality.isBlank() && quality.length() <= 32) {
                    result.add(quality);
                    if (result.size() >= MAX_QUALITIES) {
                        break;
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static String text(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        try {
            String text = value.getAsString();
            return text.isBlank() || text.length() > maximum
                    || text.chars().anyMatch(Character::isISOControl) ? null : text;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String optionalText(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : text(object, key, maximum);
    }

    private static boolean hasValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull();
    }

    private static String optionalSnapshotText(String value, int maximum) {
        return value == null ? null : snapshotText(value, maximum);
    }

    private static Long durationSeconds(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return null;
        }
        long seconds = duration.getSeconds();
        return duration.getNano() == 0 || seconds == Long.MAX_VALUE ? seconds : seconds + 1;
    }

    private static Duration duration(JsonObject object) {
        JsonElement value = object.get("durationSeconds");
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            long seconds = value.getAsLong();
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private static String snapshotText(String value, int maximum) {
        if (invalidSnapshotText(value, Integer.MAX_VALUE)) {
            return null;
        }
        return truncate(value, maximum);
    }

    private static boolean invalidSnapshotText(String value, int maximum) {
        return value == null || value.isBlank() || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl);
    }

    private static String truncate(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean validSource(String source) {
        return source.matches("[A-Za-z0-9_-]+");
    }

    private static boolean validMusicInfo(JsonElement value, String source) {
        if (value == null || !value.isJsonObject()) {
            return false;
        }
        JsonElement declaredSource = value.getAsJsonObject().get("source");
        if (declaredSource == null || declaredSource.isJsonNull()) {
            return true;
        }
        return declaredSource.isJsonPrimitive()
                && declaredSource.getAsJsonPrimitive().isString()
                && source.equalsIgnoreCase(declaredSource.getAsString());
    }
}
