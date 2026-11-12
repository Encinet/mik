package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/** Short-lived consent for one exact binding, principal, conversation and runtime generation. */
public final class UnlinkConfirmations implements AutoCloseable {
    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private static final int CAPACITY = 2_048;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<Key, Pending> pending = new LinkedHashMap<>();
    private boolean closed;

    public UnlinkConfirmations() { this(Clock.systemUTC()); }
    UnlinkConfirmations(Clock clock) { this.clock = java.util.Objects.requireNonNull(clock, "clock"); }

    public synchronized String issue(SocialCommandContext context, ExternalIdentityKey identity, UUID bindingId) {
        if (closed) throw new IllegalStateException("Unlink confirmations are closed");
        Instant now = clock.instant();
        pending.values().removeIf(value -> !now.isBefore(value.expiresAt()));
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.putLast(key(context, identity), new Pending(token, bindingId, now.plus(LIFETIME)));
        while (pending.size() > CAPACITY) pending.remove(pending.keySet().iterator().next());
        return token;
    }

    public synchronized Optional<UUID> consume(SocialCommandContext context, ExternalIdentityKey identity, String token) {
        if (closed) return Optional.empty();
        Key key = key(context, identity);
        Pending value = pending.get(key);
        if (value == null) return Optional.empty();
        if (!clock.instant().isBefore(value.expiresAt())) {
            pending.remove(key);
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(value.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        pending.remove(key);
        return Optional.of(value.bindingId());
    }

    @Override
    public synchronized void close() { closed = true; pending.clear(); }

    private static Key key(SocialCommandContext context, ExternalIdentityKey identity) {
        return new Key(identity, context.message().conversation(), context.generationId());
    }

    private record Key(ExternalIdentityKey identity, SocialConversation conversation, UUID generation) { }
    private record Pending(String token, UUID bindingId, Instant expiresAt) { }
}
