package org.encinet.mik.module.governance.platform.paper.membership;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.i18n.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Samples AFK eligibility and persists bounded, midnight-split play intervals. */
public final class EffectivePlaytimeTracker implements Listener {
    private static final long SAMPLE_INTERVAL_TICKS = 20L * 60L;
    private static final Duration MAX_ACCOUNTED_SAMPLE = Duration.ofSeconds(75);

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MembershipService membership;
    private final MembershipRoleSnapshot roles;
    private final AutomaticPromotionCoordinator promotions;
    private final GovernanceDeliveryCoordinator delivery;
    private final GovernanceTaskExecutor executor;
    private final GovernanceText text;
    private final Map<UUID, SampleCursor> samples = new HashMap<>();

    private BukkitTask sampleTask;

    public EffectivePlaytimeTracker(
            JavaPlugin plugin,
            AfkService afkService,
            MembershipService membership,
            MembershipRoleSnapshot roles,
            AutomaticPromotionCoordinator promotions,
            GovernanceDeliveryCoordinator delivery,
            GovernanceTaskExecutor executor,
            GovernanceText text
    ) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.membership = membership;
        this.roles = roles;
        this.promotions = promotions;
        this.delivery = delivery;
        this.executor = executor;
        this.text = text;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            beginSession(player, now);
            roles.remember(player);
            observeLogin(player);
        }
        sampleTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::sampleOnlinePlayers,
                SAMPLE_INTERVAL_TICKS, SAMPLE_INTERVAL_TICKS);
    }

    public void stop() {
        if (sampleTask != null) sampleTask.cancel();
        sampleTask = null;
        HandlerList.unregisterAll(this);

        Instant now = Instant.now();
        List<PlayInterval> finalIntervals = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayInterval interval = closeSample(player.getUniqueId(), now,
                    afkService.isActivityEligible(player.getUniqueId()));
            if (interval != null) finalIntervals.add(interval);
        }
        samples.clear();
        if (!finalIntervals.isEmpty()) {
            executor.submit(() -> recordIntervals(finalIntervals));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        beginSession(player, Instant.now());
        roles.remember(player);
        observeLogin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayInterval interval = closeSample(player.getUniqueId(), Instant.now(),
                afkService.isActivityEligible(player.getUniqueId()));
        if (interval != null) {
            executor.submit(() -> recordIntervals(List.of(interval)));
        }
    }

    private void beginSession(Player player, Instant now) {
        samples.put(player.getUniqueId(), new SampleCursor(
                now, afkService.isActivityEligible(player.getUniqueId())));
    }

    private void observeLogin(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        long firstPlayed = player.getFirstPlayed();
        Instant firstJoinedAt = firstPlayed > 0
                ? Instant.ofEpochMilli(firstPlayed) : Instant.now();
        boolean member = roles.hasMemberRole(player);
        executor.submit(() -> {
            membership.observeLogin(playerId, playerName, firstJoinedAt, member);
            PromotionPause pause = membership.promotionEligibility(playerId).activePause();
            if (pause == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null) return;
                text.send(online, Message.GOVERNANCE_PAUSE_NOTICE,
                        NamedTextColor.YELLOW, pause.reason(),
                        text.time(text.language(online), pause.endsAt()), pause.appealPath());
            });
        });
    }

    private void sampleOnlinePlayers() {
        Instant now = Instant.now();
        List<PlayInterval> intervals = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            boolean eligibleNow = afkService.isActivityEligible(playerId);
            PlayInterval interval = closeSample(playerId, now, eligibleNow);
            if (interval != null) intervals.add(interval);
            samples.put(playerId, new SampleCursor(now, eligibleNow));
        }
        List<AutomaticPromotionCoordinator.Candidate> candidates =
                promotions.captureCandidates();
        executor.submit(() -> {
            recordIntervals(intervals);
            promotions.review(candidates);
            delivery.reviewOpenVotes();
        });
    }

    private PlayInterval closeSample(UUID playerId, Instant now, boolean eligibleNow) {
        SampleCursor previous = samples.remove(playerId);
        if (previous == null || !previous.eligible() || !eligibleNow
                || !now.isAfter(previous.sampledAt())) {
            return null;
        }
        Instant earliest = now.minus(MAX_ACCOUNTED_SAMPLE);
        Instant from = previous.sampledAt().isBefore(earliest)
                ? earliest : previous.sampledAt();
        return new PlayInterval(playerId, from, now);
    }

    private void recordIntervals(List<PlayInterval> intervals) {
        for (PlayInterval interval : intervals) {
            try {
                membership.recordEligibleInterval(
                        interval.playerId(), interval.from(), interval.to());
            } catch (GovernanceException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not persist effective playtime for "
                                + interval.playerId(), error);
            }
        }
    }

    private record SampleCursor(Instant sampledAt, boolean eligible) {
    }

    private record PlayInterval(UUID playerId, Instant from, Instant to) {
    }
}
