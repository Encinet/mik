package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.feature.community.identity.*;
import org.encinet.mik.feature.community.social.SocialTestReplies;
import org.encinet.mik.feature.community.social.api.*;
import org.encinet.mik.feature.community.social.command.*;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnlinkConfirmationTest {
    private static final ExternalIdentity IDENTITY = new ExternalIdentity(new ExternalIdentityKey("qq", "app", "", "alice"), "Alice");

    @Test
    void confirmationIsScopedExpiresAndCanOnlyBeConsumedOnce() {
        var clock = new MutableClock();
        try (var store = new UnlinkConfirmations(clock)) {
            var context = context("room", UUID.randomUUID());
            var binding = UUID.randomUUID();
            String token = store.issue(context, IDENTITY.key(), binding);
            assertTrue(store.consume(context("other-room", context.generationId()), IDENTITY.key(), token).isEmpty());
            assertTrue(store.consume(context("room", UUID.randomUUID()), IDENTITY.key(), token).isEmpty());
            var otherUser = new ExternalIdentityKey("qq", "app", "", "bob");
            assertTrue(store.consume(context, otherUser, token).isEmpty());
            assertTrue(store.consume(context, IDENTITY.key(), "wrong").isEmpty());
            assertEquals(Optional.of(binding), store.consume(context, IDENTITY.key(), token));
            assertTrue(store.consume(context, IDENTITY.key(), token).isEmpty());
            String expired = store.issue(context, IDENTITY.key(), binding);
            clock.now = clock.now.plusSeconds(120);
            assertTrue(store.consume(context, IDENTITY.key(), expired).isEmpty());
            String previous = store.issue(context, IDENTITY.key(), binding);
            String replacement = store.issue(context, IDENTITY.key(), binding);
            assertTrue(store.consume(context, IDENTITY.key(), previous).isEmpty());
            assertEquals(Optional.of(binding), store.consume(context, IDENTITY.key(), replacement));
        }
    }

    @Test
    void aConfirmationForAnOldBindingCannotDeleteItsReplacement() {
        var current = new AtomicReference<>(binding("Alice"));
        var bindings = mock(IdentityBindingManager.class);
        when(bindings.find(IDENTITY.key())).thenAnswer(_ -> Optional.ofNullable(current.get()));
        when(bindings.unlink(eq(IDENTITY.key()), any(UUID.class))).thenAnswer(invocation -> {
            var value = current.get();
            return value != null && value.bindingId().equals(invocation.getArgument(1)) && current.compareAndSet(value, null)
                    ? Optional.of(value) : Optional.empty();
        });
        var languages = FluentTestSupport.languages();
        try (var store = new UnlinkConfirmations()) {
            var command = new UnlinkIdentityCommand(bindings, new SocialCommandLanguageResolver(bindings, languages), languages, store);
            var context = context("room", UUID.randomUUID());
            assertInstanceOf(UnlinkIdentityCommand.Result.Expired.class,
                    command.execute(context, new UnlinkIdentityCommand.Request(Optional.of("invented"))).value());
            verify(bindings, never()).unlink(any(), any());
            var prompt = assertInstanceOf(UnlinkIdentityCommand.Result.Prompt.class,
                    command.execute(context, new UnlinkIdentityCommand.Request(Optional.empty())).value());
            var replacement = binding("Bob");
            current.set(replacement);
            assertInstanceOf(UnlinkIdentityCommand.Result.Expired.class,
                    command.execute(context, new UnlinkIdentityCommand.Request(Optional.of(prompt.token()))).value());
            assertEquals(replacement, current.get());
            var fresh = assertInstanceOf(UnlinkIdentityCommand.Result.Prompt.class,
                    command.execute(context, new UnlinkIdentityCommand.Request(Optional.empty())).value());
            var completed = command.execute(context, new UnlinkIdentityCommand.Request(Optional.of(fresh.token())));
            assertEquals(Optional.of(replacement), assertInstanceOf(UnlinkIdentityCommand.Result.Unlinked.class, completed.value()).binding());
            assertEquals(Optional.of(SocialCompletionReceipt.BINDING_UNLINKED), completed.receipt());
            assertNull(current.get());
            assertInstanceOf(UnlinkIdentityCommand.Result.Expired.class,
                    command.execute(context, new UnlinkIdentityCommand.Request(Optional.of(fresh.token()))).value());
            verify(bindings, times(2)).unlink(any(), any());
        }
    }

    private static IdentityBinding binding(String name) {
        return new IdentityBinding(UUID.randomUUID(), UUID.randomUUID(), name, IDENTITY.key(), name, "", Instant.EPOCH, Instant.EPOCH);
    }

    private static SocialCommandContext context(String room, UUID generation) {
        var message = new SocialInboundMessage("event", new SocialConversation(room, SocialConversation.Type.GROUP),
                Optional.of(IDENTITY), "/解绑", false, SocialTestReplies::accepted);
        return new SocialCommandContext(new SocialPlatformDescriptor("qq", "QQ", 0), message,
                SocialCommandInput.explicit("解绑", ""), SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/"), generation);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.EPOCH;
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
