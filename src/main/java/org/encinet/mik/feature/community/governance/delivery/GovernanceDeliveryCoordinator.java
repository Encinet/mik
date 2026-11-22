package org.encinet.mik.feature.community.governance.delivery;

import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.model.user.User;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.GovernanceText;
import org.encinet.mik.feature.community.governance.gateway.GovernanceViews;
import org.encinet.mik.protocol.GovernanceDeliveryReceipt;
import org.encinet.mik.protocol.GovernanceGameGrant;
import org.encinet.mik.protocol.GovernanceGameGrant.Permission;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.protocol.GovernanceVoteView;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/** Orders writes for a whole LuckPerms user without waiting on storage on the dispatch thread. */
public final class GovernanceDeliveryCoordinator implements AutoCloseable {
    private static final int MAX_PLAYERS = 8;
    private static final int REVOCATION_SLOTS = 2;

    private final JavaPlugin plugin;
    private final GovernanceGateway governance;
    private final LuckPermsGovernanceRoles roles;
    private final GovernanceText text;
    private final ExecutorService worker;
    private final AtomicBoolean polling = new AtomicBoolean();
    // Only the worker reads or updates deliveries and their observed generations.
    private final Map<UUID, Delivery> active = new HashMap<>();
    private final Set<UUID> justFailed = new HashSet<>();
    private final Set<Long> announced = new HashSet<>(); // Paper main thread only
    private boolean announcing;
    private long lastError;
    private volatile boolean closed;

