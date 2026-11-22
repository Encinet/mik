package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Extracts exact timing and musical accents from normalized note events. */
public final class NoteRhythmExtractor implements RhythmExtractor<NoteScore> {
    public static final NoteRhythmExtractor INSTANCE = new NoteRhythmExtractor();

    private static final long RAW_REFRACTORY_MILLIS = 120L;

    private NoteRhythmExtractor() {
    }

    @Override
    public void extract(NoteScore song, RhythmExtractionSink output) {
        Objects.requireNonNull(song, "song");
        Objects.requireNonNull(output, "output");
        List<Candidate> salient = retainSalient(candidates(song));
        List<RhythmPulse> pulses = salient.stream()
                .map(candidate -> new RhythmPulse(candidate.timeMillis(),
                        candidate.strength(), candidate.stereoBalance(),
                        candidate.toneBalance(), candidate.signature()))
                .toList();

        output.publish(RhythmSource.NOTE_EVENTS, pulses, Math.max(1L, millis(song.getDurationUs())),
                song.hasLoop() ? millis(song.getLoop().getStartUs()) : -1L,
                song.hasLoop() ? song.getLoop().getRepeats() : -1);
    }

    private static long millis(long us) { return (us + 500) / 1000; }

    private static List<Candidate> candidates(NoteScore song) {
        var result = new ArrayList<Candidate>();
        var notes = song.getEventsList();
        int index = 0;
        while (index < notes.size()) {
            long time = notes.get(index).getTimeUs();
            int end = index + 1;
            while (end < notes.size() && notes.get(end).getTimeUs() == time) end++;
            var candidate = candidateAtTick(notes, index, end, millis(time), time);
            if (candidate != null) result.add(candidate);
            index = end;
        }
        return result;
    }

    private static Candidate candidateAtTick(List<NoteEvent> notes, int from, int to,
                                              long timeMillis, long tick) {
        double loudest = 0.0;
        double totalPower = 0.0;
        double weightedPan = 0.0;
        double weightedTone = 0.0;
        int audibleNotes = 0;
        long signature = tick * 0x9E3779B97F4A7C15L;
        for (int index = from; index < to; index++) {
            var event = notes.get(index);
            if (!event.hasSound()) continue;
            var note = event.getSound();
            if (note.getGain() <= 0) continue;
            double amplitude = note.getGain() / 10_000.0;
            double power = amplitude * amplitude;
            loudest = Math.max(loudest, amplitude);
            totalPower += power;
            weightedPan += note.getPan() / 100.0 * power;
            weightedTone += Math.clamp(note.getPitchCents() / 2_400.0,
                    -1.0, 1.0) * power;
            audibleNotes++;
            signature = Long.rotateLeft(signature, 11)
                    ^ (note.getTone().hashCode() * 131L + note.getPitchCents() * 31L
                    + note.getVoice() * 17L + note.getGain());
        }
        if (audibleNotes == 0) return null;
        double chordPower = 1.0 - Math.exp(-totalPower);
        double chordAccent = Math.min(1.0, audibleNotes / 4.0);
        double strength = Math.clamp(0.15 + loudest * 0.55
                + chordPower * 0.25 + chordAccent * 0.05, 0.12, 1.0);
        return new Candidate(timeMillis, strength,
                weightedPan / totalPower, weightedTone / totalPower, signature);
    }

    /** Salience-first non-maximum suppression avoids choosing a weak grace note first. */
    private static List<Candidate> retainSalient(List<Candidate> candidates) {
        List<Candidate> priority = new ArrayList<>(candidates);
        priority.sort(Comparator.comparingDouble(Candidate::strength).reversed()
                .thenComparingLong(Candidate::timeMillis));
        java.util.NavigableSet<Long> selectedTimes = new java.util.TreeSet<>();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : priority) {
            Long before = selectedTimes.floor(candidate.timeMillis());
            if (before != null && candidate.timeMillis() - before
                    < RAW_REFRACTORY_MILLIS) continue;
            Long after = selectedTimes.ceiling(candidate.timeMillis());
            if (after != null && after - candidate.timeMillis()
                    < RAW_REFRACTORY_MILLIS) continue;
            selectedTimes.add(candidate.timeMillis());
            selected.add(candidate);
        }
        selected.sort(Comparator.comparingLong(Candidate::timeMillis));
        return List.copyOf(selected);
    }

    private record Candidate(long timeMillis, double strength,
                             double stereoBalance, double toneBalance,
                             long signature) {
    }

}
