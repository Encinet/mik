package org.encinet.mik.module.music.rhythm;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFlow;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuMovementPolicy;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuState;
import org.encinet.mik.module.menu.FloatingMenuViewpoint;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackGateway;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackIsolation;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackSnapshot;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackState;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCalibrationAudioOutput;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCalibrationPattern;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCuePresentation;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCuePresentationLedger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates opt-in movement input, scoring, and selectable rhythm scenes. */
public final class RhythmGameService implements Listener, AutoCloseable,
        RhythmCalibrationStatus {
    private static final long LOOK_AHEAD_MILLIS = 2_100L;
    private static final long MINIMUM_REACTION_MILLIS = 900L;
    private static final long FLASH_MILLIS = 360L;
    private static final long CALIBRATION_CAPTURE_WINDOW_MILLIS = 400L;
    private static final long CALIBRATION_INTRO_MILLIS = 2_000L;
    private static final long CALIBRATION_TRANSITION_MILLIS = 1_500L;
    private static final long CALIBRATION_BEAT_PREVIEW_MILLIS = 3_200L;
    private static final long CALIBRATION_RESULT_MILLIS = 3_000L;
    private static final long CALIBRATION_AUDIO_STALL_NANOS = 600_000_000L;
    private static final long CALIBRATION_AUDIO_STALL_CONFIRM_NANOS = 1_000_000_000L;
    private static final int CALIBRATION_AUDIO_STALL_CONSECUTIVE_TICKS = 2;
    private static final long CALIBRATION_AUDIO_STALL_STATUS_NANOS = 1_800_000_000L;
    private static final long CALIBRATION_LATE_CUE_MILLIS = 150L;
    private static final long GAME_JOIN_DELAY_MILLIS = 3_000L;
    private static final long GAME_GO_OVERLAY_MILLIS = 700L;
    private static final long GAME_JUDGEMENT_OVERLAY_MILLIS = 720L;
    private static final long GAME_STATE_RETENTION_MILLIS = 5_000L;
    private static final double NOTE_SPAWN_UP = 1.38;
    private static final double HIT_LINE_UP = -0.42;
    private static final double LANE_FORWARD = 0.30;
    private static final double LANE_COLUMN_SPACING = 0.58;
    private static final int NEUTRAL_HOTBAR_SLOT = 8;
    private static final int NO_CAPTURED_HOTBAR_SLOT = -1;
    private static final double[] GUIDE_PROGRESS = {0.18, 0.36, 0.54, 0.72, 0.90};
    private static final double[] RADIAL_TRAIL_OFFSETS = {0.055, 0.11};
    private static final Map<RhythmInput, FloatingMenuPoint> TARGETS = targetPositions();

    private final JavaPlugin plugin;
    private final RhythmPlaybackGateway playbackGateway;
    private final RhythmPlaybackIsolation playbackIsolation;
    private final RhythmCalibrationAudioOutput calibrationAudioOutput;
    private final RhythmInputTimestampSource inputTimestamps =
            new RhythmInputTimestampSource();
    private final LanguageService languageService;
    private final Map<UUID, ActiveGame> activeGames = new java.util.HashMap<>();
    private final Map<UUID, ActiveCalibration> activeCalibrations =
            new java.util.HashMap<>();
    private final Map<UUID, RhythmDifficulty> preferredDifficulties =
            new java.util.HashMap<>();
    private final Map<UUID, RhythmGameMode> preferredModes = new java.util.HashMap<>();
    private final FloatingMenuScreen<ModeSelectorView> modeScreen;
    private final FloatingMenuScreen<SelectorView> selectorScreen;
    private final FloatingMenuScreen<GameView> gameScreen;
    private final FloatingMenuScreen<CalibrationView> calibrationScreen;
    private final NamespacedKey minecraftJudgementOffsetKey;
    private final NamespacedKey minecraftAnimationOffsetKey;
    private final NamespacedKey plasmoJudgementOffsetKey;
    private final NamespacedKey plasmoAnimationOffsetKey;
    private BukkitTask tickTask;

    public RhythmGameService(JavaPlugin plugin, RhythmPlaybackGateway playbackGateway,
                             RhythmPlaybackIsolation playbackIsolation,
                             RhythmCalibrationAudioOutput calibrationAudioOutput,
                             LanguageService languageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playbackGateway = Objects.requireNonNull(playbackGateway, "playbackGateway");
        this.playbackIsolation = Objects.requireNonNull(
                playbackIsolation, "playbackIsolation");
        this.calibrationAudioOutput = Objects.requireNonNull(
                calibrationAudioOutput, "calibrationAudioOutput");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.minecraftJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_judgement_ms");
        this.minecraftAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_animation_ms");
        this.plasmoJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_judgement_ms");
        this.plasmoAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_animation_ms");
        this.modeScreen = new FloatingMenuScreen<>("jukebox-rhythm-mode",
                context -> renderModeSelector(context.player(), context.state()));
        this.selectorScreen = new FloatingMenuScreen<>("jukebox-rhythm-difficulty",
                context -> renderSelector(context.player(), context.state()));
        this.gameScreen = new FloatingMenuScreen<>("jukebox-rhythm",
                context -> render(context.player(), context.state()));
        this.calibrationScreen = new FloatingMenuScreen<>("jukebox-rhythm-calibration",
                context -> renderCalibration(context.player(), context.state()));
    }

    public void enable() {
        inputTimestamps.enable();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Opens the per-player mode selector for a live custom playback. */
    public boolean open(Player player, Location jukeboxLocation) {
        Objects.requireNonNull(player, "player");
        JukeboxTarget target = JukeboxTarget.at(jukeboxLocation);
        if (!requireCompletedCalibration(player)) return false;
        Optional<RhythmPlaybackSnapshot> current = playback(target);
        if (current.isEmpty() || current.get().status() == RhythmPlaybackState.STOPPED) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
            return false;
        }
        RhythmPlaybackSnapshot playback = current.get();
        if (playback.timeline().complete() && !playback.timeline().playable()) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_NO_BEATS, NamedTextColor.RED));
            return false;
        }

        modeScreen.open(player, new ModeSelectorView(target));
        return true;
    }

    private void start(Player player, JukeboxTarget target,
                       RhythmGameMode mode, RhythmDifficulty difficulty) {
        if (!requireCompletedCalibration(player)) return;
        Optional<RhythmPlaybackSnapshot> current = playback(target);
        if (current.isEmpty() || current.get().status() == RhythmPlaybackState.STOPPED) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
            return;
        }
        if (current.get().timeline().complete()
                && !current.get().timeline().playable()) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_NO_BEATS, NamedTextColor.RED));
            return;
        }
        ActiveCalibration previousCalibration = activeCalibrations.get(
                player.getUniqueId());
        if (previousCalibration != null) {
            removeCalibration(player, previousCalibration);
        }
        ActiveGame previousGame = activeGames.get(player.getUniqueId());
        if (previousGame != null) removeActiveGame(player, previousGame);

        Optional<RhythmPlaybackGateway.Participation> joined = playbackGateway.join(
                target.location().getBlock(), player.getUniqueId());
        if (joined.isEmpty()) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
            return;
        }
        RhythmPlaybackGateway.Participation participation = joined.get();
        Optional<RhythmPlaybackSnapshot> joinedPlayback = playback(target);
        if (joinedPlayback.isEmpty()
                || !participation.playbackId().equals(
                joinedPlayback.get().playbackId())) {
            participation.close();
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
            return;
        }
        RhythmPlaybackSnapshot playback = joinedPlayback.get();
        RhythmChartView chart = new RhythmChartView(playback.timeline(), difficulty,
                playback.positionMillis());
        RhythmLatencyProfile profile = calibrationProfiles(player)
                .forChannel(playback.audioChannel());
        long startedAtNanos = System.nanoTime();
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(
                player.getPing(), profile.judgementOffsetMillis(),
                profile.animationOffsetMillis(), startedAtNanos);
        preparePlayerForCapturedInput(player);
        int heldSlotBeforeGame = captureHotbar(player, mode);
        ActiveGame game = new ActiveGame(target, playback.playbackId(),
                player.getLocation().clone(), InputState.of(player.getCurrentInput()),
                chart, new RhythmGameSession(playback.playbackId(),
                        latency.inputPosition(playback.positionMillis()), difficulty),
                latency, mode, heldSlotBeforeGame, playback.timeline().seed(),
                saturatedAdd(playback.positionMillis(), GAME_JOIN_DELAY_MILLIS),
                participation, new RhythmMonotonicPlaybackClock(
                playback.positionMillis(),
                playback.status() == RhythmPlaybackState.PLAYING,
                startedAtNanos));
        activeGames.put(player.getUniqueId(), game);
        preferredDifficulties.put(player.getUniqueId(), difficulty);
        preferredModes.put(player.getUniqueId(), mode);
        gameScreen.open(player, new GameView(
                player.getUniqueId(), target, playback.playbackId()));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35F, 1.2F);
    }

    private static void preparePlayerForCapturedInput(Player player) {
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (player.isGliding()) player.setGliding(false);
        player.setSprinting(false);
        player.setVelocity(player.getVelocity().zero());
        player.setFallDistance(0.0F);
    }

    private static int captureHotbar(Player player, RhythmGameMode mode) {
        if (mode != RhythmGameMode.FALLING) return NO_CAPTURED_HOTBAR_SLOT;
        int previous = player.getInventory().getHeldItemSlot();
        selectNeutralHotbarSlot(player);
        return previous;
    }

    private static void selectNeutralHotbarSlot(Player player) {
        player.getInventory().setHeldItemSlot(NEUTRAL_HOTBAR_SLOT);
    }

    /** Opens the deterministic multi-stage test; no song or playback is required. */
    public boolean openCalibration(Player player, Location jukeboxLocation) {
        Objects.requireNonNull(player, "player");
        JukeboxTarget target = JukeboxTarget.at(jukeboxLocation);
        if (!isJukeboxAvailable(target)) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_JUKEBOX_UNAVAILABLE, NamedTextColor.RED));
            return false;
        }
        if (!calibrationAudioOutput.available(player)) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_CALIBRATION_VOICE_REQUIRED,
                    NamedTextColor.RED));
            return false;
        }
        startCalibration(player, target);
        return true;
    }

    private void startCalibration(Player player, JukeboxTarget target) {
        if (!isJukeboxAvailable(target)) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_JUKEBOX_UNAVAILABLE, NamedTextColor.RED));
            return;
        }
        ActiveGame previousGame = activeGames.get(player.getUniqueId());
        if (previousGame != null) removeActiveGame(player, previousGame);
        ActiveCalibration previousCalibration = activeCalibrations.get(
                player.getUniqueId());
        if (previousCalibration != null) {
            removeCalibration(player, previousCalibration);
        }
        preparePlayerForCapturedInput(player);
        UUID runId = UUID.randomUUID();
        RhythmPlaybackIsolation.SilenceLease silenceLease =
                playbackIsolation.silenceFor(player);
        ActiveCalibration calibration = new ActiveCalibration(target,
                runId, player.getLocation().clone(),
                InputState.of(player.getCurrentInput()),
                new RhythmLatencyCompensator(player.getPing(), 0,
                        System.nanoTime()),
                captureHotbar(player, RhythmGameMode.FALLING), silenceLease);
        activeCalibrations.put(player.getUniqueId(), calibration);
        try {
            calibrationScreen.open(player, new CalibrationView(
                    player.getUniqueId(), target, runId));
        } catch (RuntimeException exception) {
            removeCalibration(player, calibration);
            throw exception;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.35F, 1.2F);
    }

    @Override
    public boolean hasCompletedLatencyCalibration(Player player) {
        Objects.requireNonNull(player, "player");
        var data = player.getPersistentDataContainer();
        return data.get(minecraftJudgementOffsetKey,
                PersistentDataType.INTEGER) != null
                && data.get(minecraftAnimationOffsetKey,
                PersistentDataType.INTEGER) != null
                && data.get(plasmoJudgementOffsetKey,
                PersistentDataType.INTEGER) != null
                && data.get(plasmoAnimationOffsetKey,
                PersistentDataType.INTEGER) != null;
    }

    private boolean requireCompletedCalibration(Player player) {
        if (hasCompletedLatencyCalibration(player)) return true;
        player.sendActionBar(languageService.text(player,
                Message.MUSIC_RHYTHM_CALIBRATION_REQUIRED,
                NamedTextColor.YELLOW));
        return false;
    }

    private RhythmCalibrationProfiles calibrationProfiles(Player player) {
        var data = player.getPersistentDataContainer();
        return new RhythmCalibrationProfiles(
                readProfile(data, minecraftJudgementOffsetKey,
                        minecraftAnimationOffsetKey),
                readProfile(data, plasmoJudgementOffsetKey,
                        plasmoAnimationOffsetKey));
    }

    private static RhythmLatencyProfile readProfile(
            org.bukkit.persistence.PersistentDataContainer data,
            NamespacedKey judgementKey, NamespacedKey animationKey) {
        Integer judgementStored = data.get(judgementKey,
                PersistentDataType.INTEGER);
        Integer animationStored = data.get(animationKey,
                PersistentDataType.INTEGER);
        return new RhythmLatencyProfile(
                judgementStored == null ? 0 : judgementStored,
                animationStored == null ? 0 : animationStored);
    }

    private void saveCalibration(Player player, RhythmCalibrationProfiles profiles) {
        var data = player.getPersistentDataContainer();
        saveProfile(data, minecraftJudgementOffsetKey,
                minecraftAnimationOffsetKey, profiles.minecraft());
        saveProfile(data, plasmoJudgementOffsetKey,
                plasmoAnimationOffsetKey, profiles.plasmoVoice());
    }

    private static void saveProfile(
            org.bukkit.persistence.PersistentDataContainer data,
            NamespacedKey judgementKey, NamespacedKey animationKey,
            RhythmLatencyProfile profile) {
        data.set(judgementKey, PersistentDataType.INTEGER,
                profile.judgementOffsetMillis());
        data.set(animationKey, PersistentDataType.INTEGER,
                profile.animationOffsetMillis());
    }

    public void resetCalibration(Player player) {
        var data = player.getPersistentDataContainer();
        data.remove(minecraftJudgementOffsetKey);
        data.remove(minecraftAnimationOffsetKey);
        data.remove(plasmoJudgementOffsetKey);
        data.remove(plasmoAnimationOffsetKey);
        player.sendActionBar(languageService.text(player,
                Message.MUSIC_RHYTHM_CALIBRATION_RESET_DONE,
                NamedTextColor.GREEN));
        modeScreen.flow(player).ifPresent(FloatingMenuFlow::redraw);
    }

    private void tick() {
        long latencySampleNanos = System.nanoTime();
        for (Map.Entry<UUID, ActiveGame> entry : List.copyOf(activeGames.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ActiveGame game = entry.getValue();
            if (player == null || !player.isOnline()) {
                if (activeGames.remove(entry.getKey(), game)) {
                    game.participation.close();
                }
                continue;
            }
            Optional<FloatingMenuFlow<GameView>> flow = gameScreen.flow(player);
            if (flow.isEmpty()) {
                removeActiveGame(player, game);
                continue;
            }
            FloatingMenuState menuState = flow.get().handle().state();
            if (menuState == FloatingMenuState.SUSPENDED) {
                removeActiveGame(player, game);
                flow.get().close();
                continue;
            }
            Optional<RhythmPlaybackSnapshot> current = playback(game.target);
            if (current.isEmpty() || !current.get().playbackId().equals(game.playbackId)
                    || current.get().status() == RhythmPlaybackState.STOPPED) {
                finish(player, game, true);
                continue;
            }
            RhythmPlaybackSnapshot playback = current.get();
            game.playbackClock.observe(playback.positionMillis(),
                    playback.status() == RhythmPlaybackState.PLAYING,
                    latencySampleNanos);
            if (playback.timeline().complete() && !playback.timeline().playable()) {
                player.sendActionBar(languageService.text(player,
                        Message.MUSIC_RHYTHM_NO_BEATS, NamedTextColor.RED));
                finish(player, game, true);
                continue;
            }
            game.latency.sample(player.getPing(), latencySampleNanos);
            long inputPosition = game.latency.inputPosition(
                    playback.positionMillis());
            long missPosition = game.latency.missPosition(
                    playback.positionMillis());
            player.setFallDistance(0.0F);
            if (player.getVelocity().lengthSquared() > 1.0E-6) {
                player.setVelocity(player.getVelocity().zero());
            }
            if (menuState == FloatingMenuState.ACTIVE) {
                if (!game.ready && playback.positionMillis() >= game.readyAfterMillis
                        && game.chart.preparedThrough(
                        saturatedAdd(playback.positionMillis(),
                                MINIMUM_REACTION_MILLIS))) {
                    game.session.beginAt(inputPosition);
                    game.input = InputState.of(player.getCurrentInput());
                    game.ready = true;
                    game.goVisibleThroughMillis = saturatedAdd(
                            playback.positionMillis(), GAME_GO_OVERLAY_MILLIS);
                    game.lastCountdownNumber = 0;
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_CHIME, 0.48F, 1.72F);
                } else if (!game.ready
                        && playback.status() == RhythmPlaybackState.PLAYING) {
                    playCountdownStep(player, game, playback.positionMillis());
                }
                if (game.ready) {
                    prepareVisibleCues(game, playback.positionMillis());
                    int misses = game.session.advance(missPosition, game.chart);
                    if (misses > 0) playJudgement(player, RhythmJudgement.MISS);
                    game.discardBefore(Math.max(0L, playback.positionMillis()
                            - GAME_STATE_RETENTION_MILLIS));
                }
            }
        }
        tickCalibrations(latencySampleNanos);
    }

    private void tickCalibrations(long latencySampleNanos) {
        for (Map.Entry<UUID, ActiveCalibration> entry :
                List.copyOf(activeCalibrations.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ActiveCalibration calibration = entry.getValue();
            if (player == null || !player.isOnline()) {
                if (activeCalibrations.remove(entry.getKey(), calibration)) {
                    calibration.closeStagePlayback();
                    calibration.silenceLease.close();
                }
                continue;
            }
            Optional<FloatingMenuFlow<CalibrationView>> flow =
                    calibrationScreen.flow(player);
            if (flow.isEmpty()) {
                removeCalibration(player, calibration);
                continue;
            }
            FloatingMenuState menuState = flow.get().handle().state();
            if (menuState == FloatingMenuState.SUSPENDED) {
                removeCalibration(player, calibration);
                flow.get().close();
                continue;
            }
            if (!isJukeboxAvailable(calibration.target)) {
                endCalibration(player, calibration, true);
                continue;
            }
            if ((calibration.stage == CalibrationStage.TRANSITION_TO_PLASMO
                    || calibration.stage == CalibrationStage.PLASMO_LISTEN
                    || calibration.stage == CalibrationStage.PLASMO_AUDIO)
                    && !calibrationAudioOutput.available(player)) {
                player.sendActionBar(languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VOICE_REQUIRED,
                        NamedTextColor.RED));
                endCalibration(player, calibration, true);
                continue;
            }
            calibration.latency.sample(player.getPing(), latencySampleNanos);
            player.setFallDistance(0.0F);
            if (player.getVelocity().lengthSquared() > 1.0E-6) {
                player.setVelocity(player.getVelocity().zero());
            }
            if (menuState == FloatingMenuState.ACTIVE) {
                long nowNanos = System.nanoTime();
                if (calibration.advance(player, nowNanos)
                        == CalibrationAdvance.START_PLASMO
                        && !calibration.startPlasmoStage(
                        player, calibrationAudioOutput)) {
                    player.sendActionBar(languageService.text(player,
                            Message.MUSIC_RHYTHM_CALIBRATION_VOICE_REQUIRED,
                            NamedTextColor.RED));
                    endCalibration(player, calibration, true);
                    continue;
                }
                calibration.consumeResultProfiles().ifPresent(profiles -> {
                    saveCalibration(player, profiles);
                    player.sendActionBar(languageService.text(player,
                            Message.MUSIC_RHYTHM_CALIBRATION_DONE,
                            NamedTextColor.GREEN));
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_CHIME, 0.42F, 1.65F);
                    calibration.showResult(nowNanos);
                });
                if (calibration.resultExpired(nowNanos)) {
                    endCalibration(player, calibration, true);
                    continue;
                }
            }
        }
    }

    private FloatingMenuDefinition renderModeSelector(Player player, ModeSelectorView view) {
        Optional<RhythmPlaybackSnapshot> current = playback(view.target());
        boolean hasPlayback = current.isPresent()
                && current.get().status() != RhythmPlaybackState.STOPPED;
        boolean noBeats = current.isPresent()
                && current.get().timeline().complete()
                && !current.get().timeline().playable();
        boolean available = hasPlayback && !noBeats;
        RhythmGameMode preferred = preferredModes.getOrDefault(
                player.getUniqueId(), RhythmGameMode.FALLING);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "jukebox-rhythm-mode")
                .framing(FloatingMenuFraming.PANORAMIC)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.actions("mode", 2),
                        FloatingMenuLayouts.navigation("navigation")))
                .refreshEvery(5, (p, handle) -> modeScreen.flow(p)
                        .ifPresent(FloatingMenuFlow::redraw));
        Component track = current.<Component>map(playback -> Component.text(
                        truncate(playback.track().details().title(), 48), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true))
                .orElseGet(() -> languageService.text(player,
                        Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
        menu.textDecoration("track", new FloatingMenuPoint(0.0, 0.82, 0.18),
                track, FloatingMenuAppearance.TRANSPARENT, 3.8F, 1.3F, 0.68F,
                FloatingMenuDecoration.Alignment.CENTER);
        for (RhythmGameMode mode : RhythmGameMode.values()) {
            var node = menu.item("mode:" + mode.name(), modeMaterial(mode),
                            modeLabel(player, mode))
                    .region("mode")
                    .selected(mode == preferred)
                    .primary((p, handle) -> {
                        preferredModes.put(p.getUniqueId(), mode);
                        selectorScreen.open(p, new SelectorView(view.target(), mode));
                    });
            if (!available) {
                node.disabled(languageService.text(player,
                        noBeats ? Message.MUSIC_RHYTHM_NO_BEATS
                                : Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK,
                        NamedTextColor.RED));
            }
        }
        menu.back(languageService.text(player,
                        Message.MUSIC_BACK, NamedTextColor.RED))
                .region("navigation");
        return menu.build();
    }

    private FloatingMenuDefinition renderSelector(Player player, SelectorView view) {
        Optional<RhythmPlaybackSnapshot> current = playback(view.target());
        boolean hasPlayback = current.isPresent()
                && current.get().status() != RhythmPlaybackState.STOPPED;
        boolean noBeats = current.isPresent()
                && current.get().timeline().complete()
                && !current.get().timeline().playable();
        boolean available = hasPlayback && !noBeats;
        RhythmDifficulty preferred = preferredDifficulties.getOrDefault(
                player.getUniqueId(), RhythmDifficulty.NORMAL);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "jukebox-rhythm-difficulty")
                .framing(FloatingMenuFraming.PANORAMIC)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.actions("difficulty", 3),
                        FloatingMenuLayouts.navigation("navigation")))
                .refreshEvery(5, (p, handle) -> selectorScreen.flow(p)
                        .ifPresent(FloatingMenuFlow::redraw));
        Component track = current.<Component>map(playback -> Component.text(
                        truncate(playback.track().details().title(), 48), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)
                        .append(Component.newline())
                        .append(languageService.text(player, modeMessage(view.mode()),
                                        modeColor(view.mode()))
                                .decoration(TextDecoration.BOLD, false)))
                .orElseGet(() -> languageService.text(player,
                        Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
        menu.textDecoration("track", new FloatingMenuPoint(0.0, 0.82, 0.18),
                track, FloatingMenuAppearance.TRANSPARENT, 3.8F, 1.3F, 0.68F,
                FloatingMenuDecoration.Alignment.CENTER);
        for (RhythmDifficulty difficulty : RhythmDifficulty.values()) {
            var node = menu.block("difficulty:" + difficulty.name(),
                            difficultyMaterial(difficulty),
                            difficultyLabel(player, difficulty))
                    .region("difficulty")
                    .selected(difficulty == preferred)
                    .primary((p, handle) -> start(
                            p, view.target(), view.mode(), difficulty));
            if (!available) {
                node.disabled(languageService.text(player,
                        noBeats ? Message.MUSIC_RHYTHM_NO_BEATS
                                : Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK,
                        NamedTextColor.RED));
            }
        }
        menu.back(languageService.text(player,
                        Message.MUSIC_BACK, NamedTextColor.RED))
                .region("navigation");
        return menu.build();
    }

    private FloatingMenuDefinition renderCalibration(Player player,
                                                      CalibrationView view) {
        ActiveCalibration calibration = activeCalibrations.get(view.playerId());
        if (calibration == null || !view.runId().equals(calibration.runId)
                || !isJukeboxAvailable(view.target())) {
            return unavailableCalibration(player, view);
        }
        long renderAtNanos = System.nanoTime();
        long now = calibration.positionMillis(renderAtNanos);
        long visualNow = now;
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "jukebox-rhythm-calibration")
                .framing(FloatingMenuFraming.PANORAMIC)
                .requireSpatialPresentation()
                .stableAnchor()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .layout(FloatingMenuLayouts.fixedPoses(Map.of(
                        "exit", FloatingMenuPose.at(
                                new FloatingMenuPoint(2.08, 0.83, 0.28)))))
                .refreshEvery(1, (p, handle) -> handle.update(
                        renderCalibration(p, view)))
                .observeFrames((presentedPlayer, decorationId, sentAtNanos) ->
                        calibration.visualFramePresented(decorationId,
                                sentAtNanos))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        removeCalibration(closedPlayer, view.runId());
                    }
                });

        Component title = languageService.text(player,
                Message.MUSIC_RHYTHM_LATENCY_TEST, NamedTextColor.GOLD);
        Component stageLine;
        Component instruction;
        boolean sampling = false;
        switch (calibration.stage) {
            case INTRO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_MINECRAFT,
                                NamedTextColor.GOLD)
                        .append(Component.text("  →  ", NamedTextColor.DARK_GRAY))
                        .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO,
                                NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text("  →  ", NamedTextColor.DARK_GRAY))
                        .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_INTRO,
                        NamedTextColor.GRAY);
            }
            case MINECRAFT_AUDIO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_MINECRAFT,
                                NamedTextColor.GOLD)
                        .append(Component.text("  ·  1/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_MINECRAFT_INSTRUCTION,
                        NamedTextColor.GOLD);
                sampling = true;
            }
            case MINECRAFT_LISTEN -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_MINECRAFT,
                                NamedTextColor.GOLD)
                        .append(Component.text("  ·  1/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                        NamedTextColor.YELLOW);
            }
            case TRANSITION_TO_PLASMO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO,
                                NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("  ·  2/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_PLASMO_READY,
                        NamedTextColor.LIGHT_PURPLE)
                        .append(Component.newline())
                        .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                                NamedTextColor.GRAY));
            }
            case PLASMO_AUDIO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO,
                                NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("  ·  2/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_PLASMO_INSTRUCTION,
                        NamedTextColor.LIGHT_PURPLE);
                sampling = true;
            }
            case PLASMO_LISTEN -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO,
                                NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("  ·  2/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        calibration.plasmoRealigning(renderAtNanos)
                                ? Message.MUSIC_RHYTHM_CALIBRATION_REALIGNING
                                : Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                        calibration.plasmoRealigning(renderAtNanos)
                                ? NamedTextColor.RED : NamedTextColor.YELLOW);
            }
            case TRANSITION_TO_VISUAL -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA)
                        .append(Component.text("  ·  3/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                        NamedTextColor.GRAY);
            }
            case VISUAL_LISTEN -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA)
                        .append(Component.text("  ·  3/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                        NamedTextColor.YELLOW);
            }
            case VISUAL -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA)
                        .append(Component.text("  ·  3/3",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_INSTRUCTION,
                        NamedTextColor.AQUA);
                sampling = true;
            }
            case RESULT -> {
                stageLine = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_DONE,
                        NamedTextColor.GREEN);
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_RESULT_HOLD,
                        NamedTextColor.GRAY);
            }
            default -> throw new IllegalStateException(
                    "Unknown calibration stage " + calibration.stage);
        }
        Component status = title
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(stageLine.decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(instruction.decoration(TextDecoration.BOLD, false));
        if (sampling) {
            RhythmLatencyCalibration measurement = calibration.measurement();
            Component progress = measurement.outOfSupportedRange()
                    ? languageService.text(player,
                    Message.MUSIC_RHYTHM_CALIBRATION_OUT_OF_RANGE,
                    NamedTextColor.RED)
                    : calibration.noInputWarning(renderAtNanos)
                    ? languageService.text(player,
                    Message.MUSIC_RHYTHM_CALIBRATION_NO_INPUT,
                    NamedTextColor.YELLOW)
                    : calibrationProgress(player, measurement);
            status = status.append(Component.newline())
                    .append(progress);
        } else if (calibration.pauseSecondsRemaining() > 0) {
            status = status.append(Component.newline())
                    .append(Component.text("⏱ "
                            + calibration.pauseSecondsRemaining() + " s",
                            NamedTextColor.YELLOW));
        }
        menu.textDecoration("calibration-status",
                new FloatingMenuPoint(-2.12, 0.82, 0.34), status,
                FloatingMenuAppearance.TRANSPARENT, 4.2F, 2.7F, 0.62F,
                FloatingMenuDecoration.Alignment.LEFT);
        menu.navigation("exit", exitLabel(player))
                .primary((p, handle) -> cancelCalibration(p, handle));

        FloatingMenuPoint target = new FloatingMenuPoint(
                0.0, HIT_LINE_UP, LANE_FORWARD);
        if (calibration.visualStage()) {
            for (int marker = -4; marker <= 4; marker++) {
                menu.blockDecoration("calibration-line:" + marker,
                        new FloatingMenuPoint(marker * 0.29, HIT_LINE_UP,
                                LANE_FORWARD - 0.02),
                        Material.LIGHT_GRAY_STAINED_GLASS, 0.045F,
                        FloatingMenuDecoration.Motion.NONE);
            }
            menu.blockDecoration("calibration-target", FloatingMenuPose.at(target),
                    Material.GLASS, 0.48F, FloatingMenuDecoration.Motion.NONE);

            long earliest = Math.max(0L,
                    visualNow - CALIBRATION_CAPTURE_WINDOW_MILLIS);
            long cueIndex = calibration.pattern.cueIndexAtOrAfter(earliest);
            int rendered = 0;
            for (int scanned = 0; scanned < 10 && rendered < 6; scanned++) {
                RhythmCue cue = calibration.cue(cueIndex + scanned);
                if (cue.timeMillis() > saturatedAdd(visualNow,
                        LOOK_AHEAD_MILLIS)) break;
                if (calibration.stage == CalibrationStage.VISUAL
                        && calibration.visualMeasurement.sampled(cue.id())) continue;
                calibration.planVisualCue(cue, renderAtNanos);
                double progress = 1.0 - (cue.timeMillis() - visualNow)
                        / (double) LOOK_AHEAD_MILLIS;
                menu.decoration(FloatingMenuDecoration.block(
                        ActiveCalibration.visualDecorationId(cue.id()),
                        FloatingMenuPose.at(fallingPoint(target,
                                Math.clamp(progress, 0.0, 1.14))),
                        new ItemStack(Material.SEA_LANTERN),
                        (float) (0.25 + cue.strength() * 0.10),
                        FloatingMenuDecoration.Motion.NONE).tracking());
                rendered++;
            }
        } else {
            Material phaseMaterial = switch (calibration.stage) {
                case INTRO -> Material.CALIBRATED_SCULK_SENSOR;
                case TRANSITION_TO_PLASMO, TRANSITION_TO_VISUAL -> Material.REPEATER;
                case RESULT -> Material.EMERALD_BLOCK;
                case MINECRAFT_LISTEN, MINECRAFT_AUDIO -> Material.NOTE_BLOCK;
                case PLASMO_LISTEN, PLASMO_AUDIO -> Material.JUKEBOX;
                case VISUAL_LISTEN, VISUAL -> throw new IllegalStateException(
                        "visual stage is rendered separately");
            };
            menu.blockDecoration("calibration-speaker", FloatingMenuPose.at(
                            new FloatingMenuPoint(0.0, 0.05, LANE_FORWARD)),
                    phaseMaterial,
                    calibration.stage == CalibrationStage.RESULT ? 0.62F : 0.52F,
                    FloatingMenuDecoration.Motion.NONE);
        }
        return menu.build();
    }

    private FloatingMenuDefinition unavailableCalibration(Player player,
                                                           CalibrationView view) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "jukebox-rhythm-calibration")
                .requireSpatialPresentation()
                .stableAnchor()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .layout(FloatingMenuLayouts.adaptiveColumn(0.25))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        removeCalibration(closedPlayer, view.runId());
                    }
                });
        menu.information("unavailable", languageService.text(player,
                Message.MUSIC_JUKEBOX_UNAVAILABLE, NamedTextColor.RED));
        menu.back(languageService.text(player, Message.MUSIC_BACK,
                NamedTextColor.RED));
        return menu.build();
    }

    private FloatingMenuDefinition render(Player player, GameView view) {
        ActiveGame game = activeGames.get(view.playerId());
        Optional<RhythmPlaybackSnapshot> current = playback(view.target());
        if (game == null || current.isEmpty()
                || !view.playbackId().equals(current.get().playbackId())) {
            return unavailable(player, view);
        }
        RhythmPlaybackSnapshot playback = current.get();
        // Keep motion on the server playback clock. Its packets naturally incur
        // the downlink half of RTT; applying the judgement rewind here as well
        // would make notes visibly late on the client.
        long now = playback.positionMillis();
        long judgementNow = game.latency.inputPosition(now);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-rhythm")
                .framing(FloatingMenuFraming.PANORAMIC)
                .requireSpatialPresentation()
                .stableAnchor()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .layout(FloatingMenuLayouts.fixedPoses(Map.of(
                        "exit", FloatingMenuPose.at(new FloatingMenuPoint(2.08, 0.83, 0.28)))))
                .refreshEvery(1, (p, handle) -> handle.update(render(p, view)))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        removeActiveGame(closedPlayer, view.playbackId());
                    }
                });

        menu.textDecoration("status", new FloatingMenuPoint(-2.12, 0.82, 0.34),
                statusPanel(player, playback, game, judgementNow),
                FloatingMenuAppearance.TRANSPARENT,
                3.2F, 2.1F, 0.66F, FloatingMenuDecoration.Alignment.LEFT);
        menu.navigation("exit", exitLabel(player))
                .primary((p, handle) -> exit(p, handle));

        // The definition is rebuilt inside the floating-menu animation tick, so
        // no fixed one-tick prediction is needed before teleport packets are sent.
        long sceneNow = game.latency.visualPosition(now, 0L);
        List<RhythmCue> visible = game.ready
                ? game.chart.between(
                        Math.max(0L, sceneNow
                                - game.session.difficulty().goodWindowMillis()),
                        saturatedAdd(sceneNow, LOOK_AHEAD_MILLIS))
                : List.of();
        switch (game.mode) {
            case FALLING -> renderFallingScene(menu, player, game, sceneNow,
                    judgementNow, visible);
            case RADIAL -> renderRadialScene(menu, game, sceneNow,
                    judgementNow, visible);
        }
        renderGameOverlay(menu, player, playback, game, judgementNow);
        return menu.build();
    }

    private void renderGameOverlay(FloatingMenuDefinition.Builder menu, Player player,
                                   RhythmPlaybackSnapshot playback, ActiveGame game,
                                   long judgementNow) {
        Component overlay;
        int background = 0xB8181B22;
        float scale = 1.02F;
        if (playback.status() != RhythmPlaybackState.PLAYING
                || !playback.timeline().complete()) {
            overlay = languageService.text(player,
                            Message.MUSIC_RHYTHM_CHART_PREPARING,
                            NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .append(Component.newline())
                    .append(Component.text("◌  " + playbackTime(playback),
                                    NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false));
        } else if (!game.ready) {
            int number = countdownNumber(playback.positionMillis(),
                    game.readyAfterMillis);
            overlay = Component.text(number, countdownColor(number))
                    .decoration(TextDecoration.BOLD, true)
                    .append(Component.newline())
                    .append(languageService.text(player,
                                    Message.MUSIC_RHYTHM_GET_READY,
                                    NamedTextColor.WHITE)
                            .decoration(TextDecoration.BOLD, false));
            scale = 1.28F;
        } else if (playback.positionMillis() <= game.goVisibleThroughMillis) {
            overlay = languageService.text(player, Message.MUSIC_RHYTHM_GO,
                            NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true);
            background = FloatingMenuAppearance.TRANSPARENT;
            scale = 1.34F;
        } else {
            RhythmGameSession.View view = game.session.view();
            if (view.lastJudgement() == RhythmJudgement.NONE
                    || judgementNow - view.lastJudgementAtMillis()
                    >= GAME_JUDGEMENT_OVERLAY_MILLIS) {
                return;
            }
            overlay = judgementText(player, view.lastJudgement())
                    .decoration(TextDecoration.BOLD, true);
            if (view.combo() >= 2) {
                overlay = overlay.append(Component.newline())
                        .append(languageService.text(player,
                                        Message.MUSIC_RHYTHM_COMBO,
                                        NamedTextColor.WHITE, view.combo())
                                .decoration(TextDecoration.BOLD, false));
            }
            background = FloatingMenuAppearance.TRANSPARENT;
            scale = 0.94F;
        }
        menu.textDecoration("game-overlay",
                new FloatingMenuPoint(0.0, 0.24, 0.18), overlay,
                background, 3.6F, 1.75F, scale,
                FloatingMenuDecoration.Alignment.CENTER);
    }

    static int countdownNumber(long playbackPositionMillis,
                               long readyAfterMillis) {
        long remaining = Math.max(1L, readyAfterMillis - playbackPositionMillis);
        return (int) Math.clamp((remaining + 999L) / 1_000L, 1L, 3L);
    }

    private static NamedTextColor countdownColor(int number) {
        return switch (number) {
            case 3 -> NamedTextColor.AQUA;
            case 2 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GOLD;
        };
    }

    private static void playCountdownStep(Player player, ActiveGame game,
                                          long playbackPositionMillis) {
        int number = countdownNumber(playbackPositionMillis,
                game.readyAfterMillis);
        if (number == game.lastCountdownNumber) return;
        game.lastCountdownNumber = number;
        float pitch = switch (number) {
            case 3 -> 0.90F;
            case 2 -> 1.05F;
            default -> 1.25F;
        };
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT,
                0.42F, pitch);
    }

    private void renderFallingScene(FloatingMenuDefinition.Builder menu, Player player,
                                    ActiveGame game, long now, long judgementNow,
                                    List<RhythmCue> visible) {
        for (int marker = 0; marker <= 8; marker++) {
            menu.blockDecoration("hit-line:" + marker,
                    new FloatingMenuPoint(-1.16 + marker * 0.29,
                            HIT_LINE_UP, LANE_FORWARD - 0.02),
                    Material.LIGHT_GRAY_STAINED_GLASS, 0.045F,
                    FloatingMenuDecoration.Motion.NONE);
        }

        RhythmGameSession.View sessionView = game.session.view();
        for (RhythmInput input : RhythmInput.values()) {
            FloatingMenuPoint target = TARGETS.get(input);
            Material targetMaterial = targetMaterial(input, sessionView,
                    judgementNow);
            menu.blockDecoration("target:" + input.name(),
                    FloatingMenuPose.at(target),
                    targetMaterial, 0.44F, FloatingMenuDecoration.Motion.NONE);
            menu.textDecoration("label:" + input.name(),
                    FloatingMenuPose.at(targetOffset(target, 0.0, -0.34, 0.37)),
                    actionLabel(input), FloatingMenuAppearance.TRANSPARENT,
                    1.6F, 1.1F, 0.54F, FloatingMenuDecoration.Alignment.CENTER);
            menu.blockDecoration("source:" + input.name(),
                    FloatingMenuPose.at(fallingPoint(target, 0.0)),
                    normalMaterial(input), 0.16F, FloatingMenuDecoration.Motion.BOB);
            for (int guide = 0; guide < GUIDE_PROGRESS.length; guide++) {
                menu.blockDecoration("guide:" + input.name() + ':' + guide,
                        FloatingMenuPose.at(
                                fallingPoint(target, GUIDE_PROGRESS[guide])),
                        normalMaterial(input), 0.055F,
                        FloatingMenuDecoration.Motion.NONE);
            }
        }

        int rendered = 0;
        for (RhythmCue cue : visible) {
            if (game.session.isJudged(cue.id()) || rendered++ >= 18) continue;
            FloatingMenuPoint target = TARGETS.get(cue.input());
            double progress = 1.0 - (cue.timeMillis() - now) / (double) LOOK_AHEAD_MILLIS;
            double bounded = Math.clamp(progress, 0.0, 1.14);
            float scale = (float) (0.22 + cue.strength() * 0.10);
            FloatingMenuDecoration decoration = FloatingMenuDecoration.block(
                    "cue:" + cue.id(),
                    FloatingMenuPose.at(fallingPoint(target, bounded)),
                    new ItemStack(cueMaterial(cue.input())), scale,
                    FloatingMenuDecoration.Motion.NONE).tracking();
            menu.decoration(decoration);
        }
    }

    private void renderRadialScene(FloatingMenuDefinition.Builder menu, ActiveGame game,
                                   long now, long judgementNow,
                                   List<RhythmCue> visible) {
        int rendered = 0;
        for (RhythmCue cue : visible) {
            if (game.session.isJudged(cue.id()) || rendered++ >= 18) continue;
            double angle = game.radialPath.angleDegrees(cue);
            double progress = radialProgress(cue, now);
            double bounded = Math.clamp(progress, -0.12, 1.16);
            Location point = RhythmRadialPath.point(game.anchor, angle, bounded);
            float scale = radialCueScale(cue, bounded);
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "radial:cue:" + cue.id(), point, angle + 90.0, 0.0,
                    new ItemStack(radialCueMaterial(cue, now,
                            game.session.difficulty())), scale,
                    FloatingMenuDecoration.Motion.SPIN).tracking());

            for (int trail = 0; trail < RADIAL_TRAIL_OFFSETS.length; trail++) {
                Location trailPoint = RhythmRadialPath.point(game.anchor, angle,
                        bounded - RADIAL_TRAIL_OFFSETS[trail]);
                float trailScale = scale * (0.46F - trail * 0.12F);
                menu.decoration(FloatingMenuDecoration.worldBlock(
                        "radial:trail:" + cue.id() + ':' + trail,
                        trailPoint, angle + 90.0, 0.0,
                        new ItemStack(Material.PURPLE_STAINED_GLASS), trailScale,
                        FloatingMenuDecoration.Motion.NONE).tracking());
            }
        }

        RadialHitEffect effect = game.radialHitEffect;
        if (effect != null && judgementNow >= effect.atMillis()
                && judgementNow - effect.atMillis() < FLASH_MILLIS) {
            double life = (judgementNow - effect.atMillis()) / (double) FLASH_MILLIS;
            float scale = (float) (0.78 - life * 0.42);
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "radial:hit", effect.location(), 0.0, 0.0,
                    new ItemStack(judgementMaterial(effect.judgement())), scale,
                    FloatingMenuDecoration.Motion.SPIN).tracking());
        }
    }

    private FloatingMenuDefinition unavailable(Player player, GameView view) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-rhythm")
                .requireSpatialPresentation()
                .stableAnchor()
                .viewpoint(FloatingMenuViewpoint.STANDING)
                .layout(FloatingMenuLayouts.adaptiveColumn(0.25))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        removeActiveGame(closedPlayer, view.playbackId());
                    }
                });
        menu.information("unavailable", languageService.text(player,
                Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
        menu.navigation("exit", exitLabel(player))
                .primary((p, handle) -> exit(p, handle));
        return menu.build();
    }

    private Component exitLabel(Player player) {
        return Component.text("[", NamedTextColor.GOLD)
                .append(Component.keybind("key.sneak", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.GOLD))
                .decoration(TextDecoration.BOLD, true)
                .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_EXIT, NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component statusPanel(Player player, RhythmPlaybackSnapshot playback,
                                  ActiveGame game, long judgementNow) {
        RhythmGameSession.View view = game.session.view();
        Component panel = Component.text(truncate(playback.track().details().title(), 42),
                        NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(languageService.text(player, Message.MUSIC_RHYTHM_SCORE,
                        NamedTextColor.GOLD, view.score()))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, Message.MUSIC_RHYTHM_COMBO,
                        view.combo() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                        view.combo()))
                .append(Component.newline())
                .append(Component.text(playbackTime(playback), NamedTextColor.GRAY))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player,
                        difficultyMessage(game.session.difficulty()),
                        NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, modeMessage(game.mode),
                        modeColor(game.mode)))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, Message.MUSIC_RHYTHM_LATENCY,
                        NamedTextColor.GRAY,
                        game.latency.totalCompensationMillis()))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player,
                        Message.MUSIC_RHYTHM_ANIMATION_OFFSET,
                        NamedTextColor.GRAY,
                        signedMillis(game.latency.animationOffsetMillis())));
        if (game.mode == RhythmGameMode.RADIAL) {
            panel = panel.append(Component.newline())
                    .append(Component.keybind("key.attack", NamedTextColor.AQUA))
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.use", NamedTextColor.AQUA))
                    .append(Component.text("  ·  360°", NamedTextColor.GRAY));
        }
        if (playback.status() != RhythmPlaybackState.PLAYING
                || !playback.timeline().complete()
                || !game.chart.preparedThrough(
                playback.positionMillis() + MINIMUM_REACTION_MILLIS)) {
            return panel.append(Component.newline())
                    .append(languageService.text(player,
                            Message.MUSIC_RHYTHM_CHART_PREPARING,
                            NamedTextColor.AQUA));
        }
        if (!game.ready) {
            return panel.append(Component.newline())
                    .append(languageService.text(player,
                            Message.MUSIC_RHYTHM_GET_READY,
                            NamedTextColor.YELLOW))
                    .append(Component.text(" · " + countdownNumber(
                                    playback.positionMillis(), game.readyAfterMillis),
                            NamedTextColor.GOLD));
        }
        if (view.lastJudgement() != RhythmJudgement.NONE
                && judgementNow - view.lastJudgementAtMillis() < 850L) {
            return panel.append(Component.newline()).append(judgementText(player,
                    view.lastJudgement()));
        }
        return panel;
    }

    private void exit(Player player, org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game != null) removeActiveGame(player, game);
        handle.back();
    }

    private void cancelCalibration(
            Player player, org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        ActiveCalibration calibration = activeCalibrations.get(player.getUniqueId());
        if (calibration != null) removeCalibration(player, calibration);
        handle.back();
    }

    private void endCalibration(Player player, ActiveCalibration expected,
                                boolean resumeParent) {
        if (!removeCalibration(player, expected)) return;
        calibrationScreen.flow(player).ifPresent(flow -> {
            if (resumeParent && flow.handle().state() != FloatingMenuState.SUSPENDED) {
                flow.back();
            } else {
                flow.close();
            }
        });
    }

    private void removeCalibration(Player player, UUID runId) {
        ActiveCalibration calibration = activeCalibrations.get(player.getUniqueId());
        if (calibration != null && calibration.runId.equals(runId)) {
            removeCalibration(player, calibration);
        }
    }

    private boolean removeCalibration(Player player, ActiveCalibration expected) {
        if (!activeCalibrations.remove(player.getUniqueId(), expected)) return false;
        expected.closeStagePlayback();
        expected.silenceLease.close();
        player.getInventory().setHeldItemSlot(expected.heldSlotBeforeCalibration);
        return true;
    }

    private void finish(Player player, ActiveGame expected, boolean resumeParent) {
        if (!removeActiveGame(player, expected)) return;
        gameScreen.flow(player).ifPresent(flow -> {
            if (resumeParent && flow.handle().state() != FloatingMenuState.SUSPENDED) {
                flow.back();
            } else {
                flow.close();
            }
        });
    }

    private void removeActiveGame(Player player, UUID playbackId) {
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game != null && game.playbackId.equals(playbackId)) {
            removeActiveGame(player, game);
        }
    }

    private boolean removeActiveGame(Player player, ActiveGame expected) {
        if (!activeGames.remove(player.getUniqueId(), expected)) return false;
        expected.participation.close();
        if (expected.heldSlotBeforeGame != NO_CAPTURED_HOTBAR_SLOT) {
            player.getInventory().setHeldItemSlot(expected.heldSlotBeforeGame);
        }
        return true;
    }

    private void prepareVisibleCues(ActiveGame game, long playbackPositionMillis) {
        long from = Math.max(Math.max(0L, playbackPositionMillis
                        - game.session.difficulty().goodWindowMillis()),
                game.preparedThroughMillis == Long.MAX_VALUE
                        ? Long.MAX_VALUE : game.preparedThroughMillis + 1L);
        long through = saturatedAdd(playbackPositionMillis, LOOK_AHEAD_MILLIS);
        for (RhythmCue cue : game.chart.between(from, through)) {
            if (cue.timeMillis() - playbackPositionMillis
                    < MINIMUM_REACTION_MILLIS) {
                game.session.ignore(cue);
            }
        }
        game.preparedThroughMillis = Math.max(game.preparedThroughMillis, through);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        ActiveCalibration calibration = activeCalibrations.get(player.getUniqueId());
        if (calibration != null) {
            InputState next = InputState.of(event.getInput());
            boolean exitPressed = calibration.input.exitPressed(next);
            calibration.input = next;
            if (exitPressed) endCalibration(player, calibration, true);
            return;
        }
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game == null) return;
        InputState next = InputState.of(event.getInput());
        boolean exitPressed = game.input.exitPressed(next);
        game.input = next;
        if (exitPressed) {
            finish(player, game, true);
        }
    }

    /** Scores hotbar actions 1-4, then immediately returns the selection to 9. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        RhythmInputTimestampSource.TimedInput timedInput =
                inputTimestamps.claimHotbar(player.getUniqueId(),
                        event.getNewSlot(), System.nanoTime());
        ActiveCalibration calibration = activeCalibrations.get(player.getUniqueId());
        if (calibration != null) {
            event.setCancelled(true);
            selectNeutralHotbarSlot(player);
            RhythmInput.fromHotbarSlot(event.getNewSlot())
                    .ifPresent(ignored -> calibrationHit(player, calibration,
                            timedInput));
            return;
        }
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game == null || game.mode != RhythmGameMode.FALLING) return;
        event.setCancelled(true);
        selectNeutralHotbarSlot(player);
        if (!game.ready) return;
        RhythmInput.fromHotbarSlot(event.getNewSlot())
                .ifPresent(input -> judge(player, game, List.of(input),
                        timedInput));
    }

    /** Prevents the former F binding from moving inventory items during play. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (activeCalibrations.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game != null && game.mode == RhythmGameMode.FALLING) event.setCancelled(true);
    }

    /** Left-click air and display attacks arrive as a main-arm animation. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        ActiveGame game = radialGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        radialClick(event.getPlayer(), game, radialInput(event.getPlayer()));
    }

    /** Captures both mouse buttons when they target air or a real block. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getAction().isLeftClick()
                || event.getAction().isRightClick())) {
            return;
        }
        ActiveGame game = radialGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        radialClick(event.getPlayer(), game, radialInput(event.getPlayer()));
    }

    /** Right-clicks intercepted by a real entity still count as radial input. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ActiveGame game = radialGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        radialClick(event.getPlayer(), game, radialInput(event.getPlayer()));
    }

    /** Fallback for clicks that directly address a client-only display entity. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (!event.isAttack() && event.getHand() != EquipmentSlot.HAND) return;
        ActiveGame game = radialGame(event.getPlayer());
        if (game != null) {
            radialClick(event.getPlayer(), game, radialInput(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialBlockDamage(BlockDamageEvent event) {
        if (radialGame(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ActiveGame game = radialGame(player);
        if (game == null) return;
        event.setCancelled(true);
        radialClick(player, game, radialInput(player));
    }

    private ActiveGame radialGame(Player player) {
        ActiveGame game = activeGames.get(player.getUniqueId());
        return game != null && game.mode == RhythmGameMode.RADIAL ? game : null;
    }

    private RhythmInputTimestampSource.TimedInput radialInput(Player player) {
        return inputTimestamps.claimRadial(player.getUniqueId(), System.nanoTime());
    }

    private void radialClick(Player player, ActiveGame game,
                             RhythmInputTimestampSource.TimedInput timedInput) {
        if (!game.ready || !game.claimRadialClick(Bukkit.getCurrentTick())) return;
        Optional<RhythmPlaybackSnapshot> current = playback(game.target);
        if (current.isEmpty() || !current.get().playbackId().equals(game.playbackId)) return;
        RhythmPlaybackSnapshot playback = current.get();
        long handledAtNanos = System.nanoTime();
        game.playbackClock.observe(playback.positionMillis(),
                playback.status() == RhythmPlaybackState.PLAYING,
                handledAtNanos);
        long packetPosition = game.playbackClock.positionAt(
                timedInput.receivedAtNanos());
        long judgementPosition = game.latency.inputPosition(packetPosition);
        // Reconstruct the scene packet the player clicked. The render lead has
        // already been consumed by the following-tick menu teleport, so adding
        // it again here would aim one extra server tick ahead of the visible cube.
        long visualAimPosition = game.latency.visualNetworkPosition(
                packetPosition);
        long window = game.session.difficulty().goodWindowMillis();
        List<RhythmCue> candidates = game.chart.between(
                        Math.max(0L, judgementPosition - window),
                        saturatedAdd(judgementPosition, window)).stream()
                .filter(cue -> !game.session.isJudged(cue.id()))
                .toList();
        java.util.ArrayList<RhythmRadialAim.Target> targets =
                new java.util.ArrayList<>(candidates.size());
        for (RhythmCue cue : candidates) {
            double angle = game.radialPath.angleDegrees(cue);
            double progress = Math.clamp(radialProgress(cue, visualAimPosition),
                    -0.12, 1.16);
            Location location = RhythmRadialPath.point(game.anchor, angle, progress);
            targets.add(new RhythmRadialAim.Target(cue, location.toVector(),
                    radialAimRadius(cue, progress)));
        }
        Location eye = player.getEyeLocation();
        Optional<RhythmRadialAim.Target> selected = RhythmRadialAim.select(
                eye.toVector(), eye.getDirection(), targets);
        if (selected.isEmpty()) return;

        RhythmGameSession.Result result = game.session.hit(
                selected.get().cue(), judgementPosition, game.chart);
        if (result.judgement() == RhythmJudgement.NONE) return;
        org.bukkit.util.Vector center = selected.get().center();
        Location hitAt = new Location(game.anchor.getWorld(),
                center.getX(), center.getY(), center.getZ());
        game.radialHitEffect = new RadialHitEffect(
                hitAt, result.judgement(), judgementPosition);
        player.spawnParticle(Particle.END_ROD, hitAt, 12,
                0.22, 0.22, 0.22, 0.035);
        player.spawnParticle(Particle.CRIT, hitAt, 18,
                0.28, 0.28, 0.28, 0.08);
        playJudgement(player, result.judgement());
    }

    private void calibrationHit(Player player, ActiveCalibration calibration,
                                RhythmInputTimestampSource.TimedInput timedInput) {
        if (!calibration.claimInput(Bukkit.getCurrentTick())) return;
        if (!calibration.acceptsInput()) return;
        int compensationMillis = calibration.latency.compensationMillis();
        long adjustedInputAtNanos = adjustNanos(
                timedInput.receivedAtNanos(), compensationMillis);
        Optional<RhythmCuePresentation> closest =
                calibration.closestPresentation(adjustedInputAtNanos);
        if (closest.isEmpty()) return;
        long errorNanos = adjustedInputAtNanos
                - closest.get().presentedAtNanos();
        int errorMillis = (int) Math.clamp(Math.round(
                errorNanos / 1_000_000.0), Integer.MIN_VALUE,
                Integer.MAX_VALUE);
        if (Math.abs((long) errorMillis)
                > CALIBRATION_CAPTURE_WINDOW_MILLIS) return;
        calibration.noteInput(timedInput.receivedAtNanos());

        RhythmLatencyCalibration measurement = calibration.measurement();
        RhythmLatencyCalibration.SampleResult sample = measurement.record(
                new RhythmLatencyCalibration.Observation(
                        closest.get().cueId(), closest.get().cycleIndex(),
                        closest.get().cueIndex(), closest.get().cuesPerCycle(),
                        errorMillis, timedInput.coarse()));
        if (sample != RhythmLatencyCalibration.SampleResult.COMPLETE) return;

        RhythmLatencyCalibration.Estimate estimate = measurement.estimate();
        long nowNanos = System.nanoTime();
        if (calibration.stage == CalibrationStage.MINECRAFT_AUDIO) {
            calibration.beginPlasmoTransition(estimate, nowNanos);
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_CALIBRATION_PLASMO_READY,
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (calibration.stage == CalibrationStage.PLASMO_AUDIO) {
            calibration.beginVisualTransition(estimate, nowNanos);
            return;
        }
        if (calibration.stage == CalibrationStage.VISUAL) {
            calibration.finishFromVisualStage(estimate, nowNanos);
        }
    }

    private static long adjustNanos(long timestampNanos, int delayMillis) {
        long delayNanos = delayMillis * 1_000_000L;
        if (delayNanos >= 0L) {
            return timestampNanos < Long.MIN_VALUE + delayNanos
                    ? Long.MIN_VALUE : timestampNanos - delayNanos;
        }
        long advance = -delayNanos;
        return timestampNanos > Long.MAX_VALUE - advance
                ? Long.MAX_VALUE : timestampNanos + advance;
    }

    private void judge(Player player, ActiveGame game, List<RhythmInput> pressed,
                       RhythmInputTimestampSource.TimedInput timedInput) {
        Optional<RhythmPlaybackSnapshot> current = playback(game.target);
        if (current.isEmpty() || !current.get().playbackId().equals(game.playbackId)) return;
        RhythmPlaybackSnapshot playback = current.get();
        long handledAtNanos = System.nanoTime();
        game.playbackClock.observe(playback.positionMillis(),
                playback.status() == RhythmPlaybackState.PLAYING,
                handledAtNanos);
        long compensated = game.latency.inputPosition(
                game.playbackClock.positionAt(timedInput.receivedAtNanos()));
        RhythmGameSession.Result result = game.session.input(
                pressed, compensated, game.chart);
        if (result.judgement() != RhythmJudgement.NONE) {
            playJudgement(player, result.judgement());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            Location to = event.getTo();
            if (to == null) return;
            if (!to.getWorld().equals(calibration.anchor.getWorld())) {
                endCalibration(event.getPlayer(), calibration, false);
                return;
            }
            if (capturePositionChange(event, calibration.anchor)) {
                event.getPlayer().setFallDistance(0.0F);
            }
            return;
        }
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (game == null || to == null) return;
        if (!to.getWorld().equals(game.anchor.getWorld())) {
            finish(event.getPlayer(), game, false);
            return;
        }
        if (capturePositionChange(event, game.anchor)) {
            event.getPlayer().setFallDistance(0.0F);
        }
    }

    /**
     * Cancelling returns the player to {@code from} without another event. Rewriting
     * {@code to} would make Paper perform a PLUGIN teleport, which the rhythm game
     * correctly treats as a real exit.
     */
    static boolean capturePositionChange(PlayerMoveEvent event, Location anchor) {
        Location to = event.getTo();
        if (to == null || to.getX() == anchor.getX() && to.getY() == anchor.getY()
                && to.getZ() == anchor.getZ()) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            endCalibration(event.getPlayer(), calibration, false);
        }
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        if (game != null) finish(event.getPlayer(), game, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ActiveCalibration calibration = activeCalibrations.get(player.getUniqueId());
        if (calibration != null) endCalibration(player, calibration, true);
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game != null) finish(player, game, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            endCalibration(event.getPlayer(), calibration, false);
        }
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        if (game != null) finish(event.getPlayer(), game, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) removeCalibration(event.getPlayer(), calibration);
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        if (game != null) removeActiveGame(event.getPlayer(), game);
        gameScreen.forget(event.getPlayer());
        calibrationScreen.forget(event.getPlayer());
        selectorScreen.forget(event.getPlayer());
        modeScreen.forget(event.getPlayer());
        preferredDifficulties.remove(event.getPlayer().getUniqueId());
        preferredModes.remove(event.getPlayer().getUniqueId());
        inputTimestamps.forget(event.getPlayer().getUniqueId());
    }

    private Optional<RhythmPlaybackSnapshot> playback(JukeboxTarget target) {
        Location location = target.location();
        if (!isJukeboxAvailable(target)) {
            return Optional.empty();
        }
        return playbackGateway.rhythmPlayback(location.getBlock());
    }

    private static boolean isJukeboxAvailable(JukeboxTarget target) {
        Location location = target.location();
        return location != null && location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && location.getBlock().getState() instanceof Jukebox;
    }

    private Component actionLabel(RhythmInput input) {
        return Component.keybind("key.hotbar." + input.hotbarNumber(), keyColor(input))
                .decoration(TextDecoration.BOLD, true);
    }

    private Component calibrationProgress(Player player,
                                          RhythmLatencyCalibration measurement) {
        Message status = measurement.adapting()
                ? Message.MUSIC_RHYTHM_CALIBRATION_ADAPTING
                : Message.MUSIC_RHYTHM_CALIBRATION_ACCUMULATING;
        NamedTextColor color = measurement.adapting()
                ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        return Component.text(measurement.adapting() ? "◇ " : "◆ ", color)
                .append(languageService.text(player, status, color));
    }

    private static String signedMillis(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private Component modeLabel(Player player, RhythmGameMode mode) {
        Component hint = switch (mode) {
            case FALLING -> Component.keybind("key.hotbar.1", NamedTextColor.GRAY)
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.hotbar.2", NamedTextColor.GRAY))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.hotbar.3", NamedTextColor.GRAY))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.hotbar.4", NamedTextColor.GRAY));
            case RADIAL -> Component.keybind("key.attack", NamedTextColor.GRAY)
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.use", NamedTextColor.GRAY))
                    .append(Component.text("  ·  360°  ·  Δ≤28°",
                            NamedTextColor.GRAY));
        };
        return languageService.text(player, modeMessage(mode), modeColor(mode))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(hint.decoration(TextDecoration.BOLD, false));
    }

    private Component difficultyLabel(Player player, RhythmDifficulty difficulty) {
        String density = java.math.BigDecimal.valueOf(difficulty.maximumCuesPerSecond())
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        String multiplier = java.math.BigDecimal.valueOf(difficulty.scoreMultiplier())
                .stripTrailingZeros().toPlainString();
        return languageService.text(player, difficultyMessage(difficulty),
                        difficultyColor(difficulty))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(Component.text("≤ " + density + "/s  ·  ±"
                                + difficulty.goodWindowMillis() + " ms  ·  ×" + multiplier,
                        NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component judgementText(Player player, RhythmJudgement judgement) {
        return languageService.text(player, judgementMessage(judgement),
                judgementColor(judgement));
    }

    private void playJudgement(Player player, RhythmJudgement judgement) {
        switch (judgement) {
            case PERFECT -> player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 0.32F, 1.75F);
            case GREAT -> player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_HARP, 0.28F, 1.45F);
            case GOOD -> player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_HAT, 0.25F, 1.05F);
            case MISS -> player.playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, 0.22F, 0.72F);
            case NONE -> { }
        }
    }

    private static Material targetMaterial(RhythmInput input,
                                           RhythmGameSession.View view, long now) {
        if (view.lastInput() != input || now - view.lastJudgementAtMillis() > FLASH_MILLIS) {
            return normalMaterial(input);
        }
        return switch (view.lastJudgement()) {
            case PERFECT -> Material.EMERALD_BLOCK;
            case GREAT -> Material.GOLD_BLOCK;
            case GOOD -> Material.LAPIS_BLOCK;
            case MISS -> Material.REDSTONE_BLOCK;
            case NONE -> normalMaterial(input);
        };
    }

    private static Material judgementMaterial(RhythmJudgement judgement) {
        return switch (judgement) {
            case PERFECT -> Material.EMERALD_BLOCK;
            case GREAT -> Material.GOLD_BLOCK;
            case GOOD -> Material.LAPIS_BLOCK;
            case MISS -> Material.REDSTONE_BLOCK;
            case NONE -> Material.TARGET;
        };
    }

    private static double radialProgress(RhythmCue cue, long playbackPositionMillis) {
        return 1.0 - (cue.timeMillis() - playbackPositionMillis)
                / (double) LOOK_AHEAD_MILLIS;
    }

    private static float radialCueScale(RhythmCue cue, double progress) {
        double approach = Math.clamp(progress, 0.0, 1.0);
        return (float) (0.42 + cue.strength() * 0.16 + approach * 0.04);
    }

    private static double radialAimRadius(RhythmCue cue, double progress) {
        return Math.max(0.38, radialCueScale(cue, progress) * 0.78);
    }

    private static Material radialCueMaterial(RhythmCue cue, long now,
                                              RhythmDifficulty difficulty) {
        long error = now - cue.timeMillis();
        long absoluteError = Math.abs(error);
        if (absoluteError <= difficulty.perfectWindowMillis()) {
            return Material.SEA_LANTERN;
        }
        if (absoluteError <= difficulty.greatWindowMillis()) {
            return Material.AMETHYST_BLOCK;
        }
        if (error > 0L) return Material.REDSTONE_BLOCK;
        if (absoluteError <= difficulty.goodWindowMillis()) {
            return Material.PURPLE_CONCRETE;
        }
        return Material.MAGENTA_CONCRETE;
    }

    private static Material normalMaterial(RhythmInput input) {
        return switch (input) {
            case ONE -> Material.MAGENTA_STAINED_GLASS;
            case TWO -> Material.ORANGE_STAINED_GLASS;
            case THREE -> Material.CYAN_STAINED_GLASS;
            case FOUR -> Material.LIME_STAINED_GLASS;
        };
    }

    private static Material cueMaterial(RhythmInput input) {
        return switch (input) {
            case ONE -> Material.MAGENTA_CONCRETE;
            case TWO -> Material.ORANGE_CONCRETE;
            case THREE -> Material.CYAN_CONCRETE;
            case FOUR -> Material.LIME_CONCRETE;
        };
    }

    private static Message difficultyMessage(RhythmDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Message.MUSIC_RHYTHM_DIFFICULTY_EASY;
            case NORMAL -> Message.MUSIC_RHYTHM_DIFFICULTY_NORMAL;
            case HARD -> Message.MUSIC_RHYTHM_DIFFICULTY_HARD;
            case EXPERT -> Message.MUSIC_RHYTHM_DIFFICULTY_EXPERT;
        };
    }

    private static Message modeMessage(RhythmGameMode mode) {
        return switch (mode) {
            case FALLING -> Message.MUSIC_RHYTHM_MODE_FALLING;
            case RADIAL -> Message.MUSIC_RHYTHM_MODE_RADIAL;
        };
    }

    private static NamedTextColor modeColor(RhythmGameMode mode) {
        return switch (mode) {
            case FALLING -> NamedTextColor.AQUA;
            case RADIAL -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private static Material modeMaterial(RhythmGameMode mode) {
        return switch (mode) {
            case FALLING -> Material.SAND;
            case RADIAL -> Material.ENDER_EYE;
        };
    }

    private static NamedTextColor difficultyColor(RhythmDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.AQUA;
            case HARD -> NamedTextColor.GOLD;
            case EXPERT -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private static Material difficultyMaterial(RhythmDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Material.LIME_STAINED_GLASS;
            case NORMAL -> Material.CYAN_STAINED_GLASS;
            case HARD -> Material.ORANGE_STAINED_GLASS;
            case EXPERT -> Material.MAGENTA_STAINED_GLASS;
        };
    }

    private static Message judgementMessage(RhythmJudgement judgement) {
        return switch (judgement) {
            case PERFECT -> Message.MUSIC_RHYTHM_PERFECT;
            case GREAT -> Message.MUSIC_RHYTHM_GREAT;
            case GOOD -> Message.MUSIC_RHYTHM_GOOD;
            case MISS -> Message.MUSIC_RHYTHM_MISS;
            case NONE -> throw new IllegalArgumentException("NONE has no visible message");
        };
    }

    private static NamedTextColor judgementColor(RhythmJudgement judgement) {
        return switch (judgement) {
            case PERFECT -> NamedTextColor.AQUA;
            case GREAT -> NamedTextColor.GREEN;
            case GOOD -> NamedTextColor.YELLOW;
            case MISS -> NamedTextColor.RED;
            case NONE -> NamedTextColor.GRAY;
        };
    }

    private static NamedTextColor keyColor(RhythmInput input) {
        return switch (input) {
            case ONE -> NamedTextColor.LIGHT_PURPLE;
            case TWO -> NamedTextColor.GOLD;
            case THREE -> NamedTextColor.AQUA;
            case FOUR -> NamedTextColor.GREEN;
        };
    }

    private static Map<RhythmInput, FloatingMenuPoint> targetPositions() {
        EnumMap<RhythmInput, FloatingMenuPoint> positions = new EnumMap<>(RhythmInput.class);
        positions.put(RhythmInput.ONE, new FloatingMenuPoint(
                -1.5 * LANE_COLUMN_SPACING, HIT_LINE_UP, LANE_FORWARD));
        positions.put(RhythmInput.TWO, new FloatingMenuPoint(
                -0.5 * LANE_COLUMN_SPACING, HIT_LINE_UP, LANE_FORWARD));
        positions.put(RhythmInput.THREE, new FloatingMenuPoint(
                0.5 * LANE_COLUMN_SPACING, HIT_LINE_UP, LANE_FORWARD));
        positions.put(RhythmInput.FOUR, new FloatingMenuPoint(
                1.5 * LANE_COLUMN_SPACING, HIT_LINE_UP, LANE_FORWARD));
        return Map.copyOf(positions);
    }

    static FloatingMenuPoint fallingPoint(FloatingMenuPoint target, double progress) {
        double up = NOTE_SPAWN_UP + progress * (target.up() - NOTE_SPAWN_UP);
        return new FloatingMenuPoint(target.right(), up, target.forward());
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private static FloatingMenuPoint targetOffset(FloatingMenuPoint target,
                                                   double right, double up, double forward) {
        return new FloatingMenuPoint(target.right() + right, target.up() + up,
                target.forward() + forward);
    }

    private static String playbackTime(RhythmPlaybackSnapshot playback) {
        String elapsed = duration(playback.positionMillis());
        Duration duration = playback.track().details().audio().duration();
        return duration == null ? elapsed : elapsed + " / " + duration(duration.toMillis());
    }

    private static String duration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return "%d:%02d".formatted(seconds / 60L, seconds % 60L);
    }

    private static String truncate(String value, int maximum) {
        if (value.length() <= maximum) return value;
        return value.substring(0, maximum - 1) + "…";
    }

    @Override
    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID playerId : List.copyOf(activeGames.keySet())) {
            ActiveGame game = activeGames.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (game != null && player != null) {
                removeActiveGame(player, game);
            } else if (game != null && activeGames.remove(playerId, game)) {
                game.participation.close();
            }
            gameScreen.forget(playerId);
        }
        for (UUID playerId : List.copyOf(activeCalibrations.keySet())) {
            ActiveCalibration calibration = activeCalibrations.get(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (calibration != null) {
                if (player != null) {
                    removeCalibration(player, calibration);
                } else if (activeCalibrations.remove(playerId, calibration)) {
                    calibration.closeStagePlayback();
                    calibration.silenceLease.close();
                }
            }
            calibrationScreen.forget(playerId);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            selectorScreen.forget(player);
            modeScreen.forget(player);
        }
        activeGames.clear();
        activeCalibrations.clear();
        preferredDifficulties.clear();
        preferredModes.clear();
        calibrationAudioOutput.close();
        inputTimestamps.close();
    }

    private static final class ActiveCalibration {
        private final JukeboxTarget target;
        private final UUID runId;
        private final Location anchor;
        private final RhythmCalibrationPattern pattern;
        private InputState input;
        private final RhythmLatencyCompensator latency;
        private final int heldSlotBeforeCalibration;
        private final RhythmPlaybackIsolation.SilenceLease silenceLease;
        private final RhythmLatencyCalibration minecraftMeasurement =
                new RhythmLatencyCalibration();
        private final RhythmLatencyCalibration plasmoMeasurement =
                new RhythmLatencyCalibration();
        private final RhythmLatencyCalibration visualMeasurement =
                new RhythmLatencyCalibration();
        private final RhythmCuePresentationLedger inputPresentations =
                new RhythmCuePresentationLedger();
        private final Map<String, RhythmCuePresentation> expectedVisualCommits =
                new HashMap<>();
        private CalibrationStage stage = CalibrationStage.INTRO;
        private long stageStartedAtNanos = System.nanoTime();
        private long nextAudioCue;
        private long plasmoPhysicalBaseCycle;
        private long plasmoLogicalBaseCycle;
        private long highestPlasmoLogicalCycle = -1L;
        private int visualTapOffsetMillis;
        private int minecraftTapOffsetMillis;
        private int plasmoTapOffsetMillis;
        private RhythmCalibrationProfiles pendingResultProfiles;
        private RhythmCalibrationAudioOutput.StagePlayback stagePlayback;
        private int lastInputTick = Integer.MIN_VALUE;
        private long inputExpectedSinceNanos = Long.MIN_VALUE;
        private long lastObservedInputNanos = Long.MIN_VALUE;
        private int plasmoStallTicks;
        private long lastPlasmoRealignAtNanos = Long.MIN_VALUE;

        private ActiveCalibration(JukeboxTarget target, UUID runId,
                                  Location anchor, InputState input,
                                  RhythmLatencyCompensator latency,
                                  int heldSlotBeforeCalibration,
                                  RhythmPlaybackIsolation.SilenceLease silenceLease) {
            this.target = Objects.requireNonNull(target, "target");
            this.runId = Objects.requireNonNull(runId, "runId");
            this.anchor = Objects.requireNonNull(anchor, "anchor");
            this.pattern = RhythmCalibrationPattern.fixed();
            this.input = Objects.requireNonNull(input, "input");
            this.latency = Objects.requireNonNull(latency, "latency");
            this.heldSlotBeforeCalibration = heldSlotBeforeCalibration;
            this.silenceLease = Objects.requireNonNull(
                    silenceLease, "silenceLease");
        }

        private boolean claimInput(int tick) {
            if (lastInputTick == tick) return false;
            lastInputTick = tick;
            return true;
        }

        private CalibrationAdvance advance(Player player, long nowNanos) {
            long now = positionMillis(nowNanos);
            if (stage == CalibrationStage.INTRO
                    && now >= CALIBRATION_INTRO_MILLIS) {
                stage = CalibrationStage.MINECRAFT_LISTEN;
                resetStageClock(nowNanos);
                playMinecraftCues(player, 0L, nowNanos, false);
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.MINECRAFT_LISTEN) {
                if (now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
                    stage = CalibrationStage.MINECRAFT_AUDIO;
                    minecraftMeasurement.reset();
                    resetStageClock(nowNanos);
                    beginInputWindow(nowNanos);
                    playMinecraftCues(player, 0L, nowNanos, true);
                } else {
                    playMinecraftCues(player, now, nowNanos, false);
                }
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.MINECRAFT_AUDIO) {
                playMinecraftCues(player, now, nowNanos, true);
                minecraftMeasurement.advanceToCycle(now / pattern.durationMillis());
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.TRANSITION_TO_PLASMO
                    && now >= CALIBRATION_TRANSITION_MILLIS) {
                stage = CalibrationStage.PLASMO_LISTEN;
                resetStageClock(nowNanos);
                return CalibrationAdvance.START_PLASMO;
            }
            if (stage == CalibrationStage.PLASMO_LISTEN
                    || stage == CalibrationStage.PLASMO_AUDIO) {
                return advancePlasmo(nowNanos);
            }
            if (stage == CalibrationStage.TRANSITION_TO_VISUAL
                    && now >= CALIBRATION_TRANSITION_MILLIS) {
                stage = CalibrationStage.VISUAL_LISTEN;
                visualMeasurement.reset();
                resetStageClock(nowNanos);
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.VISUAL_LISTEN
                    && now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
                stage = CalibrationStage.VISUAL;
                visualMeasurement.reset();
                resetStageClock(nowNanos);
                beginInputWindow(nowNanos);
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.VISUAL) {
                visualMeasurement.advanceToCycle(now / pattern.durationMillis());
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
                stage = CalibrationStage.PLASMO_AUDIO;
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
            stage = CalibrationStage.PLASMO_LISTEN;
            lastPlasmoRealignAtNanos = nowNanos;
            inputPresentations.clear();
            stageStartedAtNanos = nowNanos;
        }

        private void playMinecraftCues(Player player, long nowMillis,
                                       long nowNanos, boolean collect) {
            RhythmCue cue = cue(nextAudioCue);
            while (cue.timeMillis() != Long.MAX_VALUE
                    && cue.timeMillis() <= nowMillis) {
                long lateness = nowMillis - cue.timeMillis();
                nextAudioCue++;
                if (lateness <= CALIBRATION_LATE_CUE_MILLIS) {
                    float volume = (float) (0.62 + cue.strength() * 0.22);
                    float pitch = cue.strength() >= 0.9 ? 0.88F : 1.0F;
                    player.playSound(player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
                            org.bukkit.SoundCategory.RECORDS, volume, pitch);
                    if (collect) inputPresentations.add(presentation(
                            nextAudioCue - 1L, System.nanoTime()));
                }
                cue = cue(nextAudioCue);
            }
        }

        private RhythmCue cue(long index) {
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

        private void planVisualCue(RhythmCue cue, long nowNanos) {
            if (stage != CalibrationStage.VISUAL) return;
            long globalIndex = cue.id() - 1L;
            long plannedAt = saturatedAddNanos(stageStartedAtNanos,
                    cue.timeMillis() * 1_000_000L);
            inputPresentations.add(presentation(globalIndex, plannedAt));
            if (Math.abs(cue.timeMillis() - positionMillis(nowNanos)) <= 60L) {
                expectedVisualCommits.put(visualDecorationId(cue.id()),
                        presentation(globalIndex, plannedAt));
            }
        }

        private void visualFramePresented(String decorationId,
                                          long presentedAtNanos) {
            RhythmCuePresentation expected = expectedVisualCommits.remove(decorationId);
            if (expected == null || stage != CalibrationStage.VISUAL) return;
            inputPresentations.add(new RhythmCuePresentation(expected.cueId(),
                    expected.cycleIndex(), expected.cueIndex(),
                    expected.cuesPerCycle(), presentedAtNanos,
                    expected.strength()));
        }

        private static String visualDecorationId(long cueId) {
            return "calibration-cue:" + cueId;
        }

        private RhythmLatencyCalibration measurement() {
            return switch (stage) {
                case MINECRAFT_AUDIO -> minecraftMeasurement;
                case PLASMO_AUDIO -> plasmoMeasurement;
                case VISUAL -> visualMeasurement;
                default -> throw new IllegalStateException(
                        "stage does not collect a raw latency measurement: " + stage);
            };
        }

        private Optional<RhythmCuePresentation> closestPresentation(
                long adjustedInputAtNanos) {
            return inputPresentations.closest(adjustedInputAtNanos,
                    acceptsRawMeasurement() ? measurement()::sampled
                            : cueId -> false);
        }

        private void beginPlasmoTransition(
                RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
            minecraftTapOffsetMillis = estimate.offsetMillis();
            plasmoMeasurement.reset();
            plasmoLogicalBaseCycle = 0L;
            highestPlasmoLogicalCycle = -1L;
            stage = CalibrationStage.TRANSITION_TO_PLASMO;
            closeStagePlayback();
            resetStageClock(nowNanos);
        }

        private void beginVisualTransition(
                RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
            plasmoTapOffsetMillis = estimate.offsetMillis();
            visualMeasurement.reset();
            stage = CalibrationStage.TRANSITION_TO_VISUAL;
            closeStagePlayback();
            resetStageClock(nowNanos);
        }

        private void finishFromVisualStage(
                RhythmLatencyCalibration.Estimate estimate, long nowNanos) {
            visualTapOffsetMillis = estimate.offsetMillis();
            pendingResultProfiles = RhythmCalibrationProfiles.fromTests(
                    visualTapOffsetMillis, minecraftTapOffsetMillis,
                    plasmoTapOffsetMillis);
            resetStageClock(nowNanos);
            beginInputWindow(nowNanos);
        }

        private Optional<RhythmCalibrationProfiles> consumeResultProfiles() {
            Optional<RhythmCalibrationProfiles> pending =
                    Optional.ofNullable(pendingResultProfiles);
            pendingResultProfiles = null;
            return pending;
        }

        private void finishResultNow(long nowNanos) {
            stage = CalibrationStage.RESULT;
            showResult(nowNanos);
        }

        private boolean startPlasmoStage(
                Player player, RhythmCalibrationAudioOutput output) {
            closeStagePlayback();
            stagePlayback = output.play(player, pattern).orElse(null);
            return stagePlayback != null;
        }

        private void closeStagePlayback() {
            RhythmCalibrationAudioOutput.StagePlayback playback = stagePlayback;
            stagePlayback = null;
            if (playback != null) playback.close();
        }

        private void showResult(long nowNanos) {
            closeStagePlayback();
            stage = CalibrationStage.RESULT;
            resetStageClock(nowNanos);
        }

        private boolean resultExpired(long nowNanos) {
            return stage == CalibrationStage.RESULT
                    && positionMillis(nowNanos) >= CALIBRATION_RESULT_MILLIS;
        }

        private boolean acceptsRawMeasurement() {
            return stage == CalibrationStage.MINECRAFT_AUDIO
                    || stage == CalibrationStage.PLASMO_AUDIO
                    || stage == CalibrationStage.VISUAL;
        }

        private boolean acceptsInput() {
            return acceptsRawMeasurement()
                    || stage == CalibrationStage.TRANSITION_TO_VISUAL
                    || stage == CalibrationStage.VISUAL_LISTEN
                    || stage == CalibrationStage.VISUAL;
        }

        private void noteInput(long receivedAtNanos) {
            lastObservedInputNanos = receivedAtNanos;
        }

        private void beginInputWindow(long nowNanos) {
            inputExpectedSinceNanos = nowNanos;
            lastObservedInputNanos = Long.MIN_VALUE;
        }

        private boolean noInputWarning(long nowNanos) {
            if (!acceptsInput() || inputExpectedSinceNanos == Long.MIN_VALUE) {
                return false;
            }
            long reference = lastObservedInputNanos == Long.MIN_VALUE
                    ? inputExpectedSinceNanos : lastObservedInputNanos;
            long threshold = pattern.durationMillis() * 1_000_000L;
            return nowNanos >= reference && nowNanos - reference >= threshold;
        }

        private boolean plasmoRealigning(long nowNanos) {
            return lastPlasmoRealignAtNanos != Long.MIN_VALUE
                    && (nowNanos - lastPlasmoRealignAtNanos)
                    <= CALIBRATION_AUDIO_STALL_STATUS_NANOS
                    && stage == CalibrationStage.PLASMO_LISTEN;
        }

        private boolean visualStage() {
            return stage == CalibrationStage.VISUAL_LISTEN
                    || stage == CalibrationStage.VISUAL
                    || stage == CalibrationStage.TRANSITION_TO_VISUAL;
        }

        private long positionMillis() {
            return positionMillis(System.nanoTime());
        }

        private long positionMillis(long nowNanos) {
            if (nowNanos <= stageStartedAtNanos) return 0L;
            return (nowNanos - stageStartedAtNanos) / 1_000_000L;
        }

        private void resetStageClock(long nowNanos) {
            stageStartedAtNanos = nowNanos;
            nextAudioCue = 0L;
            inputPresentations.clear();
            expectedVisualCommits.clear();
        }

        private int pauseSecondsRemaining() {
            long duration = switch (stage) {
                case INTRO -> CALIBRATION_INTRO_MILLIS;
                case TRANSITION_TO_PLASMO, TRANSITION_TO_VISUAL -> CALIBRATION_TRANSITION_MILLIS;
                case MINECRAFT_LISTEN, VISUAL_LISTEN ->
                        CALIBRATION_BEAT_PREVIEW_MILLIS;
                case RESULT -> CALIBRATION_RESULT_MILLIS;
                default -> 0L;
            };
            if (duration == 0L) return 0;
            long remaining = Math.max(0L, duration - positionMillis());
            return (int) Math.max(1L, (remaining + 999L) / 1_000L);
        }

        private static int median(List<Integer> values) {
            int[] ordered = values.stream().mapToInt(Integer::intValue)
                    .sorted().toArray();
            if (ordered.length == 0) return 0;
            int middle = ordered.length / 2;
            return ordered.length % 2 == 0
                    ? (int) Math.round((ordered[middle - 1]
                    + ordered[middle]) * 0.5) : ordered[middle];
        }

        private static long saturatedAddNanos(long value, long increment) {
            if (increment > 0L && value > Long.MAX_VALUE - increment) {
                return Long.MAX_VALUE;
            }
            return value + increment;
        }
    }

    private enum CalibrationStage {
        INTRO,
        MINECRAFT_LISTEN,
        MINECRAFT_AUDIO,
        TRANSITION_TO_PLASMO,
        PLASMO_LISTEN,
        PLASMO_AUDIO,
        TRANSITION_TO_VISUAL,
        VISUAL_LISTEN,
        VISUAL,
        RESULT
    }

    private enum CalibrationAdvance {
        NONE,
        START_PLASMO
    }

    private static final class ActiveGame {
        private final JukeboxTarget target;
        private final UUID playbackId;
        private final Location anchor;
        private InputState input;
        private final RhythmChartView chart;
        private final RhythmGameSession session;
        private final RhythmLatencyCompensator latency;
        private final RhythmGameMode mode;
        private final int heldSlotBeforeGame;
        private final RhythmRadialPath radialPath;
        private final long readyAfterMillis;
        private final RhythmPlaybackGateway.Participation participation;
        private final RhythmMonotonicPlaybackClock playbackClock;
        private long preparedThroughMillis = -1L;
        private int lastRadialClickTick = Integer.MIN_VALUE;
        private RadialHitEffect radialHitEffect;
        private boolean ready;
        private int lastCountdownNumber = Integer.MIN_VALUE;
        private long goVisibleThroughMillis = -1L;

        private ActiveGame(JukeboxTarget target, UUID playbackId, Location anchor,
                           InputState input, RhythmChartView chart,
                           RhythmGameSession session,
                           RhythmLatencyCompensator latency,
                           RhythmGameMode mode, int heldSlotBeforeGame,
                           String trackSeed, long readyAfterMillis,
                           RhythmPlaybackGateway.Participation participation,
                           RhythmMonotonicPlaybackClock playbackClock) {
            this.target = target;
            this.playbackId = playbackId;
            this.anchor = anchor;
            this.input = input;
            this.chart = chart;
            this.session = session;
            this.latency = Objects.requireNonNull(latency, "latency");
            this.mode = Objects.requireNonNull(mode, "mode");
            this.heldSlotBeforeGame = heldSlotBeforeGame;
            this.readyAfterMillis = readyAfterMillis;
            this.participation = Objects.requireNonNull(
                    participation, "participation");
            this.playbackClock = Objects.requireNonNull(
                    playbackClock, "playbackClock");
            this.radialPath = new RhythmRadialPath(trackSeed,
                    RhythmRadialPath.facingAngle(anchor.getYaw()));
        }

        private boolean claimRadialClick(int tick) {
            if (lastRadialClickTick == tick) return false;
            lastRadialClickTick = tick;
            return true;
        }

        private void discardBefore(long timeMillis) {
            chart.discardBefore(timeMillis);
            session.discardBefore(timeMillis);
            radialPath.discardBefore(timeMillis);
        }
    }

    private record RadialHitEffect(Location location, RhythmJudgement judgement,
                                   long atMillis) {
        private RadialHitEffect {
            location = Objects.requireNonNull(location, "location").clone();
            judgement = Objects.requireNonNull(judgement, "judgement");
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    private record GameView(UUID playerId, JukeboxTarget target, UUID playbackId) {
    }

    private record CalibrationView(UUID playerId, JukeboxTarget target,
                                   UUID runId) {
    }

    private record ModeSelectorView(JukeboxTarget target) {
    }

    private record SelectorView(JukeboxTarget target, RhythmGameMode mode) {
    }

    record InputState(boolean sneak) {
        static InputState of(Input input) {
            return new InputState(input.isSneak());
        }

        boolean exitPressed(InputState next) {
            return !sneak && next.sneak;
        }

    }

    private record JukeboxTarget(UUID worldId, int x, int y, int z) {
        private static JukeboxTarget at(Location location) {
            Objects.requireNonNull(location, "location");
            World world = Objects.requireNonNull(location.getWorld(), "location world");
            return new JukeboxTarget(world.getUID(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }

        private Location location() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }
}
