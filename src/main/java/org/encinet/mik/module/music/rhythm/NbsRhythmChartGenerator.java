package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Converts exact NBS note timing and musical accents into a playable raw chart. */
public final class NbsRhythmChartGenerator {
    private static final long RAW_REFRACTORY_MILLIS = 120L;

    private NbsRhythmChartGenerator() {
    }

    public static void populate(NbsSong song, RhythmTimeline timeline) {
        Objects.requireNonNull(song, "song");
        Objects.requireNonNull(timeline, "timeline");
        List<Candidate> candidates = candidates(song);
        List<Candidate> salient = retainSalient(candidates);
        RhythmLaneSequencer lanes = new RhythmLaneSequencer(timeline.seed());
        List<RhythmTimeline.TimedInput> chart = new ArrayList<>(salient.size());
        for (Candidate candidate : salient) {
            chart.add(new RhythmTimeline.TimedInput(candidate.timeMillis(),
                    lanes.next(candidate.signature(), candidate.strength(),
                            candidate.stereoBalance(), candidate.toneBalance()),
                    candidate.strength()));
        }

        Timing timing = timing(song);
        timeline.publishNbs(chart, Math.max(1L, Math.round(timing.durationMillis())),
                song.loopEnabled() ? Math.round(timing.loopStartMillis()) : -1L,
                song.loopEnabled() ? song.maxLoopCount() : -1);
    }

    /** Computes exact song time with tempo changes applying after their declared tick. */
    static double timeAtTick(NbsSong song, int targetTick) {
        int target = Math.clamp(targetTick, 0, song.lengthTicks());
        double tempo = song.ticksPerSecond();
        double milliseconds = 0.0;
        int currentTick = 0;
        List<NbsNote> notes = song.notes();
        int index = 0;
        while (index < notes.size()) {
            int tick = notes.get(index).tick();
            if (tick >= target) break;
            milliseconds += (tick - currentTick) * 1000.0 / tempo;
            currentTick = tick;
            int end = index + 1;
            while (end < notes.size() && notes.get(end).tick() == tick) end++;
            for (int noteIndex = index; noteIndex < end; noteIndex++) {
                NbsNote note = notes.get(noteIndex);
                if (note.type() == NbsNoteType.TEMPO_CHANGE
                        && NbsSong.isPlayableTempo(note.tempoChangeTicksPerSecond())) {
                    tempo = note.tempoChangeTicksPerSecond();
                }
            }
            index = end;
        }
        return milliseconds + (target - currentTick) * 1000.0 / tempo;
    }

    private static List<Candidate> candidates(NbsSong song) {
        List<Candidate> result = new ArrayList<>();
        List<NbsNote> notes = song.notes();
        double tempo = song.ticksPerSecond();
        double timeMillis = 0.0;
        int currentTick = 0;
        int index = 0;
        while (index < notes.size()) {
            int tick = notes.get(index).tick();
            timeMillis += (tick - currentTick) * 1000.0 / tempo;
            currentTick = tick;
            int end = index + 1;
            while (end < notes.size() && notes.get(end).tick() == tick) end++;

            Candidate candidate = candidateAtTick(notes, index, end,
                    Math.round(timeMillis), tick);
            if (candidate != null) result.add(candidate);
            for (int noteIndex = index; noteIndex < end; noteIndex++) {
                NbsNote note = notes.get(noteIndex);
                if (note.type() == NbsNoteType.TEMPO_CHANGE
                        && NbsSong.isPlayableTempo(note.tempoChangeTicksPerSecond())) {
                    tempo = note.tempoChangeTicksPerSecond();
                }
            }
            index = end;
        }
        return result;
    }

    private static Candidate candidateAtTick(List<NbsNote> notes, int from, int to,
                                               long timeMillis, int tick) {
        double loudest = 0.0;
        double totalPower = 0.0;
        double weightedPan = 0.0;
        double weightedTone = 0.0;
        int audibleNotes = 0;
        long signature = tick * 0x9E3779B97F4A7C15L;
        for (int index = from; index < to; index++) {
            NbsNote note = notes.get(index);
            if (note.type() != NbsNoteType.SOUND
                    || note.velocity() <= 0 || note.layerVolume() <= 0) continue;
            double amplitude = note.velocity() * note.layerVolume() / 10_000.0;
            double power = amplitude * amplitude;
            loudest = Math.max(loudest, amplitude);
            totalPower += power;
            weightedPan += note.panning() / 100.0 * power;
            weightedTone += Math.clamp(note.playbackPitchCents() / 2_400.0,
                    -1.0, 1.0) * power;
            audibleNotes++;
            signature = Long.rotateLeft(signature, 11)
                    ^ (note.instrument() * 131L + note.key() * 31L
                    + note.layer() * 17L + note.velocity());
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

    private static Timing timing(NbsSong song) {
        double duration = timeAtTick(song, song.lengthTicks());
        double loopStart = song.loopEnabled()
                ? timeAtTick(song, song.loopStartTick()) : -1.0;
        return new Timing(duration, loopStart);
    }

    private record Candidate(long timeMillis, double strength,
                             double stereoBalance, double toneBalance,
                             long signature) {
    }

    private record Timing(double durationMillis, double loopStartMillis) {
    }
}
