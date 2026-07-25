package org.encinet.mik.module.music.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Persistent server-wide play counts and last-played timestamps. */
public final class MusicPlaybackHistory implements MusicPlaybackStats, AutoCloseable {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_TRACKS = 100_000;
    private static final int MAX_TRACK_ID_LENGTH = 4096;
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;

    private final Path file;
    private final Consumer<String> warningLogger;
    private final Clock clock;
    private final ExecutorService saveExecutor;
    private final Object lock = new Object();
    private final Object saveLock = new Object();
    private final Map<String, TrackStats> tracks = new HashMap<>();
    private long changeVersion;
    private long savedVersion;
    private boolean saveRunning;
    private boolean closed;

    public MusicPlaybackHistory(Path file, Consumer<String> warningLogger) {
        this(file, warningLogger, Clock.systemUTC(),
                Executors.newSingleThreadExecutor(Thread.ofVirtual()
                        .name("mik-music-history-", 0).factory()));
    }

    MusicPlaybackHistory(Path file, Consumer<String> warningLogger, Clock clock,
                         ExecutorService saveExecutor) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.saveExecutor = Objects.requireNonNull(saveExecutor, "saveExecutor");
        load();
    }

    /** Counts one playback after the jukebox coordinator accepts it as substantial. */
    public void recordPlayback(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        String trackId = track.id();
        synchronized (lock) {
            if (closed) {
                return;
            }
            TrackStats previous = tracks.getOrDefault(trackId, TrackStats.EMPTY);
            long count = previous.playCount() == Long.MAX_VALUE
                    ? Long.MAX_VALUE : previous.playCount() + 1;
            tracks.put(trackId, new TrackStats(count, clock.instant()));
            pruneIfNeeded();
            changeVersion++;
            scheduleSave();
        }
    }

    @Override
    public TrackStats stats(String trackId) {
        if (trackId == null) {
            return TrackStats.EMPTY;
        }
        synchronized (lock) {
            return tracks.getOrDefault(trackId, TrackStats.EMPTY);
        }
    }

    public Map<String, TrackStats> snapshot() {
        synchronized (lock) {
            return Map.copyOf(tracks);
        }
    }

    private void scheduleSave() {
        if (saveRunning) {
            return;
        }
        saveRunning = true;
        try {
            saveExecutor.execute(this::saveUntilCurrent);
        } catch (RuntimeException exception) {
            saveRunning = false;
            warningLogger.accept("Failed to schedule music playback history save: "
                    + exception.getMessage());
        }
    }

    private void saveUntilCurrent() {
        while (true) {
            Map<String, TrackStats> snapshot;
            long version;
            synchronized (lock) {
                snapshot = Map.copyOf(tracks);
                version = changeVersion;
            }
            try {
                saveVersion(snapshot, version);
            } catch (IOException | RuntimeException exception) {
                warningLogger.accept("Failed to save music playback history: "
                        + exception.getMessage());
                synchronized (lock) {
                    saveRunning = false;
                }
                return;
            }
            synchronized (lock) {
                if (changeVersion == version) {
                    saveRunning = false;
                    return;
                }
            }
        }
    }

    private void load() {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > MAX_FILE_BYTES) {
                throw new IOException("history file is not a regular file or is too large");
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException("history root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (integer(root.get("version")) != FORMAT_VERSION
                    || !root.has("tracks") || !root.get("tracks").isJsonObject()) {
                throw new IOException("unsupported music playback history format");
            }
            Map<String, TrackStats> loaded = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry
                    : root.getAsJsonObject("tracks").entrySet()) {
                if (loaded.size() >= MAX_TRACKS || !validTrackId(entry.getKey())) {
                    continue;
                }
                TrackStats value = readStats(entry.getValue());
                if (value != null) {
                    loaded.put(entry.getKey(), value);
                }
            }
            tracks.putAll(loaded);
        } catch (IOException | RuntimeException exception) {
            tracks.clear();
            warningLogger.accept("Failed to load music playback history " + file.getFileName()
                    + ": " + exception.getMessage());
        }
    }

    private void save(Map<String, TrackStats> snapshot) throws IOException {
        Path directory = file.getParent();
        if (directory == null) {
            throw new IOException("music playback history has no parent directory");
        }
        Files.createDirectories(directory);
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        JsonObject values = new JsonObject();
        snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    JsonObject value = new JsonObject();
                    value.addProperty("playCount", entry.getValue().playCount());
                    value.addProperty("lastPlayedAt", entry.getValue().lastPlayedAt().toString());
                    values.add(entry.getKey(), value);
                });
        root.add("tracks", values);
        byte[] serialized = root.toString().getBytes(StandardCharsets.UTF_8);
        if (serialized.length > MAX_FILE_BYTES) {
            throw new IOException("music playback history exceeds size limit");
        }
        Path temporary = Files.createTempFile(directory, ".music-playback-", ".json.tmp");
        try {
            Files.write(temporary, serialized);
            moveIntoPlace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void saveVersion(Map<String, TrackStats> snapshot, long version) throws IOException {
        synchronized (saveLock) {
            synchronized (lock) {
                if (version <= savedVersion) {
                    return;
                }
            }
            save(snapshot);
            synchronized (lock) {
                savedVersion = Math.max(savedVersion, version);
            }
        }
    }

    private void pruneIfNeeded() {
        if (tracks.size() <= MAX_TRACKS) {
            return;
        }
        String removable = tracks.entrySet().stream()
                .min(Comparator.comparingLong((Map.Entry<String, TrackStats> entry) ->
                                entry.getValue().playCount())
                        .thenComparing(entry -> entry.getValue().lastPlayedAt())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(null);
        if (removable != null) {
            tracks.remove(removable);
        }
    }

    private static TrackStats readStats(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        Long count = nonNegativeLong(object.get("playCount"));
        String lastPlayed = string(object.get("lastPlayedAt"));
        if (count == null || count == 0 || lastPlayed == null) {
            return null;
        }
        try {
            return new TrackStats(count, Instant.parse(lastPlayed));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int integer(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive()
                    && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : -1;
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static Long nonNegativeLong(JsonElement element) {
        try {
            long value = element != null && element.isJsonPrimitive()
                    && element.getAsJsonPrimitive().isNumber() ? element.getAsLong() : -1;
            return value >= 0 ? value : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String string(JsonElement element) {
        try {
            return element != null && element.isJsonPrimitive()
                    && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean validTrackId(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TRACK_ID_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveExecutor.shutdownNow();
        }
        Map<String, TrackStats> finalSnapshot;
        synchronized (lock) {
            finalSnapshot = changeVersion == savedVersion ? null : Map.copyOf(tracks);
        }
        if (finalSnapshot != null) {
            try {
                saveVersion(finalSnapshot, changeVersion);
            } catch (IOException | RuntimeException exception) {
                warningLogger.accept("Failed to flush music playback history: "
                        + exception.getMessage());
            }
        }
    }
}
