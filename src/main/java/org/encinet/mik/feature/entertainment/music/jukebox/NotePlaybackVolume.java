package org.encinet.mik.feature.entertainment.music.jukebox;

final class NotePlaybackVolume {
    private NotePlaybackVolume() { }

    static float volume(int gain, int volumePercent) {
        return gain / 10000.0F * (Math.clamp(volumePercent, 0, 100) / 100.0F);
    }
}
