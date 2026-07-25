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
        List<NbsNote> notes
) {
    public static final double MAX_TICKS_PER_SECOND = 655.35;

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
        List<NbsNote> sortedNotes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
        for (NbsNote note : sortedNotes) {
            Objects.requireNonNull(note, "notes must not contain null");
            if (note.tick() >= lengthTicks) {
                throw new IllegalArgumentException("note tick must be within the song length");
            }
        }
        sortedNotes.sort(Comparator.comparingInt(NbsNote::tick));
        notes = List.copyOf(sortedNotes);
    }

    public String displayAuthor() {
        return author != null ? author : originalAuthor;
    }

    public Duration duration() {
        long seconds = Math.max(1L, (long) Math.ceil(lengthTicks / ticksPerSecond));
        return Duration.ofSeconds(seconds);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
