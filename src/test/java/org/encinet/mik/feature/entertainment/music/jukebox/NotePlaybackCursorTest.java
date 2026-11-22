package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.NoteScores;
import org.encinet.mik.protocol.music.NoteEvent;
import org.encinet.mik.protocol.music.NoteLoop;
import org.encinet.mik.protocol.music.NoteScore;
import org.encinet.mik.protocol.music.NoteStop;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotePlaybackCursorTest {
    @Test void preservesMicrosecondTimeAndOrderBelowTheServerTick() {
        var stop = NoteEvent.newBuilder().setTimeUs(25_001).setStop(NoteStop.getDefaultInstance()).build();
        var note = NoteScores.sound(25_001, 10_000);
        var score = NoteScore.newBuilder().setDurationUs(75_000).addEvents(NoteScores.sound(0, 10_000))
                .addEvents(stop).addEvents(note).addEvents(NoteScores.sound(50_000, 10_000)).build();
        var cursor = new NotePlaybackCursor(score, 0);
        assertEquals(List.of(0L), times(cursor.poll(0)));
        assertTrue(cursor.poll(25_000_999).notes().isEmpty());
        assertEquals(List.of(stop, note), cursor.poll(25_001_000).notes());
        assertEquals(List.of(50_000L), times(cursor.poll(50_000_000)));
        assertTrue(cursor.poll(75_000_000).finished());
    }

    @Test void honorsFiniteLoopAtAnExactBoundaryAndSupportsUnboundedLoop() {
        var score = NoteScores.evenlySpaced(100_000, 3);
        var loop = NoteLoop.newBuilder().setStartUs(100_000);
        var cursor = new NotePlaybackCursor(score.setLoop(loop.setRepeats(1)).build(), 0);
        assertEquals(List.of(0L), times(cursor.poll(0)));
        assertEquals(List.of(100_000L, 200_000L), times(cursor.poll(200_000_000)));
        assertEquals(List.of(100_000L), times(cursor.poll(300_000_000)));
        assertEquals(List.of(200_000L), times(cursor.poll(400_000_000)));
        assertTrue(cursor.poll(500_000_000).finished());

        var unbounded = new NotePlaybackCursor(score.setLoop(loop.clearRepeats()).build(), 0);
        unbounded.poll(0);
        var later = unbounded.poll(1_060_000_000L);
        assertFalse(later.finished());
        assertTrue(later.resetSounds());
        assertEquals(260, later.positionMillis());
    }

    @Test void skipsStaleEventsAfterAStallAndReportsTheSamePositionAsOutput() {
        var cursor = new NotePlaybackCursor(NoteScores.evenlySpaced(50_000, 200).build(), 0);
        assertEquals(List.of(0L), times(cursor.poll(0)));
        var delayed = cursor.poll(5_000_000_000L);
        assertEquals(List.of(4_750_000L, 4_800_000L, 4_850_000L, 4_900_000L, 4_950_000L, 5_000_000L), times(delayed));
        assertTrue(delayed.resetSounds());
        assertEquals(5_000L, delayed.positionMillis());
        assertEquals(List.of(5_050_000L), times(cursor.poll(5_050_000_000L)));
        assertTrue(cursor.poll(60_000_000_000L).finished());
        assertEquals(10_000L, cursor.poll(61_000_000_000L).positionMillis());
    }

    @Test void seeksToMediaTimeWithoutReplayingPastEventsAndResumesWithoutJumping() {
        var cursor = new NotePlaybackCursor(NoteScores.evenlySpaced(100_000, 3).build(), 150);
        assertTrue(cursor.poll(0).notes().isEmpty());
        cursor.resume(10_000_000_000L);
        assertEquals(List.of(200_000L), times(cursor.poll(10_050_000_000L)));
    }

    private static List<Long> times(NotePlaybackCursor.PollResult result) {
        return result.notes().stream().map(NoteEvent::getTimeUs).toList();
    }
}
