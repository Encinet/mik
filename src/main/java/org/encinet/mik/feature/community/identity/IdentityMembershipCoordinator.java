package org.encinet.mik.feature.community.identity;

import org.encinet.mik.shared.util.ShutdownSequence;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Reconciles authoritative platform rosters with durable bindings on the identity writer. */
final class IdentityMembershipCoordinator implements AutoCloseable {
    private final Map<UUID, Watch> watches = new ConcurrentHashMap<>();
    private final IdentityBindingService service;
    private final Executor writes;
    private final Consumer<IdentityBinding> removed;
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private volatile boolean closed;
    private boolean storageUnavailable;

    IdentityMembershipCoordinator(IdentityBindingService service, Executor writes, Consumer<IdentityBinding> removed) {
        this.service = service;
        this.writes = writes;
        this.removed = removed;
    }

    synchronized AutoCloseable watch(IdentityMemberships memberships) {
        if (closed) throw new IllegalStateException("Membership coordinator is closed");
        UUID id = UUID.randomUUID();
        AutoCloseable subscription = memberships.subscribe(this::request);
        watches.put(id, new Watch(memberships, subscription));
        request();
        return () -> closeWatch(id);
    }

    IdentityMemberships.State state(ExternalIdentityKey identity) {
        boolean relevant = false;
        boolean unknown = false;
        for (Watch watch : watches.values()) {
            if (!watch.memberships().owns(identity)) continue;
            relevant = true;
            IdentityMemberships.State state = Objects.requireNonNull(watch.memberships().state(identity));
            if (state == IdentityMemberships.State.JOINED) return state;
            unknown |= state == IdentityMemberships.State.UNKNOWN;
        }
        return relevant && !unknown ? IdentityMemberships.State.ABSENT : IdentityMemberships.State.UNKNOWN;
    }

    void request() {
        if (closed) return;
        dirty.set(true);
        if (!queued.compareAndSet(false, true)) return;
        try {
            writes.execute(this::reconcile);
        } catch (RejectedExecutionException ignored) {
            queued.set(false);
            // The admission heartbeat retries; saturated command queues cannot discard revocations.
        }
    }

    void retry() { if (dirty.get()) request(); }

    private void reconcile() {
        boolean retryLater = false;
        try {
            while (!closed && dirty.getAndSet(false)) {
                synchronized (service) {
                    for (IdentityBinding binding : service.bindings()) {
                        if (state(binding.externalKey()) == IdentityMemberships.State.ABSENT) {
                            service.unlinkOwned(binding.playerId(), binding.bindingId()).ifPresent(removed);
                        }
                    }
                }
            }
            storageUnavailable = false;
        } catch (RuntimeException error) {
            dirty.set(true);
            retryLater = true;
            if (!storageUnavailable) {
                storageUnavailable = true;
                System.getLogger(IdentityMembershipCoordinator.class.getName()).log(System.Logger.Level.ERROR,
                        "Could not reconcile identity memberships; retrying on the next heartbeat", error);
            }
        } finally {
            queued.set(false);
            if (dirty.get() && !closed && !retryLater) request();
        }
    }

    private void closeWatch(UUID id) throws Exception {
        Watch watch = watches.remove(id);
        if (watch != null) watch.subscription().close();
    }

    @Override
    public synchronized void close() {
        closed = true;
        var shutdown = new ShutdownSequence();
        for (UUID id : watches.keySet()) {
            shutdown.attempt("membership subscription", () -> closeWatch(id));
        }
        shutdown.finish("identity memberships");
    }

    private record Watch(IdentityMemberships memberships, AutoCloseable subscription) { }
}
