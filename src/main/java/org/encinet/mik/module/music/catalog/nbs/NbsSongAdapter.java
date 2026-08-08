package org.encinet.mik.module.music.catalog.nbs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Converts NoteBlockLib's mutable format model into MIK's validated model. */
final class NbsSongAdapter {

    private static final int MAX_LAYERS = 4096;
    private static final int MAX_NOTES = 200_000;
    private static final int MAX_TICK = 10_000_000;

    private NbsSongAdapter() {
    }

    static NbsSong adapt(
            net.raphimc.noteblocklib.format.nbs.model.NbsSong source)
            throws IOException {
        int version = source.getVersion();
        if (version < 0 || version > 6) {
            throw new IOException("Unsupported NBS version: " + version);
        }

        int vanillaInstrumentCount = source.getVanillaInstrumentCount();
        if (vanillaInstrumentCount < 1
                || vanillaInstrumentCount > NbsInstruments.VANILLA_INSTRUMENT_COUNT) {
            throw new IOException("NBS vanilla instrument count is out of range: "
                    + vanillaInstrumentCount);
        }

        int layerCount = Short.toUnsignedInt(source.getLayerCount());
        if (layerCount > MAX_LAYERS) {
            throw new IOException("NBS layer count is out of range: " + layerCount);
        }

        int tempoHundredths = Short.toUnsignedInt(source.getTempo());
        if (tempoHundredths == 0) {
            throw new IOException("NBS tempo must be at least 0.01 ticks per second");
        }

        List<NbsCustomInstrument> customInstruments = adaptCustomInstruments(source);
        List<NbsLayer> layers = adaptLayers(source, layerCount);
        List<NbsNote> notes = adaptNotes(source, layers, customInstruments,
                vanillaInstrumentCount,
                layers.stream().anyMatch(NbsLayer::solo));

        int lastTick = notes.stream().mapToInt(NbsNote::tick).max().orElse(-1);
        int declaredLengthTicks = version == 0 || version >= 3
                ? Short.toUnsignedInt(source.getLength()) : 0;
        int lengthTicks = Math.max(1, Math.max(declaredLengthTicks, lastTick + 1));
        if (lengthTicks > MAX_TICK) {
            throw new IOException("NBS song length exceeds the supported limit");
        }

        int loopStartTick = version >= 4
                ? Short.toUnsignedInt(source.getLoopStartTick()) : 0;
        boolean loopEnabled = version >= 4 && source.isLoop()
                && loopStartTick < lengthTicks;
        if (!loopEnabled) {
            loopStartTick = 0;
        }

        NbsFileMetadata metadata = new NbsFileMetadata(
                vanillaInstrumentCount,
                declaredLengthTicks,
                source.isAutoSave() ? 1 : 0,
                source.getAutoSaveInterval(),
                source.getTimeSignature(),
                source.getMinutesSpent(),
                source.getLeftClicks(),
                source.getRightClicks(),
                source.getNoteBlocksAdded(),
                source.getNoteBlocksRemoved(),
                decodeString(source.getSourceFileName()));

        try {
            return new NbsSong(
                    version,
                    decodeString(source.getTitle()),
                    decodeString(source.getAuthor()),
                    decodeString(source.getOriginalAuthor()),
                    decodeString(source.getDescription()),
                    tempoHundredths / 100.0,
                    lengthTicks,
                    loopEnabled,
                    loopEnabled ? source.getMaxLoopCount() : 0,
                    loopStartTick,
                    notes,
                    metadata,
                    layers,
                    customInstruments);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid NBS data: " + messageOf(exception), exception);
        }
    }

