package org.encinet.mik.feature.server.monitor;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.player.afk.AfkActivityService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.util.ShutdownSequence;
import org.encinet.mik.shared.util.TextBadge;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Staff patrols with automatic camera placement and target rotation. */
public final class MonitorModule implements Listener {
    private static final long ROTATION_NANOS = Duration.ofSeconds(30).toNanos();

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final AfkService afk;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private final LongSupplier clock;
    private final Consumer<Runnable> mainThread;
    private final Supplier<MonitorCamera> cameras;
    private final MonitorPlayerPackets tab;
    private final Supplier<MonitorCameraView> views;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Consumer<UUID> tabRefresh = _ -> { };
    private BukkitTask task;
    private volatile boolean running = true;

    public MonitorModule(JavaPlugin plugin, LanguageService languages, AfkService afk) {
        this(plugin, languages, afk, () -> plugin.getServer().getOnlinePlayers(), System::nanoTime,
                action -> plugin.getServer().getScheduler().runTask(plugin, action), MonitorCamera::new,
                new MonitorPlayerPackets(), MonitorCameraView::new);
    }

    MonitorModule(JavaPlugin plugin, LanguageService languages, AfkService afk,
                  Supplier<Collection<? extends Player>> onlinePlayers, LongSupplier clock,
                  Consumer<Runnable> mainThread, Supplier<MonitorCamera> cameras, MonitorPlayerPackets tab, Supplier<MonitorCameraView> views) {
        this.plugin = plugin;
        this.languages = languages;
        this.afk = afk;
        this.onlinePlayers = onlinePlayers;
        this.clock = clock;
        this.mainThread = mainThread;
        this.cameras = cameras;
        this.tab = tab;
        this.views = views;
    }

    public void onTabChange(Consumer<UUID> refresh) {
        tabRefresh = java.util.Objects.requireNonNull(refresh, "refresh");
    }

    /** Per-viewer badge; ordinary players receive no monitoring state. */
    public Component tabBadge(Player viewer, Player subject) {
        if (!RolePermissions.canModerate(viewer) || !isMonitoring(subject)) return Component.empty();
        return TextBadge.suffix(languages.t(viewer, Message.PLAYER_MONITOR_LABEL), NamedTextColor.AQUA);
    }

