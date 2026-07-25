package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.OnlineTrackSnapshot;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Persistent metadata catalog for fully cached online tracks. */
final class CachedOnlineTrackCatalog {

    static final String FILE_SUFFIX = ".track.json";
    private static final String PART_SUFFIX = ".part";

    private final Path directory;
    private final Consumer<String> warningLogger;
    private final ConcurrentHashMap<String, MusicTrack> tracks = new ConcurrentHashMap<>();

    CachedOnlineTrackCatalog(Path directory, Consumer<String> warningLogger) {
        this.directory = directory;
        this.warningLogger = warningLogger;
    }

    List<MusicTrack> tracks() {
        Map<String, MusicTrack> byTrackId = new LinkedHashMap<>();
        tracks.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> byTrackId.putIfAbsent(
                        entry.getValue().id(), entry.getValue()));
        return byTrackId.values().stream()
                .sorted(java.util.Comparator
                        .comparing((MusicTrack track) -> track.details().title(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MusicTrack::id))
                .toList();
    }

    MusicTrack track(String entryKey) {
        return tracks.get(entryKey);
    }

    boolean store(String entryKey, MusicTrack track) {
        String snapshot = OnlineTrackSnapshot.serialize(track);
        if (snapshot == null) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            Path part = Files.createTempFile(directory, entryKey + "-track-", PART_SUFFIX);
            try {
                Files.writeString(part, snapshot, StandardCharsets.UTF_8);
                moveIntoPlace(part, path(entryKey));
            } finally {
                Files.deleteIfExists(part);
            }
            tracks.put(entryKey, track);
            return true;
        } catch (IOException exception) {
            warningLogger.accept("Failed to index cached online track " + track.id()
                    + ": " + exception.getMessage());
            return false;
        }
    }

    void load(String entryKey) {
        Path path = path(entryKey);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            MusicTrack track = OnlineTrackSnapshot.deserialize(
                    Files.readString(path, StandardCharsets.UTF_8));
            if (track == null || !(track.target() instanceof TrackTarget.Lx target)
                    || !entryKey.equals(OnlineAudioCache.cacheKey(target))) {
                Files.deleteIfExists(path);
                return;
            }
            tracks.put(entryKey, track);
        } catch (IOException | RuntimeException exception) {
            tracks.remove(entryKey);
            deleteQuietly(path);
            warningLogger.accept("Failed to load cached online track metadata "
                    + path.getFileName() + ": " + exception.getMessage());
        }
    }

    void deleteOrphans(Set<String> audioEntries) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(value -> Files.isRegularFile(
                            value, LinkOption.NOFOLLOW_LINKS))
                    .filter(CachedOnlineTrackCatalog::isCatalogFile).toList()) {
                String entryKey = entryKey(path);
                if (!audioEntries.contains(entryKey)) {
                    tracks.remove(entryKey);
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    void forget(String entryKey) {
        tracks.remove(entryKey);
    }

    void remove(String entryKey) {
        tracks.remove(entryKey);
        Path path = path(entryKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            warningLogger.accept("Failed to remove cached online track metadata "
                    + entryKey + ": " + exception.getMessage());
        }
    }

    void clear() {
        tracks.clear();
    }

    static boolean isCatalogFile(Path path) {
        return path.getFileName().toString().endsWith(FILE_SUFFIX);
    }

    private Path path(String entryKey) {
        return directory.resolve(entryKey + FILE_SUFFIX);
    }

    private static String entryKey(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - FILE_SUFFIX.length());
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later startup scan retries orphan cleanup.
        }
    }
}
