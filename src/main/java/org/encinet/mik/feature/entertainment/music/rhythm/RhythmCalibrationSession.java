package org.encinet.mik.feature.entertainment.music.rhythm;

import org.encinet.mik.feature.entertainment.music.rhythm.calibration.CalibrationStage;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCalibrationAudioOutput;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCalibrationPattern;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCuePresentation;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.RhythmCuePresentationLedger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Four-stage calibration evidence, transitions and transport ownership, independent of Bukkit state. */
final class RhythmCalibrationSession implements AutoCloseable {
    static final long CAPTURE_WINDOW_MILLIS = 400L;
    private static final long CALIBRATION_CAPTURE_WINDOW_NANOS =
            CAPTURE_WINDOW_MILLIS * 1_000_000L;
    private static final long CALIBRATION_INTRO_MILLIS = 2_000L;
    private static final long CALIBRATION_TRANSITION_MILLIS = 1_500L;
    private static final long CALIBRATION_BEAT_PREVIEW_MILLIS = 3_200L;
    private static final long CALIBRATION_RESULT_MILLIS = 3_000L;
    private static final long CALIBRATION_AUDIO_STALL_NANOS = 600_000_000L;
    private static final long CALIBRATION_AUDIO_STALL_CONFIRM_NANOS = 1_000_000_000L;
    private static final int CALIBRATION_AUDIO_STALL_CONSECUTIVE_TICKS = 2;
    private static final long CALIBRATION_AUDIO_STALL_STATUS_NANOS = 1_800_000_000L;
    private static final long CALIBRATION_LATE_CUE_MILLIS = 150L;

    private final RhythmLatencyCalibration minecraftMeasurement =
            new RhythmLatencyCalibration();
    private final RhythmLatencyCalibration plasmoMeasurement =
            new RhythmLatencyCalibration();
    private final RhythmLatencyCalibration visualMeasurement =
            new RhythmLatencyCalibration();
    private final RhythmLatencyCalibration pointerMeasurement =
            new RhythmLatencyCalibration();
    private final RhythmCuePresentationLedger inputPresentations =
            new RhythmCuePresentationLedger();
    private final Map<String, RhythmCuePresentation> expectedVisualCommits =
            new HashMap<>();
    private boolean closed;
    private CalibrationStage stage = CalibrationStage.INTRO;
    private long stageStartedAtNanos;
    private long nextAudioCue;
    private long plasmoPhysicalBaseCycle;
    private long plasmoLogicalBaseCycle;
    private long highestPlasmoLogicalCycle = -1L;
    private int visualTapOffsetMillis;
    private int minecraftTapOffsetMillis;
    private int plasmoTapOffsetMillis;
    private RhythmCalibrationResult pendingResult;
    private RhythmCalibrationAudioOutput.StagePlayback stagePlayback;
    private int lastInputTick = Integer.MIN_VALUE;
    private long inputExpectedSinceNanos = Long.MIN_VALUE;
    private long lastObservedInputNanos = Long.MIN_VALUE;
    private int plasmoStallTicks;
    private long lastPlasmoRealignAtNanos = Long.MIN_VALUE;
    private final RhythmCalibrationPattern pattern = RhythmCalibrationPattern.fixed();
    private final LongSupplier nanoClock;

    RhythmCalibrationSession(long startedAtNanos) {
        this(startedAtNanos, System::nanoTime);
    }

