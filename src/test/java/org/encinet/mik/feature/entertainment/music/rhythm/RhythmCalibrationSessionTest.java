package org.encinet.mik.feature.entertainment.music.rhythm;

import org.encinet.mik.feature.entertainment.music.rhythm.calibration.CalibrationStage;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCalibrationAudioOutput;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCuePresentation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.encinet.mik.feature.entertainment.music.rhythm.RhythmCalibrationSession.CalibrationAdvance.NONE;
import static org.encinet.mik.feature.entertainment.music.rhythm.RhythmCalibrationSession.CalibrationAdvance.START_PLASMO;
import static org.junit.jupiter.api.Assertions.*;

class RhythmCalibrationSessionTest {
    @Test
    void completesAllFourMeasurementsFromPresentedCuesWithChangingNetworkLatency() {
        Run run = new Run();
        run.beginMinecraft();
        run.minecraftSamples(90);
        assertEquals(CalibrationStage.TRANSITION_TO_PLASMO, run.session.stage());

        run.beginPlasmo();
        run.plasmoSamples(0, 12, 140);
        assertEquals(CalibrationStage.TRANSITION_TO_VISUAL, run.session.stage());
        assertEquals(1, run.output.closes);

        run.beginVisual();
        run.visualSamples(false, 50);
        assertEquals(CalibrationStage.TRANSITION_TO_POINTER, run.session.stage());

        run.beginVisual();
        run.visualSamples(true, 70);
        assertEquals(RhythmCalibrationResult.fromTests(50, 90, 140, 70),
                run.session.consumeResult().orElseThrow());
        assertTrue(run.session.consumeResult().isEmpty());

        run.session.showResult(run.clock.get());
        assertFalse(run.session.resultExpired(run.clock.get() + 2_999_000_000L));
        assertTrue(run.session.resultExpired(run.clock.get() + 3_000_000_000L));
    }

    @Test
    void previewAndWrongModalityCannotConsumeAKeyboardTapOrSampleTheSameCueTwice() {
        Run run = new Run();
        run.advance(2_000);
        assertFalse(run.tap(false, 40, 0));
        run.advance(3_200);
        assertFalse(run.tap(true, 40, 0));
        assertTrue(run.tap(false, 40, 0));
        assertFalse(run.tap(false, 40, 0));
        assertTrue(run.session.sampled(1L));
        assertEquals(CalibrationStage.MINECRAFT_AUDIO, run.session.stage());
    }

    @Test
    void stalledVoiceOutputRequiresTwoChecksAndKeepsEvidenceAcrossItsReplacement() {
        Run run = new Run();
        run.beginMinecraft();
        run.minecraftSamples(90);
        run.beginPlasmo();
        run.plasmoSamples(0, 8, 140);
        Output first = run.output;

        assertEquals(NONE, run.advance(700));
        assertEquals(START_PLASMO, run.advance(50));
        assertEquals(1, first.closes);
        assertEquals(CalibrationStage.PLASMO_LISTEN, run.session.stage());
        assertTrue(run.session.plasmoRealigning(run.clock.get()));
        run.startVoiceOutput();
        run.plasmoSamples(0, 4, 140);

        assertEquals(CalibrationStage.TRANSITION_TO_VISUAL, run.session.stage(),
                "the first eight samples must survive output recovery with distinct logical cue IDs");
        assertEquals(1, run.output.closes);
    }

    @Test
    void closingReleasesVoiceOutputOnceAndRejectsFurtherTicksAndOutputCreation() {
        Run run = new Run();
        run.beginMinecraft();
        run.minecraftSamples(90);
        run.beginPlasmo();

        run.session.close();
        run.session.close();

        assertEquals(1, run.output.closes);
        assertEquals(NONE, run.advance(10_000));
        assertFalse(run.tap(false, 40, 0));
        assertFalse(run.session.startPlasmoStage(() -> {
            fail("a closed calibration cannot create another voice output");
            return null;
        }));
        assertTrue(run.session.consumeResult().isEmpty());
    }

