package org.encinet.mik.module.musicdisc;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parser for audio file metadata (sample rate, duration, file size)
 */
public class AudioMetadataParser {

    /**
     * Get formatted file size
     */
    public String getFileSize(Path filePath) {
        try {
            long bytes = Files.size(filePath);

            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get audio duration in formatted string (M:SS)
     */
    public String getDuration(Path filePath, String extension) {
        try {
            int durationSeconds = switch (extension.toLowerCase()) {
                case "mp3" -> getMp3Duration(filePath);
                case "flac" -> getFlacDuration(filePath);
                case "ogg", "opus" -> getOggDuration(filePath);
                case "m4a", "aac" -> getM4aDuration(filePath);
                default -> getWavDuration(filePath);
            };

            if (durationSeconds > 0) {
                int minutes = durationSeconds / 60;
                int seconds = durationSeconds % 60;
                return String.format("%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Get audio sample rate in formatted string
     */
    public String getSampleRate(Path filePath, String extension) {
        try {
            int sampleRate = switch (extension.toLowerCase()) {
                case "mp3" -> getMp3SampleRate(filePath);
                case "flac" -> getFlacSampleRate(filePath);
                case "ogg", "opus" -> getOggSampleRate(filePath);
                case "m4a", "aac" -> getM4aSampleRate(filePath);
                default -> getWavSampleRate(filePath);
            };

            if (sampleRate > 0) {
                if (sampleRate >= 1000) {
                    return String.format("%.1f kHz", sampleRate / 1000.0);
                } else {
                    return String.format("%d Hz", sampleRate);
                }
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
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            long fileSize = raf.length();
            raf.seek(Math.max(0, fileSize - 65536));

            byte[] buffer = new byte[65536];
            int bytesRead = raf.read(buffer);

            for (int i = bytesRead - 4; i >= 0; i--) {
                if (buffer[i] == 'O' && buffer[i+1] == 'g' && buffer[i+2] == 'g' && buffer[i+3] == 'S') {
                    long granulePos = ((long)(buffer[i+6] & 0xFF)) |
                                     ((long)(buffer[i+7] & 0xFF) << 8) |
                                     ((long)(buffer[i+8] & 0xFF) << 16) |
                                     ((long)(buffer[i+9] & 0xFF) << 24) |
                                     ((long)(buffer[i+10] & 0xFF) << 32) |
                                     ((long)(buffer[i+11] & 0xFF) << 40) |
                                     ((long)(buffer[i+12] & 0xFF) << 48) |
                                     ((long)(buffer[i+13] & 0xFF) << 56);

                    int sampleRate = getOggSampleRate(filePath);
                    if (sampleRate > 0 && granulePos > 0) {
                        return (int) (granulePos / sampleRate);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getOggSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] header = new byte[58];
            raf.read(header);

            if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') {
                int segments = header[26] & 0xFF;
                raf.skipBytes(segments);

                byte[] packet = new byte[19];
                raf.read(packet);

                if (packet[0] == 'O' && packet[1] == 'p' && packet[2] == 'u' && packet[3] == 's') {
                    return 48000;
                }

                if (packet[1] == 'v' && packet[2] == 'o' && packet[3] == 'r' &&
                    packet[4] == 'b' && packet[5] == 'i' && packet[6] == 's') {
                    return ((packet[12] & 0xFF) | ((packet[13] & 0xFF) << 8) |
                           ((packet[14] & 0xFF) << 16) | ((packet[15] & 0xFF) << 24));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getM4aDuration(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[8];
            int sampleRate = -1;
            long duration = -1;

            while (raf.getFilePointer() < raf.length() - 8) {
                raf.read(buffer);
                int boxSize = ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
                             ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                String boxType = new String(buffer, 4, 4);

                if ("moov".equals(boxType) || "trak".equals(boxType) || "mdia".equals(boxType)) {
                    continue;
                }

                if ("mdhd".equals(boxType)) {
                    byte[] mdhd = new byte[24];
                    raf.read(mdhd);
                    int version = mdhd[0];

                    if (version == 0) {
                        sampleRate = ((mdhd[12] & 0xFF) << 24) | ((mdhd[13] & 0xFF) << 16) |
                                    ((mdhd[14] & 0xFF) << 8) | (mdhd[15] & 0xFF);
                        duration = ((long) (mdhd[16] & 0xFF) << 24) | ((mdhd[17] & 0xFF) << 16) |
                                  ((mdhd[18] & 0xFF) << 8) | (mdhd[19] & 0xFF);
                    }

                    if (sampleRate > 0 && duration > 0) {
                        return (int) (duration / sampleRate);
                    }
                }

                if (boxSize > 8) {
                    raf.skipBytes(boxSize - 8);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getM4aSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[8];

            while (raf.getFilePointer() < raf.length() - 8) {
                raf.read(buffer);
                int boxSize = ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
                             ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                String boxType = new String(buffer, 4, 4);

                if ("moov".equals(boxType) || "trak".equals(boxType) || "mdia".equals(boxType)) {
                    continue;
                }

                if ("mdhd".equals(boxType)) {
                    raf.skipBytes(4);
                    raf.read(buffer);
                    return ((buffer[4] & 0xFF) << 24) | ((buffer[5] & 0xFF) << 16) |
                           ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);
                }

                if (boxSize > 8) {
                    raf.skipBytes(boxSize - 8);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }
}
