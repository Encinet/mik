package org.encinet.mik.module.music.rhythm.analysis;

import org.encinet.mik.module.music.catalog.nbs.NbsNote;
import org.encinet.mik.module.music.catalog.nbs.NbsNoteType;
import org.encinet.mik.module.music.catalog.nbs.NbsSong;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbsRhythmExtractorTest {

    @Test
    void preservesTempoChangesWhenBuildingChart() {
        NbsNote tempo = new NbsNote(3, 0, 45, 100, 100, 0, 300,
                0, 20, 0, 45, NbsNoteType.TEMPO_CHANGE);
        NbsSong song = new NbsSong(6, "Tempo", null, null, null,
                10.0, 10, false, 0, 0,
                List.of(
                        new NbsNote(0, 0, 45, 100, 100, 0, 0),
                        tempo,
                        new NbsNote(4, 1, 48, 100, 100, 0, 0),
                        new NbsNote(9, 2, 52, 100, 100, 0, 0)));
        RhythmTimeline timeline = new RhythmTimeline("tempo");

        RhythmExtractor<NbsSong> extractor = NbsRhythmExtractor.INSTANCE;
        extractor.extract(song, timeline);

        assertEquals(300.0, NbsRhythmExtractor.timeAtTick(song, 3), 0.001);
        assertEquals(350.0, NbsRhythmExtractor.timeAtTick(song, 4), 0.001);
        assertEquals(List.of(0L, 350L, 600L), timeline.between(0, 1_000).stream()
                .map(RhythmBeat::timeMillis).toList());
    }

    @Test
    void nearbyWeakDecorationDoesNotDisplaceTheStrongAccent() {
        NbsSong song = new NbsSong(6, "Accent", null, null, null,
                10.0, 4, false, 0, 0,
                List.of(
                        new NbsNote(0, 0, 45, 20, 50, 0, 0),
                        new NbsNote(1, 0, 45, 100, 100, 0, 0),
                        new NbsNote(3, 0, 45, 80, 100, 0, 0)));
        RhythmTimeline timeline = new RhythmTimeline("accent");

        NbsRhythmExtractor.INSTANCE.extract(song, timeline);

        List<RhythmBeat> beats = timeline.between(0, 500);
        assertEquals(List.of(100L, 300L), beats.stream()
                .map(RhythmBeat::timeMillis).toList());
        assertTrue(beats.getFirst().strength() > 0.8);
        assertEquals(RhythmSource.NBS_NOTES, timeline.source());
    }
}
