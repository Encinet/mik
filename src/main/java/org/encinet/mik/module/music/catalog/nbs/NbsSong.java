package org.encinet.mik.module.music.catalog.nbs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.time.Duration;

public record NbsSong(
        int version,
        String title,
        String author,
        String originalAuthor,
        String description,
        double ticksPerSecond,
        int lengthTicks,
        boolean loopEnabled,
        int maxLoopCount,
        int loopStartTick,
        List<NbsNote> notes,
        NbsFileMetadata fileMetadata,
        List<NbsLayer> layers,
        List<NbsCustomInstrument> customInstruments
) {
    public static final double MAX_TICKS_PER_SECOND = 655.35;

    public NbsSong(int version, String title, String author, String originalAuthor,
                   String description, double ticksPerSecond, int lengthTicks,
                   boolean loopEnabled, int maxLoopCount, int loopStartTick,
                   List<NbsNote> notes) {
        this(version, title, author, originalAuthor, description, ticksPerSecond,
                lengthTicks, loopEnabled, maxLoopCount, loopStartTick, notes,
                NbsFileMetadata.defaults(version, lengthTicks),
                List.of(NbsLayer.defaults()), List.of());
    }

    public NbsSong {
        title = normalize(title);
        author = normalize(author);
        originalAuthor = normalize(originalAuthor);
        description = normalize(description);
        if (!Double.isFinite(ticksPerSecond) || ticksPerSecond <= 0
                || ticksPerSecond > MAX_TICKS_PER_SECOND) {
            throw new IllegalArgumentException("ticksPerSecond is out of range");
        }
        if (lengthTicks < 1) {
            throw new IllegalArgumentException("lengthTicks must be positive");
        }
        if (maxLoopCount < 0 || maxLoopCount > 255) {
            throw new IllegalArgumentException("maxLoopCount is out of range");
        }
        if (loopStartTick < 0 || loopStartTick >= lengthTicks) {
            throw new IllegalArgumentException("loopStartTick is out of range");
        }
        fileMetadata = Objects.requireNonNull(fileMetadata, "fileMetadata");
        layers = layers == null ? List.of() : List.copyOf(layers);
        customInstruments = customInstruments == null
                ? List.of() : List.copyOf(customInstruments);
        for (NbsLayer layer : layers) {
            Objects.requireNonNull(layer, "layers must not contain null");
        }
        for (NbsCustomInstrument instrument : customInstruments) {
            Objects.requireNonNull(instrument, "customInstruments must not contain null");
        }
        List<NbsNote> sortedNotes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
        for (NbsNote note : sortedNotes) {
            Objects.requireNonNull(note, "notes must not contain null");
            if (note.tick() >= lengthTicks) {
                throw new IllegalArgumentException("note tick must be within the song length");
            }
            if (note.layer() >= layers.size()) {
                throw new IllegalArgumentException("note layer must be within the layer list");
            }
        }
        sortedNotes.sort(Comparator.comparingInt(NbsNote::tick));
        notes = List.copyOf(sortedNotes);
    }

    public String displayAuthor() {
        return author != null ? author : originalAuthor;
    }

    public Duration duration() {
        double seconds = durationSeconds();
        long roundedSeconds = Math.max(1L, (long) Math.ceil(seconds));
        return Duration.ofSeconds(roundedSeconds);
    }

    public double ticksPerSecondBefore(int tick) {
        double tempo = ticksPerSecond;
        for (NbsNote note : notes) {
            if (note.tick() >= tick) {
                break;
            }
            double changedTempo = note.tempoChangeTicksPerSecond();
            if (isPlayableTempo(changedTempo)) {
                tempo = changedTempo;
            }
        }
        return tempo;
    }

    private double durationSeconds() {
        double tempo = ticksPerSecond;
        double seconds = 0;
        int tick = 0;
        for (NbsNote note : notes) {
            if (note.type() != NbsNoteType.TEMPO_CHANGE || note.tick() < tick) {
                continue;
            }
            seconds += (note.tick() - tick) / tempo;
            tick = note.tick();
            double changedTempo = note.tempoChangeTicksPerSecond();
            if (isPlayableTempo(changedTempo)) {
                tempo = changedTempo;
            }
        }
        return seconds + (lengthTicks - tick) / tempo;
    }

    public static boolean isPlayableTempo(double tempo) {
        return Double.isFinite(tempo) && tempo > 0 && tempo <= MAX_TICKS_PER_SECOND;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