    private static List<NbsLayer> adaptLayers(
            net.raphimc.noteblocklib.format.nbs.model.NbsSong source,
            int layerCount) throws IOException {
        List<NbsLayer> layers = new ArrayList<>(layerCount);
        for (int index = 0; index < layerCount; index++) {
            layers.add(NbsLayer.defaults());
        }

        for (var entry : source.getLayers().entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= layerCount) {
                throw new IOException("NBS note layer is out of range: " + index);
            }
            var layer = entry.getValue();
            int volume = layer.getVolume();
            int encodedPanning = layer.getPanning();
            if (volume > 100 || encodedPanning > 200) {
                throw new IOException("NBS layer volume or panning is out of range");
            }
            int lockState = switch (layer.getStatus()) {
                case NONE -> 0;
                case LOCKED -> 1;
                case SOLO -> 2;
            };
            layers.set(index, new NbsLayer(decodeString(layer.getName()),
                    lockState, volume, encodedPanning - 100));
        }
        return List.copyOf(layers);
    }

    private static List<NbsCustomInstrument> adaptCustomInstruments(
            net.raphimc.noteblocklib.format.nbs.model.NbsSong source)
            throws IOException {
        List<NbsCustomInstrument> instruments = new ArrayList<>(
                source.getCustomInstruments().size());
        for (var instrument : source.getCustomInstruments()) {
            if (instrument.getPitch() > 87) {
                throw new IOException("NBS custom instrument key is out of range: "
                        + instrument.getPitch());
            }
            instruments.add(new NbsCustomInstrument(
                    decodeString(instrument.getName()),
                    decodeString(instrument.getSoundFilePath()),
                    instrument.getPitch(),
                    instrument.isPressKey() ? 1 : 0));
        }
        return List.copyOf(instruments);
    }

    private static List<NbsNote> adaptNotes(
            net.raphimc.noteblocklib.format.nbs.model.NbsSong source,
            List<NbsLayer> layers,
            List<NbsCustomInstrument> customInstruments,
            int vanillaInstrumentCount,
            boolean hasSoloLayers) throws IOException {
        List<NbsNote> notes = new ArrayList<>();
        for (var layerEntry : source.getLayers().entrySet()) {
            int layerIndex = layerEntry.getKey();
            if (layerIndex < 0 || layerIndex >= layers.size()) {
                throw new IOException("NBS note layer is out of range: " + layerIndex);
            }
            NbsLayer layer = layers.get(layerIndex);
            for (var noteEntry : layerEntry.getValue().getNotes().entrySet()) {
                int tick = noteEntry.getKey();
                if (tick < 0 || tick > MAX_TICK) {
                    throw new IOException("NBS note tick is out of range: " + tick);
                }
                var sourceNote = noteEntry.getValue();
                if (sourceNote.getKey() > 87) {
                    throw new IOException("NBS note key is out of range: "
                            + sourceNote.getKey());
                }

                int sourceInstrument = sourceNote.getInstrument();
                NbsNoteType type = NbsInstruments.noteType(source.getVersion(),
                        sourceInstrument, vanillaInstrumentCount, customInstruments);
                int encodedPanning = sourceNote.getPanning();
                if (type == NbsNoteType.SOUND
                        && (sourceNote.getVelocity() > 100 || encodedPanning > 200)) {
                    throw new IOException(
                            "NBS note velocity or panning is out of range");
                }
                int notePanning = encodedPanning - 100;
                int playbackPanning = type == NbsNoteType.SOUND
                        ? combinePanning(notePanning, layer.panning()) : 0;
                int playbackLayerVolume = type == NbsNoteType.SOUND
                        && (layer.locked() || hasSoloLayers && !layer.solo())
                        ? 0 : layer.volume();
                notes.add(new NbsNote(
                        tick,
                        NbsInstruments.resolve(sourceInstrument,
                                vanillaInstrumentCount, customInstruments),
                        sourceNote.getKey(),
                        sourceNote.getVelocity(),
                        playbackLayerVolume,
                        playbackPanning,
                        sourceNote.getPitch(),
                        layerIndex,
                        sourceInstrument,
                        notePanning,
                        NbsInstruments.instrumentKey(sourceInstrument,
                                vanillaInstrumentCount, customInstruments),
                        type));
                if (notes.size() > MAX_NOTES) {
                    throw new IOException("NBS file contains too many notes");
                }
            }
        }
        return List.copyOf(notes);
    }

    private static int combinePanning(int notePanning, int layerPanning) {
        return layerPanning == 0
                ? notePanning : (notePanning + layerPanning) / 2;
    }

    /**
     * NoteBlockLib 3.x exposes NBS strings as one character per stored byte.
     * Decode valid UTF-8 while retaining legacy single-byte strings unchanged.
     */
    private static String decodeString(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        byte[] bytes = new byte[value.length()];
        boolean containsNonAscii = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character > 255) {
                return value;
            }
            bytes[index] = (byte) character;
            containsNonAscii |= character > 127;
        }
        if (!containsNonAscii) {
            return value;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return value;
        }
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName() : message;
    }
}
