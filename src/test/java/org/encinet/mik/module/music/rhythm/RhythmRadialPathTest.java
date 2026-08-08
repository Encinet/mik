package org.encinet.mik.module.music.rhythm;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmRadialPathTest {

    @Test
    void turnsAreStableAndChangeDirectionProgressively() {
        RhythmRadialPath path = new RhythmRadialPath("gradual-turns", 90.0);
        double previousAngle = 0.0;
        double previousTurn = 0.0;
        boolean first = true;
        Set<Integer> visitedOctants = new HashSet<>();

        for (int index = 0; index < 80; index++) {
            RhythmCue cue = new RhythmCue(index + 1L, index * 250L,
                    RhythmInput.values()[index % RhythmInput.values().length], 0.8);
            double angle = path.angleDegrees(cue);
            visitedOctants.add((int) (angle / 45.0));
            assertEquals(angle, path.angleDegrees(cue), 0.0);
            if (!first) {
                double turn = RhythmRadialPath.shortestDelta(previousAngle, angle);
                assertTrue(Math.abs(turn)
                        <= RhythmRadialPath.MAXIMUM_TURN_DEGREES + 1.0E-9);
                assertTrue(Math.abs(turn - previousTurn)
                        <= RhythmRadialPath.MAXIMUM_TURN_ACCELERATION_DEGREES + 1.0E-9);
                previousTurn = turn;
            }
            first = false;
            previousAngle = angle;
        }
        assertTrue(visitedOctants.size() >= 6,
                () -> "visited octants=" + visitedOctants);
    }

    @Test
    void firstCueStartsAheadOfThePlayersCurrentView() {
        RhythmRadialPath path = new RhythmRadialPath("forward-start", 212.0);
        RhythmCue first = new RhythmCue(1L, 1_000L, RhythmInput.ONE, 0.8);

        assertEquals(212.0, path.angleDegrees(first), 0.0);
        assertEquals(90.0, RhythmRadialPath.facingAngle(0.0F), 0.0);
        assertEquals(180.0, RhythmRadialPath.facingAngle(90.0F), 0.0);
        assertEquals(0.0, RhythmRadialPath.facingAngle(-90.0F), 0.0);
    }

    @Test
    void blocksUseWorldSpaceApproachThePlayerAndContinuePastTheHitShell() {
        Location anchor = new Location(null, 8.1, 64.0, -3.4);
        Location spawn = RhythmRadialPath.point(anchor, 35.0, 0.0);
        Location hit = RhythmRadialPath.point(anchor, 35.0, 1.0);
        Location late = RhythmRadialPath.point(anchor, 35.0, 1.1);

        assertEquals(RhythmRadialPath.SPAWN_RADIUS,
                horizontalDistance(anchor, spawn), 1.0E-9);
        assertEquals(RhythmRadialPath.HIT_RADIUS,
                horizontalDistance(anchor, hit), 1.0E-9);
        assertTrue(horizontalDistance(anchor, late) > 0.0);
        assertTrue((spawn.getX() - anchor.getX()) * (late.getX() - anchor.getX()) < 0.0);
        assertTrue(spawn.getY() > anchor.getY());
        assertEquals(spawn.getY(), hit.getY(), 0.0);
    }

    private static double horizontalDistance(Location first, Location second) {
        return Math.hypot(first.getX() - second.getX(), first.getZ() - second.getZ());
    }
}
