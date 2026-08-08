package org.encinet.mik.module.music.rhythm;

/** Projects extracted musical features into deterministic four-lane patterns. */
final class RhythmLaneSequencer {
    private static final RhythmInput[] LANES = {
            RhythmInput.ONE, RhythmInput.TWO, RhythmInput.THREE, RhythmInput.FOUR
    };

    private final long seed;
    private long step;
    private RhythmInput previous;

    RhythmLaneSequencer(String seed) {
        this.seed = mix(seed == null ? 0L : seed.hashCode());
    }

    /**
     * Spatial and timbral accents influence a lane without becoming part of the
     * extracted track, so another game mode can choose a different projection.
     */
    RhythmInput next(long signature, double stereoBalance, double toneBalance) {
        double pan = Math.clamp(stereoBalance, -1.0, 1.0);
        double tone = Math.clamp(toneBalance, -1.0, 1.0);
        long mixed = mix(seed ^ signature ^ (++step * 0x9E3779B97F4A7C15L));
        RhythmInput selected;
        if (pan < -0.28 && previous != RhythmInput.ONE) {
            selected = RhythmInput.ONE;
        } else if (pan > 0.28 && previous != RhythmInput.FOUR) {
            selected = RhythmInput.FOUR;
        } else if (tone > 0.34 && previous != RhythmInput.THREE) {
            selected = RhythmInput.THREE;
        } else if (tone < -0.34 && previous != RhythmInput.TWO) {
            selected = RhythmInput.TWO;
        } else {
            int index = Math.floorMod((int) (mixed ^ (mixed >>> 32)), LANES.length);
            selected = LANES[index];
            if (selected == previous) {
                selected = LANES[(index + 1 + (int) (step & 1L))
                        % LANES.length];
            }
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
