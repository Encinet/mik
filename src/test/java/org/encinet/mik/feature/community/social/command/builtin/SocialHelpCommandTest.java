package org.encinet.mik.feature.community.social.command.builtin;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
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
import org.encinet.mik.feature.community.social.command.NoArguments;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandInput;
import org.encinet.mik.feature.community.social.command.SocialCommandLanguageResolver;
import org.encinet.mik.feature.community.social.command.SocialCommandSpec;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialHelpCommandTest {
    @Test
    void actionHintsUseTheRegisteredAliasAndActualPrefixInTheResolvedLanguage() {
        var languages = org.encinet.mik.shared.i18n.FluentTestSupport.languages();
        var bindings = new EmptyBindings();
        var resolver = new SocialCommandLanguageResolver(bindings, languages);
        String unknown = new UnknownSocialCommand(resolver, languages).present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(), languages), NoArguments.INSTANCE).plainText();
        assertTrue(unknown.contains("!ヘルプ"), unknown);
        String unlink = new UnlinkIdentityCommand(bindings, resolver, languages, new UnlinkConfirmations())
                .present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(), languages), new UnlinkIdentityCommand.Result.Prompt(Optional.of(new IdentityBinding(
                        UUID.randomUUID(), UUID.randomUUID(), "Alice",
                        new ExternalIdentityKey("matrix", "example.org", "", "@alice:example.org"),
                        "Alice", "", java.time.Instant.EPOCH, java.time.Instant.EPOCH)), "confirmation-token")).plainText();
        assertTrue(unlink.contains("!解除 確認 confirmation-token"), unlink);
    }

    @Test
    void commandBuildsHelpDirectlyFromInstalledCommandMetadata() {
        StubLanguageService languages = new StubLanguageService();
        EmptyBindings bindings = new EmptyBindings();
        var online = describedCommand(
                SocialCommandSpec.localizedNatural("server.online",
                        SocialCommandSpec.aliases(Language.EN_US, "online"),
                        SocialCommandSpec.aliases(Language.JA_JP, "オンライン")),
                Message.SOCIAL_ONLINE_COMMAND_DESCRIPTION);
        SocialHelpCommand help = new SocialHelpCommand(
                new SocialCommandLanguageResolver(bindings, languages),
                languages, List.of(online, SocialHelpCommand.DEFINITION));

        SocialDocument document = help.present(org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.presentation(context(), languages), NoArguments.INSTANCE);
        SocialDocument.ItemList list = (SocialDocument.ItemList) document.blocks().getFirst();

        assertEquals("📖 SOCIAL_TITLE_COMMAND_HELP", document.title());
        assertEquals(TRUSTED, document.moderation());
        assertTrue(list.items().contains(
                "!オンライン — SOCIAL_ONLINE_COMMAND_DESCRIPTION"));
        assertTrue(list.items().contains("!ヘルプ — SOCIAL_HELP_COMMAND_DESCRIPTION"));
    }

    private static org.encinet.mik.feature.community.social.command.SocialCommandDefinition<NoArguments> describedCommand(
            SocialCommandSpec spec, Message description) {
        return org.encinet.mik.feature.community.social.command.SocialCommandDefinition.visible(spec, description,
                org.encinet.mik.feature.community.social.command.SocialArguments.none());
    }

    private static SocialCommandContext context() {
        SocialInboundMessage message = new SocialInboundMessage(
                "event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "!ヘルプ", false,
                org.encinet.mik.feature.community.social.SocialTestReplies::accepted);
        SocialCommandInput input = new SocialCommandInput(
                "ヘルプ", "", SocialCommandInput.InvocationType.EXPLICIT_TEXT,
                Map.of(), Optional.of(Language.JA_JP));
        return new SocialCommandContext(
                new SocialPlatformDescriptor("test", "Test", 0x55FFFF), message, input,
                SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "!"));
    }

    private static final class StubLanguageService extends LanguageService {
        private StubLanguageService() {
            super(null);
        }

        @Override
        public String t(Language language, Message message, Object... args) {
            return message.name();
        }
    }

    private static final class EmptyBindings implements IdentityBindingManager {
        @Override
        public IdentityLinkResult redeem(String code, ExternalIdentity identity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IdentityBinding> find(ExternalIdentityKey key) {
            return Optional.empty();
        }

        @Override
        public Optional<IdentityBinding> unlink(ExternalIdentityKey key, java.util.UUID expectedBindingId) {
            return Optional.empty();
        }

        @Override
        public IdentityPlatformRegistration registerPlatform(IdentityPlatform platform, org.encinet.mik.feature.community.identity.IdentityMemberships memberships) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IdentityBinding> findByPlayer(UUID playerId) {
            return List.of();
        }
    }
}
