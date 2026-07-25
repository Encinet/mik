package org.encinet.mik.module.music.catalog.nbs;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parses legacy and modern Open Note Block Studio files without loading custom samples. */
public final class NbsParser {

    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 64 * 1024;
    private static final int MAX_LAYERS = 4096;
    private static final int MAX_NOTES = 200_000;
    private static final int MAX_NOTES_PER_TICK = 256;
    private static final int MAX_TICK = 10_000_000;
    private static final int MAX_SUPPORTED_VERSION = 5;

    public NbsSong parse(Path path) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > MAX_FILE_BYTES) {
            throw new IOException("NBS file size is out of range");
        }
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return parse(input);
        }
    }

    NbsSong parse(InputStream input) throws IOException {
        LittleEndianInput data = new LittleEndianInput(input);
        int firstLength = data.unsignedShort();
        int version;
        int vanillaInstrumentCount;
        int declaredLength;
        if (firstLength == 0) {
            version = data.unsignedByte();
            if (version < 1 || version > MAX_SUPPORTED_VERSION) {
                throw new IOException("Unsupported NBS version: " + version);
            }
            vanillaInstrumentCount = data.unsignedByte();
            if (vanillaInstrumentCount < 1
                    || vanillaInstrumentCount > NbsInstruments.VANILLA_INSTRUMENT_COUNT) {
                throw new IOException("NBS vanilla instrument count is out of range: "
                        + vanillaInstrumentCount);
            }
            declaredLength = version >= 3 ? data.unsignedShort() : 0;
        } else {
            version = 0;
            vanillaInstrumentCount = 10;
            declaredLength = firstLength;
        }

        int layerCount = data.unsignedShort();
        if (layerCount < 1 || layerCount > MAX_LAYERS) {
            throw new IOException("NBS layer count is out of range: " + layerCount);
        }

        String title = data.string();
        String author = data.string();
        String originalAuthor = data.string();
        String description = data.string();
        int tempo = data.unsignedShort();
        if (tempo == 0) {
            throw new IOException("NBS tempo must be at least 0.01 ticks per second");
        }

        data.skipFully(3);
        data.skipFully(5L * Integer.BYTES);
        data.string();

        boolean loopEnabled = false;
        int maxLoopCount = 0;
        int loopStartTick = 0;
        if (version >= 4) {
            loopEnabled = data.unsignedByte() != 0;
            maxLoopCount = data.unsignedByte();
            loopStartTick = data.unsignedShort();
        }

        List<RawNote> rawNotes = readNotes(data, version, layerCount);
        List<Layer> layers = readLayers(data, version, layerCount);
        List<NbsInstruments.CustomInstrument> customInstruments = readCustomInstruments(data);

        List<NbsNote> notes = new ArrayList<>(rawNotes.size());
        int lastTick = 0;
        for (RawNote raw : rawNotes) {
            Layer layer = layers.get(raw.layer());
            int panning = clamp(raw.panning() + layer.panning(), -100, 100);
            notes.add(new NbsNote(raw.tick(), NbsInstruments.resolve(raw.instrument(),
                            vanillaInstrumentCount, customInstruments),
                    raw.key(), raw.velocity(), layer.volume(), panning, raw.finePitch()));
            lastTick = Math.max(lastTick, raw.tick());
        }

        int lengthTicks = Math.max(Math.max(1, declaredLength), lastTick + 1);
        if (lengthTicks > MAX_TICK) {
            throw new IOException("NBS song length exceeds the supported limit");
        }
        if (loopStartTick >= lengthTicks) {
            loopEnabled = false;
            loopStartTick = 0;
        }
        return new NbsSong(version, title, author, originalAuthor, description,
                tempo / 100.0, lengthTicks, loopEnabled, maxLoopCount,
                loopStartTick, notes);
    }

    private static List<RawNote> readNotes(LittleEndianInput data, int version, int layerCount)
            throws IOException {
        List<RawNote> notes = new ArrayList<>();
        int tick = -1;
        while (true) {
            int tickJump = data.unsignedShort();
            if (tickJump == 0) {
                break;
            }
            tick = addJump(tick, tickJump, "tick");
            if (tick < 0 || tick > MAX_TICK) {
                throw new IOException("NBS note tick is out of range");
            }

            int layer = -1;
            int notesThisTick = 0;
            while (true) {
                int layerJump = data.unsignedShort();
                if (layerJump == 0) {
                    break;
                }
                layer = addJump(layer, layerJump, "layer");
                if (layer < 0 || layer >= layerCount) {
                    throw new IOException("NBS note layer is out of range");
                }
                int instrument = data.unsignedByte();
                int key = data.unsignedByte();
                if (key > 87) {
                    throw new IOException("NBS note key is out of range: " + key);
                }
                int velocity = 100;
                int panning = 0;
                int finePitch = 0;
                if (version >= 4) {
                    velocity = data.unsignedByte();
                    panning = data.unsignedByte() - 100;
                    finePitch = data.signedShort();
                    if (velocity > 100 || panning < -100 || panning > 100) {
                        throw new IOException("NBS note velocity or panning is out of range");
                    }
                }
                notes.add(new RawNote(tick, layer, instrument, key,
                        velocity, panning, finePitch));
                if (++notesThisTick > MAX_NOTES_PER_TICK) {
                    throw new IOException("NBS tick contains too many simultaneous notes");
                }
                if (notes.size() > MAX_NOTES) {
                    throw new IOException("NBS file contains too many notes");
                }
            }
        }
        return notes;
    }

    private static List<Layer> readLayers(LittleEndianInput data, int version, int layerCount)
            throws IOException {
        List<Layer> layers = new ArrayList<>(layerCount);
        for (int index = 0; index < layerCount; index++) {
            data.string();
            if (version >= 4) {
                data.unsignedByte();
            }
            int volume = data.unsignedByte();
            int panning = version >= 2 ? data.unsignedByte() - 100 : 0;
            if (volume > 100 || panning < -100 || panning > 100) {
                throw new IOException("NBS layer volume or panning is out of range");
            }
            layers.add(new Layer(volume, panning));
        }
        return layers;
    }

    private static List<NbsInstruments.CustomInstrument> readCustomInstruments(
            LittleEndianInput data) throws IOException {
        int count = data.unsignedByte();
        List<NbsInstruments.CustomInstrument> instruments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String name = data.string();
            String fileName = data.string();
            data.unsignedByte();
            data.unsignedByte();
            instruments.add(new NbsInstruments.CustomInstrument(name, fileName));
        }
        return List.copyOf(instruments);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int addJump(int current, int jump, String field) throws IOException {
        try {
            return Math.addExact(current, jump);
        } catch (ArithmeticException exception) {
            throw new IOException("NBS " + field + " jump overflow", exception);
        }
    }

    private record RawNote(int tick, int layer, int instrument, int key,
                           int velocity, int panning, int finePitch) {
    }

    private record Layer(int volume, int panning) {
    }

    private static final class LittleEndianInput {
        private final InputStream input;

        private LittleEndianInput(InputStream input) {
            this.input = input;
        }

        private int unsignedByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("Unexpected end of NBS file");
            }
            return value;
        }

        private int unsignedShort() throws IOException {
            return unsignedByte() | unsignedByte() << 8;
        }

        private int signedShort() throws IOException {
            int value = unsignedShort();
            return value > Short.MAX_VALUE ? value - 65536 : value;
        }

        private int signedInt() throws IOException {
            return unsignedByte()
                    | unsignedByte() << 8
                    | unsignedByte() << 16
                    | unsignedByte() << 24;
        }

        private String string() throws IOException {
            int length = signedInt();
            if (length < 0 || length > MAX_STRING_BYTES) {
                throw new IOException("NBS string length is out of range: " + length);
            }
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length) {
                throw new EOFException("Unexpected end of NBS string");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private void skipFully(long bytes) throws IOException {
            long remaining = bytes;
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                    continue;
                }
                if (input.read() < 0) {
                    throw new EOFException("Unexpected end of NBS file");
                }
                remaining--;
            }
        }
    }
}
