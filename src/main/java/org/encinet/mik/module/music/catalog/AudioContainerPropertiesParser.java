package org.encinet.mik.module.music.catalog;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads raw technical properties from supported audio containers as a metadata fallback. */
final class AudioContainerPropertiesParser {

    Long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (Exception e) {
            return null;
        }
    }

    Duration getDuration(Path filePath, String extension) {
        try {
            int durationSeconds = switch (extension.toLowerCase(Locale.ROOT)) {
                case "mp3" -> getMp3Duration(filePath);
                case "flac" -> getFlacDuration(filePath);
                case "ogg", "oga", "opus" -> getOggDuration(filePath);
                case "m4a", "mp4" -> getM4aDuration(filePath);
                case "wav" -> getWavDuration(filePath);
                default -> -1;
            };

            if (durationSeconds > 0) {
                return Duration.ofSeconds(durationSeconds);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    Integer getSampleRate(Path filePath, String extension) {
        try {
            int sampleRate = switch (extension.toLowerCase(Locale.ROOT)) {
                case "mp3" -> getMp3SampleRate(filePath);
                case "flac" -> getFlacSampleRate(filePath);
                case "ogg", "oga", "opus" -> getOggSampleRate(filePath);
                case "m4a", "mp4" -> getM4aSampleRate(filePath);
                case "wav" -> getWavSampleRate(filePath);
                default -> -1;
            };

            if (sampleRate > 0) {
                return sampleRate;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private int getWavDuration(Path filePath) {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(filePath.toFile());
            if (fileFormat.getFrameLength() != AudioSystem.NOT_SPECIFIED) {
                AudioFormat format = fileFormat.getFormat();
                float frameRate = format.getFrameRate();
                if (frameRate > 0) {
                    return (int) (fileFormat.getFrameLength() / frameRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getWavSampleRate(Path filePath) {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(filePath.toFile());
            AudioFormat format = fileFormat.getFormat();
            float rate = format.getSampleRate();
            return rate != AudioSystem.NOT_SPECIFIED ? (int) rate : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int getMp3Duration(Path filePath) {
        try {
            long fileSize = Files.size(filePath);
            int sampleRate = getMp3SampleRate(filePath);
            if (sampleRate <= 0) return -1;

            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
                byte[] header = new byte[4];
                raf.read(header, 0, 3);
                long dataStart = 0;
                if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                    raf.seek(6);
                    raf.read(header, 0, 4);
                    int tagSize = ((header[0] & 0x7F) << 21) | ((header[1] & 0x7F) << 14) |
                                 ((header[2] & 0x7F) << 7) | (header[3] & 0x7F);
                    dataStart = tagSize + 10;
                }

                raf.seek(dataStart);
                raf.read(header);
                if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
                    int bitrateIndex = (header[2] >> 4) & 0x0F;
                    int version = (header[1] >> 3) & 0x03;

                    int[][] bitrates = {
                        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0},
                        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0},
                        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0}
                    };

                    int bitrate = bitrates[version][bitrateIndex];
                    if (bitrate > 0) {
                        long audioSize = fileSize - dataStart;
                        return (int) (audioSize * 8 / (bitrate * 1000));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getMp3SampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] header = new byte[4];
            raf.read(header, 0, 3);
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                raf.seek(6);
                raf.read(header, 0, 4);
                int tagSize = ((header[0] & 0x7F) << 21) | ((header[1] & 0x7F) << 14) |
                             ((header[2] & 0x7F) << 7) | (header[3] & 0x7F);
                raf.seek(tagSize + 10);
            } else {
                raf.seek(0);
            }

            for (int i = 0; i < 8192; i++) {
                raf.read(header);
                if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
                    int version = (header[1] >> 3) & 0x03;
                    int sampleRateIndex = (header[2] >> 2) & 0x03;

                    int[][] sampleRates = {
                        {11025, 12000, 8000, 0},
                        {0, 0, 0, 0},
                        {22050, 24000, 16000, 0},
                        {44100, 48000, 32000, 0}
                    };

                    return sampleRates[version][sampleRateIndex];
                }
                raf.seek(raf.getFilePointer() - 3);
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getFlacDuration(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] marker = new byte[4];
            raf.read(marker);

            if (marker[0] == 'f' && marker[1] == 'L' && marker[2] == 'a' && marker[3] == 'C') {
                raf.skipBytes(4);
                byte[] streamInfo = new byte[18];
                raf.read(streamInfo);

                int sampleRate = ((streamInfo[10] & 0xFF) << 12) |
                                ((streamInfo[11] & 0xFF) << 4) |
                                ((streamInfo[12] & 0xF0) >> 4);

                long totalSamples = ((long)(streamInfo[13] & 0x0F) << 32) |
                                   ((long)(streamInfo[14] & 0xFF) << 24) |
                                   ((long)(streamInfo[15] & 0xFF) << 16) |
                                   ((long)(streamInfo[16] & 0xFF) << 8) |
                                   (streamInfo[17] & 0xFF);

                if (sampleRate > 0 && totalSamples > 0) {
                    return (int) (totalSamples / sampleRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getFlacSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] marker = new byte[4];
            raf.read(marker);

            if (marker[0] == 'f' && marker[1] == 'L' && marker[2] == 'a' && marker[3] == 'C') {
                raf.skipBytes(4);
                byte[] streamInfo = new byte[18];
                raf.read(streamInfo);

                return ((streamInfo[10] & 0xFF) << 12) |
                       ((streamInfo[11] & 0xFF) << 4) |
                       ((streamInfo[12] & 0xF0) >> 4);
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getOggDuration(Path filePath) {
        OggStreamInfo stream = readOggStreamInfo(filePath);
        if (stream == null) {
            return -1;
        }
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileSize = raf.length();
            long start = Math.max(0, fileSize - 65536);
            raf.seek(start);

            byte[] buffer = new byte[Math.toIntExact(fileSize - start)];
            int bytesRead = raf.read(buffer);
            for (int i = bytesRead - 27; i >= 0; i--) {
                OggPage page = readOggPage(buffer, i, bytesRead);
                if (page != null && page.streamSerial() == stream.streamSerial()) {
                    long seconds = page.granulePosition() < 0 ? -1
                            : page.granulePosition() / stream.sampleRate();
                    return seconds > 0 && seconds <= Integer.MAX_VALUE ? (int) seconds : -1;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getOggSampleRate(Path filePath) {
        OggStreamInfo stream = readOggStreamInfo(filePath);
        return stream == null ? -1 : stream.sampleRate();
    }

    private OggStreamInfo readOggStreamInfo(Path filePath) {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            byte[] header = new byte[27];
            raf.readFully(header);
            if (!hasAscii(header, 0, "OggS") || header[4] != 0) {
                return null;
            }
            int segments = header[26] & 0xff;
            byte[] lacing = new byte[segments];
            raf.readFully(lacing);
            int packetBytes = 0;
            for (byte value : lacing) {
                int length = value & 0xff;
                packetBytes = Math.addExact(packetBytes, length);
                if (length < 255) {
                    break;
                }
            }
            if (packetBytes < 16 || packetBytes > 64 * 1024) {
                return null;
            }
            byte[] packet = new byte[packetBytes];
            raf.readFully(packet);
            if (hasAscii(packet, 0, "OpusHead")) {
                return new OggStreamInfo(48000, littleEndianInt(header, 14));
            }
            if (packet[0] == 1 && hasAscii(packet, 1, "vorbis")) {
                int sampleRate = littleEndianInt(packet, 12);
                return sampleRate > 0
                        ? new OggStreamInfo(sampleRate, littleEndianInt(header, 14)) : null;
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static OggPage readOggPage(byte[] bytes, int offset, int limit) {
        if (offset < 0 || limit > bytes.length || offset > limit - 27
                || !hasAscii(bytes, offset, "OggS") || bytes[offset + 4] != 0) {
            return null;
        }
        int segmentCount = bytes[offset + 26] & 0xff;
        int headerEnd = offset + 27 + segmentCount;
        if (headerEnd > limit) {
            return null;
        }
        int payloadBytes = 0;
        for (int index = 0; index < segmentCount; index++) {
            payloadBytes += bytes[offset + 27 + index] & 0xff;
        }
        if (payloadBytes > limit - headerEnd) {
            return null;
        }
        return new OggPage(littleEndianLong(bytes, offset + 6),
                littleEndianInt(bytes, offset + 14));
    }

    private int getM4aDuration(Path filePath) {
        Mp4AudioMetadata metadata = readMp4AudioMetadata(filePath);
        if (metadata != null && metadata.timescale() > 0 && metadata.duration() > 0) {
            long seconds = metadata.duration() / metadata.timescale();
            return seconds > 0 && seconds <= Integer.MAX_VALUE ? (int) seconds : -1;
        }
        return -1;
    }

    private int getM4aSampleRate(Path filePath) {
        Mp4AudioMetadata metadata = readMp4AudioMetadata(filePath);
        return metadata == null ? -1 : metadata.sampleRate();
    }

    private Mp4AudioMetadata readMp4AudioMetadata(Path filePath) {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            Mp4Box moov = findMp4Box(raf, 0, raf.length(), "moov");
            if (moov == null) {
                return null;
            }
            for (Mp4Box track : readMp4Boxes(raf, moov.payloadStart(), moov.end())) {
                if (!"trak".equals(track.type())) {
                    continue;
                }
                Mp4AudioMetadata metadata = readMp4AudioTrack(raf, track);
                if (metadata != null) {
                    return metadata;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Mp4AudioMetadata readMp4AudioTrack(RandomAccessFile raf, Mp4Box track)
            throws java.io.IOException {
        Mp4Box media = findMp4Box(raf, track.payloadStart(), track.end(), "mdia");
        if (media == null) {
            return null;
        }
        Mp4Box handler = findMp4Box(raf, media.payloadStart(), media.end(), "hdlr");
        if (handler == null || !isAudioHandler(raf, handler)) {
            return null;
        }
        Mp4Box mediaHeader = findMp4Box(raf, media.payloadStart(), media.end(), "mdhd");
        Mp4MediaHeader header = mediaHeader == null ? null
                : readMdhd(raf, mediaHeader.payloadStart(), mediaHeader.end());
        if (header == null) {
            return null;
        }
        int sampleRate = readMp4SampleRate(raf, media);
        return new Mp4AudioMetadata(header.timescale(), header.duration(), sampleRate);
    }

    private static boolean isAudioHandler(RandomAccessFile raf, Mp4Box handler)
            throws java.io.IOException {
        if (handler.end() - handler.payloadStart() < 12) {
            return false;
        }
        raf.seek(handler.payloadStart() + 8);
        byte[] type = new byte[4];
        raf.readFully(type);
        return "soun".equals(new String(type, StandardCharsets.US_ASCII));
    }

    private int readMp4SampleRate(RandomAccessFile raf, Mp4Box media)
            throws java.io.IOException {
        Mp4Box mediaInfo = findMp4Box(raf, media.payloadStart(), media.end(), "minf");
        Mp4Box sampleTable = mediaInfo == null ? null
                : findMp4Box(raf, mediaInfo.payloadStart(), mediaInfo.end(), "stbl");
        Mp4Box sampleDescriptions = sampleTable == null ? null
                : findMp4Box(raf, sampleTable.payloadStart(), sampleTable.end(), "stsd");
        if (sampleDescriptions == null || sampleDescriptions.end()
                - sampleDescriptions.payloadStart() < 8) {
            return -1;
        }
        raf.seek(sampleDescriptions.payloadStart() + 4);
        long entryCount = Integer.toUnsignedLong(raf.readInt());
        long entryOffset = sampleDescriptions.payloadStart() + 8;
        for (long index = 0; index < entryCount && entryOffset < sampleDescriptions.end(); index++) {
            Mp4Box entry = readMp4Box(raf, entryOffset, sampleDescriptions.end());
            if (entry == null) {
                return -1;
            }
            if (MP4_AUDIO_SAMPLE_ENTRIES.contains(entry.type())
                    && entry.end() - entry.payloadStart() >= 28) {
                raf.seek(entry.payloadStart() + 24);
                int sampleRate = (int) (Integer.toUnsignedLong(raf.readInt()) >>> 16);
                if (sampleRate > 0) {
                    return sampleRate;
                }
            }
            entryOffset = entry.end();
        }
        return -1;
    }

    private Mp4Box findMp4Box(RandomAccessFile raf, long start, long end, String type)
            throws java.io.IOException {
        for (Mp4Box box : readMp4Boxes(raf, start, end)) {
            if (type.equals(box.type())) {
                return box;
            }
        }
        return null;
    }

    private List<Mp4Box> readMp4Boxes(RandomAccessFile raf, long start, long end)
            throws java.io.IOException {
        if (start < 0 || end < start || end > raf.length()) {
            throw new java.io.IOException("Invalid MP4 box range");
        }
        List<Mp4Box> boxes = new ArrayList<>();
        long offset = start;
        while (offset <= end - 8) {
            if (boxes.size() >= 10_000) {
                throw new java.io.IOException("MP4 container has too many boxes");
            }
            Mp4Box box = readMp4Box(raf, offset, end);
            if (box == null) {
                throw new java.io.IOException("Invalid MP4 box at offset " + offset);
            }
            boxes.add(box);
            offset = box.end();
        }
        if (offset != end) {
            throw new java.io.IOException("Truncated MP4 box header");
        }
        return List.copyOf(boxes);
    }

    private Mp4Box readMp4Box(RandomAccessFile raf, long offset, long containerEnd)
            throws java.io.IOException {
        if (offset < 0 || containerEnd - offset < 8) {
            return null;
        }
        raf.seek(offset);
        long size = Integer.toUnsignedLong(raf.readInt());
        byte[] typeBytes = new byte[4];
        raf.readFully(typeBytes);
        String type = new String(typeBytes, StandardCharsets.US_ASCII);
        long headerBytes = 8;
        if (size == 1) {
            if (containerEnd - offset < 16) {
                return null;
            }
            size = raf.readLong();
            headerBytes = 16;
        } else if (size == 0) {
            size = containerEnd - offset;
        }
        if (size < headerBytes || size > containerEnd - offset) {
            return null;
        }
        return new Mp4Box(type, offset + headerBytes, offset + size);
    }

    private static Mp4MediaHeader readMdhd(
            RandomAccessFile raf, long payloadStart, long boxEnd) throws java.io.IOException {
        raf.seek(payloadStart);
        int version = raf.readUnsignedByte();
        raf.skipBytes(3);
        if (version == 0 && boxEnd - raf.getFilePointer() >= 16) {
            raf.skipBytes(8);
            int timescale = positiveInt(raf.readInt());
            long duration = Integer.toUnsignedLong(raf.readInt());
            return validMediaHeader(timescale, duration);
        }
        if (version == 1 && boxEnd - raf.getFilePointer() >= 28) {
            raf.skipBytes(16);
            int timescale = positiveInt(raf.readInt());
            long duration = raf.readLong();
            return duration < 0 ? null : validMediaHeader(timescale, duration);
        }
        return null;
    }

    private static Mp4MediaHeader validMediaHeader(int timescale, long duration) {
        return timescale > 0 && duration > 0 ? new Mp4MediaHeader(timescale, duration) : null;
    }

    private static int positiveInt(int value) {
        return value > 0 ? value : -1;
    }

    private static boolean hasAscii(byte[] bytes, int offset, String value) {
        if (offset < 0 || offset + value.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + Integer.BYTES > bytes.length) {
            return -1;
        }
        return bytes[offset] & 0xff
                | (bytes[offset + 1] & 0xff) << 8
                | (bytes[offset + 2] & 0xff) << 16
                | (bytes[offset + 3] & 0xff) << 24;
    }

    private static long littleEndianLong(byte[] bytes, int offset) {
        if (offset < 0 || offset + Long.BYTES > bytes.length) {
            return -1;
        }
        long result = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            result |= (long) (bytes[offset + index] & 0xff) << (index * 8);
        }
        return result;
    }

    private static final Set<String> MP4_AUDIO_SAMPLE_ENTRIES = Set.of(
            "mp4a", "enca", "alac", "fLaC", "Opus", "ac-3", "ec-3", "lpcm", "sowt", "twos"
    );

    private record Mp4Box(String type, long payloadStart, long end) {
    }

    private record Mp4MediaHeader(int timescale, long duration) {
    }

    private record Mp4AudioMetadata(int timescale, long duration, int sampleRate) {
    }

    private record OggStreamInfo(int sampleRate, int streamSerial) {
    }

    private record OggPage(long granulePosition, int streamSerial) {
    }
}
