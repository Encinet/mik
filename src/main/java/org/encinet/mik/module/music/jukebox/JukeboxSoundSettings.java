package org.encinet.mik.module.music.jukebox;

/** Per-jukebox output settings shared by every MIK playback backend. */
public record JukeboxSoundSettings(int volumePercent, int rangeBlocks) {

    public static final int DEFAULT_VOLUME_PERCENT = 100;
    public static final int MIN_VOLUME_PERCENT = 0;
    public static final int MAX_VOLUME_PERCENT = 100;
    public static final int VOLUME_COARSE_STEP = 10;
    public static final int VOLUME_FINE_STEP = 1;

    public static final int DEFAULT_RANGE_BLOCKS = 64;
    public static final int MIN_RANGE_BLOCKS = 8;
    public static final int MAX_RANGE_BLOCKS = 64;
    public static final int RANGE_COARSE_STEP = 8;
    public static final int RANGE_FINE_STEP = 1;

    public JukeboxSoundSettings {
        volumePercent = clamp(volumePercent, MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT);
        rangeBlocks = clamp(rangeBlocks, MIN_RANGE_BLOCKS, MAX_RANGE_BLOCKS);
    }

    public static JukeboxSoundSettings defaults() {
        return new JukeboxSoundSettings(DEFAULT_VOLUME_PERCENT, DEFAULT_RANGE_BLOCKS);
    }

    public JukeboxSoundSettings withVolumeDelta(int delta) {
        return new JukeboxSoundSettings(saturatedAdd(volumePercent, delta), rangeBlocks);
    }

    public JukeboxSoundSettings withRangeDelta(int delta) {
        return new JukeboxSoundSettings(volumePercent, saturatedAdd(rangeBlocks, delta));
    }

    private static int saturatedAdd(int value, int delta) {
        long result = (long) value + delta;
        return result < Integer.MIN_VALUE ? Integer.MIN_VALUE
                : result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
