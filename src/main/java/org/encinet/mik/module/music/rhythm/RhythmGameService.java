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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
import org.bukkit.util.Vector;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.afk.AfkActivityService;
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
import org.encinet.mik.module.music.rhythm.input.RhythmInputTimestampSource;
import org.encinet.mik.module.music.rhythm.input.RhythmWorldAim;
import org.encinet.mik.module.music.rhythm.mode.falling.RhythmFallingLayout;
import org.encinet.mik.module.music.rhythm.mode.radial.RhythmRadialPath;
import org.encinet.mik.module.music.rhythm.mode.spatial.RhythmSpatialArena;
import org.encinet.mik.module.music.rhythm.mode.spatial.RhythmSpatialGameplay;
import org.encinet.mik.module.music.rhythm.mode.spatial.RhythmSpatialPath;
import org.encinet.mik.module.music.rhythm.mode.spatial.RhythmSpatialProfile;
import org.encinet.mik.module.music.rhythm.mode.spatial.RhythmSpatialSlider;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackGateway;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackIsolation;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackSnapshot;
import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackState;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCalibrationAudioOutput;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCalibrationPattern;
import org.encinet.mik.module.music.rhythm.calibration.CalibrationStage;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCuePresentation;
import org.encinet.mik.module.music.rhythm.calibration.RhythmCuePresentationLedger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Coordinates opt-in movement input, scoring, and selectable rhythm scenes. */
public final class RhythmGameService implements Listener, AutoCloseable,
        RhythmCalibrationStatus {
    private static final long LOOK_AHEAD_MILLIS = 2_100L;
    private static final long MINIMUM_REACTION_MILLIS = 900L;
    private static final long FLASH_MILLIS = 360L;
    private static final long CALIBRATION_CAPTURE_WINDOW_MILLIS = 400L;
    private static final long CALIBRATION_CAPTURE_WINDOW_NANOS =
            CALIBRATION_CAPTURE_WINDOW_MILLIS * 1_000_000L;
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
    private static final long GAME_EXIT_HOLD_NANOS = 600_000_000L;
    private static final int NEUTRAL_HOTBAR_SLOT = 8;
    private static final int NO_CAPTURED_HOTBAR_SLOT = -1;
    private static final String AFK_REASON_GAME = "rhythm-game";
    private static final String AFK_REASON_CALIBRATION = "rhythm-calibration";
    private static final double[] GUIDE_PROGRESS = {0.18, 0.36, 0.54, 0.72, 0.90};
    private static final double[] RADIAL_TRAIL_OFFSETS = {0.055, 0.11};
    private static final int SPATIAL_PATH_MARKERS = 4;
    private static final double SPATIAL_APPROACH_START_RADIUS = 2.30;
    private static final double SPATIAL_SATELLITE_SCALE = 0.105;
    private static final double SPATIAL_COARSE_AIM_GRACE_DEGREES = 1.0;
    private static final RhythmCalibrationProfiles UNCALIBRATED_PROFILES =
            new RhythmCalibrationProfiles(new RhythmLatencyProfile(0, 0),
                    new RhythmLatencyProfile(0, 0));

    private final JavaPlugin plugin;
    private final RhythmPlaybackGateway playbackGateway;
    private final RhythmPlaybackIsolation playbackIsolation;
    private final RhythmCalibrationAudioOutput calibrationAudioOutput;
    private final AfkActivityService afkActivityService;
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
    private final FloatingMenuScreen<ResultView> resultScreen;
    private final FloatingMenuScreen<CalibrationView> calibrationScreen;
    private final NamespacedKey minecraftJudgementOffsetKey;
    private final NamespacedKey minecraftAnimationOffsetKey;
    private final NamespacedKey plasmoJudgementOffsetKey;
    private final NamespacedKey plasmoAnimationOffsetKey;
    private final NamespacedKey pointerInputDeltaKey;
    private BukkitTask tickTask;

    public RhythmGameService(JavaPlugin plugin, RhythmPlaybackGateway playbackGateway,
                             RhythmPlaybackIsolation playbackIsolation,
                             RhythmCalibrationAudioOutput calibrationAudioOutput,
                             LanguageService languageService) {
        this(plugin, playbackGateway, playbackIsolation, calibrationAudioOutput,
                languageService, AfkActivityService.NONE);
    }

    public RhythmGameService(JavaPlugin plugin, RhythmPlaybackGateway playbackGateway,
                             RhythmPlaybackIsolation playbackIsolation,
                             RhythmCalibrationAudioOutput calibrationAudioOutput,
                             LanguageService languageService,
                             AfkActivityService afkActivityService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playbackGateway = Objects.requireNonNull(playbackGateway, "playbackGateway");
        this.playbackIsolation = Objects.requireNonNull(
                playbackIsolation, "playbackIsolation");
        this.calibrationAudioOutput = Objects.requireNonNull(
                calibrationAudioOutput, "calibrationAudioOutput");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.afkActivityService = Objects.requireNonNull(
                afkActivityService, "afkActivityService");
        this.minecraftJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_judgement_ms");
        this.minecraftAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_minecraft_animation_ms");
        this.plasmoJudgementOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_judgement_ms");
        this.plasmoAnimationOffsetKey = new NamespacedKey(plugin,
                "rhythm_plasmo_animation_ms");
        this.pointerInputDeltaKey = new NamespacedKey(plugin,
                "rhythm_pointer_input_delta_ms");
        this.modeScreen = new FloatingMenuScreen<>("jukebox-rhythm-mode",
                context -> renderModeSelector(context.player(), context.state()));
        this.selectorScreen = new FloatingMenuScreen<>("jukebox-rhythm-difficulty",
                context -> renderSelector(context.player(), context.state()));
        this.gameScreen = new FloatingMenuScreen<>("jukebox-rhythm",
                context -> render(context.player(), context.state()));
        this.resultScreen = new FloatingMenuScreen<>("jukebox-rhythm-result",
                context -> renderResult(context.player(), context.state()));
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
        if (!requirePlayableNetwork(player)) return false;
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
        if (!requirePlayableNetwork(player)) return;
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
        RhythmSpatialProfile spatialProfile = mode == RhythmGameMode.SPATIAL_AIM
                ? RhythmSpatialProfile.forDifficulty(difficulty) : null;
        RhythmSpatialArena spatialArena = spatialProfile == null ? null
                : RhythmSpatialArena.inspect(player.getEyeLocation(), spatialProfile);
        if (spatialArena != null && !spatialArena.playable()) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_SPATIAL_NO_ROOM, NamedTextColor.RED));
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
        if (mode.pointerInput()) {
            profile = profile.withInputDelta(pointerInputDelta(player));
        }
        long startedAtNanos = System.nanoTime();
        RhythmLatencyCompensator latency = new RhythmLatencyCompensator(
                player.getPing(), profile.judgementOffsetMillis(),
                profile.animationOffsetMillis(), startedAtNanos);
        preparePlayerForCapturedInput(player);
        inputTimestamps.rememberView(player.getUniqueId(),
                player.getYaw(), player.getPitch(), startedAtNanos);
        int heldSlotBeforeGame = captureHotbar(player, mode);
        AfkActivityService.ActivityLease afkLease;
        try {
            afkLease = afkActivityService.suppressAutomaticAfk(
                    player.getUniqueId(), AFK_REASON_GAME);
        } catch (RuntimeException exception) {
            participation.close();
            throw exception;
        }
        ActiveGame game;
        try {
            game = new ActiveGame(target, playback.playbackId(),
                    player.getLocation().clone(), player.getEyeLocation().clone(),
                    InputState.of(player.getCurrentInput()),
                    chart, new RhythmGameSession(playback.playbackId(),
                            latency.inputPosition(playback.positionMillis()), difficulty),
                    latency, mode, heldSlotBeforeGame, playback.timeline().seed(),
                    saturatedAdd(playback.positionMillis(), GAME_JOIN_DELAY_MILLIS),
                    participation, new RhythmMonotonicPlaybackClock(
                    playback.positionMillis(),
                    playback.status() == RhythmPlaybackState.PLAYING,
                    startedAtNanos), spatialProfile, spatialArena,
                    new RhythmNetworkLatencyGuard(startedAtNanos), afkLease);
        } catch (RuntimeException exception) {
            afkLease.close();
            participation.close();
            throw exception;
        }
        activeGames.put(player.getUniqueId(), game);
        preferredDifficulties.put(player.getUniqueId(), difficulty);
        preferredModes.put(player.getUniqueId(), mode);
        try {
            gameScreen.open(player, new GameView(
                    player.getUniqueId(), target, playback.playbackId()));
        } catch (RuntimeException exception) {
            removeActiveGame(player, game);
            throw exception;
        }
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
        int previous = player.getInventory().getHeldItemSlot();
        if (mode == RhythmGameMode.FALLING) {
            selectNeutralHotbarSlot(player);
            return previous;
        }
        if (mode.pointerInput()) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item == null || item.getType().isAir()) {
                    player.getInventory().setHeldItemSlot(slot);
                    return previous;
                }
            }
        }
        return NO_CAPTURED_HOTBAR_SLOT;
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
        if (!requirePlayableNetwork(player)) return false;
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
        if (!requirePlayableNetwork(player)) return;
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
        AfkActivityService.ActivityLease afkLease;
        try {
            afkLease = afkActivityService.suppressAutomaticAfk(
                    player.getUniqueId(), AFK_REASON_CALIBRATION);
        } catch (RuntimeException exception) {
            silenceLease.close();
            throw exception;
        }
        long startedAtNanos = System.nanoTime();
        ActiveCalibration calibration;
        try {
            calibration = new ActiveCalibration(target,
                    runId, player.getLocation().clone(),
                    InputState.of(player.getCurrentInput()),
                    new RhythmLatencyCompensator(player.getPing(), 0,
                            startedAtNanos),
                    captureHotbar(player, RhythmGameMode.FALLING), silenceLease,
                    new RhythmNetworkLatencyGuard(startedAtNanos),
                    afkLease, startedAtNanos);
        } catch (RuntimeException exception) {
            afkLease.close();
            silenceLease.close();
            throw exception;
        }
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
        return storedCalibration(player).isPresent();
    }

    private Optional<RhythmCalibrationResult> storedCalibration(Player player) {
        var data = player.getPersistentDataContainer();
        return RhythmCalibrationResult.fromStored(
                data.get(minecraftJudgementOffsetKey,
                        PersistentDataType.INTEGER),
                data.get(minecraftAnimationOffsetKey,
                        PersistentDataType.INTEGER),
                data.get(plasmoJudgementOffsetKey,
                        PersistentDataType.INTEGER),
                data.get(plasmoAnimationOffsetKey,
                        PersistentDataType.INTEGER),
                data.get(pointerInputDeltaKey,
                        PersistentDataType.INTEGER));
    }

    private boolean requireCompletedCalibration(Player player) {
        if (hasCompletedLatencyCalibration(player)) return true;
        player.sendActionBar(languageService.text(player,
                Message.MUSIC_RHYTHM_CALIBRATION_REQUIRED,
                NamedTextColor.YELLOW));
        return false;
    }

    private boolean requirePlayableNetwork(Player player) {
        int pingMillis = Math.max(0, player.getPing());
        if (RhythmNetworkLatencyGuard.allowsEntry(pingMillis)) return true;
        player.sendActionBar(networkLatencyMessage(player,
                Message.MUSIC_RHYTHM_NETWORK_TOO_HIGH,
                pingMillis, NamedTextColor.RED));
        return false;
    }

    private Component networkLatencyMessage(Player player, Message message,
                                            int pingMillis,
                                            NamedTextColor color) {
        return languageService.text(player, message, color, pingMillis,
                RhythmNetworkLatencyGuard.MAXIMUM_PLAYABLE_RTT_MILLIS);
    }

    private Component networkLatencyWarning(
            Player player, RhythmNetworkLatencyGuard guard) {
        return networkLatencyMessage(player,
                Message.MUSIC_RHYTHM_NETWORK_WARNING,
                guard.lastObservedRttMillis(), NamedTextColor.YELLOW);
    }

    private boolean showNetworkLatencyWarning(
            Player player, RhythmNetworkLatencyGuard guard) {
        if (!guard.warningActive()) return false;
        player.sendActionBar(networkLatencyWarning(player, guard));
        return true;
    }

    private boolean networkLatencyExceeded(
            Player player, RhythmNetworkLatencyGuard guard,
            long sampledAtNanos) {
        RhythmNetworkLatencyGuard.Update update = guard.sample(
                player.getPing(), sampledAtNanos);
        if (update.warning()) {
            player.sendActionBar(networkLatencyMessage(player,
                    Message.MUSIC_RHYTHM_NETWORK_WARNING,
                    update.rttMillis(), NamedTextColor.YELLOW));
        }
        if (!update.excessive()) return false;
        player.sendActionBar(networkLatencyMessage(player,
                Message.MUSIC_RHYTHM_NETWORK_TOO_HIGH,
                update.rttMillis(), NamedTextColor.RED));
        return true;
    }

    private RhythmCalibrationProfiles calibrationProfiles(Player player) {
        return storedCalibration(player)
                .map(RhythmCalibrationResult::profiles)
                .orElse(UNCALIBRATED_PROFILES);
    }

    private int pointerInputDelta(Player player) {
        return storedCalibration(player)
                .map(RhythmCalibrationResult::pointerInputDeltaMillis)
                .orElse(0);
    }

    private void saveCalibration(Player player, RhythmCalibrationResult result) {
        var data = player.getPersistentDataContainer();
        saveProfile(data, minecraftJudgementOffsetKey,
                minecraftAnimationOffsetKey, result.profiles().minecraft());
        saveProfile(data, plasmoJudgementOffsetKey,
                plasmoAnimationOffsetKey, result.profiles().plasmoVoice());
        data.set(pointerInputDeltaKey,
                PersistentDataType.INTEGER,
                result.pointerInputDeltaMillis());
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
        Objects.requireNonNull(player, "player");
        ActiveCalibration active = activeCalibrations.get(player.getUniqueId());
        if (active != null) endCalibration(player, active, true);
        var data = player.getPersistentDataContainer();
        data.remove(minecraftJudgementOffsetKey);
        data.remove(minecraftAnimationOffsetKey);
        data.remove(plasmoJudgementOffsetKey);
        data.remove(plasmoAnimationOffsetKey);
        data.remove(pointerInputDeltaKey);
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
                    releaseGameResources(game);
                }
                continue;
            }
            if (game.exitReady(latencySampleNanos)) {
                finish(player, game, true);
                continue;
            }
            if (game.input.sneak()) {
                player.sendActionBar(exitLabel(player).append(
                        Component.text("  " + exitProgressBar(
                                game.exitProgress(latencySampleNanos)),
                                NamedTextColor.RED)));
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
                if (game.ready) {
                    long terminalPosition = current.isPresent()
                            && current.get().playbackId().equals(game.playbackId)
                            ? current.get().positionMillis()
                            : game.playbackClock.positionAt(latencySampleNanos);
                    long finishPosition = saturatedAdd(
                            terminalPosition,
                            game.session.difficulty().goodWindowMillis());
                    game.session.advanceResults(finishPosition, game.chart);
                }
                finish(player, game, true, true);
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
            if (networkLatencyExceeded(player, game.networkLatency,
                    latencySampleNanos)) {
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
                    if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                        if (!showNetworkLatencyWarning(
                                player, game.networkLatency)) {
                            showSpatialTutorialHud(player);
                        }
                    }
                }
                if (game.ready) {
                    prepareVisibleCues(game, playback.positionMillis());
                    if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                        ignoreUnfairSpatialCues(game, missPosition);
                    }
                    List<RhythmGameSession.Result> misses =
                            game.session.advanceResults(missPosition, game.chart);
                    if (!misses.isEmpty()) {
                        playJudgement(player, RhythmJudgement.MISS);
                        if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                            for (RhythmGameSession.Result miss : misses) {
                                RhythmSpatialGameplay.Placement placement =
                                        game.spatial.placement(
                                                miss.cue());
                                if (placement != null) {
                                    Location at = placement.location();
                                    game.worldHitEffect = new WorldHitEffect(
                                            at, RhythmJudgement.MISS, missPosition);
                                    player.spawnParticle(Particle.SMOKE, at, 8,
                                            0.16, 0.16, 0.16, 0.02);
                                }
                                game.spatial.miss(miss.cue());
                            }
                        }
                    }
                    if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                        showSpatialHud(player, game, playback.positionMillis(),
                                inputPosition);
                    }
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
                    releaseCalibrationResources(calibration);
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
            if (calibration.stage.requiresPlasmoVoice()
                    && !calibrationAudioOutput.available(player)) {
                player.sendActionBar(languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VOICE_REQUIRED,
                        NamedTextColor.RED));
                endCalibration(player, calibration, true);
                continue;
            }
            if (networkLatencyExceeded(player, calibration.networkLatency,
                    latencySampleNanos)) {
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
                try {
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
                    calibration.consumeResult().ifPresent(result -> {
                        saveCalibration(player, result);
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
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Latency calibration failed for " + player.getName(),
                            exception);
                    player.sendActionBar(languageService.text(player,
                            Message.MUSIC_RHYTHM_CALIBRATION_FAILED,
                            NamedTextColor.RED));
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
                        FloatingMenuLayouts.actions("mode", 3),
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
                                NamedTextColor.AQUA))
                        .append(Component.text("  →  ", NamedTextColor.DARK_GRAY))
                        .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_POINTER,
                                NamedTextColor.GREEN));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_INTRO,
                        NamedTextColor.GRAY);
            }
            case MINECRAFT_AUDIO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_MINECRAFT,
                                NamedTextColor.GOLD)
                        .append(Component.text("  ·  1/4",
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
                        .append(Component.text("  ·  1/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_LISTEN,
                        NamedTextColor.YELLOW);
            }
            case TRANSITION_TO_PLASMO -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO,
                                NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("  ·  2/4",
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
                        .append(Component.text("  ·  2/4",
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
                        .append(Component.text("  ·  2/4",
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
                        .append(Component.text("  ·  3/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_PREVIEW,
                        NamedTextColor.GRAY);
            }
            case VISUAL_LISTEN -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA)
                        .append(Component.text("  ·  3/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_PREVIEW,
                        NamedTextColor.YELLOW);
            }
            case VISUAL -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL,
                                NamedTextColor.AQUA)
                        .append(Component.text("  ·  3/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_INSTRUCTION,
                        NamedTextColor.AQUA);
                sampling = true;
            }
            case TRANSITION_TO_POINTER -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_POINTER,
                                NamedTextColor.GREEN)
                        .append(Component.text("  ·  4/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_PREVIEW,
                        NamedTextColor.GRAY);
            }
            case POINTER_LISTEN -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_POINTER,
                                NamedTextColor.GREEN)
                        .append(Component.text("  ·  4/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_VISUAL_PREVIEW,
                        NamedTextColor.YELLOW);
            }
            case POINTER_VISUAL -> {
                stageLine = languageService.text(player,
                                Message.MUSIC_RHYTHM_CALIBRATION_STAGE_POINTER,
                                NamedTextColor.GREEN)
                        .append(Component.text("  ·  4/4",
                                NamedTextColor.DARK_GRAY));
                instruction = languageService.text(player,
                        Message.MUSIC_RHYTHM_CALIBRATION_POINTER_INSTRUCTION,
                        NamedTextColor.GREEN);
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
        if (calibration.networkLatency.warningActive()) {
            status = status.append(Component.newline())
                    .append(networkLatencyWarning(
                            player, calibration.networkLatency));
        }
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
                0.0, RhythmFallingLayout.HIT_LINE_UP,
                RhythmFallingLayout.LANE_FORWARD);
        if (calibration.stage.presentsVisualCues()) {
            for (int marker = -4; marker <= 4; marker++) {
                menu.blockDecoration("calibration-line:" + marker,
                        new FloatingMenuPoint(marker * 0.29,
                                RhythmFallingLayout.HIT_LINE_UP,
                                RhythmFallingLayout.LANE_FORWARD - 0.02),
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
                if ((calibration.stage == CalibrationStage.VISUAL
                        || calibration.stage == CalibrationStage.POINTER_VISUAL)
                        && calibration.measurement().sampled(cue.id())) continue;
                calibration.planVisualCue(cue, renderAtNanos);
                double progress = 1.0 - (cue.timeMillis() - visualNow)
                        / (double) LOOK_AHEAD_MILLIS;
                menu.decoration(FloatingMenuDecoration.block(
                        ActiveCalibration.visualDecorationId(cue.id()),
                        FloatingMenuPose.at(RhythmFallingLayout.point(target,
                                Math.clamp(progress, 0.0, 1.14))),
                        new ItemStack(Material.SEA_LANTERN),
                        (float) (0.25 + cue.strength() * 0.10),
                        FloatingMenuDecoration.Motion.NONE).tracking());
                rendered++;
            }
        } else {
            Material phaseMaterial = switch (calibration.stage) {
                case INTRO -> Material.CALIBRATED_SCULK_SENSOR;
                case TRANSITION_TO_PLASMO, TRANSITION_TO_VISUAL,
                     TRANSITION_TO_POINTER -> Material.REPEATER;
                case RESULT -> Material.EMERALD_BLOCK;
                case MINECRAFT_LISTEN, MINECRAFT_AUDIO -> Material.NOTE_BLOCK;
                case PLASMO_LISTEN, PLASMO_AUDIO -> Material.JUKEBOX;
                case VISUAL_LISTEN, VISUAL, POINTER_LISTEN,
                     POINTER_VISUAL -> throw new IllegalStateException(
                        "visual stage is rendered separately");
            };
            menu.blockDecoration("calibration-speaker", FloatingMenuPose.at(
                            new FloatingMenuPoint(0.0, 0.05,
                                    RhythmFallingLayout.LANE_FORWARD)),
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
                .observeFrames((presentedPlayer, decorationId, sentAtNanos) -> {
                    if (game.spatial != null) {
                        game.spatial.framePresented(decorationId);
                    }
                })
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
        long lookAhead = lookAheadMillis(game);
        List<RhythmCue> visible = game.ready
                ? game.chart.between(
                        Math.max(0L, sceneNow
                                - game.session.difficulty().goodWindowMillis()),
                        saturatedAdd(sceneNow, lookAhead))
                : List.of();
        switch (game.mode) {
            case FALLING -> renderFallingScene(menu, player, game, sceneNow,
                    judgementNow, visible);
            case RADIAL -> renderRadialScene(menu, game, sceneNow,
                    judgementNow, visible);
            case SPATIAL_AIM -> renderSpatialScene(menu, game, sceneNow,
                    judgementNow, visible);
        }
        renderGameOverlay(menu, player, playback, game, judgementNow);
        return menu.build();
    }

    private FloatingMenuDefinition renderResult(Player player, ResultView view) {
        RhythmGameResult result = view.result();
        Component summary = languageService.text(player,
                        Message.MUSIC_RHYTHM_RESULTS, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(languageService.text(player, Message.MUSIC_RHYTHM_SCORE,
                                NamedTextColor.WHITE, result.score())
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("  ·  ◎ " + accuracyText(result),
                                NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("  ·  MAX ×" + result.maximumCombo(),
                        NamedTextColor.GREEN))
                .append(Component.newline())
                .append(judgementCount(player, Message.MUSIC_RHYTHM_PERFECT,
                        NamedTextColor.AQUA, result.perfectHits()))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(judgementCount(player, Message.MUSIC_RHYTHM_GREAT,
                        NamedTextColor.GREEN, result.greatHits()))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(judgementCount(player, Message.MUSIC_RHYTHM_GOOD,
                        NamedTextColor.YELLOW, result.goodHits()))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(judgementCount(player, Message.MUSIC_RHYTHM_MISS,
                        NamedTextColor.RED, result.misses()))
                .append(Component.newline())
                .append(languageService.text(player, modeMessage(view.mode()),
                        modeColor(view.mode())))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player,
                        difficultyMessage(view.difficulty()),
                        NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("  ·  Δ " + signedMillis(Math.round(
                                result.meanTimingErrorMillis())) + " ms",
                        timingColor(result.meanTimingErrorMillis())));
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "jukebox-rhythm-result")
                .framing(FloatingMenuFraming.PANORAMIC)
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.navigation("navigation")));
        menu.textDecoration("result", new FloatingMenuPoint(0.0, 0.36, 0.18),
                summary, 0xD0181B22, 5.8F, 2.5F, 0.74F,
                FloatingMenuDecoration.Alignment.CENTER);
        menu.back(languageService.text(player, Message.MUSIC_BACK,
                        NamedTextColor.RED))
                .region("navigation");
        return menu.build();
    }

    private Component judgementCount(Player player, Message message,
                                     NamedTextColor color, long count) {
        return languageService.text(player, message, color)
                .append(Component.text(" " + count, NamedTextColor.WHITE));
    }

    private static NamedTextColor timingColor(double millis) {
        if (Math.abs(millis) <= 8.0) return NamedTextColor.GREEN;
        return millis < 0.0 ? NamedTextColor.AQUA : NamedTextColor.GOLD;
    }

    static String accuracyText(RhythmGameResult result) {
        Objects.requireNonNull(result, "result");
        return result.totalJudgements() == 0L ? "—"
                : String.format(java.util.Locale.ROOT, "%.2f%%",
                result.accuracy() * 100.0);
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
            overlay = judgementFeedback(player, view)
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
                            RhythmFallingLayout.HIT_LINE_UP,
                            RhythmFallingLayout.LANE_FORWARD - 0.02),
                    Material.LIGHT_GRAY_STAINED_GLASS, 0.045F,
                    FloatingMenuDecoration.Motion.NONE);
        }

        RhythmGameSession.View sessionView = game.session.view();
        for (RhythmInput input : RhythmInput.values()) {
            FloatingMenuPoint target = RhythmFallingLayout.target(input);
            Material targetMaterial = targetMaterial(input, sessionView,
                    judgementNow);
            menu.blockDecoration("target:" + input.name(),
                    FloatingMenuPose.at(target),
                    targetMaterial, 0.44F, FloatingMenuDecoration.Motion.NONE);
            menu.textDecoration("label:" + input.name(),
                    FloatingMenuPose.at(RhythmFallingLayout.offset(
                            target, 0.0, -0.34, 0.37)),
                    actionLabel(input), FloatingMenuAppearance.TRANSPARENT,
                    1.6F, 1.1F, 0.54F, FloatingMenuDecoration.Alignment.CENTER);
            menu.blockDecoration("source:" + input.name(),
                    FloatingMenuPose.at(RhythmFallingLayout.point(target, 0.0)),
                    normalMaterial(input), 0.16F, FloatingMenuDecoration.Motion.BOB);
            for (int guide = 0; guide < GUIDE_PROGRESS.length; guide++) {
                menu.blockDecoration("guide:" + input.name() + ':' + guide,
                        FloatingMenuPose.at(
                                RhythmFallingLayout.point(
                                        target, GUIDE_PROGRESS[guide])),
                        normalMaterial(input), 0.055F,
                        FloatingMenuDecoration.Motion.NONE);
            }
        }

        int rendered = 0;
        for (RhythmCue cue : visible) {
            if (game.session.isJudged(cue.id()) || rendered++ >= 18) continue;
            FloatingMenuPoint target = RhythmFallingLayout.target(cue.input());
            double progress = 1.0 - (cue.timeMillis() - now) / (double) LOOK_AHEAD_MILLIS;
            double bounded = Math.clamp(progress, 0.0, 1.14);
            float scale = (float) (0.22 + cue.strength() * 0.10);
            FloatingMenuDecoration decoration = FloatingMenuDecoration.block(
                    "cue:" + cue.id(),
                    FloatingMenuPose.at(RhythmFallingLayout.point(target, bounded)),
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

        WorldHitEffect effect = game.worldHitEffect;
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

    private void renderSpatialScene(FloatingMenuDefinition.Builder menu, ActiveGame game,
                                    long now, long judgementNow,
                                    List<RhythmCue> visible) {
        if (!game.ready) {
            renderSpatialTutorial(menu, game, now);
            return;
        }
        List<RhythmSpatialGameplay.VisibleTarget> targets = new ArrayList<>();
        int maximum = game.spatial.profile().maximumVisibleCues(
                game.session.difficulty());
        for (RhythmCue cue : visible) {
            if (game.session.isJudged(cue.id()) || targets.size() >= maximum) continue;
            RhythmSpatialGameplay.Placement placement =
                    game.spatial.placement(cue);
            if (placement == null) {
                game.spatial.ignore(cue);
                continue;
            }
            if (!game.spatial.placementVisible(cue, placement, now)) {
                game.spatial.ignore(cue);
                continue;
            }
            game.spatial.expectFrame(cue, now);
            targets.add(new RhythmSpatialGameplay.VisibleTarget(cue, placement));
        }

        RhythmSpatialSlider.Link previewSlider = targets.size() >= 2
                ? game.spatial.previewSlider(targets.get(0), targets.get(1)) : null;
        RhythmSpatialSlider.Presentation displayedSlider =
                game.spatial.displayedSlider(previewSlider, now);

        for (int index = 0; index < targets.size(); index++) {
            RhythmSpatialGameplay.VisibleTarget target = targets.get(index);
            RhythmCue cue = target.cue();
            RhythmSpatialGameplay.Placement placement = target.placement();
            Location center = placement.location();
            float coreScale = spatialCoreScale(placement.hitRadius());
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "spatial:core:" + cue.id(), center, 0.0, 0.0,
                    new ItemStack(spatialCoreMaterial(index, placement.depth(),
                            cue, now, game.session.difficulty())),
                    coreScale, FloatingMenuDecoration.Motion.NONE));

            double progress = spatialProgress(cue, now, game.spatial.profile());
            double satelliteRadius = placement.hitRadius()
                    * (1.0 + (SPATIAL_APPROACH_START_RADIUS - 1.0)
                    * (1.0 - Math.clamp(progress, 0.0, 1.0)));
            SpatialAxes axes = spatialAxes(game.eyeAnchor, center);
            Vector primary = (index & 1) == 0 ? axes.right() : axes.up();
            addSpatialSatellitePair(menu, cue, "primary", center,
                    primary, satelliteRadius, placement.hitRadius());
            if (index == 0) {
                Vector secondary = (index & 1) == 0 ? axes.up() : axes.right();
                addSpatialSatellitePair(menu, cue, "secondary", center,
                        secondary, satelliteRadius, placement.hitRadius());
            }

            if (index < 4) {
                Location labelAt = center.clone().add(axes.direction().clone()
                        .multiply(-placement.hitRadius() * 0.62));
                Location facing = labelAt.clone();
                facing.setDirection(game.eyeAnchor.toVector().subtract(
                        labelAt.toVector()));
                menu.decoration(FloatingMenuDecoration.worldText(
                        "spatial:label:" + cue.id(), labelAt,
                        facing.getYaw(), facing.getPitch(),
                        Component.text((displayedSlider != null
                                        && displayedSlider.link().toCueId() == cue.id()
                                        ? "↝ " : "")
                                        + (index + 1) + " "
                                        + spatialDepthMarker(placement.depth()),
                                index == 0
                                ? NamedTextColor.WHITE : NamedTextColor.GRAY),
                        FloatingMenuAppearance.TRANSPARENT,
                        0.52F, 0.52F, 0.50F,
                        FloatingMenuDecoration.Alignment.CENTER, false));
            }
        }

        if (displayedSlider != null) {
            renderSpatialSlider(menu, game, displayedSlider, now);
        } else if (targets.size() >= 2) {
            Location from = targets.get(0).placement().location();
            Location to = targets.get(1).placement().location();
            Vector delta = to.toVector().subtract(from.toVector());
            for (int marker = 1; marker <= SPATIAL_PATH_MARKERS; marker++) {
                double fraction = marker / (double) (SPATIAL_PATH_MARKERS + 1);
                Location point = from.clone().add(delta.clone().multiply(fraction));
                menu.decoration(FloatingMenuDecoration.worldBlock(
                        "spatial:path:" + marker, point, 0.0, 0.0,
                        new ItemStack(Material.PURPLE_STAINED_GLASS), 0.075F,
                        FloatingMenuDecoration.Motion.NONE));
            }
        }

        WorldHitEffect effect = game.worldHitEffect;
        if (effect != null && judgementNow >= effect.atMillis()
                && judgementNow - effect.atMillis() < FLASH_MILLIS) {
            double life = (judgementNow - effect.atMillis()) / (double) FLASH_MILLIS;
            float scale = (float) (0.82 - life * 0.46);
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "spatial:hit", effect.location(), 0.0, 0.0,
                    new ItemStack(judgementMaterial(effect.judgement())), scale,
                    FloatingMenuDecoration.Motion.SPIN).tracking());
        }
    }

    private static void renderSpatialSlider(
            FloatingMenuDefinition.Builder menu, ActiveGame game,
            RhythmSpatialSlider.Presentation slider, long now) {
        RhythmSpatialSlider.Link link = slider.link();
        double progress = slider.progress(now);
        for (int marker = 1; marker <= RhythmSpatialSlider.PATH_MARKERS; marker++) {
            double fraction = marker
                    / (double) (RhythmSpatialSlider.PATH_MARKERS + 1);
            Vector vector = link.pointAtProgress(fraction);
            Location point = new Location(game.anchor.getWorld(),
                    vector.getX(), vector.getY(), vector.getZ());
            Material material = fraction <= progress
                    ? Material.CYAN_STAINED_GLASS
                    : Material.LIGHT_BLUE_STAINED_GLASS;
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "spatial:slider:path:" + link.toCueId() + ':' + marker,
                    point, 0.0, 0.0, new ItemStack(material), 0.068F,
                    FloatingMenuDecoration.Motion.NONE));
        }
        if (!slider.active()) return;
        Vector headVector = slider.point(now);
        Location head = new Location(game.anchor.getWorld(),
                headVector.getX(), headVector.getY(), headVector.getZ());
        double depth = headVector.distance(game.eyeAnchor.toVector());
        double radius = game.spatial.profile().worldHitRadius(depth) * 0.48;
        menu.decoration(FloatingMenuDecoration.worldBlock(
                "spatial:slider:head:" + link.toCueId(), head, 0.0, 0.0,
                new ItemStack(progress >= 1.0
                        ? Material.SEA_LANTERN : Material.AMETHYST_CLUSTER),
                spatialCoreScale(radius),
                FloatingMenuDecoration.Motion.SPIN).tracking());
    }

    private static void renderSpatialTutorial(FloatingMenuDefinition.Builder menu,
                                              ActiveGame game, long now) {
        Vector direction = RhythmSpatialPath.direction(game.anchor.getYaw(), 0.0);
        Location center = game.eyeAnchor.clone().add(direction.multiply(3.5));
        double progress = 1.0 - Math.clamp(
                game.readyAfterMillis - now, 0L,
                game.spatial.profile().approachMillis())
                / (double) game.spatial.profile().approachMillis();
        double hitRadius = game.spatial.profile().worldHitRadius(3.5);
        double satelliteRadius = hitRadius
                * (1.0 + (SPATIAL_APPROACH_START_RADIUS - 1.0)
                * (1.0 - progress));
        menu.decoration(FloatingMenuDecoration.worldBlock(
                "spatial:tutorial:core", center, 0.0, 0.0,
                new ItemStack(progress >= 0.96
                        ? Material.SEA_LANTERN : Material.TARGET),
                spatialCoreScale(hitRadius), FloatingMenuDecoration.Motion.NONE));
        RhythmCue tutorial = new RhythmCue(-1L, game.readyAfterMillis,
                RhythmInput.ONE, 1.0);
        SpatialAxes axes = spatialAxes(game.eyeAnchor, center);
        addSpatialSatellitePair(menu, tutorial, "tutorial-x", center,
                axes.right(), satelliteRadius, hitRadius);
        addSpatialSatellitePair(menu, tutorial, "tutorial-y", center,
                axes.up(), satelliteRadius, hitRadius);
    }

    private static void addSpatialSatellitePair(
            FloatingMenuDefinition.Builder menu, RhythmCue cue, String axisName,
            Location center, Vector axis, double radius, double hitRadius) {
        float scale = (float) Math.clamp(hitRadius * 0.24,
                0.075, SPATIAL_SATELLITE_SCALE);
        for (int sign : new int[]{-1, 1}) {
            Location point = center.clone().add(axis.clone().multiply(radius * sign));
            menu.decoration(FloatingMenuDecoration.worldBlock(
                    "spatial:approach:" + cue.id() + ':' + axisName + ':' + sign,
                    point, 0.0, 0.0,
                    new ItemStack(Material.AMETHYST_BLOCK), scale,
                    FloatingMenuDecoration.Motion.NONE).tracking());
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
        if (game.mode.pointerInput()) {
            panel = panel.append(Component.newline())
                    .append(Component.keybind("key.attack", NamedTextColor.AQUA))
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.use", NamedTextColor.AQUA))
                    .append(Component.text(game.mode == RhythmGameMode.RADIAL
                                    ? "  ·  360°" : "  ·  3D",
                            NamedTextColor.GRAY));
        }
        if (game.networkLatency.warningActive()) {
            return panel.append(Component.newline())
                    .append(networkLatencyWarning(player,
                            game.networkLatency));
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
            return panel.append(Component.newline()).append(
                    judgementFeedback(player, view));
        }
        return panel;
    }

    private void showSpatialHud(Player player, ActiveGame game, long visualPosition,
                                long judgementPosition) {
        if (game.input.sneak()) {
            player.sendActionBar(exitLabel(player).append(
                    Component.text("  " + exitProgressBar(
                            game.exitProgress(System.nanoTime())),
                            NamedTextColor.RED)));
            return;
        }
        if (showNetworkLatencyWarning(player, game.networkLatency)) return;
        RhythmGameSession.View view = game.session.view();
        Component hud = languageService.text(player, Message.MUSIC_RHYTHM_SCORE,
                        NamedTextColor.GOLD, view.score())
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(languageService.text(player, Message.MUSIC_RHYTHM_COMBO,
                        view.combo() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                        view.combo()));
        if (view.lastJudgement() != RhythmJudgement.NONE
                && judgementPosition - view.lastJudgementAtMillis()
                < GAME_JUDGEMENT_OVERLAY_MILLIS) {
            hud = hud.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(judgementFeedback(player, view));
        } else if (game.spatial.earlyFeedbackVisible(judgementPosition)) {
            hud = hud.append(Component.text("  ·  ◇ +"
                            + game.spatial.earlyFeedbackMillis() + " ms",
                    NamedTextColor.AQUA));
        }

        RhythmSpatialSlider.Presentation slider = game.spatial.displayedSlider(
                null, visualPosition);
        Vector guidePoint = null;
        if (slider != null) {
            guidePoint = slider.point(visualPosition);
            double viewError = slider.viewErrorDegrees(
                    player.getEyeLocation().getDirection(), visualPosition);
            boolean tracing = viewError <= game.spatial.profile().aimRadiusDegrees()
                    * 1.8;
            int completedLinks = game.spatial.completedSliderLinks();
            String chain = completedLinks > 0 ? " ×" + completedLinks : "";
            hud = hud.append(Component.text("  ·  ↝ "
                            + (tracing ? "◆" : "◇") + chain,
                    tracing ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        } else {
            RhythmCue current = game.chart.between(
                            Math.max(0L, visualPosition
                                    - game.session.difficulty().goodWindowMillis()),
                            saturatedAdd(visualPosition, lookAheadMillis(game))).stream()
                    .filter(cue -> !game.session.isJudged(cue.id()))
                    .findFirst().orElse(null);
            if (current != null) {
                RhythmSpatialGameplay.Placement placement =
                        game.spatial.placement(current);
                if (placement != null) guidePoint = placement.location().toVector();
            }
        }
        if (guidePoint != null) {
                Location facing = game.eyeAnchor.clone();
                facing.setDirection(guidePoint.subtract(game.eyeAnchor.toVector()));
                double yawDelta = normalizeDegrees(facing.getYaw() - player.getYaw());
                double pitchDelta = facing.getPitch() - player.getPitch();
                String arrow = directionArrow(yawDelta, pitchDelta);
                if (arrow != null) {
                    hud = hud.append(Component.text("  ·  ◆ " + arrow,
                            NamedTextColor.AQUA));
                }
        }
        player.sendActionBar(hud);
    }

    private void showSpatialTutorialHud(Player player) {
        player.sendActionBar(languageService.text(player,
                        Message.MUSIC_RHYTHM_MODE_SPATIAL_AIM,
                        NamedTextColor.GOLD)
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.keybind("key.attack", NamedTextColor.AQUA))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.keybind("key.use", NamedTextColor.AQUA))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.keybind("key.sneak", NamedTextColor.RED))
                .append(Component.text(" 0.6 s", NamedTextColor.GRAY)));
    }

    static String exitProgressBar(double progress) {
        int filled = (int) Math.ceil(Math.clamp(progress, 0.0, 1.0) * 5.0);
        return "▰".repeat(filled) + "▱".repeat(5 - filled);
    }

    static String directionArrow(double yawDelta, double pitchDelta) {
        if (Math.abs(yawDelta) <= 34.0 && Math.abs(pitchDelta) <= 26.0) return null;
        int horizontal = Math.abs(yawDelta) <= 18.0 ? 0 : yawDelta > 0.0 ? 1 : -1;
        int vertical = Math.abs(pitchDelta) <= 14.0 ? 0 : pitchDelta > 0.0 ? 1 : -1;
        if (vertical < 0) {
            return horizontal < 0 ? "↖" : horizontal > 0 ? "↗" : "↑";
        }
        if (vertical > 0) {
            return horizontal < 0 ? "↙" : horizontal > 0 ? "↘" : "↓";
        }
        return horizontal < 0 ? "←" : "→";
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized > 180.0) normalized -= 360.0;
        if (normalized < -180.0) normalized += 360.0;
        return normalized;
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
        releaseCalibrationResources(expected);
        try {
            player.getInventory().setHeldItemSlot(
                    expected.heldSlotBeforeCalibration);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to restore the hotbar after latency calibration",
                    exception);
        }
        return true;
    }

    private void releaseCalibrationResources(ActiveCalibration calibration) {
        try {
            calibration.closeStagePlayback();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to close latency calibration audio", exception);
        }
        try {
            calibration.silenceLease.close();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to restore jukebox audio after latency calibration",
                    exception);
        }
        try {
            calibration.afkLease.close();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to release latency calibration AFK suppression",
                    exception);
        }
    }

    private void finish(Player player, ActiveGame expected, boolean resumeParent) {
        finish(player, expected, resumeParent, false);
    }

    private void finish(Player player, ActiveGame expected, boolean resumeParent,
                        boolean showResults) {
        RhythmGameResult result = RhythmGameResult.from(expected.session.view());
        if (!removeActiveGame(player, expected)) return;
        gameScreen.flow(player).ifPresent(flow -> {
            if (resumeParent && flow.handle().state() != FloatingMenuState.SUSPENDED) {
                flow.back();
                if (showResults && expected.ready) {
                    resultScreen.open(player, new ResultView(result, expected.mode,
                            expected.session.difficulty()));
                }
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
        releaseGameResources(expected);
        if (expected.heldSlotBeforeGame != NO_CAPTURED_HOTBAR_SLOT) {
            player.getInventory().setHeldItemSlot(expected.heldSlotBeforeGame);
        }
        return true;
    }

    private void releaseGameResources(ActiveGame game) {
        try {
            game.participation.close();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to leave rhythm playback participation", exception);
        }
        try {
            game.afkLease.close();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Unable to release rhythm game AFK suppression", exception);
        }
    }

    private void prepareVisibleCues(ActiveGame game, long playbackPositionMillis) {
        long from = Math.max(Math.max(0L, playbackPositionMillis
                        - game.session.difficulty().goodWindowMillis()),
                game.preparedThroughMillis == Long.MAX_VALUE
                        ? Long.MAX_VALUE : game.preparedThroughMillis + 1L);
        long through = saturatedAdd(playbackPositionMillis, lookAheadMillis(game));
        for (RhythmCue cue : game.chart.between(from, through)) {
            if (cue.timeMillis() - playbackPositionMillis
                    < MINIMUM_REACTION_MILLIS) {
                if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                    game.spatial.ignore(cue);
                } else {
                    game.session.ignore(cue);
                }
            }
        }
        game.preparedThroughMillis = Math.max(game.preparedThroughMillis, through);
    }

    private void ignoreUnfairSpatialCues(ActiveGame game, long missPositionMillis) {
        long cutoff = Math.max(0L, missPositionMillis
                - game.session.difficulty().goodWindowMillis());
        long from = Math.max(0L, cutoff - GAME_STATE_RETENTION_MILLIS);
        for (RhythmCue cue : game.chart.between(from, cutoff)) {
            if (!game.session.isJudged(cue.id())
                    && !game.spatial.presentationFair(cue)) {
                game.spatial.ignore(cue);
            }
        }
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
        if (!game.input.sneak() && next.sneak()) {
            game.beginExitHold(System.nanoTime());
        } else if (game.input.sneak() && !next.sneak()) {
            game.cancelExitHold();
        }
        game.input = next;
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
                            timedInput, false));
            return;
        }
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game == null) return;
        if (game.mode.pointerInput()) {
            event.setCancelled(true);
            return;
        }
        if (game.mode != RhythmGameMode.FALLING) return;
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
        if (game != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRhythmDrop(PlayerDropItemEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (activeGames.containsKey(playerId)
                || activeCalibrations.containsKey(playerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRhythmInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (activeGames.containsKey(player.getUniqueId())
                || activeCalibrations.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendActionBar(exitLabel(player));
        }
    }

    /** Left-click air and display attacks arrive as a main-arm animation. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            event.setCancelled(true);
            calibrationHit(event.getPlayer(), calibration,
                    pointerInput(event.getPlayer()), true);
            return;
        }
        ActiveGame game = pointerGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        pointerClick(event.getPlayer(), game, pointerInput(event.getPlayer()));
    }

    /** Captures both mouse buttons when they target air or a real block. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getAction().isLeftClick()
                || event.getAction().isRightClick())) {
            return;
        }
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            event.setCancelled(true);
            calibrationHit(event.getPlayer(), calibration,
                    pointerInput(event.getPlayer()), true);
            return;
        }
        ActiveGame game = pointerGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        pointerClick(event.getPlayer(), game, pointerInput(event.getPlayer()));
    }

    /** Right-clicks intercepted by a real entity still count as radial input. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            event.setCancelled(true);
            calibrationHit(event.getPlayer(), calibration,
                    pointerInput(event.getPlayer()), true);
            return;
        }
        ActiveGame game = pointerGame(event.getPlayer());
        if (game == null) return;
        event.setCancelled(true);
        pointerClick(event.getPlayer(), game, pointerInput(event.getPlayer()));
    }

    /** Fallback for clicks that directly address a client-only display entity. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (!event.isAttack() && event.getHand() != EquipmentSlot.HAND) return;
        ActiveCalibration calibration = activeCalibrations.get(
                event.getPlayer().getUniqueId());
        if (calibration != null) {
            calibrationHit(event.getPlayer(), calibration,
                    pointerInput(event.getPlayer()), true);
            return;
        }
        ActiveGame game = pointerGame(event.getPlayer());
        if (game != null) {
            pointerClick(event.getPlayer(), game, pointerInput(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialBlockDamage(BlockDamageEvent event) {
        if (pointerGame(event.getPlayer()) != null
                || activeCalibrations.containsKey(
                event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRadialEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ActiveCalibration calibration = activeCalibrations.get(
                player.getUniqueId());
        if (calibration != null) {
            event.setCancelled(true);
            calibrationHit(player, calibration, pointerInput(player), true);
            return;
        }
        ActiveGame game = pointerGame(player);
        if (game == null) return;
        event.setCancelled(true);
        pointerClick(player, game, pointerInput(player));
    }

    private ActiveGame pointerGame(Player player) {
        ActiveGame game = activeGames.get(player.getUniqueId());
        return game != null && game.mode.pointerInput() ? game : null;
    }

    private RhythmInputTimestampSource.TimedInput pointerInput(Player player) {
        return inputTimestamps.claimPointer(
                player.getUniqueId(), System.nanoTime());
    }

    private void pointerClick(Player player, ActiveGame game,
                              RhythmInputTimestampSource.TimedInput timedInput) {
        if (!game.ready || !game.claimPointerClick(Bukkit.getCurrentTick())) return;
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
        java.util.ArrayList<RhythmWorldAim.Target> targets =
                new java.util.ArrayList<>(candidates.size());
        for (RhythmCue cue : candidates) {
            if (game.mode == RhythmGameMode.RADIAL) {
                double angle = game.radialPath.angleDegrees(cue);
                double progress = Math.clamp(radialProgress(cue, visualAimPosition),
                        -0.12, 1.16);
                Location location = RhythmRadialPath.point(game.anchor, angle, progress);
                targets.add(new RhythmWorldAim.Target(cue, location.toVector(),
                        radialAimRadius(cue, progress)));
                continue;
            }
            RhythmSpatialGameplay.Placement placement =
                    game.spatial.placement(cue);
            if (placement == null || !game.spatial.presentationFair(cue)) continue;
            double radius = placement.hitRadius();
            if (!timedInput.hasView()) {
                radius += placement.depth() * Math.tan(Math.toRadians(
                        SPATIAL_COARSE_AIM_GRACE_DEGREES));
            }
            targets.add(new RhythmWorldAim.Target(cue,
                    placement.location().toVector(), radius));
        }
        Location eye = game.mode == RhythmGameMode.SPATIAL_AIM
                ? game.eyeAnchor : player.getEyeLocation();
        Vector clickDirection = timedInput.viewDirection()
                .orElseGet(() -> player.getEyeLocation().getDirection());
        Optional<RhythmWorldAim.Target> selected = RhythmWorldAim.select(
                eye.toVector(), clickDirection, targets, judgementPosition);
        if (selected.isEmpty()) {
            if (game.mode == RhythmGameMode.SPATIAL_AIM) {
                earlySpatialFeedback(player, game, eye, clickDirection,
                        judgementPosition, visualAimPosition);
            }
            return;
        }

        RhythmGameSession.Result result = game.session.hit(
                selected.get().cue(), judgementPosition, game.chart);
        if (result.judgement() == RhythmJudgement.NONE) return;
        afkActivityService.recordTrustedActivity(player.getUniqueId());
        Vector center = selected.get().center();
        Location hitAt = new Location(game.anchor.getWorld(),
                center.getX(), center.getY(), center.getZ());
        game.worldHitEffect = new WorldHitEffect(
                hitAt, result.judgement(), judgementPosition);
        if (game.mode == RhythmGameMode.SPATIAL_AIM) {
            game.spatial.hit(result.cue(), judgementPosition,
                    visualAimPosition);
        }
        player.spawnParticle(Particle.END_ROD, hitAt, 12,
                0.22, 0.22, 0.22, 0.035);
        player.spawnParticle(Particle.CRIT, hitAt, 18,
                0.28, 0.28, 0.28, 0.08);
        playJudgement(player, result.judgement());
    }

    private void earlySpatialFeedback(Player player, ActiveGame game, Location eye,
                                      Vector clickDirection,
                                      long judgementPosition,
                                      long visualPosition) {
        long window = game.session.difficulty().goodWindowMillis();
        long from = saturatedAdd(judgementPosition, window + 1L);
        long through = saturatedAdd(Math.max(judgementPosition, visualPosition),
                game.spatial.profile().approachMillis());
        List<RhythmWorldAim.Target> future = game.chart.between(from, through).stream()
                .filter(cue -> !game.session.isJudged(cue.id())
                        && game.spatial.presented(cue))
                .map(cue -> {
                    RhythmSpatialGameplay.Placement placement =
                            game.spatial.placement(cue);
                    return placement == null ? null : new RhythmWorldAim.Target(cue,
                            placement.location().toVector(), placement.hitRadius());
                })
                .filter(Objects::nonNull)
                .toList();
        RhythmWorldAim.select(eye.toVector(), clickDirection, future,
                        judgementPosition)
                .ifPresent(target -> {
                    game.spatial.showEarlyFeedback(target.cue().timeMillis()
                            - judgementPosition, judgementPosition);
                    player.playSound(player.getLocation(),
                            Sound.UI_BUTTON_CLICK, 0.12F, 0.62F);
                    Location at = new Location(game.anchor.getWorld(),
                            target.center().getX(), target.center().getY(),
                            target.center().getZ());
                    player.spawnParticle(Particle.SMOKE, at, 3,
                            0.06, 0.06, 0.06, 0.0);
                });
    }

    private void calibrationHit(Player player, ActiveCalibration calibration,
                                RhythmInputTimestampSource.TimedInput timedInput,
                                boolean pointerInput) {
        if (!calibration.acceptsModality(pointerInput)) return;
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
        afkActivityService.recordTrustedActivity(player.getUniqueId());
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
            calibration.beginPointerTransition(estimate, nowNanos);
            return;
        }
        if (calibration.stage == CalibrationStage.POINTER_VISUAL) {
            calibration.finishFromPointerStage(estimate, nowNanos);
        }
    }

    private static long adjustNanos(long timestampNanos, int delayMillis) {
        long delayNanos = delayMillis * 1_000_000L;
        // System.nanoTime values are modular. Plain subtraction keeps short
        // elapsed intervals correct even when the signed long wraps.
        return timestampNanos - delayNanos;
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
            afkActivityService.recordTrustedActivity(player.getUniqueId());
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
        resultScreen.forget(event.getPlayer());
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

    private static String signedMillis(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
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
            case SPATIAL_AIM -> Component.keybind("key.attack", NamedTextColor.GRAY)
                    .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.keybind("key.use", NamedTextColor.GRAY))
                    .append(Component.text("  ·  3D  ·  360°",
                            NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.keybind("key.sneak", NamedTextColor.RED))
                    .append(Component.text(" 0.6 s",
                            NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(languageService.text(player,
                            Message.MUSIC_RHYTHM_SPATIAL_SLIDER_HINT,
                            NamedTextColor.AQUA));
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

    private Component judgementFeedback(Player player,
                                        RhythmGameSession.View view) {
        Component judgement = judgementText(player, view.lastJudgement());
        if (view.lastJudgement() == RhythmJudgement.NONE
                || view.lastJudgement() == RhythmJudgement.MISS) {
            return judgement;
        }
        return judgement.append(Component.text(
                "  ·  Δ " + signedMillis(view.lastTimingErrorMillis()) + " ms",
                timingColor(view.lastTimingErrorMillis())));
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

    private static long lookAheadMillis(ActiveGame game) {
        return game.mode == RhythmGameMode.SPATIAL_AIM
                ? game.spatial.profile().approachMillis() : LOOK_AHEAD_MILLIS;
    }

    private static double spatialProgress(RhythmCue cue, long playbackPositionMillis,
                                          RhythmSpatialProfile profile) {
        return 1.0 - (cue.timeMillis() - playbackPositionMillis)
                / (double) profile.approachMillis();
    }

    private static float spatialCoreScale(double hitRadius) {
        return (float) (hitRadius * 2.0 / Math.sqrt(3.0));
    }

    private static SpatialAxes spatialAxes(Location eye, Location center) {
        Vector direction = center.toVector().subtract(eye.toVector()).normalize();
        Vector right = new Vector(-direction.getZ(), 0.0, direction.getX());
        if (right.lengthSquared() < 1.0E-8) right = new Vector(1.0, 0.0, 0.0);
        else right.normalize();
        Vector up = right.clone().crossProduct(direction).normalize();
        return new SpatialAxes(direction, right, up);
    }

    private static Material spatialCoreMaterial(
            int order, double depth, RhythmCue cue, long now,
            RhythmDifficulty difficulty) {
        long error = now - cue.timeMillis();
        if (Math.abs(error) <= difficulty.perfectWindowMillis()) {
            return Material.SEA_LANTERN;
        }
        if (error > difficulty.goodWindowMillis()) return Material.REDSTONE_BLOCK;
        if (order == 0) return Material.END_STONE_BRICKS;
        if (depth < 3.0) return Material.CYAN_CONCRETE;
        if (depth < 4.0) return Material.PURPLE_CONCRETE;
        return Material.BLUE_CONCRETE;
    }

    static String spatialDepthMarker(double depth) {
        if (depth < 3.0) return "•";
        if (depth < 4.0) return "••";
        return "•••";
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
            case SPATIAL_AIM -> Message.MUSIC_RHYTHM_MODE_SPATIAL_AIM;
        };
    }

    private static NamedTextColor modeColor(RhythmGameMode mode) {
        return switch (mode) {
            case FALLING -> NamedTextColor.AQUA;
            case RADIAL -> NamedTextColor.LIGHT_PURPLE;
            case SPATIAL_AIM -> NamedTextColor.GOLD;
        };
    }

    private static Material modeMaterial(RhythmGameMode mode) {
        return switch (mode) {
            case FALLING -> Material.SAND;
            case RADIAL -> Material.ENDER_EYE;
            case SPATIAL_AIM -> Material.END_CRYSTAL;
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

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
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
                releaseGameResources(game);
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
                    releaseCalibrationResources(calibration);
                }
            }
            calibrationScreen.forget(playerId);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            selectorScreen.forget(player);
            modeScreen.forget(player);
            resultScreen.forget(player);
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
        private final RhythmNetworkLatencyGuard networkLatency;
        private final int heldSlotBeforeCalibration;
        private final RhythmPlaybackIsolation.SilenceLease silenceLease;
        private final AfkActivityService.ActivityLease afkLease;
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

        private ActiveCalibration(JukeboxTarget target, UUID runId,
                                  Location anchor, InputState input,
                                  RhythmLatencyCompensator latency,
                                  int heldSlotBeforeCalibration,
                                  RhythmPlaybackIsolation.SilenceLease silenceLease,
                                  RhythmNetworkLatencyGuard networkLatency,
                                  AfkActivityService.ActivityLease afkLease,
                                  long startedAtNanos) {
            this.target = Objects.requireNonNull(target, "target");
            this.runId = Objects.requireNonNull(runId, "runId");
            this.anchor = Objects.requireNonNull(anchor, "anchor");
            this.pattern = RhythmCalibrationPattern.fixed();
            this.input = Objects.requireNonNull(input, "input");
            this.latency = Objects.requireNonNull(latency, "latency");
            this.networkLatency = Objects.requireNonNull(
                    networkLatency, "networkLatency");
            this.heldSlotBeforeCalibration = heldSlotBeforeCalibration;
            this.silenceLease = Objects.requireNonNull(
                    silenceLease, "silenceLease");
            this.afkLease = Objects.requireNonNull(afkLease, "afkLease");
            this.stageStartedAtNanos = startedAtNanos;
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
                transitionTo(CalibrationStage.MINECRAFT_LISTEN);
                resetStageClock(nowNanos);
                playMinecraftCues(player, 0L, false);
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.MINECRAFT_LISTEN) {
                if (now >= CALIBRATION_BEAT_PREVIEW_MILLIS) {
                    transitionTo(CalibrationStage.MINECRAFT_AUDIO);
                    minecraftMeasurement.reset();
                    resetStageClock(nowNanos);
                    beginInputWindow(nowNanos);
                    playMinecraftCues(player, 0L, true);
                } else {
                    playMinecraftCues(player, now, false);
                }
                return CalibrationAdvance.NONE;
            }
            if (stage == CalibrationStage.MINECRAFT_AUDIO) {
                playMinecraftCues(player, now, true);
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

        private void playMinecraftCues(Player player, long nowMillis,
                                       boolean collect) {
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

        private void visualFramePresented(String decorationId,
                                          long presentedAtNanos) {
            RhythmCuePresentation expected = expectedVisualCommits.remove(decorationId);
            if (expected == null || stage != CalibrationStage.VISUAL
                    && stage != CalibrationStage.POINTER_VISUAL) return;
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
                case POINTER_VISUAL -> pointerMeasurement;
                default -> throw new IllegalStateException(
                        "stage does not collect a raw latency measurement: " + stage);
            };
        }

        private Optional<RhythmCuePresentation> closestPresentation(
                long adjustedInputAtNanos) {
            return inputPresentations.closestWithin(adjustedInputAtNanos,
                    CALIBRATION_CAPTURE_WINDOW_NANOS,
                    acceptsRawMeasurement() ? measurement()::sampled
                            : cueId -> false);
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

        private Optional<RhythmCalibrationResult> consumeResult() {
            Optional<RhythmCalibrationResult> pending =
                    Optional.ofNullable(pendingResult);
            pendingResult = null;
            return pending;
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
            transitionTo(CalibrationStage.RESULT);
            resetStageClock(nowNanos);
        }

        private boolean resultExpired(long nowNanos) {
            return stage == CalibrationStage.RESULT
                    && positionMillis(nowNanos) >= CALIBRATION_RESULT_MILLIS;
        }

        private boolean acceptsRawMeasurement() {
            return stage.samplesInput();
        }

        private boolean acceptsInput() {
            return stage.samplesInput();
        }

        private boolean acceptsModality(boolean pointerInput) {
            return stage.acceptsInput(pointerInput);
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
            long elapsed = nowNanos - reference;
            return elapsed >= threshold;
        }

        private boolean plasmoRealigning(long nowNanos) {
            long elapsed = nowNanos - lastPlasmoRealignAtNanos;
            return lastPlasmoRealignAtNanos != Long.MIN_VALUE
                    && elapsed >= 0L
                    && elapsed <= CALIBRATION_AUDIO_STALL_STATUS_NANOS
                    && stage == CalibrationStage.PLASMO_LISTEN;
        }

        private void transitionTo(CalibrationStage next) {
            stage = stage.transitionTo(next);
        }

        private long positionMillis() {
            return positionMillis(System.nanoTime());
        }

        private long positionMillis(long nowNanos) {
            long elapsed = nowNanos - stageStartedAtNanos;
            return elapsed <= 0L ? 0L : elapsed / 1_000_000L;
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
                case TRANSITION_TO_PLASMO, TRANSITION_TO_VISUAL,
                     TRANSITION_TO_POINTER -> CALIBRATION_TRANSITION_MILLIS;
                case MINECRAFT_LISTEN, VISUAL_LISTEN, POINTER_LISTEN ->
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

    private enum CalibrationAdvance {
        NONE,
        START_PLASMO
    }

    private static final class ActiveGame {
        private final JukeboxTarget target;
        private final UUID playbackId;
        private final Location anchor;
        private final Location eyeAnchor;
        private InputState input;
        private final RhythmChartView chart;
        private final RhythmGameSession session;
        private final RhythmLatencyCompensator latency;
        private final RhythmNetworkLatencyGuard networkLatency;
        private final RhythmGameMode mode;
        private final int heldSlotBeforeGame;
        private final RhythmRadialPath radialPath;
        private final RhythmSpatialGameplay spatial;
        private final long readyAfterMillis;
        private final RhythmPlaybackGateway.Participation participation;
        private final AfkActivityService.ActivityLease afkLease;
        private final RhythmMonotonicPlaybackClock playbackClock;
        private long preparedThroughMillis = -1L;
        private int lastPointerClickTick = Integer.MIN_VALUE;
        private WorldHitEffect worldHitEffect;
        private boolean ready;
        private long exitHeldSinceNanos = Long.MIN_VALUE;
        private int lastCountdownNumber = Integer.MIN_VALUE;
        private long goVisibleThroughMillis = -1L;

        private ActiveGame(JukeboxTarget target, UUID playbackId, Location anchor,
                           Location eyeAnchor,
                           InputState input, RhythmChartView chart,
                           RhythmGameSession session,
                           RhythmLatencyCompensator latency,
                           RhythmGameMode mode, int heldSlotBeforeGame,
                           String trackSeed, long readyAfterMillis,
                           RhythmPlaybackGateway.Participation participation,
                           RhythmMonotonicPlaybackClock playbackClock,
                           RhythmSpatialProfile spatialProfile,
                           RhythmSpatialArena spatialArena,
                           RhythmNetworkLatencyGuard networkLatency,
                           AfkActivityService.ActivityLease afkLease) {
            this.target = target;
            this.playbackId = playbackId;
            this.anchor = anchor;
            this.eyeAnchor = Objects.requireNonNull(eyeAnchor, "eyeAnchor").clone();
            this.input = input;
            this.chart = chart;
            this.session = session;
            this.latency = Objects.requireNonNull(latency, "latency");
            this.networkLatency = Objects.requireNonNull(
                    networkLatency, "networkLatency");
            this.mode = Objects.requireNonNull(mode, "mode");
            this.heldSlotBeforeGame = heldSlotBeforeGame;
            this.readyAfterMillis = readyAfterMillis;
            this.participation = Objects.requireNonNull(
                    participation, "participation");
            this.afkLease = Objects.requireNonNull(afkLease, "afkLease");
            this.playbackClock = Objects.requireNonNull(
                    playbackClock, "playbackClock");
            this.radialPath = mode == RhythmGameMode.RADIAL
                    ? new RhythmRadialPath(trackSeed,
                    RhythmRadialPath.facingAngle(anchor.getYaw())) : null;
            this.spatial = mode == RhythmGameMode.SPATIAL_AIM
                    ? new RhythmSpatialGameplay(trackSeed, anchor.getYaw(),
                    this.eyeAnchor, chart, session,
                    Objects.requireNonNull(spatialProfile, "spatialProfile"),
                    Objects.requireNonNull(spatialArena, "spatialArena"),
                    MINIMUM_REACTION_MILLIS) : null;
        }

        private boolean claimPointerClick(int tick) {
            if (lastPointerClickTick == tick) return false;
            lastPointerClickTick = tick;
            return true;
        }

        private void beginExitHold(long nowNanos) {
            if (exitHeldSinceNanos == Long.MIN_VALUE) exitHeldSinceNanos = nowNanos;
        }

        private void cancelExitHold() {
            exitHeldSinceNanos = Long.MIN_VALUE;
        }

        private boolean exitReady(long nowNanos) {
            return exitHeldSinceNanos != Long.MIN_VALUE
                    && nowNanos - exitHeldSinceNanos >= GAME_EXIT_HOLD_NANOS;
        }

        private double exitProgress(long nowNanos) {
            if (exitHeldSinceNanos == Long.MIN_VALUE) return 0.0;
            return Math.clamp((nowNanos - exitHeldSinceNanos)
                    / (double) GAME_EXIT_HOLD_NANOS, 0.0, 1.0);
        }

        private void discardBefore(long timeMillis) {
            chart.discardBefore(timeMillis);
            session.discardBefore(timeMillis);
            if (radialPath != null) radialPath.discardBefore(timeMillis);
            if (spatial != null) spatial.discardBefore(timeMillis);
        }
    }

    private record WorldHitEffect(Location location, RhythmJudgement judgement,
                                   long atMillis) {
        private WorldHitEffect {
            location = Objects.requireNonNull(location, "location").clone();
            judgement = Objects.requireNonNull(judgement, "judgement");
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    private record SpatialAxes(Vector direction, Vector right, Vector up) {
        private SpatialAxes {
            direction = direction.clone();
            right = right.clone();
            up = up.clone();
        }

        @Override
        public Vector direction() {
            return direction.clone();
        }

        @Override
        public Vector right() {
            return right.clone();
        }

        @Override
        public Vector up() {
            return up.clone();
        }
    }

    private record GameView(UUID playerId, JukeboxTarget target, UUID playbackId) {
    }

    private record ResultView(RhythmGameResult result, RhythmGameMode mode,
                              RhythmDifficulty difficulty) {
        private ResultView {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(difficulty, "difficulty");
        }
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
