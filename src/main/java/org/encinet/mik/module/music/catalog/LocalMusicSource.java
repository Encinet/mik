package org.encinet.mik.module.music.catalog;

import org.encinet.mik.module.music.catalog.nbs.NbsParser;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class LocalMusicSource {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "m4a", "mp4", "wav", "flac", "ogg", "oga", "aac", "opus", "mka", "webm", "nbs"
    );

    private final Path root;
    private final LocalTrackMetadataReader metadataReader;
    private final NbsParser nbsParser;
    private final Consumer<String> warningLogger;

    public LocalMusicSource(Path root) {
        this(root, ignored -> {});
    }

    public LocalMusicSource(Path root, Consumer<String> warningLogger) {
        this(root, new LocalTrackMetadataReader(), new NbsParser(), warningLogger);
    }

    LocalMusicSource(Path root, LocalTrackMetadataReader metadataReader,
                     NbsParser nbsParser, Consumer<String> warningLogger) {
        this.root = root.toAbsolutePath().normalize();
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.nbsParser = Objects.requireNonNull(nbsParser, "nbsParser");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
    }

    public List<MusicTrack> load() throws IOException {
        Files.createDirectories(root);
        return findSupportedAudioFiles(root).stream()
                .map(this::parse)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((MusicTrack track) -> track.details().title(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MusicTrack::id))
                .toList();
    }

    private MusicTrack parse(Path path) {
        String id = toTrackId(root, path);
        try {
            String extension = getFileExtension(id);
            if (extension.equals("nbs")) {
                return parseNbs(path, id);
            }
            TrackDetails details = metadataReader.read(path, extension, displayName(id));
            if (details == null) {
                warningLogger.accept("Skipping unsupported or damaged local music file " + id);
                return null;
            }
            return new MusicTrack(id, details, new TrackTarget.LocalFile(path, root));
        } catch (RuntimeException exception) {
            warningLogger.accept("Skipping invalid local music file " + id + ": "
                    + exception.getMessage());
            return null;
        }
    }

    private MusicTrack parseNbs(Path path, String id) {
        try {
            NbsSong song = nbsParser.parse(path);
            String displayName = song.title() == null ? displayName(id)
                    : limit(song.title(), 512);
            return new MusicTrack(id,
                    new TrackDetails(displayName, limit(song.displayAuthor(), 512), null, "NBS",
                            new AudioProperties(fileSize(path), null,
                                    song.duration())),
                    new TrackTarget.NbsFile(path, root));
        } catch (IOException | RuntimeException exception) {
            warningLogger.accept("Skipping invalid NBS file " + id + ": " + exception.getMessage());
            return null;
        }
    }

    private static Long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String limit(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value : value.substring(0, maximumLength);
    }

    static List<Path> findSupportedAudioFiles(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<Path> files = new java.util.ArrayList<>();
        Files.walkFileTree(normalizedRoot, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (attributes.isRegularFile() && !Files.isSymbolicLink(file)
                                && LocalMusicSource.isSupportedAudioFile(file)) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) {
                        return file.equals(normalizedRoot)
                                ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }
                });
        return List.copyOf(files);
    }

    static boolean isSupportedAudioFile(Path path) {
        return SUPPORTED_EXTENSIONS.contains(getFileExtension(path.getFileName().toString())
                .toLowerCase(Locale.ROOT));
    }

    static String toTrackId(Path root, Path path) {
        return root.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }

    static String formatDisplayName(String id) {
        int lastDot = id.lastIndexOf('.');
        String name = lastDot > 0 ? id.substring(0, lastDot) : id;
        return name.replace("\"", "")
                .replace("'", "")
                .replace("_", " ")
                .replace("-", " - ")
                .replace("/", " / ");
    }

    private static String displayName(String id) {
        String formatted = formatDisplayName(id).strip();
        return formatted.isEmpty() ? id : formatted;
    }

    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
