package org.encinet.mik.module.music.catalog.nbs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbsParserTest {

    private final NbsParser parser = new NbsParser();

    @Test
    void parsesVersionFiveLayersLoopsAndAllTrumpetStages() throws Exception {
        Bytes bytes = modernHeader(5, 20, 4, 1, "Copper Quartet", 1000,
                true, 1, 1);
        for (int tick = 0; tick < 4; tick++) {
            bytes.u16(1).u16(1).u8(16 + tick).u8(45 + tick)
                    .u8(80).u8(110).i16(50).u16(0);
        }
        bytes.u16(0);
        bytes.string("Lead").u8(0).u8(50).u8(90);
        bytes.u8(0);

        NbsSong song = parse(bytes);

        assertEquals(5, song.version());
        assertEquals("Copper Quartet", song.title());
        assertEquals(10.0, song.ticksPerSecond());
        assertEquals(4, song.lengthTicks());
        assertTrue(song.loopEnabled());
        assertEquals(1, song.maxLoopCount());
        assertEquals(1, song.loopStartTick());
        assertEquals(List.of(
                NbsInstruments.TRUMPET,
                NbsInstruments.TRUMPET_EXPOSED,
                NbsInstruments.TRUMPET_WEATHERED,
                NbsInstruments.TRUMPET_OXIDIZED),
                song.notes().stream().map(NbsNote::instrument).toList());
        assertEquals(80, song.notes().getFirst().velocity());
        assertEquals(50, song.notes().getFirst().layerVolume());
        assertEquals(0, song.notes().getFirst().panning());
        assertEquals(50, song.notes().getFirst().finePitch());
    }

    @Test
    void recognizesTrumpetStagesFromCustomInstrumentNames() throws Exception {
        Bytes bytes = modernHeader(5, 16, 4, 1, "Custom Brass", 1000,
                false, 0, 0);
        for (int tick = 0; tick < 4; tick++) {
            bytes.u16(1).u16(1).u8(16 + tick).u8(45)
                    .u8(100).u8(100).i16(0).u16(0);
        }
        bytes.u16(0);
        bytes.string("Lead").u8(0).u8(100).u8(100);
        bytes.u8(4)
                .customInstrument("小号", "brass.ogg")
                .customInstrument("斑驳小号", "exposed.ogg")
                .customInstrument("锈蚀小号", "weathered.ogg")
                .customInstrument("氧化小号", "oxidised.ogg");

        NbsSong song = parse(bytes);

        assertEquals(List.of(
                NbsInstruments.TRUMPET,
                NbsInstruments.TRUMPET_EXPOSED,
                NbsInstruments.TRUMPET_WEATHERED,
                NbsInstruments.TRUMPET_OXIDIZED),
                song.notes().stream().map(NbsNote::instrument).toList());
    }

    @Test
    void parsesVersionTwoWithoutModernSongLengthField() throws Exception {
        Bytes bytes = modernHeader(2, 10, 0, 1, "Old Modern", 500,
                false, 0, 0);
        bytes.u16(1).u16(1).u8(0).u8(45).u16(0).u16(0);
        bytes.string("Layer").u8(100).u8(100).u8(0);

        NbsSong song = parse(bytes);

        assertEquals(2, song.version());
        assertEquals(1, song.lengthTicks());
        assertEquals(5.0, song.ticksPerSecond());
        assertFalse(song.loopEnabled());
        assertEquals(1, song.notes().size());
    }

    @Test
    void parsesLegacyNbsFormat() throws Exception {
        Bytes bytes = new Bytes();
        bytes.u16(2).u16(1)
                .string("Legacy").string("Composer").string("").string("")
                .u16(800).u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0).string("")
                .u16(1).u16(1).u8(0).u8(45).u16(0)
                .u16(0)
                .string("Layer").u8(75)
                .u8(0);

        NbsSong song = parse(bytes);

        assertEquals(0, song.version());
        assertEquals(2, song.lengthTicks());
        assertEquals(8.0, song.ticksPerSecond());
        assertEquals(100, song.notes().getFirst().velocity());
        assertEquals(75, song.notes().getFirst().layerVolume());
        assertEquals(0, song.notes().getFirst().panning());
    }

    @Test
    void preservesVolumeFieldsAcrossEverySupportedNbsVersion() throws Exception {
        for (int version = 1; version <= 5; version++) {
            Bytes bytes = modernHeader(version, 16, 1, 1,
                    "Version " + version, 1000, false, 0, 0);
            bytes.u16(1).u16(1).u8(0).u8(45);
            if (version >= 4) {
                bytes.u8(35).u8(100).i16(0);
            }
            bytes.u16(0).u16(0)
                    .string("Layer");
            if (version >= 4) {
                bytes.u8(0);
            }
            bytes.u8(65);
            if (version >= 2) {
                bytes.u8(100);
            }
            bytes.u8(0);

            NbsNote note = parse(bytes).notes().getFirst();

            assertEquals(65, note.layerVolume(), "layer volume in version " + version);
            assertEquals(version >= 4 ? 35 : 100, note.velocity(),
                    "note velocity in version " + version);
        }
    }

    @Test
    void rejectsTruncatedAndUnsupportedFiles() {
        assertThrows(IOException.class,
                () -> parser.parse(new ByteArrayInputStream(new byte[]{0, 0, 99, 16})));

        Bytes truncated = modernHeader(5, 16, 1, 1, "Broken", 1000,
                false, 0, 0);
        truncated.u16(1).u16(1).u8(0);
        assertThrows(IOException.class, () -> parse(truncated));

        Bytes missingCustomInstrumentCount = modernHeader(5, 16, 1, 1,
                "Missing tail", 1000, false, 0, 0);
        missingCustomInstrumentCount.u16(0)
                .string("Layer").u8(0).u8(100).u8(100);
        assertThrows(IOException.class, () -> parse(missingCustomInstrumentCount));

        Bytes invalidVanillaCount = modernHeader(5, 21, 1, 1,
                "Invalid instruments", 1000, false, 0, 0);
        assertThrows(IOException.class, () -> parse(invalidVanillaCount));
    }

    @Test
    void acceptsTheFullNbsTempoFieldInsteadOfRejectingSongsAboveTwentyTps() throws Exception {
        Bytes aboveTwenty = emptySong(2500);
        Bytes maximum = emptySong(65535);

        assertEquals(25.0, parse(aboveTwenty).ticksPerSecond());
        assertEquals(NbsSong.MAX_TICKS_PER_SECOND, parse(maximum).ticksPerSecond());
    }

    @Test
    void rejectsZeroTempo() {
        Bytes bytes = modernHeader(5, 16, 1, 1,
                "Stopped", 0, false, 0, 0);

        assertThrows(IOException.class, () -> parse(bytes));
    }

    @Test
    void rejectsMoreThan256NotesInOneTick() {
        int noteCount = 257;
        Bytes bytes = modernHeader(5, 16, 1, noteCount,
                "Too dense", 1000, false, 0, 0);
        bytes.u16(1);
        for (int note = 0; note < noteCount; note++) {
            bytes.u16(1).u8(0).u8(45)
                    .u8(100).u8(100).i16(0);
        }

        assertThrows(IOException.class, () -> parse(bytes));
    }

    @Test
    void preservesTheFullSignedShortFinePitchRange() throws Exception {
        Bytes bytes = modernHeader(5, 20, 1, 1, "Fine pitch", 1000,
                false, 0, 0);
        bytes.u16(1).u16(1).u8(0).u8(45)
                .u8(100).u8(100).i16(Short.MIN_VALUE).u16(0)
                .u16(0)
                .string("Layer").u8(0).u8(100).u8(100)
                .u8(0);

        NbsSong song = parse(bytes);

        assertEquals(Short.MIN_VALUE, song.notes().getFirst().finePitch());
    }

    @Test
    void songDefensivelySortsNotesAndRejectsTicksOutsideItsLength() {
        NbsNote later = new NbsNote(2, 0, 45, 100, 100, 0, 0);
        NbsNote earlier = new NbsNote(0, 0, 45, 100, 100, 0, 0);

        NbsSong song = new NbsSong(5, "Sorted", null, null, null,
                10, 3, false, 0, 0, List.of(later, earlier));

        assertEquals(List.of(earlier, later), song.notes());
        assertThrows(IllegalArgumentException.class, () -> new NbsSong(
                5, "Invalid", null, null, null,
                10, 2, false, 0, 0, List.of(later)));
    }

    @Test
    void preservesZeroAndLowNoteAndLayerVolumesWithoutIntegerMerging() throws Exception {
        Bytes bytes = modernHeader(5, 16, 2, 2, "Dynamics", 1000,
                false, 0, 0);
        bytes.u16(1)
                .u16(1).u8(0).u8(45).u8(1).u8(100).i16(0)
                .u16(1).u8(0).u8(45).u8(100).u8(100).i16(0)
                .u16(0)
                .u16(1).u16(1).u8(0).u8(45).u8(0).u8(100).i16(0).u16(0)
                .u16(0)
                .string("Quiet").u8(0).u8(1).u8(100)
                .string("Silent").u8(0).u8(0).u8(100)
                .u8(0);

        NbsSong song = parse(bytes);

        assertEquals(1, song.notes().get(0).velocity());
        assertEquals(1, song.notes().get(0).layerVolume());
        assertEquals(100, song.notes().get(1).velocity());
        assertEquals(0, song.notes().get(1).layerVolume());
        assertEquals(0, song.notes().get(2).velocity());
        assertEquals(1, song.notes().get(2).layerVolume());
    }

    @Test
    void validatesBothNbsVolumeComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> new NbsNote(0, 0, 45, -1, 100, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NbsNote(0, 0, 45, 101, 100, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NbsNote(0, 0, 45, 100, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new NbsNote(0, 0, 45, 100, 101, 0, 0));
    }

    private NbsSong parse(Bytes bytes) throws IOException {
        return parser.parse(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static Bytes modernHeader(int version, int vanillaInstruments,
                                      int length, int layers, String title, int tempo,
                                      boolean loop, int maxLoops, int loopStart) {
        Bytes bytes = new Bytes();
        bytes.u16(0).u8(version).u8(vanillaInstruments);
        if (version >= 3) {
            bytes.u16(length);
        }
        bytes.u16(layers)
                .string(title).string("Composer").string("Original").string("Description")
                .u16(tempo)
                .u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0)
                .string("");
        if (version >= 4) {
            bytes.u8(loop ? 1 : 0).u8(maxLoops).u16(loopStart);
        }
        return bytes;
    }

    private static Bytes emptySong(int tempo) {
        return modernHeader(5, 20, 1, 1, "Fast", tempo, false, 0, 0)
                .u16(0)
                .string("Layer").u8(0).u8(100).u8(100)
                .u8(0);
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

        private Bytes i16(int value) {
            return u16(value & 0xffff);
        }

        private Bytes i32(int value) {
            return u8(value).u8(value >>> 8).u8(value >>> 16).u8(value >>> 24);
        }

        private Bytes string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            i32(bytes.length);
            output.writeBytes(bytes);
            return this;
        }

        private Bytes customInstrument(String name, String fileName) {
            return string(name).string(fileName).u8(45).u8(0);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
