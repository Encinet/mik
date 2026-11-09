package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackAttemptTest {
    @Test
    void aBackendDeliveredAfterCloseIsReleasedWithoutPreparationOrStart() {
        PlaybackAttempt attempt = new PlaybackAttempt();
        Session session = new Session();

        attempt.prepare(() -> {
            attempt.close();
            return session;
        });
        attempt.start();
        attempt.close();

        assertEquals(0, session.prepares);
        assertEquals(0, session.starts);
        assertEquals(1, session.stops);
        assertNull(attempt.session());
    }

    @Test
    void retiredCallbacksCannotFinishOrFailTheReplacementRun() {
        List<String> events = new ArrayList<>();
        PlaybackAttempt previous = new PlaybackAttempt();
        PlaybackCallbacks oldCallbacks = previous.guard(callbacks(events));
        previous.group().publishPositionMillis(42_000L);
        previous.close();
        try (PlaybackAttempt replacement = new PlaybackAttempt()) {
            PlaybackCallbacks currentCallbacks = replacement.guard(callbacks(events));

            oldCallbacks.started();
            oldCallbacks.retrying();
            oldCallbacks.failed(new IllegalStateException("late failure"));
            oldCallbacks.finished();
            oldCallbacks.cancelled();
            currentCallbacks.started();

            assertFalse(oldCallbacks.isValid());
            assertTrue(currentCallbacks.isValid());
            assertEquals(List.of("started"), events);
            assertNotEquals(previous.id(), replacement.id());
            assertFalse(replacement.group().playbackHasStarted(),
                    "a fresh rhythm run must not inherit the previous song position");
            assertEquals(0L, replacement.group().synchronizedPositionMillis());
        }
    }

    @Test
    void preparationLyricsAndPlaybackCountingBelongToOneRun() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();
        PlaybackAttempt attempt = new PlaybackAttempt(new JukeboxPlaybackGroup(clock::get), clock::get);
        Session session = new Session();
        AtomicInteger factories = new AtomicInteger();
        AtomicInteger lyricCloses = new AtomicInteger();
        for (int call = 0; call < 2; call++) {
            attempt.prepare(() -> {
                factories.incrementAndGet();
                return session;
            });
        }
        assertEquals(1, factories.get());
        assertEquals(1, session.prepares);
        assertFalse(attempt.claimPlaybackRecord(true, Duration.ofSeconds(100)),
                "preparation alone cannot count as a playback");
        assertTrue(attempt.claimStarted());
        assertFalse(attempt.claimStarted(), "an online retry must not repeat start effects");
        session.positionMillis = 99_999L;
        clock.set(69_999_000_000L);
        assertFalse(attempt.claimPlaybackRecord(false, Duration.ofSeconds(100)));
        attempt.countingPaused(true);
        clock.addAndGet(60_000_000_000L);
        assertFalse(attempt.claimPlaybackRecord(false, Duration.ofSeconds(100)));
        attempt.countingPaused(false);
        clock.addAndGet(1_000_000L);
        assertTrue(attempt.claimPlaybackRecord(false, Duration.ofSeconds(100)));
        assertFalse(attempt.claimPlaybackRecord(true, Duration.ofSeconds(100)));

        attempt.attachLyrics(lyricCloses::incrementAndGet);
        attempt.close();
        attempt.close();
        attempt.attachLyrics(lyricCloses::incrementAndGet);

        assertEquals(1, session.stops);
        assertEquals(2, lyricCloses.get(), "both the owned and late lyric displays must close");
    }

    @Test
    void lyricCleanupFailureStillStopsTheBackendAndInvalidatesItsCallbacks() {
        PlaybackAttempt attempt = new PlaybackAttempt();
        Session session = new Session();
        List<String> events = new ArrayList<>();
        PlaybackCallbacks callbacks = attempt.guard(callbacks(events));
        session.onStop = () -> {
            callbacks.cancelled();
            throw new IllegalArgumentException("backend");
        };
        attempt.prepare(() -> session);
        attempt.attachLyrics(() -> { throw new IllegalStateException("lyrics"); });

        IllegalStateException failure = assertThrows(IllegalStateException.class, attempt::close);
        attempt.close();

        assertEquals(1, session.stops);
        assertEquals(2, failure.getSuppressed().length,
                "independent lyric and backend cleanup failures must both be reported");
        assertFalse(attempt.active());
        assertEquals(List.of(), events);
    }

    private static PlaybackCallbacks callbacks(List<String> events) {
        return new PlaybackCallbacks() {
            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public void started() {
                events.add("started");
            }

            @Override
            public void retrying() {
                events.add("retrying");
            }

            @Override
            public void failed(Throwable error) {
                events.add("failed");
            }

            @Override
            public void finished() {
                events.add("finished");
            }

            @Override
            public void cancelled() {
                events.add("cancelled");
            }
        };
    }

    private static final class Session implements PlaybackSession {
        private int prepares;
        private int starts;
        private int stops;
        private long positionMillis;
        private Runnable onStop = () -> { };

        @Override
        public void prepare() {
            prepares++;
        }

        @Override
        public void start() {
            starts++;
        }

        @Override
        public PlaybackStatus status() {
            return PlaybackStatus.LOADING;
        }

        @Override
        public long positionMillis() {
            return positionMillis;
        }

        @Override
        public void stop() {
            stops++;
            onStop.run();
        }
    }
}