    public void enable() {
        running = true;
        tab.enable();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick,
                MonitorCamera.UPDATE_TICKS, MonitorCamera.UPDATE_TICKS);
    }

    public void disable() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        ShutdownSequence shutdown = new ShutdownSequence();
        for (Session session : List.copyOf(sessions.values())) {
            shutdown.attempt("monitor " + session.observer.getUniqueId(), () -> {
                try {
                    if (!restore(session)) {
                        throw new IllegalStateException("Monitor return teleport was rejected");
                    }
                } finally {
                    reveal(session);
                }
            });
        }
        sessions.clear();
        shutdown.attempt("monitor tab", tab::disable);
        shutdown.finish("monitor mode");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                command().build(), languages.t(Language.DEFAULT, Message.MONITOR_COMMAND_DESCRIPTION)));
    }

    LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("monitor")
                .requires(source -> source.getSender() instanceof Player
                        && RolePermissions.canModerate(source.getSender()))
                .executes(context -> execute(context.getSource().getSender(), false, null))
                .then(Commands.literal("stop")
                        .executes(context -> execute(context.getSource().getSender(), true, null)))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (context.getSource().getSender() instanceof Player viewer) {
                                String prefix = builder.getRemainingLowerCase();
                                onlinePlayers.get().stream().filter(candidate -> observable(viewer, candidate))
                                        .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                                        .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeTarget(context.getSource().getSender(),
                                StringArgumentType.getString(context, "player"))));
    }

    private int executeTarget(CommandSender sender, String name) {
        if (!running || !(sender instanceof Player viewer) || !RolePermissions.canModerate(viewer)) return 0;
        Player target = onlinePlayers.get().stream().filter(candidate -> name.equalsIgnoreCase(candidate.getName())
                && observable(viewer, candidate)).findFirst().orElse(null);
        if (target == null) {
            viewer.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
            return 0;
        }
        return execute(sender, false, target);
    }

    private int execute(CommandSender sender, boolean stopRequested, Player requestedTarget) {
        // Recheck at execution: a previously parsed command may outlive a role grant.
        if (!running || !(sender instanceof Player player) || !RolePermissions.canModerate(player)) return 0;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            if (stopRequested) return Command.SINGLE_SUCCESS;
            if (player.isDead() || player.isInsideVehicle()) {
                send(player, Message.MONITOR_UNAVAILABLE, NamedTextColor.YELLOW);
                return 0;
            }
            session = new Session(player, cameras.get(), views.get());
            if (requestedTarget != null) session.pinnedTarget = requestedTarget.getUniqueId();
            sessions.put(player.getUniqueId(), session);
            tab.begin(player, session.originalMode);
            player.setGameMode(GameMode.SPECTATOR);
            if (player.getGameMode() != GameMode.SPECTATOR) {
                sessions.remove(player.getUniqueId());
                tab.end(player);
                send(player, Message.MONITOR_UNAVAILABLE, NamedTextColor.YELLOW);
                return 0;
            }
            session.observation = afk.beginPassiveObservation(player);
            refreshVisibility(session);
            // Permissions are now cached for every recipient; refresh the initial mode accordingly.
            tab.refreshMode(player);
            tabRefresh.accept(player.getUniqueId());
            player.setSpectatorTarget(null);
            send(player, Message.MONITOR_STARTED, NamedTextColor.GREEN);
            selectTarget(session);
        } else if (session.returning) {
            send(player, Message.MONITOR_RETURNING, NamedTextColor.YELLOW);
        } else if (requestedTarget != null) {
            session.pinnedTarget = requestedTarget.getUniqueId();
            session.camera.reset();
            selectTarget(session);
        } else {
            stop(session);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Queried on the main thread by presence notifications. */
    public boolean isMonitoring(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Main-thread Tab rendering capability; entity visibility remains hidden. */
    public boolean retainsTabEntry(Player viewer, Player subject) {
        Session session = sessions.get(subject.getUniqueId());
        return session != null && session.tabViewers.contains(viewer.getUniqueId());
    }

    void tick() {
        if (!running || sessions.isEmpty()) return;
        for (Session session : List.copyOf(sessions.values())) {
            Player observer = session.observer;
            refreshVisibility(session);
            if (session.returning) continue;
            if (!RolePermissions.canModerate(observer) || observer.getGameMode() != GameMode.SPECTATOR
                    || afk.isAfk(observer.getUniqueId())) {
                if (observer.getGameMode() != GameMode.SPECTATOR) session.exitMode = observer.getGameMode();
                stop(session);
                continue;
            }
            if (session.target == null || !eligible(session, session.target)
                    || (session.pinnedTarget == null && clock.getAsLong() - session.selectedAt >= ROTATION_NANOS)) {
                selectTarget(session);
            } else {
                follow(session);
            }
        }
    }

    private boolean observable(Player observer, Player candidate) {
        return candidate.isOnline() && !candidate.equals(observer) && !candidate.isDead()
                && candidate.getGameMode() != GameMode.SPECTATOR && !isMonitoring(candidate)
                && observer.canSee(candidate);
    }

    private boolean eligible(Session session, Player candidate) {
        return observable(session.observer, candidate)
                && (session.pinnedTarget != null || !afk.isIdleAfk(candidate.getUniqueId()));
    }

    private void selectTarget(Session session) {
        List<? extends Player> candidates = onlinePlayers.get().stream().filter(player -> eligible(session, player)).toList();
        if (session.pinnedTarget != null) {
            session.target = candidates.stream().filter(player -> session.pinnedTarget.equals(player.getUniqueId()))
                    .findFirst().orElse(null);
            session.selectedAt = clock.getAsLong();
            follow(session);
            return;
        }
        Set<UUID> eligibleIds = new HashSet<>();
        candidates.forEach(player -> eligibleIds.add(player.getUniqueId()));
        session.visited.retainAll(eligibleIds);
        List<Player> unvisited = new ArrayList<>(candidates.stream()
                .filter(player -> !session.visited.contains(player.getUniqueId())).toList());
        if (unvisited.isEmpty()) {
            session.visited.clear();
            unvisited.addAll(candidates);
            // Start a new shuffled round without watching the same player twice in a row.
            if (unvisited.size() > 1) unvisited.remove(session.target);
        }
        session.target = unvisited.isEmpty() ? null
                : unvisited.get(ThreadLocalRandom.current().nextInt(unvisited.size()));
        session.selectedAt = clock.getAsLong();
        if (session.target != null) session.visited.add(session.target.getUniqueId());
        follow(session);
    }

    private void follow(Session session) {
        Player observer = session.observer;
        if (session.target == null) {
            session.view.clear(observer);
            observer.setSpectatorTarget(null);
            session.camera.reset();
            observer.sendActionBar(languages.text(observer, Message.MONITOR_WAITING, NamedTextColor.YELLOW));
            return;
        }
        if (session.targetTeleported) {
            session.targetTeleported = false;
            session.camera.reset();
        }
        MonitorCamera.Frame frame = session.camera.update(session.target.getUniqueId(), session.target.getEyeLocation(),
                session.target.getEyeHeight(), observer.getEyeLocation(), observer.getEyeHeight());
        boolean watching;
        if (frame == null) {
            session.view.clear(observer);
            if (observer.getSpectatorTarget() != session.target) observer.setSpectatorTarget(session.target);
            watching = observer.getSpectatorTarget() == session.target;
        } else {
            Location camera = frame.feet();
            if (observer.getSpectatorTarget() != null) observer.setSpectatorTarget(null);
            // A cancelled detach leaves the native camera attached; do not teleport it out of sync.
            watching = observer.getSpectatorTarget() == null
                    && (sameView(observer.getLocation(), camera) || observer.teleport(camera, TeleportCause.SPECTATE));
            if (watching) session.view.move(observer, camera, frame.cut());
        }
        if (watching) {
            observer.sendActionBar(languages.text(observer, Message.MONITOR_WATCHING, NamedTextColor.AQUA,
                    session.target.getName()));
        }
    }

    private static boolean sameView(Location current, Location requested) {
        return current.getWorld().equals(requested.getWorld()) && current.distanceSquared(requested) < 1.0e-6
                && Math.abs(Math.IEEEremainder(current.getYaw() - requested.getYaw(), 360)) < 0.1
                && Math.abs(current.getPitch() - requested.getPitch()) < 0.1;
    }

    private void stop(Session session) {
        session.returning = true;
        send(session.observer, Message.MONITOR_RETURNING, NamedTextColor.YELLOW);
        Location origin = session.origin;
        if (origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
            finishReturn(session);
            return;
        }
        // Load only; the guarded main-thread continuation performs the teleport. A stale future
        // after quit, disable or a later session must never teleport the player again.
        origin.getWorld().getChunkAtAsync(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)
                .whenComplete((_, error) -> {
                    if (!running) return;
                    mainThread.accept(() -> {
                        if (!running || sessions.get(session.observer.getUniqueId()) != session) return;
                        if (error == null) finishReturn(session);
                        else returnFailed(session);
                    });
                });
    }

    private void finishReturn(Session session) {
        if (restore(session)) send(session.observer, Message.MONITOR_STOPPED, NamedTextColor.GREEN);
        else returnFailed(session);
    }

    private void returnFailed(Session session) {
        session.returning = false;
        send(session.observer, Message.MONITOR_RETURN_FAILED, NamedTextColor.RED);
    }

    private boolean restore(Session session) {
        Player observer = session.observer;
        session.view.clear(observer);
        if (observer.getGameMode() == GameMode.SPECTATOR) observer.setSpectatorTarget(null);
        if (observer.getSpectatorTarget() != null) return false;
        // Keep spectator protection if another plugin refuses the return teleport.
        if (!observer.teleport(session.origin, TeleportCause.SPECTATE)) return false;
        tab.end(observer);
        observer.setGameMode(session.exitMode);
        if (observer.getGameMode() != session.exitMode) {
            tab.begin(observer, session.originalMode);
            tab.refreshMode(observer);
            return false;
        }
        sessions.remove(observer.getUniqueId());
        reveal(session);
        if (session.exitMode == session.originalMode) {
            observer.setAllowFlight(session.allowFlight);
            observer.setFlying(session.allowFlight && session.flying);
        }
        observer.sendActionBar(Component.empty());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetTeleport(PlayerTeleportEvent event) {
        UUID targetId = event.getPlayer().getUniqueId();
        for (Session session : sessions.values()) {
            if (session.target != null && targetId.equals(session.target.getUniqueId())) session.targetTeleported = true;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        tab.staff(event.getPlayer().getUniqueId(), false);
        for (Session active : sessions.values()) {
            active.hiddenFrom.remove(event.getPlayer().getUniqueId());
            active.tabViewers.remove(event.getPlayer().getUniqueId());
        }
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        // Disconnect/disable cannot wait for a later tick; restore before player data is saved.
        try {
            if (!restore(session)) plugin.getLogger().warning("Monitor return rejected for " + event.getPlayer().getName());
        } finally {
            sessions.remove(event.getPlayer().getUniqueId());
            reveal(session);
        }
    }

    private void refreshVisibility(Session session) {
        for (Player viewer : onlinePlayers.get()) updateVisibility(session, viewer);
    }

    private void updateVisibility(Session session, Player viewer) {
        boolean staff = RolePermissions.canModerate(viewer);
        tab.staff(viewer.getUniqueId(), staff);
        if (viewer.equals(session.observer)) return;
        UUID id = viewer.getUniqueId();
        if (staff) {
            session.tabViewers.remove(id);
            if (session.hiddenFrom.remove(id)) viewer.showPlayer(plugin, session.observer);
        } else if (session.hiddenFrom.add(id)) {
            boolean visible = viewer.canSee(session.observer);
            viewer.hidePlayer(plugin, session.observer);
            if (visible) {
                session.tabViewers.add(id);
                tab.showTab(viewer, session.observer);
            }
        }
    }

    private void reveal(Session session) {
        session.view.clear(session.observer);
        tab.end(session.observer);
        for (Player viewer : onlinePlayers.get()) {
            if (session.hiddenFrom.remove(viewer.getUniqueId())) viewer.showPlayer(plugin, session.observer);
        }
        session.hiddenFrom.clear();
        session.tabViewers.clear();
        var observation = session.observation;
        session.observation = AfkActivityService.ActivityLease.NONE;
        observation.close();
        tab.refreshMode(session.observer);
        tabRefresh.accept(session.observer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        for (Session session : sessions.values()) updateVisibility(session, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!allowCommand(event.getPlayer(), event.getMessage().substring(1))) event.setCancelled(true);
    }

    /** Also checked before teleport consent and again when a delayed request is accepted. */
    public boolean allowCommand(Player sender, String command) {
        if (sessions.isEmpty() || RolePermissions.canModerate(sender)) return true;
        // Native entity arguments do not honor Bukkit's per-viewer hidePlayer state.
        if (!MonitorCommandGuard.targetsMonitor(sender, command, this::isMonitoring)) return true;
        sender.sendMessage(Component.translatable("argument.entity.notfound.entity").color(NamedTextColor.RED));
        return false;
    }

    private void send(Player player, Message message, NamedTextColor color) {
        player.sendMessage(languages.text(player, message, color));
    }

    private static final class Session {
        final Player observer;
        final Location origin;
        final GameMode originalMode;
        final boolean allowFlight;
        final boolean flying;
        final Set<UUID> visited = new HashSet<>();
        final Set<UUID> hiddenFrom = new HashSet<>();
        final Set<UUID> tabViewers = new HashSet<>();
        final MonitorCamera camera;
        final MonitorCameraView view;
        AfkActivityService.ActivityLease observation = AfkActivityService.ActivityLease.NONE;
        GameMode exitMode;
        Player target;
        UUID pinnedTarget;
        long selectedAt;
        boolean returning;
        boolean targetTeleported;

        Session(Player observer, MonitorCamera camera, MonitorCameraView view) {
            this.observer = observer;
            this.camera = camera;
            this.view = view;
            origin = observer.getLocation().clone();
            originalMode = observer.getGameMode();
            exitMode = originalMode;
            allowFlight = observer.getAllowFlight();
            flying = observer.isFlying();
        }
    }
}
