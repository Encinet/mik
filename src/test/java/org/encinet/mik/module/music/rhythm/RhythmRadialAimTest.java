package org.encinet.mik.module.music.rhythm;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmRadialAimTest {

    @Test
    void selectsTheCubeIntersectedByThePlayersViewRay() {
        RhythmCue ahead = cue(1L);
        RhythmCue beside = cue(2L);
        List<RhythmRadialAim.Target> targets = List.of(
                new RhythmRadialAim.Target(beside, new Vector(1.3, 0.0, 0.0), 0.42),
                new RhythmRadialAim.Target(ahead, new Vector(0.08, 0.02, 1.25), 0.42));

        RhythmRadialAim.Target selected = RhythmRadialAim.select(
                new Vector(), new Vector(0.0, 0.0, 1.0), targets).orElseThrow();

        assertEquals(ahead, selected.cue());
    }

    @Test
    void rejectsEmptySpaceAndTargetsBehindThePlayer() {
        RhythmCue behind = cue(1L);
        RhythmCue missed = cue(2L);
        List<RhythmRadialAim.Target> targets = List.of(
                new RhythmRadialAim.Target(behind, new Vector(0.0, 0.0, -1.2), 0.4),
                new RhythmRadialAim.Target(missed, new Vector(1.0, 0.0, 1.2), 0.4));

        assertTrue(RhythmRadialAim.select(new Vector(),
                new Vector(0.0, 0.0, 1.0), targets).isEmpty());
    }

    @Test
    void overlappingTargetsPreferTheMostPreciselyAimedCube() {
        RhythmCue nearCenter = cue(1L);
        RhythmCue edge = cue(2L);
        List<RhythmRadialAim.Target> targets = List.of(
                new RhythmRadialAim.Target(edge, new Vector(0.30, 0.0, 1.0), 0.4),
                new RhythmRadialAim.Target(nearCenter, new Vector(0.04, 0.0, 1.4), 0.4));

        RhythmRadialAim.Target selected = RhythmRadialAim.select(
                new Vector(), new Vector(0.0, 0.0, 1.0), targets).orElseThrow();

        assertEquals(nearCenter, selected.cue());
    }

    private static RhythmCue cue(long id) {
        return new RhythmCue(id, 1_000L, RhythmInput.ONE, 0.8);
    }
}
