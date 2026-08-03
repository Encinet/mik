package org.encinet.mik.module.music.rhythm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFlow;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuMovementPolicy;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuState;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates opt-in movement input, scoring, and the six-lane spatial rhythm scene. */
public final class RhythmGameService implements Listener, AutoCloseable {
    private static final long LOOK_AHEAD_MILLIS = 2_100L;
    private static final long MINIMUM_REACTION_MILLIS = 900L;
    private static final long FLASH_MILLIS = 360L;
    private static final double FAR_FORWARD = -1.85;
    private static final double TARGET_FORWARD = 0.24;
    private static final double[] GUIDE_PROGRESS = {0.28, 0.55, 0.80};
    private static final Map<RhythmInput, FloatingMenuPoint> TARGETS = targetPositions();

    private final JavaPlugin plugin;
    private final RhythmPlaybackSource playbackSource;
    private final LanguageService languageService;
    private final Map<UUID, ActiveGame> activeGames = new java.util.HashMap<>();
    private final Map<UUID, RhythmDifficulty> preferredDifficulties =
            new java.util.HashMap<>();
    private final FloatingMenuScreen<SelectorView> selectorScreen;
    private final FloatingMenuScreen<GameView> gameScreen;
    private BukkitTask tickTask;

    public RhythmGameService(JavaPlugin plugin, RhythmPlaybackSource playbackSource,
                             LanguageService languageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playbackSource = Objects.requireNonNull(playbackSource, "playbackSource");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.selectorScreen = new FloatingMenuScreen<>("jukebox-rhythm-difficulty",
                context -> renderSelector(context.player(), context.state()));
        this.gameScreen = new FloatingMenuScreen<>("jukebox-rhythm",
                context -> render(context.player(), context.state()));
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Opens the per-player difficulty selector for a live custom playback. */
    public boolean open(Player player, Location jukeboxLocation) {
        Objects.requireNonNull(player, "player");
        JukeboxTarget target = JukeboxTarget.at(jukeboxLocation);
        Optional<RhythmPlaybackSnapshot> current = playback(target);
        if (current.isEmpty() || current.get().status() != RhythmPlaybackState.PLAYING) {
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

        selectorScreen.open(player, new SelectorView(target));
        return true;
    }

    private void start(Player player, JukeboxTarget target,
                       RhythmDifficulty difficulty) {
        Optional<RhythmPlaybackSnapshot> current = playback(target);
        if (current.isEmpty() || current.get().status() != RhythmPlaybackState.PLAYING) {
            player.sendActionBar(languageService.text(player,
                    Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
            return;
        }
        RhythmPlaybackSnapshot playback = current.get();
        RhythmChartView chart = new RhythmChartView(playback.timeline(), difficulty,
                playback.positionMillis());
        preparePlayerForCapturedInput(player);
        ActiveGame game = new ActiveGame(target, playback.playbackId(),
                player.getLocation().clone(), InputState.of(player.getCurrentInput()),
                chart, new RhythmGameSession(playback.playbackId(),
                        playback.positionMillis(), difficulty));
        activeGames.put(player.getUniqueId(), game);
        preferredDifficulties.put(player.getUniqueId(), difficulty);
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

    private void tick() {
        for (Map.Entry<UUID, ActiveGame> entry : List.copyOf(activeGames.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ActiveGame game = entry.getValue();
            if (player == null || !player.isOnline()) {
                activeGames.remove(entry.getKey(), game);
                continue;
            }
            Optional<FloatingMenuFlow<GameView>> flow = gameScreen.flow(player);
            if (flow.isEmpty()) {
                activeGames.remove(entry.getKey(), game);
                continue;
            }
            FloatingMenuState menuState = flow.get().handle().state();
            if (menuState == FloatingMenuState.SUSPENDED) {
                activeGames.remove(entry.getKey(), game);
                flow.get().close();
                continue;
            }
            Optional<RhythmPlaybackSnapshot> current = playback(game.target);
            if (current.isEmpty() || !current.get().playbackId().equals(game.playbackId)
                    || current.get().status() != RhythmPlaybackState.PLAYING) {
                finish(player, game, true);
                continue;
            }
            RhythmPlaybackSnapshot playback = current.get();
            player.setFallDistance(0.0F);
            if (player.getVelocity().lengthSquared() > 1.0E-6) {
                player.setVelocity(player.getVelocity().zero());
            }
            if (menuState == FloatingMenuState.ACTIVE) {
                if (!game.ready && game.chart.preparedThrough(
                        playback.positionMillis() + MINIMUM_REACTION_MILLIS)) {
                    game.session.beginAt(playback.positionMillis());
                    game.input = InputState.of(player.getCurrentInput());
                    game.ready = true;
                }
                if (game.ready) {
                    prepareVisibleCues(game, playback.positionMillis());
                    int misses = game.session.advance(
                            playback.positionMillis(), game.chart);
                    if (misses > 0) playJudgement(player, RhythmJudgement.MISS);
                }
            }
            flow.get().redraw();
        }
    }

    private FloatingMenuDefinition renderSelector(Player player, SelectorView view) {
        Optional<RhythmPlaybackSnapshot> current = playback(view.target());
        boolean available = current.isPresent()
                && current.get().status() == RhythmPlaybackState.PLAYING
                && (!current.get().timeline().complete()
                || current.get().timeline().playable());
        boolean noBeats = current.isPresent()
                && current.get().timeline().complete()
                && !current.get().timeline().playable();
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
                        .decoration(TextDecoration.BOLD, true))
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
                    .primary((p, handle) -> start(p, view.target(), difficulty));
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

    private FloatingMenuDefinition render(Player player, GameView view) {
        ActiveGame game = activeGames.get(view.playerId());
        Optional<RhythmPlaybackSnapshot> current = playback(view.target());
        if (game == null || current.isEmpty()
                || !view.playbackId().equals(current.get().playbackId())) {
            return unavailable(player, view);
        }
        RhythmPlaybackSnapshot playback = current.get();
        long now = playback.positionMillis();
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-rhythm")
                .framing(FloatingMenuFraming.PANORAMIC)
                .requireSpatialPresentation()
                .stableAnchor()
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .layout(FloatingMenuLayouts.fixedPoses(Map.of(
                        "exit", FloatingMenuPose.at(new FloatingMenuPoint(2.08, 0.83, 0.28)))))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        activeGames.computeIfPresent(closedPlayer.getUniqueId(),
                                (ignored, active) -> active.playbackId.equals(view.playbackId())
                                        ? null : active);
                    }
                });

        menu.textDecoration("status", new FloatingMenuPoint(-2.12, 0.82, 0.34),
                statusPanel(player, playback, game), FloatingMenuAppearance.TRANSPARENT,
                3.2F, 2.1F, 0.66F, FloatingMenuDecoration.Alignment.LEFT);
        menu.navigation("exit", exitLabel(player))
                .primary((p, handle) -> exit(p, handle));
        menu.on(FloatingMenuInteraction.HOTKEY,
                (p, handle, input) -> exit(p, handle));

        RhythmGameSession.View sessionView = game.session.view();
        for (RhythmInput input : RhythmInput.values()) {
            FloatingMenuPoint target = TARGETS.get(input);
            Material targetMaterial = targetMaterial(input, sessionView, now);
            menu.blockDecoration("target:" + input.name(),
                    FloatingMenuPose.oriented(target, targetYaw(target), 0.0),
                    targetMaterial, 0.44F, FloatingMenuDecoration.Motion.NONE);
            menu.textDecoration("label:" + input.name(),
                    FloatingMenuPose.oriented(targetOffset(target, 0.0, 0.0, 0.37),
                            targetYaw(target), 0.0),
                    actionLabel(player, input), FloatingMenuAppearance.TRANSPARENT,
                    1.6F, 1.1F, 0.54F, FloatingMenuDecoration.Alignment.CENTER);
            menu.blockDecoration("source:" + input.name(),
                    FloatingMenuPose.oriented(travelPoint(target, 0.0),
                            targetYaw(target), 0.0),
                    normalMaterial(input), 0.16F, FloatingMenuDecoration.Motion.BOB);
            for (int guide = 0; guide < GUIDE_PROGRESS.length; guide++) {
                menu.blockDecoration("guide:" + input.name() + ':' + guide,
                        FloatingMenuPose.oriented(
                                travelPoint(target, GUIDE_PROGRESS[guide]),
                                targetYaw(target), 0.0),
                        normalMaterial(input), 0.055F,
                        FloatingMenuDecoration.Motion.NONE);
            }
        }

        List<RhythmCue> visible = game.ready
                ? game.chart.between(
                        Math.max(0L, now
                                - game.session.difficulty().goodWindowMillis()),
                        now + LOOK_AHEAD_MILLIS)
                : List.of();
        int rendered = 0;
        for (RhythmCue cue : visible) {
            if (game.session.isJudged(cue.id()) || rendered++ >= 18) continue;
            FloatingMenuPoint target = TARGETS.get(cue.input());
            double progress = 1.0 - (cue.timeMillis() - now) / (double) LOOK_AHEAD_MILLIS;
            double bounded = Math.clamp(progress, 0.0, 1.14);
            float scale = (float) (0.22 + cue.strength() * 0.10);
            FloatingMenuDecoration decoration = FloatingMenuDecoration.block(
                    "cue:" + cue.id(),
                    FloatingMenuPose.oriented(travelPoint(target, bounded),
                            targetYaw(target), 0.0),
                    new ItemStack(cueMaterial(cue.input())), scale,
                    FloatingMenuDecoration.Motion.NONE).tracking();
            menu.decoration(decoration);
        }
        return menu.build();
    }

    private FloatingMenuDefinition unavailable(Player player, GameView view) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("jukebox-rhythm")
                .requireSpatialPresentation()
                .stableAnchor()
                .layout(FloatingMenuLayouts.adaptiveColumn(0.25))
                .lifecycle((closedPlayer, handle, previous, next, reason) -> {
                    if (next == FloatingMenuState.CLOSED) {
                        activeGames.remove(closedPlayer.getUniqueId());
                    }
                });
        menu.information("unavailable", languageService.text(player,
                Message.MUSIC_RHYTHM_REQUIRES_PLAYBACK, NamedTextColor.RED));
        menu.navigation("exit", exitLabel(player))
                .primary((p, handle) -> exit(p, handle));
        menu.on(FloatingMenuInteraction.HOTKEY,
                (p, handle, input) -> exit(p, handle));
        return menu.build();
    }

    private Component exitLabel(Player player) {
        return Component.text("[F] ", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(languageService.text(player,
                                Message.MUSIC_RHYTHM_EXIT, NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false));
    }

    private Component statusPanel(Player player, RhythmPlaybackSnapshot playback,
                                  ActiveGame game) {
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
                        NamedTextColor.LIGHT_PURPLE));
        if (!game.ready || !game.chart.preparedThrough(
                playback.positionMillis() + MINIMUM_REACTION_MILLIS)) {
            return panel.append(Component.newline())
                    .append(languageService.text(player, Message.MUSIC_LOADING_TITLE,
                            NamedTextColor.AQUA));
        }
        if (view.lastJudgement() != RhythmJudgement.NONE
                && playback.positionMillis() - view.lastJudgementAtMillis() < 850L) {
            return panel.append(Component.newline()).append(judgementText(player,
                    view.lastJudgement()));
        }
        return panel;
    }

    private void exit(Player player, org.encinet.mik.module.menu.FloatingMenuHandle handle) {
        activeGames.remove(player.getUniqueId());
        handle.back();
    }

    private void finish(Player player, ActiveGame expected, boolean resumeParent) {
        if (!activeGames.remove(player.getUniqueId(), expected)) return;
        gameScreen.flow(player).ifPresent(flow -> {
            if (resumeParent && flow.handle().state() != FloatingMenuState.SUSPENDED) {
                flow.back();
            } else {
                flow.close();
            }
        });
    }

    private void prepareVisibleCues(ActiveGame game, long playbackPositionMillis) {
        long from = Math.max(0L, playbackPositionMillis
                - game.session.difficulty().goodWindowMillis());
        for (RhythmCue cue : game.chart.between(from,
                playbackPositionMillis + LOOK_AHEAD_MILLIS)) {
            if (!game.announcedCues.add(cue.id())) continue;
            if (cue.timeMillis() - playbackPositionMillis
                    < MINIMUM_REACTION_MILLIS) {
                game.session.ignore(cue.id());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game == null) return;
        InputState next = InputState.of(event.getInput());
        List<RhythmInput> pressed = game.input.risingEdges(next);
        game.input = next;
        if (!game.ready || pressed.isEmpty()) return;
        Optional<RhythmPlaybackSnapshot> current = playback(game.target);
        if (current.isEmpty() || !current.get().playbackId().equals(game.playbackId)) return;
        RhythmPlaybackSnapshot playback = current.get();
        long compensated = Math.max(0L, playback.positionMillis()
                - Math.clamp(player.getPing(), 0, 250));
        RhythmGameSession.Result result = game.session.input(
                pressed, compensated, game.chart);
        if (result.judgement() != RhythmJudgement.NONE) {
            playJudgement(player, result.judgement());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) return;
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (game == null || to == null) return;
        if (!to.getWorld().equals(game.anchor.getWorld())) {
            finish(event.getPlayer(), game, false);
            return;
        }
        if (to.getX() == game.anchor.getX() && to.getY() == game.anchor.getY()
                && to.getZ() == game.anchor.getZ()) return;
        Location locked = to.clone();
        locked.setX(game.anchor.getX());
        locked.setY(game.anchor.getY());
        locked.setZ(game.anchor.getZ());
        event.setTo(locked);
        event.getPlayer().setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        if (game != null) finish(event.getPlayer(), game, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ActiveGame game = activeGames.get(player.getUniqueId());
        if (game != null) finish(player, game, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ActiveGame game = activeGames.get(event.getPlayer().getUniqueId());
        if (game != null) finish(event.getPlayer(), game, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeGames.remove(event.getPlayer().getUniqueId());
        gameScreen.forget(event.getPlayer());
        selectorScreen.forget(event.getPlayer());
        preferredDifficulties.remove(event.getPlayer().getUniqueId());
    }

    private Optional<RhythmPlaybackSnapshot> playback(JukeboxTarget target) {
        Location location = target.location();
        if (location == null || !location.getWorld().isChunkLoaded(
                location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || !(location.getBlock().getState() instanceof Jukebox)) {
            return Optional.empty();
        }
        return playbackSource.rhythmPlayback(location.getBlock());
    }

    private Component actionLabel(Player player, RhythmInput input) {
        return Component.text(key(input), keyColor(input))
                .decoration(TextDecoration.BOLD, true)
                .append(Component.newline())
                .append(languageService.text(player, actionMessage(input),
                        NamedTextColor.WHITE));
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

    private static Material normalMaterial(RhythmInput input) {
        return switch (input) {
            case FORWARD -> Material.CYAN_STAINED_GLASS;
            case BACKWARD -> Material.ORANGE_STAINED_GLASS;
            case LEFT -> Material.MAGENTA_STAINED_GLASS;
            case RIGHT -> Material.LIME_STAINED_GLASS;
            case JUMP -> Material.YELLOW_STAINED_GLASS;
            case SNEAK -> Material.BLUE_STAINED_GLASS;
        };
    }

    private static Material cueMaterial(RhythmInput input) {
        return switch (input) {
            case FORWARD -> Material.CYAN_CONCRETE;
            case BACKWARD -> Material.ORANGE_CONCRETE;
            case LEFT -> Material.MAGENTA_CONCRETE;
            case RIGHT -> Material.LIME_CONCRETE;
            case JUMP -> Material.YELLOW_CONCRETE;
            case SNEAK -> Material.BLUE_CONCRETE;
        };
    }

    private static Message actionMessage(RhythmInput input) {
        return switch (input) {
            case FORWARD -> Message.MUSIC_RHYTHM_FORWARD;
            case BACKWARD -> Message.MUSIC_RHYTHM_BACKWARD;
            case LEFT -> Message.MUSIC_RHYTHM_LEFT;
            case RIGHT -> Message.MUSIC_RHYTHM_RIGHT;
            case JUMP -> Message.MUSIC_RHYTHM_JUMP;
            case SNEAK -> Message.MUSIC_RHYTHM_SNEAK;
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

    private static String key(RhythmInput input) {
        return switch (input) {
            case FORWARD -> "↑";
            case BACKWARD -> "↓";
            case LEFT -> "←";
            case RIGHT -> "→";
            case JUMP -> "↥";
            case SNEAK -> "⇣";
        };
    }

    private static NamedTextColor keyColor(RhythmInput input) {
        return switch (input) {
            case FORWARD -> NamedTextColor.AQUA;
            case BACKWARD -> NamedTextColor.GOLD;
            case LEFT -> NamedTextColor.LIGHT_PURPLE;
            case RIGHT -> NamedTextColor.GREEN;
            case JUMP -> NamedTextColor.YELLOW;
            case SNEAK -> NamedTextColor.BLUE;
        };
    }

    private static Map<RhythmInput, FloatingMenuPoint> targetPositions() {
        EnumMap<RhythmInput, FloatingMenuPoint> positions = new EnumMap<>(RhythmInput.class);
        positions.put(RhythmInput.FORWARD, new FloatingMenuPoint(0.0, 0.48, TARGET_FORWARD));
        positions.put(RhythmInput.BACKWARD, new FloatingMenuPoint(0.0, -0.18, TARGET_FORWARD));
        positions.put(RhythmInput.LEFT, new FloatingMenuPoint(-0.76, -0.18, 0.30));
        positions.put(RhythmInput.RIGHT, new FloatingMenuPoint(0.76, -0.18, 0.30));
        positions.put(RhythmInput.JUMP, new FloatingMenuPoint(1.48, -0.72, 0.40));
        positions.put(RhythmInput.SNEAK, new FloatingMenuPoint(-1.48, -0.72, 0.40));
        return Map.copyOf(positions);
    }

    private static FloatingMenuPoint travelPoint(FloatingMenuPoint target, double progress) {
        double forward = FAR_FORWARD + progress * (target.forward() - FAR_FORWARD);
        return new FloatingMenuPoint(target.right(), target.up(), forward);
    }

    private static FloatingMenuPoint targetOffset(FloatingMenuPoint target,
                                                   double right, double up, double forward) {
        return new FloatingMenuPoint(target.right() + right, target.up() + up,
                target.forward() + forward);
    }

    private static double targetYaw(FloatingMenuPoint target) {
        return -target.right() * 7.0;
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
            gameScreen.forget(playerId);
        }
        for (Player player : Bukkit.getOnlinePlayers()) selectorScreen.forget(player);
        activeGames.clear();
        preferredDifficulties.clear();
    }

    private static final class ActiveGame {
        private final JukeboxTarget target;
        private final UUID playbackId;
        private final Location anchor;
        private InputState input;
        private final RhythmChartView chart;
        private final RhythmGameSession session;
        private final java.util.Set<Long> announcedCues = new java.util.HashSet<>();
        private boolean ready;

        private ActiveGame(JukeboxTarget target, UUID playbackId, Location anchor,
                           InputState input, RhythmChartView chart,
                           RhythmGameSession session) {
            this.target = target;
            this.playbackId = playbackId;
            this.anchor = anchor;
            this.input = input;
            this.chart = chart;
            this.session = session;
        }
    }

    private record GameView(UUID playerId, JukeboxTarget target, UUID playbackId) {
    }

    private record SelectorView(JukeboxTarget target) {
    }

    private record InputState(boolean forward, boolean backward, boolean left,
                              boolean right, boolean jump, boolean sneak) {
        private static InputState of(Input input) {
            return new InputState(input.isForward(), input.isBackward(), input.isLeft(),
                    input.isRight(), input.isJump(), input.isSneak());
        }

        private List<RhythmInput> risingEdges(InputState next) {
            java.util.ArrayList<RhythmInput> pressed = new java.util.ArrayList<>(2);
            if (!forward && next.forward) pressed.add(RhythmInput.FORWARD);
            if (!backward && next.backward) pressed.add(RhythmInput.BACKWARD);
            if (!left && next.left) pressed.add(RhythmInput.LEFT);
            if (!right && next.right) pressed.add(RhythmInput.RIGHT);
            if (!jump && next.jump) pressed.add(RhythmInput.JUMP);
            if (!sneak && next.sneak) pressed.add(RhythmInput.SNEAK);
            return List.copyOf(pressed);
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
