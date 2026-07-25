package org.encinet.mik.module.music.lyrics;

import org.encinet.mik.module.music.catalog.TrackTarget;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads unsynchronized or LRC-formatted lyrics embedded in a local audio tag. */
final class LocalEmbeddedLyricsReader {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "flac", "ogg", "oga", "m4a", "mp4", "wav");

    LyricSourceText read(TrackTarget.LocalFile target) throws IOException {
        Path path = prepare(target.path(), target.root());
        String extension = extension(path);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return new LyricSourceText(null, null, null);
        }
        try {
            AudioFile file = AudioFileIO.readAs(path.toFile(),
                    extension.equals("oga") ? "ogg" : extension);
            return new LyricSourceText(extract(file.getTag()), null, null);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception ignored) {
            return new LyricSourceText(null, null, null);
        }
    }

    static String extract(Tag tag) {
        if (tag == null) {
            return null;
        }
        try {
            List<String> values = tag.getAll(FieldKey.LYRICS);
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized != null) {
                    return normalized;
                }
            }
            return normalize(tag.getFirst(FieldKey.LYRICS));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path prepare(Path path, Path root) throws IOException {
        if (!path.startsWith(root) || Files.isSymbolicLink(root)) {
            throw new IOException("Local music path is outside its trusted root");
        }
        Path current = root;
        for (Path component : root.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Local music path contains a symbolic link");
            }
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local music file is unavailable");
        }
        return path;
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n').replace('\0', ' ').strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
