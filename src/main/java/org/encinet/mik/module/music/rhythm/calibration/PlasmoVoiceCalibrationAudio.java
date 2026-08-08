package org.encinet.mik.module.music.rhythm.calibration;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sends one continuously looping, player-private 48 kHz calibration drum. */
public final class PlasmoVoiceCalibrationAudio
        implements RhythmCalibrationAudioOutput {
    private final PlasmoVoiceServer voiceServer;
    private final ServerSourceLine sourceLine;
    private final Set<Playback> playbacks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlasmoVoiceCalibrationAudio(JavaPlugin plugin,
                                       PlasmoVoiceServer voiceServer) {
        Objects.requireNonNull(plugin, "plugin");
        this.voiceServer = Objects.requireNonNull(voiceServer, "voiceServer");
        this.sourceLine = voiceServer.getSourceLineManager()
                .createBuilder(plugin, "rhythm_calibration",
                        "soundCategory.record",
                        "plasmovoice:textures/icons/speaker_disc.png", 1)
                .setDefaultVolume(1.0)
                .build();
    }

    @Override
    public boolean available(Player player) {
        if (closed.get() || player == null || !player.isOnline()) return false;
        VoiceServerPlayer voicePlayer = voiceServer.getPlayerManager()
                .getPlayerByInstance(player);
        return voicePlayer != null && voicePlayer.hasVoiceChat()
                && !voicePlayer.isVoiceDisabled();
    }

    @Override
    public Optional<StagePlayback> play(Player player,
                                        RhythmCalibrationPattern pattern) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(pattern, "pattern");
        if (!available(player)) {
            return Optional.empty();
        }
        VoiceServerPlayer voicePlayer = voiceServer.getPlayerManager()
                .getPlayerByInstance(player);
        LoopingAudioFrameProvider provider = new LoopingAudioFrameProvider(
                voiceServer, pattern);
        try {
            ServerDirectSource source = sourceLine.createDirectSource(
                    voicePlayer, false);
            source.setIconVisible(false);
            source.setName("Rhythm latency calibration");
            // Push source metadata over TCP before the first UDP audio frame.
            source.setSender(voicePlayer);
            AudioSender sender = source.createAudioSender(provider);
            Playback playback = new Playback(provider, source, sender);
            playbacks.add(playback);
            sender.onStop(playback::close);
            sender.start();
            return Optional.of(playback);
        } catch (RuntimeException exception) {
            provider.close();
            throw exception;
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List.copyOf(playbacks).forEach(Playback::close);
        playbacks.clear();
        voiceServer.getSourceLineManager().unregister(sourceLine);
    }

    private final class Playback implements StagePlayback {
        private final LoopingAudioFrameProvider provider;
        private final ServerDirectSource source;
        private final AudioSender sender;
        private final AtomicBoolean stopped = new AtomicBoolean();

        private Playback(LoopingAudioFrameProvider provider,
                         ServerDirectSource source, AudioSender sender) {
            this.provider = provider;
            this.source = source;
            this.sender = sender;
        }

        @Override
        public boolean active() {
            return !stopped.get();
        }

        @Override
        public List<RhythmCuePresentation> drainPresentations() {
            return provider.drainPresentations();
        }

        @Override
        public PlaybackProgress progress() {
            return provider.progress();
        }

        @Override
        public void close() {
            if (!stopped.compareAndSet(false, true)) return;
            playbacks.remove(this);
            sender.stop();
            source.remove();
            provider.close();
        }
    }
}
