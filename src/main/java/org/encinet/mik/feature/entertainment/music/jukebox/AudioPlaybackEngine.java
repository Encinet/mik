package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.device.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import su.plo.slib.api.server.position.ServerPos3d;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.AudioFrameResult;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerProximitySource;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Owns one decoder and media reader per playback group. Devices are independent voice outputs. */
final class AudioPlaybackEngine implements AutoCloseable {
    private static final int FRAME_MILLIS = 20;
    private static final int WINDOW_FRAMES = 8;
    private static final int MAX_LAG_FRAMES = 3;
    private static final AudioFrameResult IDLE = new AudioFrameResult.Provided(null);

    private final JavaPlugin plugin;
    private final PlasmoVoiceServer voiceServer;
    private final AudioTrackLoader loader;
    private final OfflineRhythmAnalyzer rhythmAnalyzer;
    private final ServerSourceLine sourceLine;
    private final Predicate<UUID> audibleToPlayer;
    private final ScheduledThreadPoolExecutor renderers = new ScheduledThreadPoolExecutor(
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4),
            Thread.ofPlatform().daemon().name("mik-music-render-", 0).factory());
    private final Map<JukeboxPlaybackGroup, Session> sessions = new HashMap<>();
    private volatile BiPredicate<Location, UUID> outputAudience = (_, _) -> true;
    private volatile Predicate<Location> hasAudience = _ -> true;
    private volatile boolean closed;

    AudioPlaybackEngine(JavaPlugin plugin, PlasmoVoiceServer voiceServer, MusicMedia media,
                        Predicate<UUID> audibleToPlayer) {
        this.plugin = plugin;
        this.voiceServer = voiceServer;
        this.audibleToPlayer = audibleToPlayer;
        int sampleRate = voiceServer.getConfig() == null ? 48_000 : voiceServer.getConfig().voice().sampleRate();
        this.loader = new AudioTrackLoader(new PlaybackResourceResolver(media), sampleRate);
        this.rhythmAnalyzer = new OfflineRhythmAnalyzer(loader, message -> plugin.getLogger().warning(message));
        this.sourceLine = voiceServer.getSourceLineManager()
                .createBuilder(plugin, "music", "soundCategory.record",
                        "plasmovoice:textures/icons/speaker_disc.png", 10)
                .setDefaultVolume(1.0).build();
        renderers.setRemoveOnCancelPolicy(true);
    }

    void setOutputAudience(BiPredicate<Location, UUID> filter) { outputAudience = filter; }

    void setAudiencePresence(Predicate<Location> filter) { hasAudience = filter; }

    PlaybackSession create(Location location, MusicTrack music, JukeboxSoundSettings settings,
                           PlaybackCallbacks callbacks, RhythmTimeline timeline,
                           JukeboxExperienceMode mode, JukeboxPlaybackGroup group, MusicMedia.MediaLease lease) {
        if (closed) throw new IllegalStateException("Audio playback backend is closed");
        var session = sessions.computeIfAbsent(group, key -> new Session(key, music, timeline, mode, java.util.Objects.requireNonNull(lease)));
        var output = new Output(session, location.clone(), music.details().title(), settings, callbacks);
        session.outputs.add(output);
        return output;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List.copyOf(sessions.values()).forEach(session -> session.finish(null, false));
        renderers.shutdown();
        rhythmAnalyzer.close();
        loader.close();
        voiceServer.getSourceLineManager().unregister(sourceLine);
    }

    private boolean onMain(Runnable action) {
        if (closed) return false;
        try {
            Bukkit.getScheduler().runTask(plugin, action);
            return true;
        } catch (IllegalStateException disabled) {
            return false;
        }
    }

    private final class Session {
        private final JukeboxPlaybackGroup group;
        private final MusicTrack music;
        private final RhythmTimeline timeline;
        private final JukeboxExperienceMode mode;
        private final Set<Output> outputs = new LinkedHashSet<>();
        private final CorruptAudioRecovery recovery = new CorruptAudioRecovery();
        private volatile List<Output> active = List.of();
        private JukeboxPlaybackSnapshot inactive = new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0L);
        private long appliedSeekVersion;
        private volatile Run run;
        private boolean preparing;
        private boolean stopped;
        private CompletableFuture<AudioTrackLoader.LoadedAudio> loading;
        private CompletableFuture<Boolean> recovering;
        private OfflineRhythmAnalyzer.Analysis analysis;
        private AudioTrackLoader.LoadedAudio prepared;
        private MusicMedia.MediaLease initial;

        private Session(JukeboxPlaybackGroup group, MusicTrack music, RhythmTimeline timeline, JukeboxExperienceMode mode, MusicMedia.MediaLease initial) {
            this.group = group;
            this.music = music;
            this.timeline = timeline;
            this.mode = mode;
            this.initial = initial;
            group.follow(this::snapshot);
        }

        private JukeboxPlaybackSnapshot snapshot() {
            var current = run;
            if (current == null) return group.present(inactive);
            var sample = current.clock;
            if (sample.status() == PlaybackStatus.LOADING && inactive.status() == PlaybackStatus.BUFFERING) {
                sample = sample.withStatus(PlaybackStatus.BUFFERING);
            }
            return group.present(sample);
        }

        private void prepare() {
            if (preparing || stopped) return;
            preparing = true;
            var initialLease = initial;
            initial = null;
            try {
                if (mode.waitsForRhythmAnalysis()) {
                    analysis = rhythmAnalyzer.analyze(music, timeline, initialLease);
                    analysis.completion().whenComplete((_, failure) -> onMain(() -> {
                        if (stopped) return;
                        if (failure != null) { finish(failure, true); return; }
                        group.pinVersion(analysis.mediaVersion().id());
                        load(loader.loadExact(music, analysis.mediaVersion()));
                    }));
                } else {
                    load(loader.load(music, loader.resource(initialLease)));
                }
            } catch (RuntimeException failure) {
                initialLease.close();
                finish(failure, true);
            }
        }

        private void load(CompletableFuture<AudioTrackLoader.LoadedAudio> request) {
            loading = request;
            request.whenComplete((loaded, failure) -> {
                if (!onMain(() -> {
                    if (stopped || loading != request) {
                        if (loaded != null) loaded.close();
                        return;
                    }
                    loading = null;
                    if (failure != null) {
                        failed(failure, failure instanceof AudioTrackLoader.DecodeFailure decode ? decode.version() : null);
                    } else {
                        prepared = loaded;
                        startIfReady();
                    }
                }) && loaded != null) loaded.close();
            });
        }

        private void startIfReady() {
            if (stopped || active.isEmpty()) return;
            if (run == null && prepared != null) {
                var loaded = prepared;
                prepared = null;
                try {
                    var next = new Run(this, loaded);
                    run = next;
                    next.start();
                } catch (RuntimeException | MusicMedia.ReadException failure) {
                    if (run == null) loaded.close();
                    failed(failure, loaded.version());
                    return;
                }
            }
            if (run != null && run.clock.status() != PlaybackStatus.LOADING) attachOutputs();
        }

        private void attachOutputs() { List.copyOf(active).forEach(Output::attach); }

        private void refreshOutputs() {
            active = outputs.stream().filter(output -> output.requested && !output.stopped).toList();
        }

        private void detach(Output output) {
            outputs.remove(output);
            refreshOutputs();
            if (outputs.isEmpty()) finish(null, false);
        }

        private void failed(Throwable failure, MediaVersion version) {
            if (stopped) return;
            if (version == null || !CorruptAudioRecovery.containsUnexpectedEof(failure)) {
                finish(failure, true);
                return;
            }
            var report = loader.reportCorrupt(music, version);
            var current = run;
            boolean preparedFrame = current != null && current.clock.status() != PlaybackStatus.LOADING;
            if (!recovery.takeRetry(analysis != null || group.resumeVersion() != null, preparedFrame)) {
                finish(failure, true);
                return;
            }
            if (current != null) {
                var sample = current.clock;
                inactive = sample.withStatus(sample.status() == PlaybackStatus.LOADING
                        ? PlaybackStatus.LOADING : PlaybackStatus.BUFFERING);
                appliedSeekVersion = current.version;
            }
            releaseRun();
            recovering = report;
            report.whenComplete((retry, error) -> onMain(() -> {
                if (stopped || recovering != report) return;
                recovering = null;
                if (error == null && Boolean.TRUE.equals(retry) && group.resumeVersion() == null)
                    load(loader.loadAvoiding(music, version));
                else finish(error == null ? failure : error, true);
            }));
        }

        private void finish(Throwable failure, boolean notify) {
            if (stopped) return;
            stopped = true;
            var sample = run == null ? inactive : run.clock;
            if (sessions.remove(group, this)) group.releaseClock();
            inactive = sample.withStatus(failure == null && notify ? PlaybackStatus.FINISHED : PlaybackStatus.STOPPED);
            if (loading != null && !loading.cancel(false) && !loading.isCompletedExceptionally()) {
                var undelivered = loading.getNow(null);
                if (undelivered != null) undelivered.close();
            }
            if (recovering != null) {
                recovering.cancel(false);
                recovering = null;
            }
            if (analysis != null) analysis.close();
            if (initial != null) initial.close();
            if (prepared != null) prepared.close();
            releaseRun();
            var ending = List.copyOf(outputs);
            outputs.clear();
            active = List.of();
            for (Output output : ending) {
                output.release();
                if (notify) {
                    if (failure == null) output.callbacks.finished();
                    else output.callbacks.failed(failure);
                }
            }
        }

        private void releaseRun() {
            Run old = run;
            run = null;
            if (old != null) old.stop();
        }
    }

    private final class Run {
        private final Session session;
        private final AudioTrackLoader.LoadedAudio loaded;
        private final AudioTrack track;
        private final AudioPlayer player;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Map<Integer, Variant> variants = new ConcurrentHashMap<>();
        private volatile ScheduledFuture<?> renderTask;
        private volatile boolean stopped;
        private volatile long version;
        private volatile JukeboxPlaybackSnapshot clock;
        private final long duration;
        private final boolean seekable;
        private long pauseVersion;
        private long sequence;
        private long renderAt;
        private int drain;
        private int emptyFrames;
        private final BitSet gains = new BitSet(JukeboxSoundSettings.MAX_VOLUME_PERCENT + 1);
        private final BitSet failedGains = new BitSet(JukeboxSoundSettings.MAX_VOLUME_PERCENT + 1);
        private short[] samples = new short[0];
        private short[] scaled = new short[0];

        private Run(Session session, AudioTrackLoader.LoadedAudio loaded) {
            this.session = session;
            this.loaded = loaded;
            this.track = loaded.track();
            this.player = loader.createPlayer();
            duration = track.getDuration() == Long.MAX_VALUE ? 0L : Math.max(0L, track.getDuration());
            seekable = loaded.seekable() && track.isSeekable() && duration > 0;
            clock = new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, 0L, duration, false);
        }

        private void start() throws MusicMedia.ReadException {
            var seek = session.group.currentSeek();
            version = seek.version();
            long requested = seek.version() == session.appliedSeekVersion ? session.inactive.positionMillis() : seek.positionMillis();
            if (requested > 0 && !seekable) {
                throw new MusicMedia.ReadException(org.encinet.mik.protocol.music.MusicFailure.Code.UNSUPPORTED_MEDIA);
            }
            long position = CorruptAudioRecovery.resumePositionMillis(requested, seekable, duration);
            if (position != 0L) track.setPosition(position);
            clock = new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, position, duration, false);
            player.addListener(new AudioEventAdapter() {
                @Override public void onTrackException(AudioPlayer ignored, AudioTrack failed, FriendlyException error) {
                    failure.compareAndSet(null, error);
                }
                @Override public void onTrackEnd(AudioPlayer ignored, AudioTrack ended, AudioTrackEndReason reason) {
                    if (reason == AudioTrackEndReason.LOAD_FAILED) {
                        failure.compareAndSet(null, new IllegalStateException("Audio decoder could not start"));
                    }
                }
            });
            player.playTrack(track);
            renderTask = renderers.scheduleAtFixedRate(this::render, FRAME_MILLIS, FRAME_MILLIS, TimeUnit.MILLISECONDS);
        }

        private synchronized void render() {
            if (stopped || session.run != this) return;
            long now = System.nanoTime();
            if (now < renderAt) return;
            renderAt = now + TimeUnit.MILLISECONDS.toNanos(FRAME_MILLIS - 5);
            try {
                var error = failure.get();
                if (error != null) {
                    terminal(error);
                    return;
                }
                var seek = session.group.currentSeek();
                if (seek.version() != version) {
                    version = seek.version();
                    long position = CorruptAudioRecovery.resumePositionMillis(
                            seek.positionMillis(), seekable, duration);
                    track.setPosition(position);
                    var status = clock.status() == PlaybackStatus.LOADING ? PlaybackStatus.LOADING : PlaybackStatus.BUFFERING;
                    clock = new JukeboxPlaybackSnapshot(status, position, duration, seekable);
                    closeVariants();
                    drain = 0;
                }
                var pause = session.group.currentPause();
                if (pauseVersion != pause.version()) {
                    pauseVersion = pause.version();
                    closeVariants();
                    player.setPaused(pause.paused());
                }
                if (pause.paused() || track.getState() == AudioTrackState.SEEKING) return;
                var frame = player.provide();
                if (frame == null || frame.isTerminator()) {
                    if (player.getPlayingTrack() == null) {
                        if (++drain > MAX_LAG_FRAMES) terminal(failure.get());
                    } else if (++emptyFrames == MAX_LAG_FRAMES) {
                        closeVariants();
                        if (clock.status() == PlaybackStatus.PLAYING) clock = clock.withStatus(PlaybackStatus.BUFFERING);
                    }
                    return;
                }
                drain = 0;
                emptyFrames = 0;
                boolean first = clock.status() == PlaybackStatus.LOADING;
                clock = new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING, frame.getTimecode(), duration, seekable);
                if (first) onMain(() -> { if (session.run == this) session.attachOutputs(); });
                long frameSequence = ++sequence;
                gains.clear();
                for (Output output : session.active) {
                    int gain = output.settings.volumePercent();
                    if (gain > 0 && !output.stopped && hasAudience.test(output.location)) gains.set(gain);
                }
                variants.entrySet().removeIf(entry -> {
                    if (gains.get(entry.getKey())) return false;
                    entry.getValue().close();
                    return true;
                });
                if (gains.isEmpty()) return;
                byte[] pcm = frame.getData();
                if (samples.length != pcm.length / Short.BYTES) {
                    samples = new short[pcm.length / Short.BYTES];
                    scaled = new short[samples.length];
                }
                for (int sample = 0, offset = 0; sample < samples.length; sample++, offset += Short.BYTES) {
                    samples[sample] = (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
                }
                for (int gain = gains.nextSetBit(1); gain >= 0; gain = gains.nextSetBit(gain + 1)) {
                    if (failedGains.get(gain)) continue;
                    try {
                        encode(gain, frameSequence);
                    } catch (Exception outputFailure) {
                        failOutputs(gain, outputFailure);
                    }
                }
            } catch (Exception error) {
                terminal(error);
            }
        }

        private void encode(int gain, long frameSequence) throws Exception {
            var variant = variants.get(gain);
            if (variant == null) {
                variant = new Variant();
                variants.put(gain, variant);
            }
            short[] input = samples;
            if (gain != JukeboxSoundSettings.MAX_VOLUME_PERCENT) {
                for (int sample = 0; sample < samples.length; sample++) {
                    scaled[sample] = (short) (samples[sample] * gain / 100);
                }
                input = scaled;
            }
            variant.publish(new EncodedFrame(frameSequence, version, pauseVersion,
                    voiceServer.getDefaultEncryption().encrypt(variant.encoder.encode(input))));
        }

        private void failOutputs(int gain, Exception failure) {
            failedGains.set(gain);
            var variant = variants.remove(gain);
            if (variant != null) variant.close();
            onMain(() -> {
                if (session.run != this) return;
                for (Output output : session.active) {
                    if (output.settings.volumePercent() == gain) {
                        output.stop();
                        output.callbacks.failed(failure);
                    }
                }
                renderers.execute(() -> {
                    synchronized (this) { failedGains.clear(gain); }
                });
            });
        }

        private void terminal(Throwable error) {
            if (renderTask != null) renderTask.cancel(false);
            onMain(() -> {
                if (session.run != this) return;
                if (error == null) session.finish(null, true);
                else session.failed(error, loaded.version());
            });
        }

        private void stop() {
            stopped = true;
            if (renderTask != null) renderTask.cancel(false);
            renderers.execute(this::release);
        }

        private synchronized void release() {
            try {
                loaded.close();
            } finally {
                try { player.destroy(); }
                finally { closeVariants(); }
            }
        }

        private void closeVariants() {
            variants.values().forEach(Variant::close);
            variants.clear();
        }
    }

    private record EncodedFrame(long sequence, long version, long pauseVersion, byte[] bytes) { }

    private final class Variant implements AutoCloseable {
        private final AudioEncoder encoder;
        private final AtomicReferenceArray<EncodedFrame> frames = new AtomicReferenceArray<>(WINDOW_FRAMES);
        private volatile long latest;

        private Variant() throws Exception {
            encoder = voiceServer.createOpusEncoder(true);
            try {
                if (!encoder.isOpen()) encoder.open();
            } catch (Exception error) {
                encoder.close();
                throw error;
            }
        }

        private void publish(EncodedFrame frame) {
            frames.set((int) (frame.sequence() % WINDOW_FRAMES), frame);
            latest = frame.sequence();
        }

        private EncodedFrame next(long wanted) {
            long head = latest;
            if (head == 0L || wanted > head) return null;
            long sequence = Math.max(wanted, head - MAX_LAG_FRAMES + 1);
            EncodedFrame frame = frames.get((int) (sequence % WINDOW_FRAMES));
            if (frame == null || frame.sequence() != sequence) {
                frame = frames.get((int) (head % WINDOW_FRAMES));
            }
            return frame;
        }

        @Override public void close() { encoder.close(); }
    }

    private final class Output implements PlaybackSession {
        @Override public String mediaVersion() {
            var run = session.run;
            return run == null ? null : run.loaded.version().id();
        }

        private final Session session;
        private final Location location;
        private final String name;
        private final PlaybackCallbacks callbacks;
        private volatile JukeboxSoundSettings settings;
        private boolean requested;
        private volatile boolean stopped;
        private AudioSender sender;
        private ServerProximitySource<?> source;
        private Run frameRun;
        private long frameVersion = -1;
        private long framePauseVersion = -1;
        private Variant frameVariant;
        private long next;
        private EncodedFrame selected;
        private boolean sending;

        private Output(Session session, Location location, String name, JukeboxSoundSettings settings, PlaybackCallbacks callbacks) {
            this.session = session;
            this.location = location;
            this.name = name;
            this.settings = settings;
            this.callbacks = callbacks;
        }

        @Override public void prepare() { session.prepare(); }

        @Override public void start() {
            if (stopped || requested) return;
            requested = true;
            session.refreshOutputs();
            session.prepare();
            session.startIfReady();
        }

        private void attach() {
            if (stopped || sender != null) return;
            if (!callbacks.isValid()) {
                stop();
                callbacks.cancelled();
                return;
            }
            try {
                var position = new ServerPos3d(voiceServer.getMinecraftServer().getWorld(location.getWorld()),
                        location.getBlockX() + 0.5, location.getBlockY() + 1.5, location.getBlockZ() + 0.5);
                var voiceSource = sourceLine.createStaticSource(position, true);
                source = voiceSource;
                voiceSource.setName(name);
                voiceSource.<VoicePlayer>addFilter(player -> {
                    UUID id = player.getInstance().getUuid();
                    return audibleToPlayer.test(id) && outputAudience.test(location, id);
                });
                var output = new AudioSender(this::nextFrame, (bytes, sequence) -> {
                    if (stopped || frameRun != session.run || selected == null || session.group.paused()
                            || selected.version() != session.group.currentSeek().version()
                            || selected.pauseVersion() != session.group.currentPause().version()
                            || frameVariant != frameRun.variants.get(settings.volumePercent())) return false;
                    return voiceSource.sendAudioFrame(bytes, sequence, (short) settings.rangeBlocks());
                }, sequence -> voiceSource.sendAudioEnd(sequence, (short) settings.rangeBlocks()));
                sender = output;
                output.onStop(() -> onMain(() -> {
                    if (!stopped) {
                        stop();
                        callbacks.failed(new IllegalStateException("Voice output stopped unexpectedly"));
                    }
                }));
                output.start();
                callbacks.started();
            } catch (RuntimeException failure) {
                stop();
                callbacks.failed(failure);
            }
        }

        private AudioFrameResult nextFrame() {
            if (stopped) return AudioFrameResult.Finished.INSTANCE;
            Run current = session.run;
            long version = session.group.currentSeek().version();
            var pause = session.group.currentPause();
            Variant variant = current == null ? null : current.variants.get(settings.volumePercent());
            if (frameRun != current || frameVersion != version || framePauseVersion != pause.version()
                    || frameVariant != variant) {
                frameRun = current;
                frameVersion = version;
                framePauseVersion = pause.version();
                frameVariant = variant;
                next = variant == null ? 0L : variant.latest;
                if (sending) return endFrames();
            }
            if (variant == null || pause.paused() || !hasAudience.test(location)) {
                return sending ? endFrames() : IDLE;
            }
            EncodedFrame frame = variant.next(next);
            if (frame == null || frame.version() != version || frame.pauseVersion() != pause.version()) return IDLE;
            next = frame.sequence() + 1;
            selected = frame;
            sending = true;
            return new AudioFrameResult.Provided(frame.bytes());
        }

        private AudioFrameResult endFrames() {
            sending = false;
            selected = null;
            return AudioFrameResult.EndOfStream.INSTANCE;
        }

        @Override public JukeboxPlaybackSnapshot snapshot() {
            var active = session.snapshot();
            return stopped && active.status() != PlaybackStatus.FINISHED ? active.withStatus(PlaybackStatus.STOPPED) : active;
        }
        @Override public void updateSettings(JukeboxSoundSettings updated) { settings = updated; }

        @Override public void stop() {
            if (stopped) return;
            try { release(); }
            finally { session.detach(this); }
        }

        private void release() {
            stopped = true;
            try {
                if (sender != null) sender.stop();
            } finally {
                if (source != null) source.remove();
            }
        }
    }
}
