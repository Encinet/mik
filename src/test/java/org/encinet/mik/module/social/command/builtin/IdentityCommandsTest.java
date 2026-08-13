package org.encinet.mik.module.social.command.builtin;

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
import org.encinet.mik.module.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.module.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IdentityCommandsTest {
    private static final ExternalIdentityKey KEY =
            new ExternalIdentityKey("qq", "app", "group", "member");

    @Test
    void linkCommandOwnsHelpRedemptionAndPresentation() {
        IdentityBinding binding = binding();
        StubBindings bindings = new StubBindings(binding);
        StubLanguageService languages = new StubLanguageService();
        LinkIdentityCommand command = new LinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);

        assertInstanceOf(LinkIdentityCommand.Result.Help.class,
                command.handle(context(true), ""));
        LinkIdentityCommand.Result.Linked linked = assertInstanceOf(
                LinkIdentityCommand.Result.Linked.class,
                command.handle(context(true), "ABCDE-ABCDE"));
        SocialDocument document = command.present(context(true), linked);

        assertEquals("🔗 SOCIAL_TITLE_IDENTITY_LINKING", document.title());
        assertEquals(binding.playerName(), document.untrustedPlainText());
        assertEquals(1, bindings.redeemCalls);
        assertInstanceOf(LinkIdentityCommand.Result.IdentityMissing.class,
                command.handle(context(false), "ABCDE-ABCDE"));
    }

    @Test
    void unlinkCommandOwnsConfirmationRemovalAndPresentation() {
        IdentityBinding binding = binding();
        StubBindings bindings = new StubBindings(binding);
        StubLanguageService languages = new StubLanguageService();
        UnlinkIdentityCommand command = new UnlinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);

        assertInstanceOf(UnlinkIdentityCommand.Result.Prompt.class,
                command.handle(context(true), new UnlinkIdentityCommand.Request(false)));
        UnlinkIdentityCommand.Result.Unlinked unlinked = assertInstanceOf(
                UnlinkIdentityCommand.Result.Unlinked.class,
                command.handle(context(true), new UnlinkIdentityCommand.Request(true)));
        SocialDocument document = command.present(context(true), unlinked);

        assertEquals("🔓 SOCIAL_TITLE_IDENTITY_UNLINKING", document.title());
        assertEquals(binding.playerName(), document.untrustedPlainText());
        assertEquals(1, bindings.unlinkCalls);
    }

    @Test
    void linkInstructionsUseTheCurrentPlatformInsteadOfHardCodingQq() {
        StubBindings bindings = new StubBindings(binding());
        StubLanguageService languages = new StubLanguageService();
        LinkIdentityCommand command = new LinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);
        SocialCommandContext matrix = context(true, "matrix");

        command.present(matrix, new LinkIdentityCommand.Result.Help(Optional.empty()));
        command.present(matrix, new LinkIdentityCommand.Result.Linked(
                new IdentityLinkResult(
                        IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE, null, null),
                Optional.empty()));

        assertEquals(List.of("matrix"),
                languages.arguments.get(Message.SOCIAL_BINDING_HELP));
        assertEquals(List.of("matrix"),
                languages.arguments.get(Message.SOCIAL_BINDING_CODE_INVALID));
    }

    private static SocialCommandContext context(boolean authenticated) {
        return context(authenticated, "qq");
    }

    private static SocialCommandContext context(boolean authenticated, String platformId) {
        Optional<ExternalIdentity> identity = authenticated
                ? Optional.of(new ExternalIdentity(KEY, "QQ member")) : Optional.empty();
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                identity, "/绑定", false,
                ignored -> CompletableFuture.completedFuture(null));
        return new SocialCommandContext(
                new SocialPlatformDescriptor(platformId, platformId), message);
    }

    private static IdentityBinding binding() {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        return new IdentityBinding(UUID.randomUUID(), "PlayerOne", KEY,
                "QQ member", now, now);
    }

    private static final class StubLanguageService extends LanguageService {
        private final Map<Message, List<Object>> arguments = new EnumMap<>(Message.class);

        private StubLanguageService() {
            super(null);
        }

        @Override
        public Optional<Language> preferredLanguage(UUID playerId) {
            return Optional.of(Language.ZH_CN);
        }

        @Override
        public String t(Language language, Message message, Object... args) {
            arguments.put(message, Arrays.asList(args));
            return message.name();
        }
    }

    private static final class StubBindings implements IdentityBindingManager {
        private final IdentityBinding binding;
        private int redeemCalls;
        private int unlinkCalls;

        private StubBindings(IdentityBinding binding) {
            this.binding = binding;
        }

        @Override
        public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
            redeemCalls++;
            return new IdentityLinkResult(IdentityLinkResult.Status.LINKED,
                    binding, null);
        }

        @Override
        public Optional<IdentityBinding> find(ExternalIdentityKey key) {
            return KEY.equals(key) ? Optional.of(binding) : Optional.empty();
        }

        @Override
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key) {
            unlinkCalls++;
            return find(key);
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return binding.playerId().equals(playerId) ? List.of(binding) : List.of();
        }
    }
}
