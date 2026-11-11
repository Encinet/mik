package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityMemberships;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Session-owned complete rosters. Absence is authoritative only after every community is loaded. */
public final class SocialMembershipDirectory implements IdentityMemberships, AutoCloseable {
    private final Predicate<ExternalIdentityKey> authority;
    private final Set<String> communities;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, Set<ExternalIdentityKey>> rosters = Map.of();
    private long revision;
    private volatile boolean closed;

    public SocialMembershipDirectory(Predicate<ExternalIdentityKey> authority, Set<String> communities) {
        this.authority = Objects.requireNonNull(authority, "authority");
        this.communities = Set.copyOf(communities);
        if (this.communities.isEmpty()) throw new IllegalArgumentException("Membership communities must be configured");
    }

    @Override
    public boolean owns(ExternalIdentityKey identity) { return authority.test(identity); }

    public boolean contains(String community, ExternalIdentityKey identity) {
        return !closed && authority.test(identity) && rosters.getOrDefault(community, Set.of()).contains(identity);
    }

    @Override
    public State state(ExternalIdentityKey identity) {
        if (closed || !authority.test(identity)) return State.UNKNOWN;
        Map<String, Set<ExternalIdentityKey>> current = rosters;
        if (current.values().stream().anyMatch(members -> members.contains(identity))) return State.JOINED;
        return current.keySet().containsAll(communities) ? State.ABSENT : State.UNKNOWN;
    }

    public synchronized long revision() { return revision; }

    /** A transport gap invalidates absence proofs until an authoritative rebuild completes. */
    public void invalidate() {
        synchronized (this) {
            if (closed) return;
            rosters = Map.of();
            revision++;
        }
        changed();
    }

    /** Each supplied roster is complete; an omitted community remains unknown. */
    public boolean replace(long expectedRevision, Map<String, Set<ExternalIdentityKey>> loaded) {
        synchronized (this) {
            if (closed || revision != expectedRevision) return false;
            if (!communities.containsAll(loaded.keySet())) throw new IllegalArgumentException("Unconfigured membership community");
            Map<String, Set<ExternalIdentityKey>> checked = new LinkedHashMap<>();
            loaded.forEach((community, members) -> {
                if (members.stream().anyMatch(authority.negate())) throw new IllegalArgumentException("Unrelated membership authority");
                checked.put(community, Set.copyOf(members));
            });
            rosters = Map.copyOf(checked);
            revision++;
        }
        changed();
        return true;
    }

    public void member(String community, ExternalIdentityKey identity, boolean joined) {
        synchronized (this) {
            if (closed || !communities.contains(community) || !authority.test(identity)) return;
            // A delta cannot establish that the rest of a previously unknown roster is absent.
            if (!rosters.containsKey(community)) {
                revision++;
                return;
            }
            Map<String, Set<ExternalIdentityKey>> updated = new LinkedHashMap<>(rosters);
            var members = new java.util.HashSet<>(updated.get(community));
            if (joined) members.add(identity); else members.remove(identity);
            updated.put(community, Set.copyOf(members));
            rosters = Map.copyOf(updated);
            revision++;
        }
        changed();
    }

    @Override
    public AutoCloseable subscribe(Runnable changed) {
        Runnable listener = Objects.requireNonNull(changed, "changed");
        if (closed) throw new IllegalStateException("Membership directory is closed");
        listeners.add(listener);
        if (closed) listeners.remove(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() { listeners.forEach(Runnable::run); }

    @Override
    public synchronized void close() {
        closed = true;
        listeners.clear();
        rosters = Map.of();
    }
}
