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
    void parsesVersionSixWithTwentyVanillaInstrumentsAndCustomBoundary() throws Exception {
        Bytes bytes = modernHeader(6, 20, 5, 1, "Version Six", 1250,
                true, 2, 1);
        for (int tick = 0; tick < 5; tick++) {
            bytes.u16(1).u16(1).u8(16 + tick).u8(45 + tick)
                    .u8(90).u8(100).i16(-25).u16(0);
        }
        bytes.u16(0)
                .string("Brass").u8(0).u8(80).u8(100)
                .u8(1).customInstrument("Custom trumpet", "custom/trumpet.ogg");

        NbsSong song = parse(bytes);

        assertEquals(6, song.version());
        assertEquals("Version Six", song.title());
        assertEquals(12.5, song.ticksPerSecond());
        assertEquals(5, song.lengthTicks());
        assertTrue(song.loopEnabled());
        assertEquals(2, song.maxLoopCount());
        assertEquals(1, song.loopStartTick());
        assertEquals(List.of(
                NbsInstruments.TRUMPET,
                NbsInstruments.TRUMPET_EXPOSED,
                NbsInstruments.TRUMPET_WEATHERED,
                NbsInstruments.TRUMPET_OXIDIZED,
                NbsInstruments.TRUMPET),
                song.notes().stream().map(NbsNote::instrument).toList());
        assertEquals(90, song.notes().getFirst().velocity());
        assertEquals(80, song.notes().getFirst().layerVolume());
        assertEquals(-25, song.notes().getFirst().finePitch());
        assertEquals(0, song.notes().getFirst().layer());
        assertEquals(16, song.notes().getFirst().sourceInstrument());
        assertEquals(0, song.notes().getFirst().notePanning());
        assertEquals(20, song.fileMetadata().vanillaInstrumentCount());
        assertEquals(5, song.fileMetadata().declaredLengthTicks());
        assertEquals(4, song.fileMetadata().timeSignature());
        assertEquals("Brass", song.layers().getFirst().name());
        assertEquals(80, song.layers().getFirst().volume());
        assertEquals(1, song.customInstruments().size());
        assertEquals("Custom trumpet", song.customInstruments().getFirst().name());
        assertEquals("custom/trumpet.ogg", song.customInstruments().getFirst().fileName());
        assertEquals(45, song.customInstruments().getFirst().key());
        assertFalse(song.customInstruments().getFirst().pressKey());
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
        for (int version = 1; version <= 6; version++) {
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
    void acceptsMoreThan256NotesInOneTickWhenTheyUseDistinctLayers() throws Exception {
        int noteCount = 257;
        Bytes bytes = modernHeader(5, 16, 1, noteCount,
                "Too dense", 1000, false, 0, 0);
        bytes.u16(1);
        for (int note = 0; note < noteCount; note++) {
            bytes.u16(1).u8(0).u8(45)
                    .u8(100).u8(100).i16(0);
        }
        bytes.u16(0).u16(0);
        for (int layer = 0; layer < noteCount; layer++) {
            bytes.string("Layer " + layer).u8(0).u8(100).u8(100);
        }
        bytes.u8(0);

        assertEquals(noteCount, parse(bytes).notes().size());
    }

    @Test
    void retainsEveryHeaderLayerAndCustomInstrumentField() throws Exception {
        Bytes bytes = new Bytes();
        bytes.u16(0).u8(6).u8(20).u16(3).u16(2)
                .string("Metadata").string("Author").string("Original").string("Description")
                .u16(750)
                .u8(1).u8(7).u8(3)
                .i32(11).i32(12).i32(13).i32(14).i32(15)
                .string("source.mid")
                .u8(1).u8(4).u16(1)
                .u16(1).u16(2).u8(20).u8(46).u8(77).u8(125).i16(123).u16(0)
                .u16(0)
                .string("Muted lead").u8(2).u8(60).u8(80)
                .string("Custom layer").u8(1).u8(70).u8(95)
                .u8(1).customInstrument("Lead", "folder/lead.ogg", 36, 1);

        NbsSong song = parse(bytes);

        assertEquals(new NbsFileMetadata(20, 3, 1, 7, 3,
                11, 12, 13, 14, 15, "source.mid"), song.fileMetadata());
        assertTrue(song.fileMetadata().autoSaveEnabled());
        assertEquals(List.of(
                new NbsLayer("Muted lead", 2, 60, -20),
                new NbsLayer("Custom layer", 1, 70, -5)), song.layers());
        assertEquals(List.of(new NbsCustomInstrument(
                "Lead", "folder/lead.ogg", 36, 1)), song.customInstruments());
        assertTrue(song.customInstruments().getFirst().pressKey());
        NbsNote note = song.notes().getFirst();
        assertEquals(1, note.layer());
        assertEquals(20, note.sourceInstrument());
        assertEquals(25, note.notePanning());
        assertEquals(10, note.panning());
        assertEquals(70, note.layerVolume());
        assertEquals(36, note.instrumentKey());
        assertEquals(-677, note.playbackPitchCents());
    }

    @Test
    void acceptsCompletelyMissingOptionalTailSections() throws Exception {
        Bytes bytes = modernHeader(6, 20, 1, 2,
                "Header and notes only", 1000, false, 0, 0)
                .u16(1).u16(2).u8(16).u8(45)
                .u8(100).u8(100).i16(0).u16(0).u16(0);

        NbsSong song = parse(bytes);

        assertEquals(2, song.layers().size());
        assertEquals(List.of(NbsLayer.defaults(), NbsLayer.defaults()), song.layers());
        assertEquals(List.of(), song.customInstruments());
        assertEquals(NbsInstruments.TRUMPET, song.notes().getFirst().instrument());
    }

    @Test
    void acceptsMissingCustomInstrumentSectionAfterCompleteLayers() throws Exception {
        Bytes bytes = modernHeader(6, 20, 1, 1,
                "No custom tail", 1000, false, 0, 0)
                .u16(0)
                .string("Layer").u8(0).u8(75).u8(100);

        NbsSong song = parse(bytes);

        assertEquals(List.of(new NbsLayer("Layer", 0, 75, 0)), song.layers());
        assertEquals(List.of(), song.customInstruments());
    }

    @Test
    void rejectsPartiallyWrittenOptionalSectionsAndTrailingGarbage() {
        Bytes partialLayer = modernHeader(6, 20, 1, 1,
                "Partial layer", 1000, false, 0, 0)
                .u16(0).string("Layer").u8(0);
        assertThrows(IOException.class, () -> parse(partialLayer));

        Bytes partialInstrument = modernHeader(6, 20, 1, 1,
                "Partial instrument", 1000, false, 0, 0)
                .u16(0).string("Layer").u8(0).u8(100).u8(100)
                .u8(1).string("Instrument");
        assertThrows(IOException.class, () -> parse(partialInstrument));

        Bytes trailing = emptySong(1000).u8(1);
        assertThrows(IOException.class, () -> parse(trailing));
    }

    @Test
    void decodesUtf8AndFallsBackForLegacySingleByteStrings() throws Exception {
        Bytes utf8 = modernHeader(6, 20, 1, 1,
                "小号", 1000, false, 0, 0)
                .u16(0).string("图层").u8(0).u8(100).u8(100).u8(0);
        assertEquals("小号", parse(utf8).title());
        assertEquals("图层", parse(utf8).layers().getFirst().name());

        Bytes legacy = modernHeaderWithRawTitle(new byte[]{'C', 'a', 'f', (byte) 0xe9});
        assertEquals("Café", parse(legacy).title());
    }

    @Test
    void identifiesEditorControlInstrumentsWithoutTurningThemIntoHarpNotes() throws Exception {
        Bytes bytes = modernHeader(6, 20, 1, 2,
                "Controls", 1000, false, 0, 0)
                .u16(1)
                .u16(1).u8(20).u8(45).u8(100).u8(100).i16(300)
                .u16(1).u8(21).u8(45).u8(100).u8(100).i16(2)
                .u16(0).u16(0)
                .string("Tempo").u8(0).u8(100).u8(100)
                .string("Stop").u8(0).u8(100).u8(100)
                .u8(2)
                .customInstrument("Tempo Changer", "", 45, 0)
                .customInstrument("Sound Stopper", "", 45, 0);

        NbsSong song = parse(bytes);

        assertEquals(List.of(NbsNoteType.TEMPO_CHANGE, NbsNoteType.SOUND_STOP),
                song.notes().stream().map(NbsNote::type).toList());
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

    private static Bytes modernHeaderWithRawTitle(byte[] title) {
        return new Bytes().u16(0).u8(6).u8(20).u16(1).u16(1)
                .rawString(title).string("").string("").string("")
                .u16(1000).u8(0).u8(10).u8(4)
                .i32(0).i32(0).i32(0).i32(0).i32(0).string("")
                .u8(0).u8(0).u16(0)
                .u16(0)
                .string("Layer").u8(0).u8(100).u8(100).u8(0);
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

        private Bytes rawString(byte[] bytes) {
            i32(bytes.length);
            output.writeBytes(bytes);
            return this;
        }

        private Bytes customInstrument(String name, String fileName) {
            return customInstrument(name, fileName, 45, 0);
        }

        private Bytes customInstrument(String name, String fileName, int key, int pressKey) {
            return string(name).string(fileName).u8(key).u8(pressKey);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
