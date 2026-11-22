package org.encinet.mik.feature.entertainment.music.jukebox;

import java.io.IOException;
import java.io.InputStream;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia.MediaLease;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.encinet.mik.protocol.music.NoteScore;

/** Validates the negotiated playback representation before it reaches the game thread. */
final class NoteMedia {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_EVENTS = 200_000;
    private static final int MAX_VOICES = 4096;
    private static final long MAX_DURATION_US = 1_000_000_000_000_000L;

    private NoteMedia() { }

    static NoteScore read(MediaLease lease) throws IOException {
        if (lease.descriptor().getRepresentation() != MusicRepresentation.NOTE_EVENTS
                || lease.descriptor().getRepresentationVersion() != 1
                || lease.contentLength() <= 0 || lease.contentLength() > MAX_BYTES) {
            throw new IOException("Invalid note event lease");
        }
        try (var reader = lease.openReader(); var input = new InputStream() {
            @Override public int read() throws IOException { return reader.read(); }
            @Override public int read(byte[] bytes, int offset, int size) throws IOException { return reader.read(bytes, offset, size); }
        }) {
            byte[] bytes = input.readNBytes(Math.toIntExact(lease.contentLength()) + 1);
            if (bytes.length != lease.contentLength()) throw new IOException("Unexpected note event score length");
            var score = NoteScore.parseFrom(bytes);
            validate(score);
            return score;
        }
    }

    static void validate(NoteScore score) throws IOException {
        if (score.getEventsCount() > MAX_EVENTS || score.getDurationUs() <= 0L || score.getDurationUs() > MAX_DURATION_US
                || (score.hasLoop() && (score.getLoop().getStartUs() < 0 || score.getLoop().getStartUs() >= score.getDurationUs()
                    || (score.getLoop().hasRepeats() && (score.getLoop().getRepeats() < 1 || score.getLoop().getRepeats() > 65_535))))) {
            throw new IOException("Invalid note event score bounds");
        }
        long previous = 0;
        for (var event : score.getEventsList()) {
            if (event.getTimeUs() < previous || event.getTimeUs() >= score.getDurationUs()) throw new IOException("Invalid note event time");
            previous = event.getTimeUs();
            switch (event.getActionCase()) {
                case SOUND -> {
                    var sound = event.getSound();
                    if (sound.getVoice() < 0 || sound.getVoice() >= MAX_VOICES || sound.getGain() < 0 || sound.getGain() > 10000
                            || sound.getPan() < -100 || sound.getPan() > 100 || !sound.getTone().matches("[a-z_]{1,32}")) {
                        throw new IOException("Invalid note sound event");
                    }
                }
                case STOP -> {
                    var stop = event.getStop();
                    if (stop.hasFirstVoice() != stop.hasLastVoice() || (stop.hasFirstVoice()
                            && (stop.getFirstVoice() < 0 || stop.getLastVoice() < stop.getFirstVoice()))) throw new IOException("Invalid note stop event");
                }
                default -> throw new IOException("Missing note event action");
            }
        }
    }
}