    private static final class Run {
        private final AtomicLong clock = new AtomicLong(1_000_000_000L);
        private final RhythmCalibrationSession session =
                new RhythmCalibrationSession(clock.get(), clock::get);
        private int tick;
        private Output output;

        private RhythmCalibrationSession.CalibrationAdvance advance(long millis) {
            clock.addAndGet(millis * 1_000_000L);
            return session.advance(clock.get(), _ -> { });
        }

        private void beginMinecraft() {
            advance(2_000);
            advance(3_200);
            assertEquals(CalibrationStage.MINECRAFT_AUDIO, session.stage());
        }

        private void minecraftSamples(int offset) {
            long start = clock.get();
            for (int index = 0; index < 12; index++) {
                clock.set(start + session.pattern().cueTimeMillis(index) * 1_000_000L);
                session.advance(clock.get(), _ -> { });
                tick++;
                assertTrue(tap(false, offset, index % 2 == 0 ? 40 : 180));
            }
        }

        private void beginPlasmo() {
            assertEquals(START_PLASMO, advance(1_500));
            startVoiceOutput();
        }

        private void startVoiceOutput() {
            output = new Output();
            assertTrue(session.startPlasmoStage(() -> output));
            clock.addAndGet(session.pattern().durationMillis() * 1_000_000L);
            output.progress = new RhythmCalibrationAudioOutput.PlaybackProgress(clock.get(), 1, 0);
            session.advance(clock.get(), _ -> { });
            assertEquals(CalibrationStage.PLASMO_AUDIO, session.stage());
        }

        private void plasmoSamples(int first, int count, int offset) {
            long start = clock.get();
            int cues = session.pattern().cueCount();
            for (int index = first; index < first + count; index++) {
                clock.set(start + session.pattern().cueTimeMillis(index) * 1_000_000L);
                long cycle = 1L + index / cues;
                output.progress = new RhythmCalibrationAudioOutput.PlaybackProgress(
                        clock.get(), cycle, 0);
                output.presentations.add(new RhythmCuePresentation(index + cues + 1L,
                        cycle, index % cues, cues, clock.get(), session.pattern().strength(index)));
                session.advance(clock.get(), _ -> { });
                tick++;
                assertTrue(tap(false, offset, index % 2 == 0 ? 40 : 180));
            }
        }

        private void beginVisual() {
            advance(1_500);
            advance(3_200);
        }

        private void visualSamples(boolean pointer, int offset) {
            long start = clock.get();
            for (int index = 0; index < 12; index++) {
                RhythmCue cue = session.cue(index);
                clock.set(start + cue.timeMillis() * 1_000_000L);
                session.advance(clock.get(), _ -> { });
                session.planVisualCue(cue, clock.get());
                // Actual frame delivery supersedes its planned time.
                clock.addAndGet(25_000_000L);
                session.visualFramePresented(RhythmCalibrationSession.visualDecorationId(cue.id()),
                        clock.get());
                tick++;
                assertTrue(tap(pointer, offset, index % 2 == 0 ? 40 : 180));
            }
        }

        private boolean tap(boolean pointer, int offset, int rtt) {
            long arrival = clock.get() + (offset + rtt) * 1_000_000L;
            boolean recorded = session.record(new RhythmCalibrationSession.Tap(tick, pointer,
                    arrival, rtt, false), arrival);
            if (recorded) clock.set(arrival);
            return recorded;
        }
    }

    private static final class Output implements RhythmCalibrationAudioOutput.StagePlayback {
        private final List<RhythmCuePresentation> presentations = new ArrayList<>();
        private RhythmCalibrationAudioOutput.PlaybackProgress progress;
        private int closes;

        @Override
        public boolean active() {
            return closes == 0;
        }

        @Override
        public List<RhythmCuePresentation> drainPresentations() {
            List<RhythmCuePresentation> result = List.copyOf(presentations);
            presentations.clear();
            return result;
        }

        @Override
        public RhythmCalibrationAudioOutput.PlaybackProgress progress() {
            return progress;
        }

        @Override
        public void close() {
            closes++;
        }
    }
}
