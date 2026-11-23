package org.encinet.mik.feature.community.governance.membership;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.GovernanceTaskExecutor;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.server.ban.BanManager;
import org.encinet.mik.protocol.GovernanceActivityInterval;
import org.encinet.mik.protocol.GovernanceBan;
import org.encinet.mik.protocol.GovernanceBanSnapshot;
import org.encinet.mik.protocol.GovernanceCredential;
import org.encinet.mik.protocol.GovernanceObservation;
import org.encinet.mik.protocol.GovernanceObservations;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public final class EffectivePlaytimeTracker implements Listener {
    private static final long SAMPLE_TICKS = 200L;
    private static final long MAX_ACCOUNTED_MS = 20_000L;
    private static final int MAX_BACKLOG = 4096;
    private static final int BATCH_SIZE = 256;

    private final JavaPlugin plugin;
    private final AfkService afk;
    private final BanManager bans;
    private final Predicate<Player> authenticated;
    private final Function<UUID, GovernanceCredential> credentials;
    private final GovernanceGateway governance;
    private final GovernanceTaskExecutor executor;
    private final Map<UUID, Sample> sessions = new HashMap<>();
    private final ArrayDeque<Observation> backlog = new ArrayDeque<>();
    private GovernanceBanSnapshot banSnapshot;
    private BukkitTask task;
    private boolean sending;

    public EffectivePlaytimeTracker(JavaPlugin plugin, AfkService afk, BanManager bans, Predicate<Player> authenticated,
                                    Function<UUID, GovernanceCredential> credentials, GovernanceGateway governance,
                                    GovernanceTaskExecutor executor) {
        this.plugin = plugin;
        this.afk = afk;
        this.bans = bans;
        this.authenticated = authenticated;
        this.credentials = credentials;
        this.governance = governance;
        this.executor = executor;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 1, SAMPLE_TICKS);
    }

    public void close() {
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        backlog.clear();
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!authenticated.test(player)) return;
        long now = System.currentTimeMillis();
        begin(player, now, now);
        flush();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var previous = sessions.remove(player.getUniqueId());
        recordInterval(player, previous, System.currentTimeMillis(), afk.isActivityEligible(player.getUniqueId()));
        flush();
    }

    public void credentialChanged(UUID id) {
        if (task == null) return;
        var player = Bukkit.getOfflinePlayer(id);
        queue(new Observation(id, player.getName() == null ? id.toString() : player.getName(),
                System.currentTimeMillis(), null, null, true));
        flush();
    }

    private void begin(Player player, long now, Long login) {
        UUID id = player.getUniqueId();
        sessions.put(id, new Sample(now, afk.isActivityEligible(id)));
        queue(new Observation(id, player.getName(), now, login, null, true));
    }

    private void sample() {
        long now = System.currentTimeMillis();
        var snapshot = GovernanceBanSnapshot.newBuilder().setObservedMs(now);
        for (var record : bans.activeRecords()) {
            var ban = GovernanceBan.newBuilder().setSubject(record.playerUuid() == null
                    ? record.normalizedName() : record.playerUuid().toString());
            if (record.expiresAt() != null) ban.setExpiresMs(record.expiresAt().toEpochMilli());
            snapshot.addBans(ban);
        }
        banSnapshot = snapshot.build();
        for (var player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!authenticated.test(player)) {
                sessions.remove(id);
                continue;
            }
            boolean eligible = afk.isActivityEligible(id);
            var previous = sessions.get(id);
            if (previous == null) {
                long login = player.getLastLogin();
                begin(player, now, login > 0 && login <= now ? login : null);
            } else {
                recordInterval(player, previous, now, eligible);
                sessions.put(id, new Sample(now, eligible));
            }
        }
        flush();
    }

    private void recordInterval(Player player, Sample previous, long now, boolean eligible) {
        if (previous == null || !previous.eligible() || !eligible || now <= previous.at()) return;
        var interval = GovernanceActivityInterval.newBuilder().setStartMs(Math.max(previous.at(), now - MAX_ACCOUNTED_MS))
                .setEndMs(now).build();
        queue(new Observation(player.getUniqueId(), player.getName(), now, null, interval, false));
    }

    private void queue(Observation observation) {
        if (backlog.size() >= MAX_BACKLOG) {
            if (!observation.credential() && observation.login() == null) return;
            var queued = backlog.descendingIterator();
            while (queued.hasNext()) {
                var item = queued.next();
                if (!item.credential() && item.login() == null) { queued.remove(); break; }
            }
        }
        if (backlog.size() < MAX_BACKLOG) backlog.addLast(observation);
    }

    private void flush() {
        if (task == null || sending || (backlog.isEmpty() && banSnapshot == null)) return;
        var selected = backlog.stream().limit(BATCH_SIZE).toList();
        var snapshot = selected.size() == backlog.size() ? banSnapshot : null;
        sending = true;
        try { executor.submit(() -> {
            boolean delivered = false;
            try {
                var request = GovernanceObservations.newBuilder();
                if (snapshot != null) request.setBanSnapshot(snapshot);
                for (var fact : selected) {
                    var observation = GovernanceObservation.newBuilder().setPlayer(GovernanceGateway.player(fact.id(), fact.name()))
                            .setObservedMs(fact.at());
                    if (fact.login() != null) observation.setLoginMs(fact.login());
                    if (fact.interval() != null) observation.addActivity(fact.interval());
                    if (fact.credential()) observation.setCredential(credentials.apply(fact.id()));
                    request.addPlayers(observation);
                }
                governance.observe(request.build());
                delivered = true;
            } finally {
                boolean committed = delivered;
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    sending = false;
                    if (task == null || !committed) return;
                    backlog.removeAll(selected);
                    if (banSnapshot == snapshot) banSnapshot = null;
                    flush();
                });
            }
        }); } catch (java.util.concurrent.RejectedExecutionException ignored) { sending = false; }
    }

    private record Sample(long at, boolean eligible) { }

    private record Observation(UUID id, String name, long at, Long login, GovernanceActivityInterval interval,
                               boolean credential) { }
}