    RhythmCalibrationSession(long startedAtNanos, LongSupplier nanoClock) {
        this.stageStartedAtNanos = startedAtNanos;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    CalibrationStage stage() {
        return stage;
    }

    RhythmCalibrationPattern pattern() {
        return pattern;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        pendingResult = null;
        inputPresentations.clear();
        expectedVisualCommits.clear();
        closeStagePlayback();
    }

    /** Matches a received tap to presented evidence and advances a completed measurement. */
    boolean record(Tap tap, long nowNanos) {
        if (closed || pendingResult != null || !stage.acceptsInput(tap.pointer())
                || !claimInput(tap.tick())) return false;
        // nanoTime is modular; subtraction preserves short elapsed intervals across overflow.
        long adjustedInputAtNanos = tap.receivedAtNanos()
                - tap.networkRttMillis() * 1_000_000L;
        Optional<RhythmCuePresentation> closest = closestPresentation(adjustedInputAtNanos);
        if (closest.isEmpty()) return false;
        RhythmCuePresentation cue = closest.get();
        long rawErrorNanos = tap.receivedAtNanos() - cue.presentedAtNanos();
        int rawErrorMillis = Math.clamp(Math.round(rawErrorNanos / 1_000_000.0),
                Integer.MIN_VALUE, Integer.MAX_VALUE);
        RhythmLatencyCalibration.Observation observation =
                new RhythmLatencyCalibration.Observation(cue.cueId(), cue.cycleIndex(),
                        cue.cueIndex(), cue.cuesPerCycle(), rawErrorMillis,
                        tap.networkRttMillis(), tap.coarse());
        if (Math.abs((long) observation.errorMillis()) > CAPTURE_WINDOW_MILLIS) return false;
        lastObservedInputNanos = tap.receivedAtNanos();
        RhythmLatencyCalibration measurement = measurement();
        if (measurement.record(observation) == RhythmLatencyCalibration.SampleResult.COMPLETE) {
            RhythmLatencyCalibration.Estimate estimate = measurement.estimate();
            switch (stage) {
                case MINECRAFT_AUDIO -> beginPlasmoTransition(estimate, nowNanos);
                case PLASMO_AUDIO -> beginVisualTransition(estimate, nowNanos);
                case VISUAL -> beginPointerTransition(estimate, nowNanos);
                case POINTER_VISUAL -> finishFromPointerStage(estimate, nowNanos);
                default -> throw new IllegalStateException("stage does not collect input: " + stage);
            }
        }
        return true;
    }

    private boolean claimInput(int tick) {
        if (lastInputTick == tick) return false;
        lastInputTick = tick;
        return true;
    }

    CalibrationAdvance advance(long nowNanos, Consumer<RhythmCue> playCue) {
        if (closed) return CalibrationAdvance.NONE;
        long now = positionMillis(nowNanos);
        if (stage == CalibrationStage.INTRO
                && now >= CALIBRATION_INTRO_MILLIS) {
            transitionTo(CalibrationStage.MINECRAFT_LISTEN);
            resetStageClock(nowNanos);
            playMinecraftCues(playCue, 0L, false);
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.MINECRAFT_LISTEN) {
            if (now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
                transitionTo(CalibrationStage.MINECRAFT_AUDIO);
                minecraftMeasurement.reset();
                resetStageClock(nowNanos);
                beginInputWindow(nowNanos);
                playMinecraftCues(playCue, 0L, true);
            } else {
                playMinecraftCues(playCue, now, false);
            }
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.MINECRAFT_AUDIO) {
            playMinecraftCues(playCue, now, true);
            minecraftMeasurement.advanceToCycle(now / pattern.durationMillis());
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.TRANSITION_TO_PLASMO
                && now >= CALIBRATION_TRANSITION_MILLIS) {
            transitionTo(CalibrationStage.PLASMO_LISTEN);
            resetStageClock(nowNanos);
            return CalibrationAdvance.START_PLASMO;
        }
        if (stage == CalibrationStage.PLASMO_LISTEN
                || stage == CalibrationStage.PLASMO_AUDIO) {
            return advancePlasmo(nowNanos);
        }
        if (stage == CalibrationStage.TRANSITION_TO_VISUAL
                && now >= CALIBRATION_TRANSITION_MILLIS) {
            transitionTo(CalibrationStage.VISUAL_LISTEN);
            visualMeasurement.reset();
            resetStageClock(nowNanos);
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.VISUAL_LISTEN
                && now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
            transitionTo(CalibrationStage.VISUAL);
            visualMeasurement.reset();
            resetStageClock(nowNanos);
            beginInputWindow(nowNanos);
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.VISUAL) {
            visualMeasurement.advanceToCycle(now / pattern.durationMillis());
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.TRANSITION_TO_POINTER
                && now >= CALIBRATION_TRANSITION_MILLIS) {
            transitionTo(CalibrationStage.POINTER_LISTEN);
            pointerMeasurement.reset();
            resetStageClock(nowNanos);
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.POINTER_LISTEN
                && now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
            transitionTo(CalibrationStage.POINTER_VISUAL);
            pointerMeasurement.reset();
            resetStageClock(nowNanos);
            beginInputWindow(nowNanos);
            return CalibrationAdvance.NONE;
        }
        if (stage == CalibrationStage.POINTER_VISUAL) {
            pointerMeasurement.advanceToCycle(now / pattern.durationMillis());
            return CalibrationAdvance.NONE;
        }
        return CalibrationAdvance.NONE;
    }

    private CalibrationAdvance advancePlasmo(long nowNanos) {
        if (stagePlayback == null || !stagePlayback.active()) {
            plasmoStallTicks = 0;
            restartPlasmo(nowNanos);
            return CalibrationAdvance.START_PLASMO;
        }
        RhythmCalibrationAudioOutput.PlaybackProgress progress =
                stagePlayback.progress();
        boolean stalled = progress.stalled(nowNanos,
                CALIBRATION_AUDIO_STALL_NANOS)
                || !progress.hasStarted()
                && positionMillis(nowNanos) * 1_000_000L
                > CALIBRATION_AUDIO_STALL_CONFIRM_NANOS;
        if (stalled) {
            if (++plasmoStallTicks < CALIBRATION_AUDIO_STALL_CONSECUTIVE_TICKS) {
                return CalibrationAdvance.NONE;
            }
            restartPlasmo(nowNanos);
            return CalibrationAdvance.START_PLASMO;
        }
        plasmoStallTicks = 0;
        List<RhythmCuePresentation> supplied =
                stagePlayback.drainPresentations();
        boolean listening = stage == CalibrationStage.PLASMO_LISTEN;
        if (listening) {
            if (progress.completedCycles() < 1L) {
                return CalibrationAdvance.NONE;
            }
            plasmoPhysicalBaseCycle = progress.completedCycles();
            inputPresentations.clear();
            lastPlasmoRealignAtNanos = Long.MIN_VALUE;
            transitionTo(CalibrationStage.PLASMO_AUDIO);
            beginInputWindow(nowNanos);
            return CalibrationAdvance.NONE;
        }

        for (RhythmCuePresentation raw : supplied) {
            if (raw.cycleIndex() < plasmoPhysicalBaseCycle) continue;
            long logicalCycle = plasmoLogicalBaseCycle
                    + raw.cycleIndex() - plasmoPhysicalBaseCycle;
            long cueId = logicalCycle * pattern.cueCount()
                    + raw.cueIndex() + 1L;
            RhythmCuePresentation mapped = new RhythmCuePresentation(
                    cueId, logicalCycle, raw.cueIndex(), pattern.cueCount(),
                    raw.presentedAtNanos(), raw.strength());
            inputPresentations.add(mapped);
            highestPlasmoLogicalCycle = Math.max(
                    highestPlasmoLogicalCycle, logicalCycle);
        }
        long completed = Math.max(0L, progress.completedCycles()
                - plasmoPhysicalBaseCycle) + plasmoLogicalBaseCycle;
        plasmoMeasurement.advanceToCycle(completed);
        return CalibrationAdvance.NONE;
    }

    private void restartPlasmo(long nowNanos) {
        closeStagePlayback();
        plasmoStallTicks = 0;
        if (highestPlasmoLogicalCycle >= plasmoLogicalBaseCycle) {
            plasmoLogicalBaseCycle = highestPlasmoLogicalCycle + 1L;
        }
        transitionTo(CalibrationStage.PLASMO_LISTEN);
        lastPlasmoRealignAtNanos = nowNanos;
        inputPresentations.clear();
        stageStartedAtNanos = nowNanos;
    }

    private void playMinecraftCues(Consumer<RhythmCue> playCue, long nowMillis,
                                   boolean collect) {
        RhythmCue cue = cue(nextAudioCue);
        while (cue.timeMillis() != Long.MAX_VALUE
                && cue.timeMillis() <= nowMillis) {
            long lateness = nowMillis - cue.timeMillis();
            nextAudioCue++;
            if (lateness <= CALIBRATION_LATE_CUE_MILLIS) {
                playCue.accept(cue);
                if (collect) inputPresentations.add(presentation(
                        nextAudioCue - 1L, nanoClock.getAsLong()));
            }
            cue = cue(nextAudioCue);
        }
    }

    RhythmCue cue(long index) {
        long safeIndex = Math.max(0L, index);
        long id = safeIndex == Long.MAX_VALUE
                ? Long.MAX_VALUE : safeIndex + 1L;
        return new RhythmCue(id, pattern.cueTimeMillis(safeIndex),
                RhythmInput.ONE, pattern.strength(safeIndex));
    }

    private RhythmCuePresentation presentation(long cueIndex,
                                                long presentedAtNanos) {
        long cycle = cueIndex / pattern.cueCount();
        int withinCycle = (int) (cueIndex % pattern.cueCount());
        return new RhythmCuePresentation(cueIndex + 1L, cycle,
                withinCycle, pattern.cueCount(), presentedAtNanos,
                pattern.strength(cueIndex));
    }

    void planVisualCue(RhythmCue cue, long nowNanos) {
        if (closed) return;
        if (stage != CalibrationStage.VISUAL
                && stage != CalibrationStage.POINTER_VISUAL) return;
        long globalIndex = cue.id() - 1L;
        long plannedAt = saturatedAddNanos(stageStartedAtNanos,
                cue.timeMillis() * 1_000_000L);
        inputPresentations.add(presentation(globalIndex, plannedAt));
        if (Math.abs(cue.timeMillis() - positionMillis(nowNanos)) <= 60L) {
            expectedVisualCommits.put(visualDecorationId(cue.id()),
                    presentation(globalIndex, plannedAt));
        }
    }

    void visualFramePresented(String decorationId,
                              long presentedAtNanos) {
        if (closed) return;
        RhythmCuePresentation expected = expectedVisualCommits.remove(decorationId);
        if (expected == null || stage != CalibrationStage.VISUAL
                && stage != CalibrationStage.POINTER_VISUAL) return;
        inputPresentations.add(new RhythmCuePresentation(expected.cueId(),
                expected.cycleIndex(), expected.cueIndex(),
                expected.cuesPerCycle(), presentedAtNanos,
                expected.strength()));
    }

    static String visualDecorationId(long cueId) {
        return "calibration-cue:" + cueId;
    }

    Progress progress() {
        RhythmLatencyCalibration measurement = measurement();
        return new Progress(measurement.adapting(), measurement.outOfSupportedRange());
    }

    boolean sampled(long cueId) {
        return stage.samplesInput() && measurement().sampled(cueId);
    }

    private RhythmLatencyCalibration measurement() {
        return switch (stage) {
            case MINECRAFT_AUDIO -> minecraftMeasurement;
            case PLASMO_AUDIO -> plasmoMeasurement;
            case VISUAL -> visualMeasurement;
            case POINTER_VISUAL -> pointerMeasurement;
            default -> throw new IllegalStateException(
                    "stage does not collect a raw latency measurement: " + stage);
        };
    }

    private Optional<RhythmCuePresentation> closestPresentation(
            long adjustedInputAtNanos) {
        return inputPresentations.closestWithin(adjustedInputAtNanos,
                CALIBRATION_CAPTURE_WINDOW_NANOS,
                acceptsInput() ? measurement()::sampled
                        : _ -> false);
    }

    private void beginPlasmoTransition(
            RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
        minecraftTapOffsetMillis = estimate.offsetMillis();
        plasmoMeasurement.reset();
        plasmoLogicalBaseCycle = 0L;
        highestPlasmoLogicalCycle = -1L;
        transitionTo(CalibrationStage.TRANSITION_TO_PLASMO);
        closeStagePlayback();
        resetStageClock(nowNanos);
    }

    private void beginVisualTransition(
            RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
        plasmoTapOffsetMillis = estimate.offsetMillis();
        visualMeasurement.reset();
        transitionTo(CalibrationStage.TRANSITION_TO_VISUAL);
        closeStagePlayback();
        resetStageClock(nowNanos);
    }

    private void beginPointerTransition(
            RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
        visualTapOffsetMillis = estimate.offsetMillis();
        pointerMeasurement.reset();
        transitionTo(CalibrationStage.TRANSITION_TO_POINTER);
        resetStageClock(nowNanos);
    }

    private void finishFromPointerStage(
            RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
        pendingResult = RhythmCalibrationResult.fromTests(
                visualTapOffsetMillis, minecraftTapOffsetMillis,
                plasmoTapOffsetMillis, estimate.offsetMillis());
        resetStageClock(nowNanos);
        beginInputWindow(nowNanos);
    }

    Optional<RhythmCalibrationResult> consumeResult() {
        Optional<RhythmCalibrationResult> pending =
                Optional.ofNullable(pendingResult);
        pendingResult = null;
        return pending;
    }

    boolean startPlasmoStage(Supplier<RhythmCalibrationAudioOutput.StagePlayback> output) {
        if (closed) return false;
        closeStagePlayback();
        stagePlayback = output.get();
        return stagePlayback != null;
    }

    private void closeStagePlayback() {
        RhythmCalibrationAudioOutput.StagePlayback playback = stagePlayback;
        stagePlayback = null;
        if (playback != null) playback.close();
    }

    void showResult(long nowNanos) {
        closeStagePlayback();
        transitionTo(CalibrationStage.RESULT);
        resetStageClock(nowNanos);
    }

    boolean resultExpired(long nowNanos) {
        return stage == CalibrationStage.RESULT
                && positionMillis(nowNanos) >= CALIBRATION_RESULT_MILLIS;
    }

    private boolean acceptsInput() {
        return stage.samplesInput();
    }

    private void beginInputWindow(long nowNanos) {
        inputExpectedSinceNanos = nowNanos;
        lastObservedInputNanos = Long.MIN_VALUE;
    }

    boolean noInputWarning(long nowNanos) {
        if (!acceptsInput() || inputExpectedSinceNanos == Long.MIN_VALUE) {
            return false;
        }
        long reference = lastObservedInputNanos == Long.MIN_VALUE
                ? inputExpectedSinceNanos : lastObservedInputNanos;
        long threshold = pattern.durationMillis() * 1_000_000L;
        long elapsed = nowNanos - reference;
        return elapsed >= threshold;
    }

    boolean plasmoRealigning(long nowNanos) {
        long elapsed = nowNanos - lastPlasmoRealignAtNanos;
        return lastPlasmoRealignAtNanos != Long.MIN_VALUE
                && elapsed >= 0L
                && elapsed <= CALIBRATION_AUDIO_STALL_STATUS_NANOS
                && stage == CalibrationStage.PLASMO_LISTEN;
    }

    private void transitionTo(CalibrationStage next) {
        stage = stage.transitionTo(next);
    }

    long positionMillis(long nowNanos) {
        long elapsed = nowNanos - stageStartedAtNanos;
        return elapsed <= 0L ? 0L : elapsed / 1_000_000L;
    }

    private void resetStageClock(long nowNanos) {
        stageStartedAtNanos = nowNanos;
        nextAudioCue = 0L;
        inputPresentations.clear();
        expectedVisualCommits.clear();
    }

    int pauseSecondsRemaining(long nowNanos) {
        long duration = switch (stage) {
            case INTRO -> CALIBRATION_INTRO_MILLIS;
            case TRANSITION_TO_PLASMO, TRANSITION_TO_VISUAL,
                 TRANSITION_TO_POINTER -> CALIBRATION_TRANSITION_MILLIS;
            case MINECRAFT_LISTEN, VISUAL_LISTEN, POINTER_LISTEN ->
                    CALIBRATION_BEAT_PREVIEW_MILLIS;
            case RESULT -> CALIBRATION_RESULT_MILLIS;
            default -> 0L;
        };
        if (duration == 0L) return 0;
        long remaining = Math.max(0L, duration - positionMillis(nowNanos));
        return (int) Math.max(1L, (remaining + 999L) / 1_000L);
    }

    private static long saturatedAddNanos(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    record Tap(int tick, boolean pointer, long receivedAtNanos,
               int networkRttMillis, boolean coarse) {
    }

    record Progress(boolean adapting, boolean outOfSupportedRange) {
    }

    enum CalibrationAdvance {
        NONE,
        START_PLASMO
    }
}
