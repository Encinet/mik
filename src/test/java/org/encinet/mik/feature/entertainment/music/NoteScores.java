package org.encinet.mik.feature.entertainment.music;

import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteScore;
import org.encinet.mik.protocol.music.NoteSound;

public final class NoteScores {
    private NoteScores() { }

    public static NoteEvent sound(long timeUs, int gain) {
        return NoteEvent.newBuilder().setTimeUs(timeUs)
                .setSound(NoteSound.newBuilder().setTone("harp").setGain(gain)).build();
    }

    public static NoteScore.Builder evenlySpaced(long intervalUs, int count) {
        var score = NoteScore.newBuilder().setDurationUs(intervalUs * count);
        for (int index = 0; index < count; index++) score.addEvents(sound(intervalUs * index, 10_000));
        return score;
    }
}
