package org.encinet.mik.module.music.catalog.nbs;

import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads bounded NBS input with NoteBlockLib and exposes MIK's playback model. */
public final class NbsParser {

    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    public NbsSong parse(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) {
            throw new IOException("NBS file size is out of range");
        }
        try (InputStream input = Files.newInputStream(path)) {
            return parse(input);
        }
    }

    NbsSong parse(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new IOException("NBS file size is out of range");
        }

        try {
            var source = (net.raphimc.noteblocklib.format.nbs.model.NbsSong)
                    NoteBlockLib.readSong(bytes, SongFormat.NBS);
            return NbsSongAdapter.adapt(source);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            Throwable cause = rootCause(exception);
            throw new IOException("Invalid NBS data: " + messageOf(cause), cause);
        }
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName() : message;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
