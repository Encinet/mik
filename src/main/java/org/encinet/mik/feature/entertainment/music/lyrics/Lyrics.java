package org.encinet.mik.feature.entertainment.music.lyrics;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** A revision fixed to one playback representation. Each language has its own intervals or plain text. */
public record Lyrics(String mediaVersion, String revision, Integer defaultOriginal, List<Track> tracks) {
    public Lyrics {
        tracks = List.copyOf(tracks);
    }

    public enum Role { ORIGINAL, TRANSLATION, PRONUNCIATION }

    public record Cue(long startMs, Long endMs, String text) {
        public Cue {
            if (startMs < 0 || endMs != null && endMs <= startMs || text == null || text.isBlank()) {
                throw new IllegalArgumentException("Invalid lyric interval");
            }
        }
    }

    public record Track(int key, Role role, String language, List<Cue> cues, List<String> lines) {
        public Track {
            cues = List.copyOf(cues);
            lines = List.copyOf(lines);
            if (cues.isEmpty() == lines.isEmpty()) throw new IllegalArgumentException("Expected timed cues or plain text");
            long boundary = 0;
            for (Cue cue : cues) {
                if (cue.startMs() < boundary) throw new IllegalArgumentException("Overlapping lyric intervals");
                boundary = cue.endMs() == null ? Long.MAX_VALUE : cue.endMs();
            }
        }

        public Optional<String> textAt(long position) {
            int low = 0;
            int high = cues.size();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (cues.get(mid).startMs() <= position) low = mid + 1;
                else high = mid;
            }
            if (low == 0) return Optional.empty();
            var cue = cues.get(low - 1);
            return cue.endMs() == null || position < cue.endMs() ? Optional.of(cue.text()) : Optional.empty();
        }

        public List<String> transcription() {
            return lines.isEmpty() ? cues.stream().map(Cue::text).toList() : lines;
        }
    }

    public Optional<Track> choose(Role role, List<Locale.LanguageRange> preference) {
        var choices = tracks.stream().filter(track -> track.role() == role).toList();
        var tags = choices.stream().map(Track::language).filter(java.util.Objects::nonNull).toList();
        String matched = Locale.lookupTag(preference, tags);
        if (matched != null) return choices.stream().filter(track -> matched.equalsIgnoreCase(track.language())).findFirst();
        return role == Role.ORIGINAL && defaultOriginal != null
                ? choices.stream().filter(track -> defaultOriginal == track.key()).findFirst() : Optional.empty();
    }
}
