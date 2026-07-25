package org.encinet.mik.module.music.lyrics;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, timestamp-ordered lyrics for one track. */
public final class Lyrics {

    private final List<LyricLine> lines;

    public Lyrics(List<LyricLine> lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lyrics must contain at least one line");
        }
        long previous = -1;
        for (LyricLine line : lines) {
            Objects.requireNonNull(line, "line");
            if (line.timestampMillis() < previous) {
                throw new IllegalArgumentException("lyric lines must be timestamp ordered");
            }
            previous = line.timestampMillis();
        }
        this.lines = List.copyOf(lines);
    }

    public List<LyricLine> lines() {
        return lines;
    }

    public Optional<LyricLine> lineAt(long positionMillis) {
        if (positionMillis < lines.getFirst().timestampMillis()) {
            return Optional.empty();
        }
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timestampMillis() <= positionMillis) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return Optional.of(lines.get(low - 1));
    }
}
