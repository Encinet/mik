package org.encinet.mik.module.music.rhythm.calibration;

import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.server.PlasmoBaseVoiceServer;
import su.plo.voice.api.server.audio.provider.AudioFrameProvider;
import su.plo.voice.api.server.audio.provider.AudioFrameResult;

import java.util.Objects;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Pre-encodes an exact 20 ms-aligned mono PCM segment and loops without EOS. */
final class LoopingAudioFrameProvider implements AudioFrameProvider, AutoCloseable {
    private static final int MONO_FRAME_SAMPLES = 960;
    private static final int FRAME_MILLIS = 20;

    private final byte[][] frames;
    private final RhythmCalibrationPattern pattern;
    private final ConcurrentLinkedQueue<RhythmCuePresentation> presentations =
            new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong lastFrameAtNanos = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong completedCycles = new AtomicLong();
    private final AtomicInteger frameIndex = new AtomicInteger();

    LoopingAudioFrameProvider(PlasmoBaseVoiceServer voiceServer,
                              RhythmCalibrationPattern pattern) {
        Objects.requireNonNull(voiceServer, "voiceServer");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        short[] samples = RhythmCalibrationDrumSynth.timeline(pattern);
        Objects.requireNonNull(samples, "samples");
        if (samples.length == 0 || samples.length % MONO_FRAME_SAMPLES != 0) {
            throw new IllegalArgumentException(
                    "mono PCM must contain complete 20 ms frames");
        }
        this.frames = encodeFrames(voiceServer, samples);
    }

    @Override
    public AudioFrameResult provide20ms() {
        if (closed.get()) return AudioFrameResult.Finished.INSTANCE;
        long now = System.nanoTime();
        int index = frameIndex.get();
        long cycle = completedCycles.get();
        for (int cueIndex = 0; cueIndex < pattern.cueCount(); cueIndex++) {
            if (pattern.cueTimesMillis().get(cueIndex) / FRAME_MILLIS != index) {
                continue;
            }
            long cueId = Math.addExact(Math.multiplyExact(cycle,
                    pattern.cueCount()), cueIndex + 1L);
            presentations.add(new RhythmCuePresentation(cueId, cycle, cueIndex,
                    pattern.cueCount(), now, pattern.strength(cueId - 1L)));
        }
        byte[] frame = frames[index];
        int next = index + 1;
        if (next == frames.length) {
            next = 0;
            completedCycles.incrementAndGet();
        }
        frameIndex.set(next);
        lastFrameAtNanos.set(now);
        return new AudioFrameResult.Provided(frame);
    }

    List<RhythmCuePresentation> drainPresentations() {
        java.util.ArrayList<RhythmCuePresentation> drained = new java.util.ArrayList<>();
        RhythmCuePresentation presentation;
        while ((presentation = presentations.poll()) != null) drained.add(presentation);
        return List.copyOf(drained);
    }

    RhythmCalibrationAudioOutput.PlaybackProgress progress() {
        return new RhythmCalibrationAudioOutput.PlaybackProgress(
                lastFrameAtNanos.get(), completedCycles.get(), frameIndex.get());
    }

    @Override
    public void close() {
        closed.set(true);
        presentations.clear();
    }

    private static byte[][] encodeFrames(PlasmoBaseVoiceServer voiceServer,
                                         short[] samples) {
        int frameCount = samples.length / MONO_FRAME_SAMPLES;
        byte[][] frames = new byte[frameCount][];
        try (AudioEncoder encoder = voiceServer.createOpusEncoder(false)) {
            if (!encoder.isOpen()) encoder.open();
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                short[] pcm = java.util.Arrays.copyOfRange(samples,
                        frameIndex * MONO_FRAME_SAMPLES,
                        (frameIndex + 1) * MONO_FRAME_SAMPLES);
                byte[] encoded = encoder.encode(pcm);
                frames[frameIndex] = voiceServer.getDefaultEncryption()
                        .encrypt(encoded);
            }
            return frames;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "unable to encode Plasmo Voice calibration frames", exception);
        }
    }
}
