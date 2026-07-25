package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicLibraryTest {

    @TempDir
    Path directory;

    @Test
    void failedCandidateBuildRetainsTheLastPublishedLibrary() throws Exception {
        Path music = directory.resolve("music");
        Files.createDirectories(music);
        Files.write(music.resolve("song.nbs"), minimalNbs());
        List<String> errors = new ArrayList<>();
        MusicLibrary library = new MusicLibrary(
                music, Runnable::run, ignored -> {}, errors::add);

        MusicLibrary.ReloadResult loaded = library.reloadAsync().join();
        assertTrue(loaded.successful());
        assertEquals(1, loaded.trackCount());
        MusicTrack published = library.tracks().getFirst();

        Files.delete(music.resolve("song.nbs"));
        Files.delete(music);
        Files.writeString(music, "not a directory");
        MusicLibrary.ReloadResult failed = library.reloadAsync().join();

        assertFalse(failed.successful());
        assertEquals(1, failed.trackCount());
        assertEquals(List.of(published), library.tracks());
        assertEquals(1, errors.size());
    }

    private static byte[] minimalNbs() {
        Bytes bytes = new Bytes();
        bytes.u16(0).u8(5).u8(20).u16(1).u16(1)
                .string("Song").string("Composer").string("").string("")
                .u16(1000).u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0).string("")
                .u8(0).u8(0).u16(0)
                .u16(1).u16(1).u8(0).u8(45).u8(100).u8(100).u16(0)
                .u16(0).u16(0)
                .string("Layer").u8(0).u8(100).u8(100).u8(0);
        return bytes.output.toByteArray();
    }

    private static final class Bytes {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Bytes u8(int value) { output.write(value & 0xff); return this; }
        private Bytes u16(int value) { return u8(value).u8(value >>> 8); }
        private Bytes i32(int value) {
            return u8(value).u8(value >>> 8).u8(value >>> 16).u8(value >>> 24);
        }
        private Bytes string(String value) {
            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            i32(data.length);
            output.writeBytes(data);
            return this;
        }
    }
}
