package org.encinet.mik.module.music.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMetadataProbeIntegrationTest {

    @TempDir
    Path directory;

    @Test
    void lavaplayerRecognizesARealPcmWavAndReportsItsDuration() throws Exception {
        Path wav = Files.write(directory.resolve("sample.wav"), taggedPcmWav(
                8_000, 8_000, null, null, null));

        PlaybackProbeResult result = new LavaplayerMetadataReader().probe(wav, "wav");

        assertTrue(result.playable());
        assertEquals(Duration.ofSeconds(1), result.metadata().audio().duration());
    }

    @Test
    void jaudiotaggerReadsWavInfoTitleArtistAlbumAndTechnicalProperties() throws Exception {
        Path wav = Files.write(directory.resolve("tagged.wav"), taggedPcmWav(
                8_000, 8_000, "Embedded title", "Embedded artist", "Embedded album"));

        LocalTrackMetadata metadata = new JaudiotaggerMetadataReader().read(wav, "wav");

        assertEquals("Embedded title", metadata.title());
        assertEquals("Embedded artist", metadata.artist());
        assertEquals("Embedded album", metadata.album());
        assertEquals(8_000, metadata.audio().sampleRateHz());
        assertEquals(Duration.ofSeconds(1), metadata.audio().duration());
    }

    private static byte[] taggedPcmWav(int sampleRate, int samples, String title,
                                       String artist, String album) throws Exception {
        byte[] audio = new byte[samples * 2];
        byte[] fmt = littleEndian(output -> {
            output.writeShort(Short.reverseBytes((short) 1));
            output.writeShort(Short.reverseBytes((short) 1));
            output.writeInt(Integer.reverseBytes(sampleRate));
            output.writeInt(Integer.reverseBytes(sampleRate * 2));
            output.writeShort(Short.reverseBytes((short) 2));
            output.writeShort(Short.reverseBytes((short) 16));
        });
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        writeChunk(chunks, "fmt ", fmt);
        if (title != null || artist != null || album != null) {
            ByteArrayOutputStream info = new ByteArrayOutputStream();
            info.writeBytes("INFO".getBytes(StandardCharsets.US_ASCII));
            writeInfo(info, "INAM", title);
            writeInfo(info, "IART", artist);
            writeInfo(info, "IPRD", album);
            writeChunk(chunks, "LIST", info.toByteArray());
        }
        writeChunk(chunks, "data", audio);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(file, chunks.size() + 4);
        file.writeBytes("WAVE".getBytes(StandardCharsets.US_ASCII));
        file.writeBytes(chunks.toByteArray());
        return file.toByteArray();
    }

    private static void writeInfo(ByteArrayOutputStream output, String key, String value) {
        if (value == null) {
            return;
        }
        byte[] text = (value + "\0").getBytes(StandardCharsets.UTF_8);
        writeChunk(output, key, text);
    }

    private static void writeChunk(ByteArrayOutputStream output, String id, byte[] data) {
        output.writeBytes(id.getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(output, data.length);
        output.writeBytes(data);
        if ((data.length & 1) != 0) {
            output.write(0);
        }
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
        output.write(value);
        output.write(value >>> 8);
        output.write(value >>> 16);
        output.write(value >>> 24);
    }

    private static byte[] littleEndian(IoWriter writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter {
        void write(DataOutputStream output) throws Exception;
    }
}
