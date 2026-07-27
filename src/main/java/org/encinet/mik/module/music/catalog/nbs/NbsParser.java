package org.encinet.mik.module.music.catalog.nbs;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.Charset;
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
    private static final int MAX_TICK = 10_000_000;
    private static final int MAX_SUPPORTED_VERSION = 6;
    private static final Charset LEGACY_STRING_CHARSET = StandardCharsets.ISO_8859_1;

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

        int autoSaveFlag = data.unsignedByte();
        int autoSaveIntervalMinutes = data.unsignedByte();
        int timeSignature = data.unsignedByte();
        int minutesSpent = data.signedInt();
        int leftClicks = data.signedInt();
        int rightClicks = data.signedInt();
        int notesAdded = data.signedInt();
        int notesRemoved = data.signedInt();
        String importedFileName = data.string();

        boolean loopEnabled = false;
        int maxLoopCount = 0;
        int loopStartTick = 0;
        if (version >= 4) {
            loopEnabled = data.unsignedByte() != 0;
            maxLoopCount = data.unsignedByte();
            loopStartTick = data.unsignedShort();
        }

        List<RawNote> rawNotes = readNotes(data, version, layerCount);
        List<NbsLayer> layers = data.hasRemaining()
                ? readLayers(data, version, layerCount) : defaultLayers(layerCount);
        List<NbsCustomInstrument> customInstruments = data.hasRemaining()
                ? readCustomInstruments(data) : List.of();
        if (data.hasRemaining()) {
            throw new IOException("Unexpected data after the NBS custom instrument section");
        }

        List<NbsNote> notes = new ArrayList<>(rawNotes.size());
        int lastTick = 0;
        for (RawNote raw : rawNotes) {
            NbsLayer layer = layers.get(raw.layer());
            int panning = combinePanning(raw.panning(), layer.panning());
            notes.add(new NbsNote(raw.tick(), NbsInstruments.resolve(raw.instrument(),
                            vanillaInstrumentCount, customInstruments),
                    raw.key(), raw.velocity(), layer.volume(), panning, raw.finePitch(),
                    raw.layer(), raw.instrument(), raw.panning(),
                    NbsInstruments.instrumentKey(raw.instrument(),
                            vanillaInstrumentCount, customInstruments),
                    NbsInstruments.noteType(raw.instrument(),
                            vanillaInstrumentCount, customInstruments)));
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
        NbsFileMetadata fileMetadata = new NbsFileMetadata(
                vanillaInstrumentCount, declaredLength, autoSaveFlag,
                autoSaveIntervalMinutes, timeSignature, minutesSpent,
                leftClicks, rightClicks, notesAdded, notesRemoved, importedFileName);
        return new NbsSong(version, title, author, originalAuthor, description,
                tempo / 100.0, lengthTicks, loopEnabled, maxLoopCount,
                loopStartTick, notes, fileMetadata, layers, customInstruments);
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
                if (notes.size() > MAX_NOTES) {
                    throw new IOException("NBS file contains too many notes");
                }
            }
        }
        return notes;
    }

    private static List<NbsLayer> readLayers(
            LittleEndianInput data, int version, int layerCount)
            throws IOException {
        List<NbsLayer> layers = new ArrayList<>(layerCount);
        for (int index = 0; index < layerCount; index++) {
            String name = data.string();
            int lockState = version >= 4 ? data.unsignedByte() : 0;
            int volume = data.unsignedByte();
            int panning = version >= 2 ? data.unsignedByte() - 100 : 0;
            if (volume > 100 || panning < -100 || panning > 100) {
                throw new IOException("NBS layer volume or panning is out of range");
            }
            layers.add(new NbsLayer(name, lockState, volume, panning));
        }
        return List.copyOf(layers);
    }

    private static List<NbsCustomInstrument> readCustomInstruments(
            LittleEndianInput data) throws IOException {
        int count = data.unsignedByte();
        List<NbsCustomInstrument> instruments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String name = data.string();
            String fileName = data.string();
            int key = data.unsignedByte();
            int pressKeyFlag = data.unsignedByte();
            instruments.add(new NbsCustomInstrument(name, fileName, key, pressKeyFlag));
        }
        return List.copyOf(instruments);
    }

    private static List<NbsLayer> defaultLayers(int layerCount) {
        List<NbsLayer> layers = new ArrayList<>(layerCount);
        for (int index = 0; index < layerCount; index++) {
            layers.add(NbsLayer.defaults());
        }
        return List.copyOf(layers);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int combinePanning(int notePanning, int layerPanning) {
        return layerPanning == 0 ? notePanning : (notePanning + layerPanning) / 2;
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

    private static final class LittleEndianInput {
        private final InputStream input;
        private int pendingByte = -1;

        private LittleEndianInput(InputStream input) {
            this.input = input;
        }

        private int unsignedByte() throws IOException {
            int value;
            if (pendingByte >= 0) {
                value = pendingByte;
                pendingByte = -1;
            } else {
                value = input.read();
            }
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
            return decodeString(bytes);
        }

        private boolean hasRemaining() throws IOException {
            if (pendingByte >= 0) {
                return true;
            }
            pendingByte = input.read();
            return pendingByte >= 0;
        }

        private static String decodeString(byte[] bytes) {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException ignored) {
                return LEGACY_STRING_CHARSET.decode(ByteBuffer.wrap(bytes)).toString();
            }
        }
    }
}
