package org.encinet.mik.feature.entertainment.music.rhythm.analysis;

import org.encinet.mik.feature.entertainment.music.NoteScores;
import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteScore;
import org.encinet.mik.protocol.music.NoteStop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoteRhythmExtractorTest {
    @Test void usesTheCompiledMediaTimelineAndIgnoresStops() {
        var score = NoteScore.newBuilder().setDurationUs(650_000)
                .addEvents(NoteScores.sound(0, 10_000))
                .addEvents(NoteEvent.newBuilder().setTimeUs(200_000).setStop(NoteStop.getDefaultInstance()))
                .addEvents(NoteScores.sound(350_000, 10_000)).addEvents(NoteScores.sound(600_000, 10_000)).build();
        var timeline = new RhythmTimeline("tempo");
        NoteRhythmExtractor.INSTANCE.extract(score, timeline);
        assertEquals(List.of(0L, 350L, 600L), timeline.between(0, 1_000).stream().map(RhythmBeat::timeMillis).toList());
        assertEquals(650L, timeline.analyzedThroughMillis());
    }

    @Test void nearbyWeakDecorationDoesNotDisplaceTheStrongAccent() {
        var score = NoteScore.newBuilder().setDurationUs(400_000)
                .addEvents(NoteScores.sound(0, 1_000)).addEvents(NoteScores.sound(100_000, 10_000))
                .addEvents(NoteScores.sound(300_000, 8_000)).build();
        var timeline = new RhythmTimeline("accent");
        NoteRhythmExtractor.INSTANCE.extract(score, timeline);
        var beats = timeline.between(0, 500);
        assertEquals(List.of(100L, 300L), beats.stream().map(RhythmBeat::timeMillis).toList());
        assertTrue(beats.getFirst().strength() > 0.8);
        assertEquals(RhythmSource.NOTE_EVENTS, timeline.source());
    }
}
