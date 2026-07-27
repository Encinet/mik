package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalMusicSourceTest {

    @TempDir
    Path musicFolder;

    @Test
    void recursivelyFindsSupportedAudioFilesIgnoringExtensionCase() throws Exception {
        Path album = Files.createDirectories(musicFolder.resolve("Album One"));
        Path nested = Files.createDirectories(album.resolve("Live"));
        Files.writeString(album.resolve("First_Track.MP3"), "audio");
        Files.writeString(nested.resolve("Second-Track.WebM"), "audio");
        Files.writeString(musicFolder.resolve("voice.OpUs"), "audio");
        Files.writeString(musicFolder.resolve("notes.txt"), "not audio");
        Path nestedDirectory = Files.createDirectories(musicFolder.resolve("collection/assets"));
        Files.writeString(nestedDirectory.resolve("nested.mp3"), "audio");

        Set<String> ids = LocalMusicSource.findSupportedAudioFiles(musicFolder).stream()
                .map(path -> LocalMusicSource.toTrackId(musicFolder, path))
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "Album One/First_Track.MP3",
                "Album One/Live/Second-Track.WebM",
                "collection/assets/nested.mp3",
                "voice.OpUs"
        ), ids);
    }

    @Test
    void ignoresSymbolicLinksToAudioOutsideTheMusicDirectory() throws Exception {
        Path outside = Files.writeString(musicFolder.getParent().resolve("outside.mp3"), "audio");
        Path link = musicFolder.resolve("linked.mp3");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            assumeTrue(false, "Symbolic links are unavailable");
        }

        assertTrue(LocalMusicSource.findSupportedAudioFiles(musicFolder).isEmpty());
    }

    @Test
    void usesRelativeIdsAndReadableDisplayNames() {
        Path track = musicFolder.resolve("Soundtracks").resolve("Boss_Theme-Final.flac");

        String id = LocalMusicSource.toTrackId(musicFolder, track);

        assertEquals("Soundtracks/Boss_Theme-Final.flac", id);
        assertEquals("Soundtracks / Boss Theme - Final", LocalMusicSource.formatDisplayName(id));
        assertFalse(id.contains(musicFolder.toString()));
    }

    @Test
    void supportsFormatsHandledByLocalLavaplayerSource() {
        for (String extension : Set.of(
                "mp3", "m4a", "mp4", "wav", "flac", "ogg", "oga", "aac", "opus", "mka", "webm", "nbs")) {
            assertTrue(LocalMusicSource.isSupportedAudioFile(Path.of("track." + extension)), extension);
        }

        assertFalse(LocalMusicSource.isSupportedAudioFile(Path.of("track.wma")));
        assertFalse(LocalMusicSource.isSupportedAudioFile(Path.of("playlist.m3u")));
        assertFalse(LocalMusicSource.isSupportedAudioFile(Path.of("track")));
    }

    @Test
    void loadsValidNbsTrackAndSkipsDamagedNbsFile() throws Exception {
        Files.write(musicFolder.resolve("brass.nbs"),
                minimalNbs("Brass Song", "Original Composer", 16));
        Files.write(musicFolder.resolve("broken.nbs"), new byte[]{0, 0, 5});
        List<String> warnings = new ArrayList<>();

        List<MusicTrack> tracks = new LocalMusicSource(musicFolder, warnings::add).load();

        assertEquals(1, tracks.size());
        MusicTrack track = tracks.getFirst();
        assertEquals("brass.nbs", track.id());
        assertEquals("Brass Song", track.details().title());
        assertEquals("Composer", track.details().artist());
        assertEquals("Original Composer", track.details().originalAuthor());
        assertEquals("NBS", track.details().format());
        assertEquals(Duration.ofSeconds(1), track.details().audio().duration());
        TrackTarget.NbsFile target = assertInstanceOf(TrackTarget.NbsFile.class,
                track.target());
        assertEquals(musicFolder.resolve("brass.nbs").toAbsolutePath().normalize(), target.path());
        assertEquals(musicFolder.toAbsolutePath().normalize(), target.root());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("broken.nbs"));
    }

    @Test
    void fallsBackToFileIdWhenSanitizedLocalOrNbsNameIsEmpty() throws Exception {
        Files.writeString(musicFolder.resolve("_.mp3"), "audio");
        Files.write(musicFolder.resolve("'.nbs"), minimalNbs("", "", 0));

        LocalTrackMetadataReader reader = new LocalTrackMetadataReader(
                (path, extension) -> LocalTrackMetadata.EMPTY,
                (path, extension) -> new PlaybackProbeResult(true, LocalTrackMetadata.EMPTY),
                (path, extension) -> new LocalTrackMetadata(null, null, null,
                        new AudioProperties(fileSize(path), null, null)),
                new MetadataFallbackPolicy());
        List<MusicTrack> tracks = new LocalMusicSource(
                musicFolder, reader, new org.encinet.mik.module.music.catalog.nbs.NbsParser(),
                ignored -> {}).load();

        assertEquals(List.of("'.nbs", "_.mp3"), tracks.stream().map(MusicTrack::id).toList());
        assertEquals(List.of("'.nbs", "_.mp3"),
                tracks.stream().map(track -> track.details().title()).toList());
    }

    @Test
    void excludesOrdinaryFilesRejectedByThePlaybackProbe() throws Exception {
        Files.writeString(musicFolder.resolve("not-audio.mp3"), "plain text");
        List<String> warnings = new ArrayList<>();

        List<MusicTrack> tracks = new LocalMusicSource(musicFolder, warnings::add).load();

        assertTrue(tracks.isEmpty());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("unsupported or damaged"));
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static byte[] minimalNbs(String title, String originalAuthor, int instrument) {
        Bytes bytes = new Bytes();
        bytes.u16(0).u8(5).u8(20).u16(1).u16(1)
                .string(title).string("Composer").string(originalAuthor).string("")
                .u16(1000).u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0).string("")
                .u8(0).u8(0).u16(0)
                .u16(1).u16(1).u8(instrument).u8(45).u8(100).u8(100).u16(0)
                .u16(0).u16(0)
                .string("Layer").u8(0).u8(100).u8(100)
                .u8(0);
        return bytes.output.toByteArray();
    }

    private static final class Bytes {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Bytes u8(int value) {
            output.write(value & 0xff);
            return this;
        }

        private Bytes u16(int value) {
            return u8(value).u8(value >>> 8);
        }

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
