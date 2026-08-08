package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.encinet.mik.module.music.rhythm.RhythmInput;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmSpatialSliderTest {
    @Test
    void selectedLinksAreDeterministicAndPreserveTheirEndpoints() {
        RhythmSpatialSlider slider = slider();
        RhythmCue from = cue(1L, 1_000L);
        Vector eye = new Vector(0.0, 65.6, 0.0);
        Vector start = eye.clone().add(
                RhythmSpatialPath.direction(0.0, 0.0).multiply(3.5));
        Vector end = eye.clone().add(
                RhythmSpatialPath.direction(32.0, 24.0).multiply(4.6));
        RhythmSpatialSlider.Link link = findSelectedLink(
                slider, from, start, end, eye);

        assertEquals(link, slider.link(from, start,
                cue(link.toCueId(), link.toTimeMillis()), end, eye).orElseThrow());
        assertEquals(0.0, start.distance(link.pointAtProgress(0.0)), 1.0E-9);
        assertEquals(0.0, end.distance(link.pointAtProgress(1.0)), 1.0E-9);
        assertTrue(link.pointAtProgress(0.5).distance(start) > 0.2);
        assertTrue(link.pointAtProgress(0.5).distance(end) > 0.2);
    }

    @Test
    void activatedHeadWaitsAtTheStartAndArrivesBeforeTheEntireGoodWindow() {
        RhythmSpatialSlider.Active active = RhythmSpatialSlider.Active.start(
                link(1L, 2L, 1_000L, 1_800L), 1_000L, 1_000L);

        assertEquals(0.0, active.progress(1_089L), 0.0);
        assertTrue(active.progress(1_300L) > 0.0);
        assertTrue(active.progress(1_500L) > active.progress(1_300L));
        assertEquals(1.0, active.progress(1_530L), 0.0);
        assertEquals(1.0, active.progress(2_020L), 0.0);
    }

    @Test
    void activationUsesTheRealHitWithoutJumpingForEarlyOrLateGoodHits() {
        RhythmSpatialSlider.Link link = link(1L, 2L, 1_000L, 1_800L);
        RhythmSpatialSlider.Active early = RhythmSpatialSlider.Active.start(
                link, 780L, 780L);
        RhythmSpatialSlider.Active late = RhythmSpatialSlider.Active.start(
                link, 1_220L, 1_220L);

        assertEquals(1_090L, early.movementStartMillis());
        assertEquals(1_220L, late.movementStartMillis());
        assertEquals(0.0, late.progress(1_219L), 0.0);
        assertTrue(late.progress(1_300L) > 0.0);
        assertEquals(1.0, late.progress(1_530L), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> RhythmSpatialSlider.Active.start(link, 779L, 779L));
        assertThrows(IllegalArgumentException.class,
                () -> RhythmSpatialSlider.Active.start(link, 1_221L, 1_221L));
    }

    @Test
    void visualClockControlsMotionWhileJudgementClockControlsHitValidity() {
        RhythmSpatialSlider.Link link = link(1L, 2L, 1_000L, 1_800L);
        RhythmSpatialSlider.Active compensated = RhythmSpatialSlider.Active.start(
                link, 1_000L, 1_140L);

        assertEquals(1_140L, compensated.movementStartMillis());
        assertEquals(0.0, compensated.progress(1_139L), 0.0);
        assertTrue(compensated.progress(1_300L) > 0.0);
        assertFalse(link.canActivate(1_000L, 1_311L));
    }

    @Test
    void previewNeverMovesBeforeTheStartCueIsHit() {
        RhythmSpatialSlider.Link link = link(1L, 2L, 1_000L, 1_800L);
        RhythmSpatialSlider.Presentation preview =
                RhythmSpatialSlider.Presentation.preview(link);

        assertFalse(preview.active());
        assertEquals(0.0, preview.progress(10_000L), 0.0);
        assertEquals(0.0, link.fromCenter().distance(
                preview.point(10_000L)), 1.0E-9);
    }

    @Test
    void intervalsWithoutEnoughTrackingTimeRemainOrdinaryClicks() {
        RhythmSpatialSlider slider = slider();
        Vector eye = new Vector();
        Vector start = new Vector(0.0, 0.0, 3.5);
        Vector end = new Vector(2.0, 1.0, 3.5);

        assertTrue(slider.link(cue(1L, 1_000L), start,
                cue(2L, 1_709L), end, eye).isEmpty());
        assertTrue(slider.link(cue(1L, 1_000L), start,
                cue(2L, 2_500L), end, eye).isEmpty());
    }

    @Test
    void stateOnlyActivatesOnHitsAndCountsCompletedLinks() {
        RhythmSpatialSlider.State state = new RhythmSpatialSlider.State();
        RhythmSpatialSlider.Link first = link(1L, 2L, 1_000L, 1_800L);
        RhythmSpatialSlider.Link second = link(2L, 3L, 1_800L, 2_600L);

        state.hit(1L, 1_000L, 1_000L, first);
        assertNotNull(state.active());
        assertEquals(0, state.completedLinks());

        state.hit(2L, 1_800L, 1_800L, second);
        assertEquals(2L, state.active().link().fromCueId());
        assertEquals(1, state.completedLinks());

        state.miss();
        assertNull(state.active());
        assertEquals(0, state.completedLinks());
    }

    @Test
    void ignoredEndpointsCancelOnlyTheConnectedActiveLink() {
        RhythmSpatialSlider.State state = new RhythmSpatialSlider.State();
        state.hit(1L, 1_000L, 1_000L,
                link(1L, 2L, 1_000L, 1_800L));

        state.invalidateCue(99L);
        assertNotNull(state.active());
        state.invalidateCue(2L);
        assertNull(state.active());
        assertEquals(0, state.completedLinks());
    }

    @Test
    void activeLinkExpiresOnlyAfterTheLastLegalGoodHit() {
        RhythmSpatialSlider.State state = new RhythmSpatialSlider.State();
        state.hit(1L, 1_000L, 1_000L,
                link(1L, 2L, 1_000L, 1_800L));

        state.expire(2_020L);
        assertNotNull(state.active());
        state.expire(2_021L);
        assertNull(state.active());
    }

    @Test
    void consecutiveLinksAreCappedBeforeAForcedRecoveryJump() {
        RhythmSpatialSlider.State state = new RhythmSpatialSlider.State();
        state.hit(1L, 1_000L, 1_000L,
                link(1L, 2L, 1_000L, 1_800L));
        state.hit(2L, 1_800L, 1_800L,
                link(2L, 3L, 1_800L, 2_600L));
        state.hit(3L, 2_600L, 2_600L,
                link(3L, 4L, 2_600L, 3_400L));

        assertEquals(2, state.completedLinks());
        assertFalse(state.mayStartAfterHit(4L));
        state.hit(4L, 3_400L, 3_400L, null);
        assertNull(state.active());
        assertEquals(0, state.completedLinks());
    }

    @Test
    void longArcsReceiveMoreBoundedVisibilitySamples() {
        RhythmSpatialSlider.Link shortArc = link(1L, 2L,
                1_000L, 1_800L, 8.0, 3.5);
        RhythmSpatialSlider.Link longArc = link(1L, 2L,
                1_000L, 1_800L, 70.0, 4.6);

        assertTrue(longArc.visibilitySampleSegments()
                > shortArc.visibilitySampleSegments());
        assertTrue(longArc.visibilitySampleSegments() <= 32);
        assertTrue(shortArc.visibilitySampleSegments()
                >= RhythmSpatialSlider.PATH_MARKERS + 1);
    }

    @Test
    void directConstructionRejectsBrokenGeometryAndTravelWindows() {
        assertThrows(IllegalArgumentException.class, () ->
                new RhythmSpatialSlider.Link(1L, 2L, 1_000L, 1_800L,
                        90L, 220L, new Vector(), new Vector(),
                        new Vector(1.0, 0.0, 3.5)));
        assertThrows(IllegalArgumentException.class, () ->
                new RhythmSpatialSlider.Link(1L, 2L, 1_000L, 1_600L,
                        90L, 220L, new Vector(),
                        new Vector(0.0, 0.0, 3.5),
                        new Vector(1.0, 0.0, 3.5)));
        RhythmSpatialSlider.Link link = link(1L, 2L, 1_000L, 1_800L);
        assertThrows(IllegalArgumentException.class, () ->
                new RhythmSpatialSlider.Active(link,
                        link.movementEndMillis() - 219L));
    }

    private static RhythmSpatialSlider slider() {
        return new RhythmSpatialSlider("slider-test", RhythmDifficulty.NORMAL,
                RhythmSpatialProfile.forDifficulty(RhythmDifficulty.NORMAL));
    }

    private static RhythmSpatialSlider.Link findSelectedLink(
            RhythmSpatialSlider slider, RhythmCue from, Vector start,
            Vector end, Vector eye) {
        for (long id = 2L; id < 200L; id++) {
            RhythmCue to = cue(id, 1_800L);
            Optional<RhythmSpatialSlider.Link> link = slider.link(
                    from, start, to, end, eye);
            if (link.isPresent()) return link.get();
        }
        throw new AssertionError("test seed produced no slider link");
    }

    private static RhythmCue cue(long id, long timeMillis) {
        return new RhythmCue(id, timeMillis, RhythmInput.ONE, 0.72,
                0.15, -0.2, id * 0x9E3779B97F4A7C15L);
    }

    private static RhythmSpatialSlider.Link link(
            long fromId, long toId, long fromTime, long toTime) {
        return link(fromId, toId, fromTime, toTime, 32.0, 4.6);
    }

    private static RhythmSpatialSlider.Link link(
            long fromId, long toId, long fromTime, long toTime,
            double toYaw, double toDepth) {
        Vector eye = new Vector();
        Vector from = RhythmSpatialPath.direction(0.0, 0.0).multiply(3.5);
        Vector to = RhythmSpatialPath.direction(toYaw, 18.0).multiply(toDepth);
        return new RhythmSpatialSlider.Link(fromId, toId, fromTime, toTime,
                90L, 220L, eye, from, to);
    }
}
