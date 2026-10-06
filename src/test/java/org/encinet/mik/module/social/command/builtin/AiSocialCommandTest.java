package org.encinet.mik.module.social.command.builtin;

import org.encinet.mik.module.ai.api.AiGateway;
import org.encinet.mik.module.ai.api.AiRequest;
import org.encinet.mik.module.ai.api.AiRequestException;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.identity.IdentityLinkResult;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.identity.IdentityPlatformRegistration;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.document.SocialDocument;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSocialCommandTest {

    @Test
    void boundIdentitySuppliesMinecraftContextAndPreferredLanguage() {
        ExternalIdentity identity = identity("alice-social");
        UUID playerId = UUID.randomUUID();
        IdentityBinding binding = new IdentityBinding(
                playerId, "Alice", identity.key(), identity.displayName(),
                Instant.EPOCH, Instant.EPOCH);
        StubLanguages languages = new StubLanguages(Optional.of(Language.JA_JP));
        FakeGateway gateway = new FakeGateway();
        AiSocialCommand command = command(
                gateway, new StubBindings(Optional.of(binding)), languages);

        AiSocialCommand.Result result = command.handle(
                context(identity, "event-1", "サーバーの状態は？", Language.EN_US),
                "サーバーの状態は？");
        SocialDocument document = command.present(
                context(identity, "event-1", "サーバーの状態は？", Language.EN_US), result);

        assertInstanceOf(AiSocialCommand.Result.Answer.class, result);
        AiRequest request = gateway.requests.getFirst();
        assertEquals("Alice", request.requesterName());
        assertEquals("ja_jp", request.language());
        assertEquals(Optional.of(playerId), request.playerId());
        assertEquals(List.of("model answer"), document.untrustedText());
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

        command.handle(context(first, "event-1", "one", Language.EN_US), "one");
        command.handle(context(first, "event-2", "two", Language.EN_US), "two");
        command.handle(context(second, "event-3", "three", Language.EN_US), "three");

        assertEquals(gateway.requests.get(0).conversationId(),
                gateway.requests.get(1).conversationId());
        assertNotEquals(gateway.requests.get(0).conversationId(),
                gateway.requests.get(2).conversationId());
        assertTrue(gateway.requests.getFirst().conversationId().startsWith("social:"));
        assertTrue(gateway.requests.getFirst().conversationId().length() < 64);
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

        AiSocialCommand.Result rejected = command.handle(context, "long");
        SocialDocument rejection = command.present(context, rejected);

        assertInstanceOf(AiSocialCommand.Result.PromptTooLong.class, rejected);
        assertTrue(rejection.plainText().contains("AI_PROMPT_TOO_LONG[64]"));

        AiSocialCommand.Result cleared = command.handle(context, "清空");
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
                Logger.getAnonymousLogger());
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
                ignored -> CompletableFuture.completedFuture(null));
        SocialCommandInput input = new SocialCommandInput(
                "ai", argument, SocialCommandInput.InvocationType.EXPLICIT_TEXT,
                Map.of(), Optional.of(language));
        return new SocialCommandContext(
                new SocialPlatformDescriptor("qq", "QQ"), message, input,
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
        private final Optional<IdentityBinding> binding;

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
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return binding.filter(value -> value.playerId().equals(playerId))
                    .map(List::of).orElseGet(List::of);
        }
    }
}
