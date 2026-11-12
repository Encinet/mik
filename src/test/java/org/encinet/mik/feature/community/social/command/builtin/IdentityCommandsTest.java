package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.community.identity.IdentityLinkResult;
import org.encinet.mik.feature.community.identity.IdentityPlatform;
import org.encinet.mik.feature.community.identity.IdentityPlatformRegistration;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityCommandsTest {
    private static final ExternalIdentityKey KEY =
            new ExternalIdentityKey("qq", "app", "group", "member");

    @Test
    void alreadyBoundHelpShowsTheAccountAndResolvedProfileInvocation() {
        var binding = binding();
        var bindings = new StubBindings(binding);
        var languages = FluentTestSupport.languages();
        var command = new LinkIdentityCommand(bindings, new SocialCommandLanguageResolver(bindings, languages), languages);
        var original = context(true, "matrix");
        var context = new SocialCommandContext(original.platform(), original.message(), original.input(),
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"));
        String reply = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context, languages), command.execute(context, "").value()).plainText();
        assertTrue(reply.contains("PlayerOne"), reply);
        assertTrue(reply.contains("!我的"), reply);
        assertTrue(!reply.contains("验证码"), reply);
        assertEquals(0, bindings.redeemCalls);
    }

    @Test
    void unboundAccountHasNoUnlinkConfirmationOrRemoval() {
        var languages = FluentTestSupport.languages();
        var bindings = mock(IdentityBindingManager.class);
        when(bindings.find(KEY)).thenReturn(Optional.empty());
        var command = new UnlinkIdentityCommand(bindings, new SocialCommandLanguageResolver(bindings, languages), languages, new UnlinkConfirmations());
        String reply = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(true), languages), command.execute(context(true),
                new UnlinkIdentityCommand.Request(Optional.empty())).value()).plainText();
        assertTrue(reply.contains("还没有绑定"), reply);
        org.mockito.Mockito.verify(bindings, org.mockito.Mockito.never()).unlink(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void localizedConfirmationParametersAreAcceptedInEveryLanguage() {
        var languages = FluentTestSupport.languages();
        var bindings = new StubBindings(binding());
        var command = new UnlinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages, new UnlinkConfirmations());
        for (var language : Language.values()) {
            String token = languages.t(language, Message.SOCIAL_UNLINK_CONFIRMATION);
            String complete = token + " confirmation-token";
            var parsed = command.definition().arguments().parser().apply(
                    org.encinet.mik.feature.community.social.command.SocialCommandInput.explicit("unbind", complete));
            var valid = assertInstanceOf(org.encinet.mik.feature.community.social.command.SocialArguments.Valid.class, parsed);
            assertEquals(Optional.of("confirmation-token"), ((UnlinkIdentityCommand.Request) valid.value()).token());
        }
    }

    @Test
    void linkCommandOwnsHelpRedemptionAndPresentation() {
        IdentityBinding binding = binding();
        StubBindings bindings = new StubBindings(binding);
        StubLanguageService languages = new StubLanguageService();
        LinkIdentityCommand command = new LinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);

        assertInstanceOf(LinkIdentityCommand.Result.Help.class,
                command.execute(context(true), "").value());
        LinkIdentityCommand.Result.Linked linked = assertInstanceOf(
                LinkIdentityCommand.Result.Linked.class,
                command.execute(context(true), "ABCDE-ABCDE").value());
        SocialDocument document = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(true), languages), linked);

        assertEquals("", document.title());
        assertEquals(REQUIRED, document.moderation());
        assertEquals(1, bindings.redeemCalls);
        assertInstanceOf(LinkIdentityCommand.Result.IdentityMissing.class,
                command.execute(context(false), "ABCDE-ABCDE").value());
    }

    @Test
    void unlinkCommandOwnsConfirmationRemovalAndPresentation() {
        IdentityBinding binding = binding();
        StubBindings bindings = new StubBindings(binding);
        StubLanguageService languages = new StubLanguageService();
        UnlinkIdentityCommand command = new UnlinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages, new UnlinkConfirmations());

        var prompt = assertInstanceOf(UnlinkIdentityCommand.Result.Prompt.class,
                command.execute(context(true), new UnlinkIdentityCommand.Request(Optional.empty())).value());
        UnlinkIdentityCommand.Result.Unlinked unlinked = assertInstanceOf(
                UnlinkIdentityCommand.Result.Unlinked.class,
                command.execute(context(true), new UnlinkIdentityCommand.Request(Optional.of(prompt.token()))).value());
        SocialDocument document = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(true), languages), unlinked);

        assertEquals("", document.title());
        assertEquals(REQUIRED, document.moderation());
        assertEquals(1, bindings.unlinkCalls);
    }

    @Test
    void linkInstructionsUseTheCurrentPlatformInsteadOfHardCodingQq() {
        StubBindings bindings = new StubBindings(binding());
        StubLanguageService languages = new StubLanguageService();
        LinkIdentityCommand command = new LinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);
        SocialCommandContext matrix = context(true, "matrix");

        command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(matrix, languages), new LinkIdentityCommand.Result.Help(Optional.empty()));
        command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(matrix, languages), new LinkIdentityCommand.Result.Linked(
                new IdentityLinkResult(
                        IdentityLinkResult.Status.INVALID_OR_EXPIRED_CODE, null, null),
                Optional.empty()));

        assertEquals(List.of("/bind matrix"),
                languages.arguments.get(Message.SOCIAL_BINDING_HELP));
        assertEquals(List.of("/bind matrix"),
                languages.arguments.get(Message.SOCIAL_BINDING_CODE_INVALID));
    }

    @Test
    void membershipFailuresExplainTheReasonInTheCommandLanguage() {
        var languages = FluentTestSupport.languages();
        var bindings = mock(IdentityBindingManager.class);
        when(bindings.find(KEY)).thenReturn(Optional.empty());
        var command = new LinkIdentityCommand(bindings,
                new SocialCommandLanguageResolver(bindings, languages), languages);
        for (var language : List.of(Language.EN_US, Language.ZH_CN)) {
            var original = context(true, "matrix");
            var context = new SocialCommandContext(original.platform(), original.message(), original.input(),
                    SocialCommandSyntax.caseInsensitive(language, "!"));
            for (var status : List.of(IdentityLinkResult.Status.MEMBERSHIP_REQUIRED,
                    IdentityLinkResult.Status.MEMBERSHIP_UNAVAILABLE)) {
                when(bindings.redeem("123456", context.message().authenticatedIdentity().orElseThrow()))
                        .thenReturn(new IdentityLinkResult(status, null, null));
                var document = command.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context, languages), command.execute(context, "123456").value());
                String expected = switch (status) {
                    case MEMBERSHIP_REQUIRED -> language == Language.EN_US ? "Join a connected community" : "请先加入";
                    case MEMBERSHIP_UNAVAILABLE -> language == Language.EN_US ? "membership cannot currently be verified" : "成员资格暂时无法核验";
                    default -> throw new AssertionError(status);
                };
                assertTrue(document.plainText().contains(expected), document.plainText());
                assertEquals(SocialDocument.Tone.WARNING, document.tone());
            }
        }
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
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        return new SocialCommandContext(
                new SocialPlatformDescriptor(platformId, platformId, 0x55FFFF), message);
    }

    private static IdentityBinding binding() {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        return new IdentityBinding(UUID.randomUUID(), UUID.randomUUID(), "PlayerOne", KEY,
                "QQ member", "", now, now);
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
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key, java.util.UUID expectedBindingId) {
            unlinkCalls++;
            return find(key);
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, org.encinet.mik.feature.community.identity.IdentityMemberships memberships) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return binding.playerId().equals(playerId) ? List.of(binding) : List.of();
        }
    }
}
