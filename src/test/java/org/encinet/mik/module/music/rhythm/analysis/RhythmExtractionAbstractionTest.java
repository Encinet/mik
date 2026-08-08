package org.encinet.mik.module.music.rhythm.analysis;

import org.encinet.mik.module.music.rhythm.RhythmChartView;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmExtractionAbstractionTest {

    @Test
    void extractorPublishesModeIndependentPulsesThroughTheSinkBoundary() {
        RhythmExtractor<String> extractor = (source, output) -> output.publish(
                RhythmSource.AUDIO_ANALYSIS,
                List.of(new RhythmPulse(240, 0.8, -0.5, 0.25,
                        source.hashCode())),
                1_000, -1L, -1);
        RhythmTimeline timeline = new RhythmTimeline("shared-track");

        extractor.extract("source", timeline);
        RhythmTrack track = timeline;

        RhythmBeat beat = track.between(0, 500).getFirst();
        assertEquals(240L, beat.timeMillis());
        assertEquals(-0.5, beat.stereoBalance());
        assertTrue(track.complete());
        assertFalse(Arrays.stream(RhythmPulse.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("input")));
    }

    @Test
    void oneExtractedTrackCanFeedIndependentModeProjections() {
        RhythmTimeline timeline = new RhythmTimeline("multi-mode");
        timeline.append(new RhythmPulse(400, 0.7));
        timeline.append(new RhythmPulse(800, 0.9));
        timeline.markComplete(1_000);
        RhythmTrack track = timeline;

        List<Long> turnTimesForAnotherMode = track.between(0, 1_000).stream()
                .map(RhythmBeat::timeMillis).toList();
        List<RhythmCue> directionCues = new RhythmChartView(
                track, RhythmDifficulty.NORMAL).between(0, 1_000);

        assertEquals(List.of(400L, 800L), turnTimesForAnotherMode);
        assertEquals(turnTimesForAnotherMode,
                directionCues.stream().map(RhythmCue::timeMillis).toList());
    }
}
