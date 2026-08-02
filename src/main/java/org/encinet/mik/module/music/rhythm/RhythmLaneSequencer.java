package org.encinet.mik.module.music.rhythm;

/** Produces deterministic, ergonomic lane patterns from musical features. */
final class RhythmLaneSequencer {
    private static final RhythmInput[] DIRECTIONS = {
            RhythmInput.FORWARD, RhythmInput.BACKWARD,
            RhythmInput.LEFT, RhythmInput.RIGHT
    };

    private final long seed;
    private long step;
    private RhythmInput previous;
    private int cuesSinceVertical = 8;

    RhythmLaneSequencer(String seed) {
        this.seed = mix(seed == null ? 0L : seed.hashCode());
    }

    RhythmInput next(long signature, double strength, double stereoBalance) {
        return next(signature, strength, stereoBalance, 0.0);
    }

    /**
     * Maps spatial and timbral accents to meaningful actions. Jump and sneak are
     * deliberately reserved for accents instead of appearing as arbitrary random lanes.
     */
    RhythmInput next(long signature, double strength, double stereoBalance,
                     double toneBalance) {
        double pan = Math.clamp(stereoBalance, -1.0, 1.0);
        double tone = Math.clamp(toneBalance, -1.0, 1.0);
        long mixed = mix(seed ^ signature ^ (++step * 0x9E3779B97F4A7C15L));
        RhythmInput selected;
        if (strength >= 0.90 && cuesSinceVertical >= 4
                && (mixed & 1L) == 0L) {
            selected = RhythmInput.JUMP;
        } else if (tone <= -0.38 && strength >= 0.58
                && cuesSinceVertical >= 4 && (mixed & 3L) == 1L) {
            selected = RhythmInput.SNEAK;
        } else if (pan < -0.28 && previous != RhythmInput.LEFT) {
            selected = RhythmInput.LEFT;
        } else if (pan > 0.28 && previous != RhythmInput.RIGHT) {
            selected = RhythmInput.RIGHT;
        } else if (tone > 0.34 && previous != RhythmInput.FORWARD) {
            selected = RhythmInput.FORWARD;
        } else if (tone < -0.34 && previous != RhythmInput.BACKWARD) {
            selected = RhythmInput.BACKWARD;
        } else {
            int index = Math.floorMod((int) (mixed ^ (mixed >>> 32)), DIRECTIONS.length);
            selected = DIRECTIONS[index];
            if (selected == previous) {
                selected = DIRECTIONS[(index + 1 + (int) (step & 1L))
                        % DIRECTIONS.length];
            }
        }
        if (selected == RhythmInput.JUMP || selected == RhythmInput.SNEAK) {
            cuesSinceVertical = 0;
        } else {
            cuesSinceVertical++;
        }
        previous = selected;
        return selected;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }
}