    public GovernanceDeliveryCoordinator(JavaPlugin plugin, GovernanceGateway governance,
                                         LuckPermsGovernanceRoles roles, GovernanceText text) {
        this(plugin, governance, roles, text, Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("mik-governance-delivery").factory()));
    }

    GovernanceDeliveryCoordinator(JavaPlugin plugin, GovernanceGateway governance,
                                  LuckPermsGovernanceRoles roles, GovernanceText text, ExecutorService worker) {
        this.plugin = plugin;
        this.governance = governance;
        this.roles = roles;
        this.text = text;
        this.worker = worker;
    }

    public void poll() {
        if (!closed && polling.compareAndSet(false, true)) dispatch(this::fetch);
    }

    private void fetch() {
        // A poll can outlive a delivery. Keep its generation fence until the reply is consumed.
        var inFlight = Map.copyOf(active);
        var deferred = new HashSet<>(inFlight.keySet());
        Set<UUID> cooling = Set.copyOf(justFailed);
        deferred.addAll(cooling);
        justFailed.clear();
        try {
            after(governance.pollDeliveries(deferred), (result, failure) -> {
                try {
                    if (failure == null) receive(result, inFlight, cooling);
                    else report("Could not poll governance deliveries", failure);
                } catch (RuntimeException error) {
                    report("Could not process governance deliveries", error);
                } finally {
                    polling.set(false);
                }
            });
        } catch (RuntimeException error) {
            polling.set(false);
            report("Could not poll governance deliveries", error);
        }
    }

    private void receive(GovernanceResult result, Map<UUID, Delivery> inFlight, Set<UUID> cooling) {
        announce(result.getVotesList());
        for (var hint : result.getGrantsList()) {
            UUID player = UUID.fromString(hint.getPlayerId());
            var atFetch = inFlight.get(player);
            if (atFetch != null && atFetch.olderThanObserved(hint)) continue;
            var delivery = active.get(player);
            if (delivery != null) {
                if (delivery.olderThanObserved(hint)) continue;
                if (delivery.remember(hint) && !hint.getEnabled()) delivery.superseded = true;
            }
            // Rust returns current targets at the poll snapshot. Withdrawing a loaded node
            // does not load an offline user, save anything, or consume a persistence slot.
            if (!hint.getEnabled()) withdrawLive(player, hint);
            if (delivery == null && !cooling.contains(player)
                    && active.size() < (hint.getEnabled() ? MAX_PLAYERS - REVOCATION_SLOTS : MAX_PLAYERS)) {
                start(player, hint);
            }
        }
    }

    private void start(UUID player, GovernanceGameGrant hint) {
        var delivery = new Delivery(player);
        delivery.remember(hint);
        active.put(player, delivery);
        reconcile(delivery);
    }

    private void reconcile(Delivery delivery) {
        delivery.superseded = false;
        try {
            var attempt = roles.load(delivery.player).thenComposeAsync(user -> {
                if (closed) return CompletableFuture.completedFuture(false);
                return governance.grantTargets(delivery.player)
                        .thenComposeAsync(targets -> write(delivery, user, targets), worker);
            }, worker);
            after(attempt, (retry, failure) -> {
                if (delivery.superseded || Boolean.TRUE.equals(retry)) {
                    if (failure != null) report("Could not deliver governance roles for " + delivery.player, failure);
                    reconcile(delivery);
                } else {
                    finish(delivery, failure);
                }
            });
        } catch (RuntimeException error) {
            finish(delivery, error);
        }
    }

    private CompletableFuture<Boolean> write(Delivery delivery, User user, List<GovernanceGameGrant> targets) {
        if (closed || targets.isEmpty() || targets.stream().anyMatch(delivery::olderThanObserved)) {
            return CompletableFuture.completedFuture(false);
        }
        var receipt = GovernanceDeliveryReceipt.newBuilder();
        for (var target : targets) {
            delivery.remember(target);
            roles.project(user, target);
            receipt.addGrants(target.getId());
        }
        // Never time out or cancel this future to free the slot. A late save could overwrite its successor.
        return roles.save(user).thenComposeAsync(_ -> {
            if (closed || delivery.superseded) return CompletableFuture.completedFuture(!closed);
            return governance.confirm(receipt.build()).thenApplyAsync(result -> {
                result.getGrantsList().forEach(delivery::remember);
                return result.getGrantsCount() != 0;
            }, worker);
        }, worker);
    }

    private void withdrawLive(UUID player, GovernanceGameGrant hint) {
        try {
            // Recheck the loaded user: a reconnect may have loaded the old saved node again.
            User loaded = roles.loaded(player);
            if (loaded != null) roles.project(loaded, hint);
        } catch (RuntimeException error) {
            report("Could not withdraw live governance role for " + player, error);
        }
    }

    private void finish(Delivery delivery, Throwable failure) {
        active.remove(delivery.player);
        if (failure != null) {
            justFailed.add(delivery.player);
            report("Could not deliver governance roles for " + delivery.player, failure);
        }
    }

    private void announce(List<GovernanceVoteView> votes) {
        if (votes.isEmpty() || announcing) return;
        announcing = true;
        try {
            onMain(() -> {
                if (closed) return;
                var receipt = GovernanceDeliveryReceipt.newBuilder();
                try {
                    for (var view : votes) {
                        if (!announced.contains(view.getId())) {
                            var vote = GovernanceViews.vote(view);
                            text.broadcast(language -> text.voteResult(language, vote), NamedTextColor.GOLD);
                            announced.add(view.getId());
                        }
                        receipt.addAnnouncements(view.getId());
                    }
                } finally {
                    dispatch(() -> confirmAnnouncements(receipt.build()));
                }
            });
        } catch (RuntimeException error) {
            announcing = false;
            report("Could not schedule governance announcements", error);
        }
    }

    private void confirmAnnouncements(GovernanceDeliveryReceipt receipt) {
        if (receipt.getAnnouncementsCount() == 0) {
            announcing = false;
            return;
        }
        try {
            after(governance.confirm(receipt), (_, failure) -> {
                announcing = false;
                if (failure == null) onMain(() -> announced.removeAll(receipt.getAnnouncementsList()));
                else report("Could not confirm governance announcements", failure);
            });
        } catch (RuntimeException error) {
            announcing = false;
            report("Could not confirm governance announcements", error);
        }
    }

    private void onMain(Runnable action) { plugin.getServer().getScheduler().runTask(plugin, action); }

    private <T> void after(CompletableFuture<T> future, BiConsumer<T, Throwable> action) {
        future.whenComplete((value, failure) -> dispatch(() -> action.accept(value, failure)));
    }

    private void dispatch(Runnable action) {
        if (closed) return;
        try { worker.execute(() -> { if (!closed) action.run(); }); }
        catch (RejectedExecutionException error) { if (!closed) throw error; }
    }

    private void report(String message, Throwable failure) {
        long now = System.nanoTime();
        if (lastError == 0 || now - lastError >= Duration.ofMinutes(1).toNanos()) {
            lastError = now;
            plugin.getLogger().log(Level.WARNING, message, failure);
        }
    }

    @Override public void close() {
        closed = true;
        worker.shutdown();
    }

    private static final class Delivery {
        private final UUID player;
        private final Map<Permission, Long> seen = new EnumMap<>(Permission.class);
        private boolean superseded;

        private Delivery(UUID player) { this.player = player; }

        private long observed(GovernanceGameGrant grant) { return seen.getOrDefault(grant.getPermission(), 0L); }
        private boolean olderThanObserved(GovernanceGameGrant grant) { return grant.getId() < observed(grant); }

        private boolean remember(GovernanceGameGrant grant) {
            if (grant.getId() <= observed(grant)) return false;
            seen.put(grant.getPermission(), grant.getId());
            return true;
        }
    }
}
