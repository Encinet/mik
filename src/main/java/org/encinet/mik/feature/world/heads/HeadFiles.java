package org.encinet.mik.feature.world.heads;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Bounded IO and atomic replacement for the feature's own files. */
public final class HeadFiles {
    private HeadFiles() { }

    public static byte[] read(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Head catalog data exceeds " + limit + " bytes");
        return bytes;
    }

    public static byte[] read(Path path, int limit) throws IOException {
        try (var input = Files.newInputStream(path)) { return read(input, limit); }
    }

    public static void replace(Path destination, Write write) throws IOException {
        Path directory = Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(directory, ".heads-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { write.to(output); }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    public interface Write { void to(OutputStream output) throws IOException; }
}
