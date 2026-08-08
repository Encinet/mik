package org.encinet.mik.module.music.rhythm.input;

import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmWorldAimTest {

    @Test
    void aimedTargetWinsBeforeDepthOrTiming() {
        RhythmCue centered = cue(1L, 1_060L);
        RhythmCue closerInTime = cue(2L, 1_005L);
        RhythmWorldAim.Target selected = RhythmWorldAim.select(
                new Vector(), new Vector(0.0, 0.0, 1.0), List.of(
                        new RhythmWorldAim.Target(closerInTime,
                                new Vector(0.25, 0.0, 2.0), 0.35),
                        new RhythmWorldAim.Target(centered,
                                new Vector(0.02, 0.0, 4.0), 0.55)),
                1_000L).orElseThrow();

        assertEquals(centered, selected.cue());
    }

    @Test
    void timingBreaksAnExactAimTieAndTargetsBehindAreRejected() {
        RhythmCue early = cue(1L, 900L);
        RhythmCue onTime = cue(2L, 1_005L);
        RhythmWorldAim.Target selected = RhythmWorldAim.select(
                new Vector(), new Vector(0.0, 0.0, 1.0), List.of(
                        new RhythmWorldAim.Target(early,
                                new Vector(0.0, 0.0, 2.0), 0.4),
                        new RhythmWorldAim.Target(onTime,
                                new Vector(0.0, 0.0, 4.0), 0.8),
                        new RhythmWorldAim.Target(cue(3L, 1_000L),
                                new Vector(0.0, 0.0, -2.0), 0.4)),
                1_000L).orElseThrow();

        assertEquals(onTime, selected.cue());
        assertTrue(RhythmWorldAim.select(new Vector(),
                new Vector(0.0, 0.0, 1.0), List.of(
                        new RhythmWorldAim.Target(cue(4L, 1_000L),
                                new Vector(0.0, 0.0, -2.0), 0.4)),
                1_000L).isEmpty());
    }

    @Test
    void emptySpaceDoesNotSelectAnOffAxisTarget() {
        assertTrue(RhythmWorldAim.select(new Vector(),
                new Vector(0.0, 0.0, 1.0), List.of(
                        new RhythmWorldAim.Target(cue(5L, 1_000L),
                                new Vector(1.0, 0.0, 1.2), 0.4)),
                1_000L).isEmpty());
    }

    @Test
    void packetYawAndPitchUseMinecraftViewConventions() {
        Vector ahead = RhythmWorldAim.viewDirection(0.0F, 0.0F);
        Vector right = RhythmWorldAim.viewDirection(-90.0F, 0.0F);
        Vector up = RhythmWorldAim.viewDirection(0.0F, -90.0F);

        assertEquals(0.0, ahead.getX(), 1.0E-9);
        assertEquals(1.0, ahead.getZ(), 1.0E-9);
        assertEquals(1.0, right.getX(), 1.0E-9);
        assertEquals(1.0, up.getY(), 1.0E-9);
    }

    private static RhythmCue cue(long id, long timeMillis) {
        return new RhythmCue(id, timeMillis, RhythmInput.ONE, 0.8);
    }
}
