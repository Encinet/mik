package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityMemberships;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink.Acceptance;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewayGroupMessage;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGroupMemberEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Durable, partial membership evidence from authenticated Gateway events, without a roster API. */
final class QqMembershipDirectory implements IdentityMemberships, AutoCloseable {
    private final QqPlatformConfig config;
    private final QqMembershipStore store;
    private final Logger logger;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, Map<String, Boolean>> observations;
    private volatile boolean available;
    private volatile boolean closed;
    private boolean storageUnavailable;

    QqMembershipDirectory(QqPlatformConfig config, Path file, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        if (config.allowedGroupOpenIds().isEmpty()) throw new IllegalArgumentException("QQ membership groups must be configured");
        this.logger = Objects.requireNonNull(logger, "logger");
        store = new QqMembershipStore(file);
        try {
            var loaded = new LinkedHashMap<>(store.load(config.appId()));
            loaded.keySet().retainAll(config.allowedGroupOpenIds());
            observations = Map.copyOf(loaded);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not load QQ membership observations", error);
        }
    }

    @Override
    public boolean owns(ExternalIdentityKey identity) {
        return QqAccountIdentity.belongsTo(config.appId(), identity);
    }

    @Override
    public State state(ExternalIdentityKey identity) {
        if (closed || !available || !owns(identity)) return State.UNKNOWN;
        Map<String, Map<String, Boolean>> current = observations;
        boolean absentEverywhere = true;
        for (String group : config.allowedGroupOpenIds()) {
            Boolean joined = current.getOrDefault(group, Map.of()).get(identity.subject());
            if (Boolean.TRUE.equals(joined)) return State.JOINED;
            if (!Boolean.FALSE.equals(joined)) absentEverywhere = false;
        }
        return absentEverywhere ? State.ABSENT : State.UNKNOWN;
    }

    boolean contains(SocialConversation conversation, ExternalIdentityKey identity) {
        return !closed && available && owns(identity) && conversation.type() == SocialConversation.Type.GROUP
                && Boolean.TRUE.equals(observations.getOrDefault(conversation.id(), Map.of()).get(identity.subject()));
    }

    void gateway(SocialSessionStatus status) {
        if (closed) return;
        available = status.state() == SocialSessionStatus.State.READY;
        changed();
    }

    Acceptance accept(QqGatewayGroupMessage message) {
        if (message.authorIsBot()) return Acceptance.IGNORED;
        return observe(message.groupOpenId(), message.memberOpenId(), true);
    }

    Acceptance accept(QqGroupMemberEvent event) {
        return observe(event.groupOpenId(), event.memberOpenId(), event.joined());
    }

    /** Gateway callbacks run off the game thread. Persist before publishing evidence or admitting commands. */
    private Acceptance observe(String group, String member, boolean joined) {
        synchronized (this) {
            if (closed || !config.allowedGroupOpenIds().contains(group)
                    || QqAccountIdentity.from(config.appId(), member, "").isEmpty()) return Acceptance.IGNORED;
            Map<String, Boolean> previous = observations.getOrDefault(group, Map.of());
            if (Boolean.valueOf(joined).equals(previous.get(member))) return Acceptance.ACCEPTED;
            var members = new LinkedHashMap<>(previous);
            members.put(member, joined);
            var next = new LinkedHashMap<>(observations);
            next.put(group, Map.copyOf(members));
            try {
                store.save(config.appId(), next);
                observations = Map.copyOf(next);
                storageUnavailable = false;
            } catch (IOException error) {
                if (!storageUnavailable) logger.log(Level.WARNING,
                        "Could not persist QQ membership event; requesting Gateway replay", error);
                storageUnavailable = true;
                return Acceptance.RETRY_LATER;
            }
        }
        changed();
        return Acceptance.ACCEPTED;
    }

    @Override
    public AutoCloseable subscribe(Runnable changed) {
        Runnable listener = Objects.requireNonNull(changed, "changed");
        if (closed) throw new IllegalStateException("QQ membership directory is closed");
        listeners.add(listener);
        if (closed) listeners.remove(listener);
        return () -> listeners.remove(listener);
    }

    private void changed() { listeners.forEach(Runnable::run); }

    @Override
    public synchronized void close() {
        closed = true;
        available = false;
        listeners.clear();
        observations = Map.of();
    }
}
