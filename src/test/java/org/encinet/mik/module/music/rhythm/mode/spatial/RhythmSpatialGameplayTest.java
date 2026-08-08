package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.Location;
import org.encinet.mik.module.music.rhythm.RhythmChartView;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.encinet.mik.module.music.rhythm.RhythmGameSession;
import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmSpatialGameplayTest {

    @Test
    void ownsPlacementPresentationAndIgnoreLifecycle() {
        AtomicBoolean clear = new AtomicBoolean(true);
        Fixture fixture = fixture("spatial-lifecycle", clear, new AtomicLong());
        RhythmCue cue = fixture.cues().getFirst();

        RhythmSpatialGameplay.Placement placement = fixture.gameplay().placement(cue);
        assertNotNull(placement);
        assertSame(placement, fixture.gameplay().placement(cue));
        assertTrue(fixture.gameplay().placementVisible(cue, placement, 100L));

        clear.set(false);
        assertTrue(fixture.gameplay().placementVisible(cue, placement, 349L));
        assertFalse(fixture.gameplay().placementVisible(cue, placement, 350L));

        fixture.gameplay().expectFrame(cue, cue.timeMillis() - 900L);
        assertFalse(fixture.gameplay().presentationFair(cue));
        fixture.gameplay().framePresented("unrelated");
        assertFalse(fixture.gameplay().presentationFair(cue));
        fixture.gameplay().framePresented("spatial:core:" + cue.id());
        assertTrue(fixture.gameplay().presentationFair(cue));

        fixture.gameplay().ignore(cue);
        assertTrue(fixture.session().isJudged(cue.id()));
        assertNull(fixture.gameplay().placement(cue));
        assertFalse(fixture.gameplay().presented(cue));
    }

    @Test
    void discardsAllPresentationCachesWithTheGameplayWindow() {
        Fixture fixture = fixture("spatial-retention",
                new AtomicBoolean(true), new AtomicLong());
        for (RhythmCue cue : fixture.cues()) {
            assertNotNull(fixture.gameplay().placement(cue));
            fixture.gameplay().expectFrame(cue, cue.timeMillis() - 900L);
            fixture.gameplay().framePresented("spatial:core:" + cue.id());
        }

        assertEquals(4, fixture.gameplay().retainedPlacementCount());
        assertEquals(4, fixture.gameplay().retainedExpectedFrameCount());
        fixture.gameplay().discardBefore(2_500L);
        assertEquals(2, fixture.gameplay().retainedPlacementCount());
        assertEquals(2, fixture.gameplay().retainedExpectedFrameCount());
        fixture.gameplay().discardBefore(4_001L);
        assertEquals(0, fixture.gameplay().retainedPlacementCount());
        assertEquals(0, fixture.gameplay().retainedExpectedFrameCount());
    }

    @Test
    void sliderVisibilityIsRecheckedAndBlockedLinksDegradeToNormalTargets() {
        AtomicBoolean clear = new AtomicBoolean(true);
        AtomicLong nowNanos = new AtomicLong();
        Fixture fixture = fixtureWithSelectedSlider(clear, nowNanos);
        RhythmCue from = fixture.cues().get(0);
        RhythmCue to = fixture.cues().get(1);
        RhythmSpatialSlider.Link preview = fixture.gameplay().previewSlider(
                target(fixture.gameplay(), from),
                target(fixture.gameplay(), to));

        assertNotNull(preview);
        assertFalse(fixture.gameplay().displayedSlider(
                preview, from.timeMillis()).active());
        assertEquals(1, fixture.gameplay().retainedSliderLinkCount());

        clear.set(false);
        assertNotNull(fixture.gameplay().displayedSlider(
                preview, from.timeMillis()));
        nowNanos.set(250_000_000L);
        assertNull(fixture.gameplay().displayedSlider(
                preview, from.timeMillis()));
        assertEquals(0, fixture.gameplay().retainedSliderLinkCount());
    }

    @Test
    void earlyFeedbackHasAnExplicitShortLifetime() {
        Fixture fixture = fixture("spatial-early-feedback",
                new AtomicBoolean(true), new AtomicLong());

        fixture.gameplay().showEarlyFeedback(420L, 1_000L);

        assertEquals(420L, fixture.gameplay().earlyFeedbackMillis());
        assertTrue(fixture.gameplay().earlyFeedbackVisible(1_360L));
        assertFalse(fixture.gameplay().earlyFeedbackVisible(1_361L));
    }

    private static Fixture fixtureWithSelectedSlider(
            AtomicBoolean clear, AtomicLong nowNanos) {
        for (int seed = 0; seed < 128; seed++) {
            Fixture fixture = fixture("spatial-slider-" + seed,
                    clear, nowNanos);
            RhythmSpatialSlider.Link link = fixture.gameplay().previewSlider(
                    target(fixture.gameplay(), fixture.cues().get(0)),
                    target(fixture.gameplay(), fixture.cues().get(1)));
            if (link != null) return fixture;
        }
        throw new AssertionError("no deterministic slider seed was selected");
    }

    private static RhythmSpatialGameplay.VisibleTarget target(
            RhythmSpatialGameplay gameplay, RhythmCue cue) {
        RhythmSpatialGameplay.Placement placement = gameplay.placement(cue);
        assertNotNull(placement);
        return new RhythmSpatialGameplay.VisibleTarget(cue, placement);
    }

    private static Fixture fixture(String seed, AtomicBoolean clear,
                                   AtomicLong nowNanos) {
        RhythmTimeline timeline = new RhythmTimeline(seed);
        timeline.publish(RhythmSource.NBS_NOTES, List.of(
                        new RhythmPulse(1_000L, 0.9),
                        new RhythmPulse(1_800L, 0.8),
                        new RhythmPulse(2_600L, 0.9),
                        new RhythmPulse(3_400L, 0.8)),
                4_000L, -1L, 0);
        RhythmChartView chart = new RhythmChartView(
                timeline, RhythmDifficulty.NORMAL);
        RhythmGameSession session = new RhythmGameSession(
                UUID.randomUUID(), 0L, RhythmDifficulty.NORMAL);
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        Location eye = new Location(null, 0.0, 65.6, 0.0);
        RhythmSpatialArena arena = new RhythmSpatialArena(
                eye, profile, (direction, distance, radius) -> clear.get());
        RhythmSpatialGameplay gameplay = new RhythmSpatialGameplay(
                seed, 0.0, eye, chart, session, profile, arena,
                900L, nowNanos::get);
        List<RhythmCue> cues = chart.between(0L, 4_000L);
        assertEquals(4, cues.size());
        return new Fixture(gameplay, session, cues);
    }

    private record Fixture(RhythmSpatialGameplay gameplay,
                           RhythmGameSession session,
                           List<RhythmCue> cues) {
    }
}
