package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.feature.community.ai.api.AiGateway;
import org.encinet.mik.feature.community.ai.api.AiRequest;
import org.encinet.mik.feature.community.ai.api.AiRequestException;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingException;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.community.identity.IdentityLinkResult;
import org.encinet.mik.feature.community.identity.IdentityPlatform;
import org.encinet.mik.feature.community.identity.IdentityPlatformRegistration;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandInput;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSocialCommandTest {

    @Test
    void boundIdentitySuppliesMinecraftContextAndPreferredLanguage() {
        ExternalIdentity identity = identity("alice-social");
        UUID playerId = UUID.randomUUID();
        IdentityBinding binding = new IdentityBinding(UUID.randomUUID(),
                playerId, "Alice", identity.key(), identity.displayName(), "",
                Instant.EPOCH, Instant.EPOCH);
        StubLanguages languages = new StubLanguages(Optional.of(Language.JA_JP));
        FakeGateway gateway = new FakeGateway();
        AiSocialCommand command = command(
                gateway, new StubBindings(Optional.of(binding)), languages);

        AiSocialCommand.Result result = command.execute(
                context(identity, "event-1", "サーバーの状態は？", Language.EN_US),
                "サーバーの状態は？").value();
        SocialDocument document = command.present(new org.encinet.mik.feature.community.social.command.SocialPresentationContext(
                        context(identity, "event-1", "サーバーの状態は？", Language.EN_US), Language.JA_JP, languages), result);

        assertInstanceOf(AiSocialCommand.Result.Answer.class, result);
        AiRequest request = gateway.requests.getFirst();
        assertEquals("Alice", request.requesterName());
        assertEquals("ja_jp", request.language());
        assertEquals(Optional.of(playerId), request.playerId());
        assertEquals("QQ", request.interaction().surface());
        assertEquals(org.encinet.mik.feature.community.ai.api.AiInteractionContext.BindingStatus.BOUND,
                request.interaction().binding());
        assertTrue(request.interaction().operations().stream()
                .anyMatch(operation -> operation.invocation().equals("/ヘルプ")));
        assertEquals(REQUIRED, document.moderation());
        assertEquals(Language.JA_JP, document.language());
    }

    @Test
    void isolatesHistoryByPlatformConversationAndAuthenticatedPrincipal() {
        StubLanguages languages = new StubLanguages(Optional.empty());
        FakeGateway gateway = new FakeGateway();
        AiSocialCommand command = command(
                gateway, new StubBindings(Optional.empty()), languages);
        ExternalIdentity first = identity("first-user");
        ExternalIdentity second = identity("second-user");

        command.execute(context(first, "event-1", "one", Language.EN_US), "one").value();
        command.execute(context(first, "event-2", "two", Language.EN_US), "two").value();
        command.execute(context(second, "event-3", "three", Language.EN_US), "three").value();

        assertEquals(gateway.requests.get(0).conversationId(),
                gateway.requests.get(1).conversationId());
        assertNotEquals(gateway.requests.get(0).conversationId(),
                gateway.requests.get(2).conversationId());
        assertTrue(gateway.requests.getFirst().conversationId().startsWith("social:"));
        assertTrue(gateway.requests.getFirst().conversationId().length() < 64);
    }

    @Test
    void bindingChangesAndUnavailableLookupsCannotReuseAnotherAccountsHistory() {
        var identity = identity("same-social-account");
        var alice = new IdentityBinding(UUID.randomUUID(), UUID.randomUUID(), "Alice", identity.key(),
                identity.displayName(), "", Instant.EPOCH, Instant.EPOCH);
        var bindings = new StubBindings(Optional.of(alice));
        var gateway = new FakeGateway();
        var command = command(gateway, bindings, new StubLanguages(Optional.empty()));
        var context = context(identity, "event", "question", Language.EN_US);
        command.execute(context, "alice question").value();
        bindings.binding = Optional.of(new IdentityBinding(alice.bindingId(), alice.playerId(), "AliceRenamed",
                alice.externalKey(), alice.externalDisplayName(), "", Instant.EPOCH, Instant.EPOCH.plusSeconds(1)));
        command.execute(context, "same binding").value();
        bindings.binding = Optional.empty();
        command.execute(context, "unbound question").value();
        bindings.binding = Optional.of(new IdentityBinding(UUID.randomUUID(), UUID.randomUUID(), "Bob", identity.key(),
                identity.displayName(), "", Instant.EPOCH, Instant.EPOCH));
        command.execute(context, "bob question").value();
        bindings.binding = Optional.of(new IdentityBinding(UUID.randomUUID(), alice.playerId(), "Alice", identity.key(),
                identity.displayName(), "", Instant.EPOCH, Instant.EPOCH));
        command.execute(context, "rebound alice question").value();
        command.execute(context, "clear").value();
        var unavailableBindings = mock(IdentityBindingManager.class);
        when(unavailableBindings.find(identity.key())).thenThrow(IdentityBindingException.class);
        command(gateway, unavailableBindings, new StubLanguages(Optional.empty()))
                .execute(context, "lookup unavailable").value();

        var ids = gateway.requests.stream().map(AiRequest::conversationId).toList();
        assertEquals(ids.get(0), ids.get(1));
        assertEquals(5, ids.stream().distinct().count());
        assertEquals(List.of(ids.get(4)), gateway.cleared);
        assertEquals(Optional.empty(), gateway.requests.get(5).playerId());
        assertEquals(org.encinet.mik.feature.community.ai.api.AiInteractionContext.BindingStatus.UNAVAILABLE,
                gateway.requests.get(5).interaction().binding());
    }

    @Test
    void localizesExpectedFailuresAndSupportsConversationClear() {
        StubLanguages languages = new StubLanguages(Optional.empty());
        FakeGateway gateway = new FakeGateway();
        gateway.response = CompletableFuture.failedFuture(new AiRequestException(
                AiRequestException.Reason.PROMPT_TOO_LONG, "too long"));
        AiSocialCommand command = command(
                gateway, new StubBindings(Optional.empty()), languages);
        SocialCommandContext context = context(
                identity("user"), "event-1", "long", Language.ZH_CN);

        AiSocialCommand.Result rejected = command.execute(context, "long").value();
        SocialDocument rejection = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context, languages), rejected);

        assertInstanceOf(AiSocialCommand.Result.PromptTooLong.class, rejected);
        assertTrue(rejection.plainText().contains("AI_PROMPT_TOO_LONG[64]"));

        AiSocialCommand.Result cleared = command.execute(context, "清空").value();
        assertInstanceOf(AiSocialCommand.Result.Cleared.class, cleared);
        assertEquals(1, gateway.cleared.size());
    }

    private static AiSocialCommand command(
            FakeGateway gateway,
            IdentityBindingManager bindings,
            LanguageService languages
    ) {
        return new AiSocialCommand(gateway,
                new SocialCommandLanguageResolver(bindings, languages), languages,
                Logger.getAnonymousLogger(), List.of(AiSocialCommand.DEFINITION, SocialHelpCommand.DEFINITION));
    }

    private static SocialCommandContext context(
            ExternalIdentity identity,
            String eventId,
            String argument,
            Language language
    ) {
        SocialInboundMessage message = new SocialInboundMessage(
                eventId, new SocialConversation("shared-room", SocialConversation.Type.GROUP),
                Optional.of(identity), "/ai " + argument, false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        SocialCommandInput input = new SocialCommandInput(
                "ai", argument, SocialCommandInput.InvocationType.EXPLICIT_TEXT,
                Map.of(), Optional.of(language));
        return new SocialCommandContext(
                new SocialPlatformDescriptor("qq", "QQ", 0xFFC0CB), message, input,
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/"));
    }

    private static ExternalIdentity identity(String subject) {
        return new ExternalIdentity(new ExternalIdentityKey(
                "qq", "bot-app", "server", subject), subject);
    }

    private static final class FakeGateway implements AiGateway {
        private final List<AiRequest> requests = new ArrayList<>();
        private final List<String> cleared = new ArrayList<>();
        private CompletableFuture<String> response =
                CompletableFuture.completedFuture("model answer");

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public int maximumPromptCharacters() {
            return 64;
        }

        @Override
        public CompletableFuture<String> ask(AiRequest request) {
            requests.add(request);
            return response;
        }

        @Override
        public void clear(String conversationId) {
            cleared.add(conversationId);
        }
    }

    private static final class StubLanguages extends LanguageService {
        private final Optional<Language> preferred;

        private StubLanguages(Optional<Language> preferred) {
            super(null);
            this.preferred = preferred;
        }

        @Override
        public Optional<Language> preferredLanguage(UUID playerId) {
            return preferred;
        }

        @Override
        public String t(Language language, Message message, Object... args) {
            return message.name() + (args.length == 0 ? "" : Arrays.toString(args));
        }
    }

    private static final class StubBindings implements IdentityBindingManager {
        private Optional<IdentityBinding> binding;

        private StubBindings(Optional<IdentityBinding> binding) {
            this.binding = binding;
        }

        @Override
        public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdentityBinding> find(ExternalIdentityKey key) {
            return binding.filter(value -> value.externalKey().equals(key));
        }

        @Override
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key, java.util.UUID expectedBindingId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, org.encinet.mik.feature.community.identity.IdentityMemberships memberships) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return binding.filter(value -> value.playerId().equals(playerId))
                    .map(List::of).orElseGet(List::of);
        }
    }
}
