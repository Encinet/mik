package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AudioContainerPropertiesParserTest {

    @TempDir
    Path directory;

    private final AudioContainerPropertiesParser parser = new AudioContainerPropertiesParser();

    @Test
    void readsVorbisSampleRateAndDurationFromOggPages() throws Exception {
        byte[] identification = new byte[30];
        identification[0] = 1;
        System.arraycopy("vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, identification, 1, 6);
        putLittleEndianInt(identification, 12, 44_100);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(oggPage(0, 42, identification));
        file.writeBytes(oggPage(88_200, 42, new byte[]{0}));
        file.writeBytes(oggPage(441_000, 99, new byte[]{0}));
        Path ogg = Files.write(directory.resolve("sample.oga"), file.toByteArray());

        assertEquals(44_100, parser.getSampleRate(ogg, "oga"));
        assertEquals(Duration.ofSeconds(2), parser.getDuration(ogg, "oga"));
    }

    @Test
    void readsSampleRateAndDurationFromTheAudioMp4Track() throws Exception {
        byte[] videoTrack = mp4Track("vide", 1_000, 120_000, -1);
        byte[] audioTrack = mp4Track("soun", 1_000, 3_000, 48_000);
        byte[] file = box("moov", concatenate(videoTrack, audioTrack));
        Path m4a = Files.write(directory.resolve("sample.m4a"), file);

        assertEquals(48_000, parser.getSampleRate(m4a, "m4a"));
        assertEquals(Duration.ofSeconds(3), parser.getDuration(m4a, "m4a"));
    }

    @Test
    void doesNotTreatUnsupportedContainersAsWavMetadata() throws Exception {
        byte[] wav = pcmWav(8_000, 8_000);
        for (String extension : new String[]{"aac", "mka", "webm"}) {
            Path file = Files.write(directory.resolve("misleading." + extension), wav);
            assertNull(parser.getSampleRate(file, extension));
            assertNull(parser.getDuration(file, extension));
        }
    }

    private static byte[] oggPage(long granulePosition, int streamSerial, byte[] packet) {
        if (packet.length > 255) {
            throw new IllegalArgumentException("test packet is too large");
        }
        byte[] page = new byte[28 + packet.length];
        System.arraycopy("OggS".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, page, 0, 4);
        putLittleEndianLong(page, 6, granulePosition);
        putLittleEndianInt(page, 14, streamSerial);
        page[26] = 1;
        page[27] = (byte) packet.length;
        System.arraycopy(packet, 0, page, 28, packet.length);
        return page;
    }

    private static byte[] box(String type, byte[] payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(payload.length + 8);
            output.write(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write(payload);
        }
        return bytes.toByteArray();
    }

    private static byte[] mp4Track(String handler, int timescale, int duration, int sampleRate)
            throws Exception {
        ByteArrayOutputStream mdhd = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(mdhd)) {
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(timescale);
            output.writeInt(duration);
            output.writeInt(0);
        }
        ByteArrayOutputStream hdlr = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(hdlr)) {
            output.writeInt(0);
            output.writeInt(0);
            output.write(handler.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        byte[] mediaPayload = concatenate(box("mdhd", mdhd.toByteArray()),
                box("hdlr", hdlr.toByteArray()));
        if (sampleRate > 0) {
            ByteArrayOutputStream entry = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(entry)) {
                output.write(new byte[6]);
                output.writeShort(1);
                output.writeShort(0);
                output.writeShort(0);
                output.writeInt(0);
                output.writeShort(2);
                output.writeShort(16);
                output.writeShort(0);
                output.writeShort(0);
                output.writeInt(sampleRate << 16);
            }
            ByteArrayOutputStream stsd = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(stsd)) {
                output.writeInt(0);
                output.writeInt(1);
                output.write(box("mp4a", entry.toByteArray()));
            }
            mediaPayload = concatenate(mediaPayload,
                    box("minf", box("stbl", box("stsd", stsd.toByteArray()))));
        }
        return box("trak", box("mdia", mediaPayload));
    }

    private static byte[] concatenate(byte[]... values) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte[] value : values) {
            result.write(value);
        }
        return result.toByteArray();
    }

    private static byte[] pcmWav(int sampleRate, int dataBytes) throws Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(result)) {
            output.writeBytes("RIFF");
            writeLittleEndianInt(output, 36 + dataBytes);
            output.writeBytes("WAVEfmt ");
            writeLittleEndianInt(output, 16);
            writeLittleEndianShort(output, 1);
            writeLittleEndianShort(output, 1);
            writeLittleEndianInt(output, sampleRate);
            writeLittleEndianInt(output, sampleRate);
            writeLittleEndianShort(output, 1);
            writeLittleEndianShort(output, 8);
            output.writeBytes("data");
            writeLittleEndianInt(output, dataBytes);
            output.write(new byte[dataBytes]);
        }
        return result.toByteArray();
    }

    private static void writeLittleEndianInt(DataOutputStream output, int value)
            throws Exception {
        output.writeInt(Integer.reverseBytes(value));
    }

    private static void writeLittleEndianShort(DataOutputStream output, int value)
            throws Exception {
        output.writeShort(Short.reverseBytes((short) value));
    }

    private static void putLittleEndianInt(byte[] target, int offset, int value) {
        for (int index = 0; index < Integer.BYTES; index++) {
            target[offset + index] = (byte) (value >>> (index * 8));
        }
    }

    private static void putLittleEndianLong(byte[] target, int offset, long value) {
        for (int index = 0; index < Long.BYTES; index++) {
            target[offset + index] = (byte) (value >>> (index * 8));
        }
    }
}
