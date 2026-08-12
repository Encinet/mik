package org.encinet.mik.module.music.jukebox;

/** Chooses whether a newly sending seekable audio stream must catch up again. */
final class JukeboxAudioSynchronizer {

    static final long FRAME_MILLIS = 20L;
    static final long MIN_CORRECTION_MILLIS = FRAME_MILLIS;
    /** Covers source-info delivery, the default jitter buffer and OpenAL startup buffers. */
    static final int CLIENT_PRE_ROLL_FRAMES = 25;
    static final long CLIENT_PRE_ROLL_MILLIS = CLIENT_PRE_ROLL_FRAMES * FRAME_MILLIS;
    static final int MAX_DISCARDED_FRAMES_PER_POLL = 50;

    private JukeboxAudioSynchronizer() {
    }

    static boolean requiresClientPreRoll(boolean joiningActivePlayback,
                                         boolean seekable) {
        return joiningActivePlayback && seekable;
    }

    static long catchUpPositionMillis(long frameTimecodeMillis,
                                      long groupPositionMillis,
                                      long durationMillis) {
        long framePosition = Math.max(0L, frameTimecodeMillis);
        long groupPosition = Math.max(0L, groupPositionMillis);
        if (groupPosition - framePosition < MIN_CORRECTION_MILLIS) {
            return -1L;
        }
        return CorruptAudioRecovery.resumePositionMillis(
                groupPosition, true, durationMillis);
    }

    static boolean frameIsBehind(long frameTimecodeMillis,
                                 long groupPositionMillis) {
        return Math.max(0L, groupPositionMillis)
                - Math.max(0L, frameTimecodeMillis) >= MIN_CORRECTION_MILLIS;
    }

    static boolean frameIsAhead(long frameTimecodeMillis,
                                long groupPositionMillis) {
        return Math.max(0L, frameTimecodeMillis)
                - Math.max(0L, groupPositionMillis) >= MIN_CORRECTION_MILLIS;
    }

    static long preRollTargetPositionMillis(long groupPositionMillis,
                                            long durationMillis) {
        return CorruptAudioRecovery.resumePositionMillis(
                Math.max(0L, groupPositionMillis) + CLIENT_PRE_ROLL_MILLIS,
                true, durationMillis);
    }
}
